//! Hypixel's public status page (status.hypixel.net, a Statuspage site): whether SkyBlock is up,
//! maintenance in progress or scheduled, and open incidents. Read-only public JSON, no key, cached
//! two minutes; a 429 or an error keeps serving the last answer, tried again after 30 seconds at the
//! earliest.

use std::sync::{Arc, Mutex, OnceLock};
use std::time::Duration;

use axum::Json;
use axum::extract::State;
use serde_json::Value;
use skypilot_protocol::mod_api::{ModIncident, ModMaintenance, ModServerStatus, ModServiceStatus};

use crate::error::ApiResult;
use crate::state::AppState;

pub const PAGE_URL: &str = "https://status.hypixel.net";
const SUMMARY: &str = "https://status.hypixel.net/api/v2/summary.json";
const MAINTENANCES: &str = "https://status.hypixel.net/api/v2/scheduled-maintenances.json";
const TTL_MS: i64 = 120_000;
const SKYBLOCK: &str = "SkyBlock";
/// Services shown even when operational (in this order); others only when something is wrong.
const MAIN_SERVICES: [&str; 4] = [SKYBLOCK, "Hypixel Minecraft Server", "Minecraft Server Authentication", "Public API"];

/// The maintenance history only feeds "recent maintenance"; it is read at most every 30 minutes,
/// in the background, because that endpoint can be slow.
const HISTORY_EVERY_MS: i64 = 1_800_000;

struct Cached {
    at: i64,
    tried_at: i64,
    status: Option<ModServerStatus>,
    history: Value,
    history_tried_at: i64,
}

fn cache() -> &'static Mutex<Cached> {
    static CACHE: OnceLock<Mutex<Cached>> = OnceLock::new();
    CACHE.get_or_init(|| Mutex::new(Cached { at: 0, tried_at: 0, status: None, history: Value::Null, history_tried_at: 0 }))
}

pub async fn server_status(State(_state): State<Arc<AppState>>) -> ApiResult<Json<ModServerStatus>> {
    let now = AppState::now_ms();
    {
        let mut c = cache().lock().expect("status cache");
        if now - c.history_tried_at > HISTORY_EVERY_MS {
            c.history_tried_at = now;
            tokio::spawn(async {
                match get(MAINTENANCES).await {
                    Ok(v) => cache().lock().expect("status cache").history = v,
                    Err(e) => tracing::warn!(error = %e, "Hypixel maintenance history not refreshed"),
                }
            });
        }
        // fresh, or a failed try less than 30 s ago: answer from the cache
        if now - c.at < TTL_MS || now - c.tried_at < 30_000 {
            return Ok(Json(c.status.clone().unwrap_or_else(|| unavailable(now))));
        }
        c.tried_at = now;
    }
    match get(SUMMARY).await {
        Ok(summary) => {
            let mut c = cache().lock().expect("status cache");
            let status = parse(&summary, &c.history, now);
            c.at = now;
            c.status = Some(status.clone());
            Ok(Json(status))
        }
        Err(e) => {
            tracing::warn!(error = %e, "Hypixel status page not refreshed");
            Ok(Json(cache().lock().expect("status cache").status.clone().unwrap_or_else(|| unavailable(now))))
        }
    }
}

async fn get(url: &'static str) -> anyhow::Result<Value> {
    let client = reqwest::Client::builder()
        .timeout(Duration::from_secs(20))
        .redirect(reqwest::redirect::Policy::none())
        .user_agent(concat!("SkyPilot-server/", env!("CARGO_PKG_VERSION"), " (self-hosted SkyBlock companion; status display)"))
        .build()?;
    let resp = client.get(url).send().await?;
    anyhow::ensure!(resp.status().is_success(), "status page answered {}", resp.status());
    let bytes = resp.bytes().await?;
    anyhow::ensure!(bytes.len() <= 4 << 20, "status page answer too large");
    Ok(serde_json::from_slice(&bytes)?)
}

