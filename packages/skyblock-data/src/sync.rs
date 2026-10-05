//! Downloads upstream data into `data/cache`:
//! * Hypixel resources (items, skills, collections, election) - keyless endpoints.
//! * NEU-REPO master tarball (MIT) extracted to `data/cache/neu`.
//!
//! Called by the API at startup when the cache is missing/stale and by `skypilot-api sync-data`.

use std::path::Path;
use std::time::Duration;

use anyhow::{Context, bail};

use crate::DataPaths;

pub const HYPIXEL_BASE: &str = "https://api.hypixel.net/v2";
pub const NEU_TARBALL: &str = "https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO/archive/refs/heads/master.tar.gz";

fn client() -> anyhow::Result<reqwest::Client> {
    Ok(reqwest::Client::builder()
        .user_agent(concat!("SkyPilot/", env!("CARGO_PKG_VERSION"), " (+https://github.com/skypilot-sb/skypilot)"))
        .timeout(Duration::from_secs(120))
        .build()?)
}

/// Fetch keyless Hypixel resources. Existing files are replaced atomically.
pub async fn sync_hypixel_resources(paths: &DataPaths) -> anyhow::Result<()> {
    let c = client()?;
    tokio::fs::create_dir_all(&paths.hypixel_dir).await?;
    for (endpoint, file) in [
        ("resources/skyblock/items", "resources_items.json"),
        ("resources/skyblock/skills", "resources_skills.json"),
        ("resources/skyblock/collections", "resources_collections.json"),
        ("resources/skyblock/election", "resources_election.json"),
    ] {
        let url = format!("{HYPIXEL_BASE}/{endpoint}");
        let resp = c.get(&url).send().await.with_context(|| format!("GET {url}"))?;
        if !resp.status().is_success() {
            bail!("GET {url} -> {}", resp.status());
        }
        let bytes = resp.bytes().await?;
        let v: serde_json::Value = serde_json::from_slice(&bytes).with_context(|| format!("invalid JSON from {url}"))?;
        if v.get("success").and_then(|s| s.as_bool()) != Some(true) {
            bail!("{url} returned success=false");
        }
        let tmp = paths.hypixel_dir.join(format!("{file}.tmp"));
        tokio::fs::write(&tmp, &bytes).await?;
        tokio::fs::rename(&tmp, paths.hypixel_dir.join(file)).await?;
        // a gz fixture must not shadow fresh data
        let _ = tokio::fs::remove_file(paths.hypixel_dir.join(format!("{file}.gz"))).await;
    }
    Ok(())
}

/// Download and extract the NEU-REPO tarball (only `constants/`, `items/`, `LICENSE`).
pub async fn sync_neu(paths: &DataPaths) -> anyhow::Result<()> {
    let c = client()?;
    let resp = c.get(NEU_TARBALL).send().await.context("downloading NEU-REPO")?;
    if !resp.status().is_success() {
        bail!("NEU-REPO download failed: {}", resp.status());
    }
    let bytes = resp.bytes().await?;
    let target = paths.neu_dir.clone();
    tokio::task::spawn_blocking(move || extract_neu(&bytes, &target)).await??;
    Ok(())
}

fn extract_neu(bytes: &[u8], target: &Path) -> anyhow::Result<()> {
    let staging = target.with_extension("staging");
    if staging.exists() {
        std::fs::remove_dir_all(&staging)?;
    }
    std::fs::create_dir_all(&staging)?;
    let mut archive = tar::Archive::new(flate2::read::GzDecoder::new(bytes));
    let mut files = 0usize;
    for entry in archive.entries()? {
        let mut entry = entry?;
        let path = entry.path()?.into_owned();
        // strip the top-level "NotEnoughUpdates-REPO-master/" component
        let mut comps = path.components();
        comps.next();
        let rel: std::path::PathBuf = comps.collect();
        let first = rel.components().next().map(|c| c.as_os_str().to_string_lossy().to_string()).unwrap_or_default();
        let keep = matches!(first.as_str(), "constants" | "items") || rel.as_os_str() == "LICENSE";
        if !keep || rel.as_os_str().is_empty() {
            continue;
        }
        // reject path traversal
        if rel.components().any(|c| matches!(c, std::path::Component::ParentDir | std::path::Component::RootDir | std::path::Component::Prefix(_))) {
            continue;
        }
        let out = staging.join(&rel);
        if entry.header().entry_type().is_dir() {
            std::fs::create_dir_all(&out)?;
            continue;
        }
        if let Some(parent) = out.parent() {
            std::fs::create_dir_all(parent)?;
        }
        entry.unpack(&out)?;
        files += 1;
    }
    if files < 100 {
        bail!("NEU-REPO archive looked incomplete ({files} files)");
    }
    if target.exists() {
        std::fs::remove_dir_all(target)?;
    }
    std::fs::rename(&staging, target)?;
    tracing::info!(files, "NEU-REPO extracted");
    Ok(())
}

/// True when the cache is missing or older than `max_age`.
pub fn needs_sync(paths: &DataPaths, max_age: Duration) -> bool {
    let probe = paths.hypixel_dir.join("resources_items.json");
    let neu_probe = paths.neu_dir.join("constants/leveling.json");
    let fresh = |p: &Path| {
        p.metadata()
            .and_then(|m| m.modified())
            .ok()
            .and_then(|t| t.elapsed().ok())
            .map(|age| age < max_age)
            .unwrap_or(false)
    };
    !(fresh(&probe) && neu_probe.exists())
}
