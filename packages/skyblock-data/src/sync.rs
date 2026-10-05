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

pub const SKYHANNI_RAW: &str = "https://raw.githubusercontent.com/hannibal002/SkyHanni-REPO/main";

/// Download SkyHanni-REPO island graphs (MIT) and keep only their named public places
/// (see [`crate::locations`]). Islands that fail to download keep their previous file.
pub async fn sync_skyhanni(paths: &DataPaths) -> anyhow::Result<()> {
    let c = client()?;
    let dir = paths.skyhanni_dir.join("island_graphs");
    tokio::fs::create_dir_all(&dir).await?;
    let mut ok = 0;
    for island in crate::locations::ISLANDS {
        let url = format!("{SKYHANNI_RAW}/constants/island_graphs/{island}.json");
        let result: anyhow::Result<()> = async {
            let resp = c.get(&url).send().await?;
            if !resp.status().is_success() {
                bail!("{}", resp.status());
            }
            let graph: serde_json::Value = serde_json::from_slice(&resp.bytes().await?)?;
            // keep the original node shape so the loader reads synced files and fixtures alike
            let mut named = serde_json::Map::new();
            if let Some(obj) = graph.as_object() {
                for (id, node) in obj {
                    if node.get("Name").and_then(|n| n.as_str()).is_some_and(|n| !n.is_empty()) {
                        let mut n = node.clone();
                        if let Some(o) = n.as_object_mut() {
                            o.remove("Neighbours");
                        }
                        named.insert(id.clone(), n);
                    }
                }
            }
            let tmp = dir.join(format!("{island}.json.tmp"));
            tokio::fs::write(&tmp, serde_json::to_vec(&named)?).await?;
            tokio::fs::rename(&tmp, dir.join(format!("{island}.json"))).await?;
            Ok(())
        }
        .await;
        match result {
            Ok(()) => ok += 1,
            Err(e) => tracing::warn!(island, error = %e, "SkyHanni island graph not updated"),
        }
    }
    if let Ok(resp) = c.get(format!("{SKYHANNI_RAW}/LICENSE")).send().await {
        if let Ok(text) = resp.text().await {
            let _ = tokio::fs::write(paths.skyhanni_dir.join("LICENSE"), text).await;
        }
    }
    if ok == 0 {
        bail!("no SkyHanni island graph could be downloaded");
    }
    tracing::info!(islands = ok, "SkyHanni island graphs synced");
    Ok(())
}

/// True when the SkyHanni location data has never been downloaded.
pub fn needs_skyhanni(paths: &DataPaths) -> bool {
    !paths.skyhanni_dir.join("island_graphs/HUB.json").exists()
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