fn unavailable(now: i64) -> ModServerStatus {
    ModServerStatus {
        available: false,
        indicator: "unknown".into(),
        description: "Not available".into(),
        services: vec![],
        maintenances: vec![],
        incidents: vec![],
        recent_maintenances: vec![],
        checked_at_ms: now,
        page_url: PAGE_URL.into(),
        source: "status.hypixel.net".into(),
    }
}

fn s<'a>(v: &'a Value, key: &str) -> &'a str {
    v.get(key).and_then(Value::as_str).unwrap_or("")
}

fn time_ms(v: &Value, key: &str) -> Option<i64> {
    chrono::DateTime::parse_from_rfc3339(v.get(key)?.as_str()?).ok().map(|t| t.timestamp_millis())
}

fn short(text: &str, max: usize) -> String {
    let t = text.trim();
    if t.chars().count() <= max { t.to_string() } else { format!("{}…", t.chars().take(max - 1).collect::<String>()) }
}

/// Ids are Statuspage's lowercase base-36 ids; anything else gives no link.
fn incident_link(id: &str) -> String {
    if !id.is_empty() && id.len() <= 32 && id.chars().all(|c| c.is_ascii_alphanumeric()) { format!("{PAGE_URL}/incidents/{id}") } else { PAGE_URL.to_string() }
}

fn services_of(v: &Value) -> Vec<String> {
    v.get("components").and_then(Value::as_array).map(|cs| cs.iter().map(|c| short(s(c, "name"), 60)).filter(|n| !n.is_empty()).collect()).unwrap_or_default()
}

/// The newest update's text (Statuspage lists updates newest first).
fn latest_update(v: &Value) -> Option<String> {
    let body = v.get("incident_updates")?.as_array()?.first()?.get("body")?.as_str()?;
    (!body.trim().is_empty()).then(|| short(body, 300))
}

/// The update that explains the maintenance: the oldest one ("SkyBlock will be in maintenance to
/// roll v0.27.2"), unless a newer one says more than Statuspage's stock texts.
fn maintenance_update(v: &Value) -> Option<String> {
    let updates = v.get("incident_updates")?.as_array()?;
    let stock = |b: &str| b.starts_with("Scheduled maintenance is") || b.starts_with("Maintenance will begin") || b.starts_with("The scheduled maintenance has been completed");
    updates.iter().filter_map(|u| u.get("body").and_then(Value::as_str)).find(|b| !b.trim().is_empty() && !stock(b)).map(|b| short(b, 300)).or_else(|| latest_update(v))
}

fn maintenance(v: &Value) -> ModMaintenance {
    let services = services_of(v);
    ModMaintenance {
        id: short(s(v, "id"), 32),
        name: short(s(v, "name"), 120),
        status: short(s(v, "status"), 20),
        starts_ms: time_ms(v, "scheduled_for"),
        ends_ms: time_ms(v, "scheduled_until"),
        skyblock: services.iter().any(|n| n == SKYBLOCK),
        services,
        update: maintenance_update(v),
        link: incident_link(s(v, "id")),
    }
}

pub fn parse(summary: &Value, history: &Value, now: i64) -> ModServerStatus {
    let components: Vec<&Value> = summary.get("components").and_then(Value::as_array).map(|a| a.iter().filter(|c| c.get("group").and_then(Value::as_bool) != Some(true)).collect()).unwrap_or_default();
    let mut services: Vec<ModServiceStatus> = MAIN_SERVICES
        .iter()
        .filter_map(|name| components.iter().find(|c| s(c, "name") == *name))
        .map(|c| ModServiceStatus { name: s(c, "name").to_string(), status: short(s(c, "status"), 30) })
        .collect();
    for c in &components {
        let name = s(c, "name");
        if !MAIN_SERVICES.contains(&name) && s(c, "status") != "operational" && !name.is_empty() {
            services.push(ModServiceStatus { name: short(name, 60), status: short(s(c, "status"), 30) });
        }
    }
    let mut maintenances: Vec<ModMaintenance> =
        summary.get("scheduled_maintenances").and_then(Value::as_array).map(|a| a.iter().map(maintenance).filter(|m| m.status != "completed").collect()).unwrap_or_default();
    // SkyBlock first, then by start
    maintenances.sort_by_key(|m| (!m.skyblock, m.starts_ms.unwrap_or(i64::MAX)));
    let incidents = summary
        .get("incidents")
        .and_then(Value::as_array)
        .map(|a| {
            a.iter()
                .filter(|i| !matches!(s(i, "status"), "resolved" | "postmortem"))
                .map(|i| {
                    let services = services_of(i);
                    ModIncident {
                        id: short(s(i, "id"), 32),
                        name: short(s(i, "name"), 120),
                        status: short(s(i, "status"), 20),
                        impact: short(s(i, "impact"), 20),
                        started_ms: time_ms(i, "started_at").or_else(|| time_ms(i, "created_at")),
                        skyblock: services.iter().any(|n| n == SKYBLOCK),
                        services,
                        update: latest_update(i),
                        link: incident_link(s(i, "id")),
                    }
                })
                .collect()
        })
        .unwrap_or_default();
    let mut recent: Vec<ModMaintenance> = history
        .get("scheduled_maintenances")
        .and_then(Value::as_array)
        .map(|a| a.iter().map(maintenance).filter(|m| m.skyblock && m.status == "completed" && m.ends_ms.is_some_and(|e| e <= now)).collect())
        .unwrap_or_default();
    recent.sort_by_key(|m| std::cmp::Reverse(m.starts_ms));
    recent.truncate(3);
    let status = summary.get("status");
    ModServerStatus {
        available: !components.is_empty(),
        indicator: status.map(|st| short(s(st, "indicator"), 20)).unwrap_or_default(),
        description: status.map(|st| short(s(st, "description"), 80)).unwrap_or_default(),
        services,
        maintenances,
        incidents,
        recent_maintenances: recent,
        checked_at_ms: now,
        page_url: PAGE_URL.into(),
        source: "status.hypixel.net".into(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    fn summary() -> Value {
        json!({
            "page": {"id": "qhddg0t7xyb0", "name": "Hypixel"},
            "status": {"indicator": "maintenance", "description": "Service Under Maintenance"},
            "components": [
                {"id": "w1rfw20svcd4", "name": "Minecraft Server Games", "status": "under_maintenance", "group": true},
                {"id": "jngfwszr7mcj", "name": "SkyBlock", "status": "under_maintenance", "group": false},
                {"id": "5c8y9qmvhy5r", "name": "Minecraft Server Authentication", "status": "operational", "group": false},
                {"id": "lf5ms8bhx9m4", "name": "Hypixel Minecraft Server", "status": "operational", "group": false},
                {"id": "3jkdfwmt3mh2", "name": "Bed Wars", "status": "operational", "group": false},
                {"id": "200q0q2bh1xk", "name": "Forums", "status": "partial_outage", "group": false}
            ],
            "incidents": [
                {"id": "abc123", "name": "Forums slow", "status": "investigating", "impact": "minor", "created_at": "2026-10-06T09:00:00.000-04:00",
                 "incident_updates": [{"status": "investigating", "body": "We are looking into it."}], "components": [{"name": "Forums"}]},
                {"id": "old1", "name": "Old", "status": "resolved", "impact": "minor", "incident_updates": [], "components": []}
            ],
            "scheduled_maintenances": [
                {"id": "zzz999", "name": "Forums Maintenance", "status": "scheduled", "scheduled_for": "2026-10-08T19:00:00.000-04:00", "scheduled_until": "2026-10-09T07:00:00.000-04:00",
                 "incident_updates": [{"body": "Forums upgrade."}], "components": [{"name": "Forums"}]},
                {"id": "28dg8187c048", "name": "SkyBlock Maintenance", "status": "in_progress",
                 "scheduled_for": "2026-10-06T08:00:00.000-04:00", "scheduled_until": "2026-10-06T14:00:00.000-04:00",
                 "incident_updates": [
                    {"status": "in_progress", "body": "Scheduled maintenance is currently in progress. We will provide updates as necessary."},
                    {"status": "scheduled", "body": "Maintenance will begin as scheduled in 60 minutes."},
                    {"status": "scheduled", "body": "SkyBlock will be in maintenance to roll v0.27.2"}
                 ],
                 "components": [{"name": "SkyBlock"}]}
            ]
        })
    }

    fn history() -> Value {
        json!({"scheduled_maintenances": [
            {"id": "28dg8187c048", "name": "SkyBlock Maintenance", "status": "in_progress", "scheduled_for": "2026-10-06T08:00:00.000-04:00", "scheduled_until": "2026-10-06T14:00:00.000-04:00", "incident_updates": [], "components": [{"name": "SkyBlock"}]},
            {"id": "a1", "name": "Forums Maintenance", "status": "completed", "scheduled_for": "2026-08-19T19:00:00.000-04:00", "scheduled_until": "2026-08-20T07:00:00.000-04:00", "incident_updates": [], "components": [{"name": "Forums"}]},
            {"id": "a2", "name": "SkyBlock Maintenance", "status": "completed", "scheduled_for": "2026-08-04T08:00:00.000-04:00", "scheduled_until": "2026-08-04T14:00:00.000-04:00",
             "incident_updates": [{"body": "The scheduled maintenance has been completed."}, {"body": "SkyBlock will be in maintenance to roll v0.27.1"}], "components": [{"name": "SkyBlock"}]},
            {"id": "a3", "name": "SkyBlock Maintenance", "status": "completed", "scheduled_for": "2026-05-19T05:00:00.000-04:00", "scheduled_until": "2026-05-19T14:00:00.000-04:00", "incident_updates": [], "components": [{"name": "SkyBlock"}]}
        ]})
    }

    #[test]
    fn reads_services_maintenance_and_incidents() {
        let now = chrono::DateTime::parse_from_rfc3339("2026-10-06T10:00:00-04:00").unwrap().timestamp_millis();
        let st = parse(&summary(), &history(), now);
        assert!(st.available);
        assert_eq!(st.indicator, "maintenance");
        // main services first in fixed order, groups left out, other services only when not operational
        let names: Vec<&str> = st.services.iter().map(|x| x.name.as_str()).collect();
        assert_eq!(names, ["SkyBlock", "Hypixel Minecraft Server", "Minecraft Server Authentication", "Forums"]);
        assert_eq!(st.services[0].status, "under_maintenance");
        // SkyBlock maintenance first; its explanation is the version line, not the stock text
        let m = &st.maintenances[0];
        assert!(m.skyblock);
        assert_eq!(m.status, "in_progress");
        assert_eq!(m.update.as_deref(), Some("SkyBlock will be in maintenance to roll v0.27.2"));
        assert_eq!(m.starts_ms, Some(chrono::DateTime::parse_from_rfc3339("2026-10-06T12:00:00Z").unwrap().timestamp_millis()));
        assert_eq!(m.ends_ms.unwrap() - m.starts_ms.unwrap(), 6 * 3_600_000);
        assert_eq!(m.link, "https://status.hypixel.net/incidents/28dg8187c048");
        assert_eq!(st.maintenances[1].name, "Forums Maintenance");
        // resolved incidents are left out
        assert_eq!(st.incidents.len(), 1);
        assert_eq!(st.incidents[0].update.as_deref(), Some("We are looking into it."));
        // recent: finished SkyBlock maintenance only, newest first
        let recent: Vec<&str> = st.recent_maintenances.iter().map(|x| x.id.as_str()).collect();
        assert_eq!(recent, ["a2", "a3"]);
        assert_eq!(st.recent_maintenances[0].update.as_deref(), Some("SkyBlock will be in maintenance to roll v0.27.1"));
    }

    #[test]
    fn odd_input_is_safe() {
        let st = parse(&Value::Null, &Value::Null, 0);
        assert!(!st.available);
        assert!(st.services.is_empty() && st.maintenances.is_empty());
        assert_eq!(incident_link("../x"), PAGE_URL);
        assert_eq!(short(&"a".repeat(400), 300).chars().count(), 300);
    }
}
