use std::collections::HashMap;
use std::sync::Arc;

use axum::Json;
use axum::extract::State;
use serde::{Deserialize, Serialize};
use serde_json::{Value, json};

use crate::error::{ApiResult, AppError};
use crate::state::AppState;

const ALLOWED_SLOTS: [&str; 6] = ["cape", "back", "hat", "aura", "pet", "companion"];

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SlotTransformDto {
    #[serde(default)]
    pub offset_x: f64,
    #[serde(default)]
    pub offset_y: f64,
    #[serde(default)]
    pub offset_z: f64,
    #[serde(default = "default_one")]
    pub scale: f64,
    #[serde(default)]
    pub rot_x: f64,
    #[serde(default)]
    pub rot_y: f64,
    #[serde(default)]
    pub rot_z: f64,
}

fn default_one() -> f64 {
    1.0
}

fn default_true() -> bool {
    true
}

fn default_none() -> String {
    "none".to_string()
}

impl SlotTransformDto {
    fn sanitized(mut self) -> Option<Self> {
        self.offset_x = clamp_finite(self.offset_x, -8.0, 8.0, 0.0);
        self.offset_y = clamp_finite(self.offset_y, -8.0, 8.0, 0.0);
        self.offset_z = clamp_finite(self.offset_z, -8.0, 8.0, 0.0);
        self.scale = clamp_finite(if self.scale <= 0.0 { 1.0 } else { self.scale }, 0.4, 2.0, 1.0);
        self.rot_x = clamp_finite(self.rot_x, -60.0, 60.0, 0.0);
        self.rot_y = clamp_finite(self.rot_y, -180.0, 180.0, 0.0);
        self.rot_z = clamp_finite(self.rot_z, -60.0, 60.0, 0.0);
        let identity = self.offset_x.abs() < 1e-4
            && self.offset_y.abs() < 1e-4
            && self.offset_z.abs() < 1e-4
            && (self.scale - 1.0).abs() < 1e-4
            && self.rot_x.abs() < 1e-4
            && self.rot_y.abs() < 1e-4
            && self.rot_z.abs() < 1e-4;
        if identity { None } else { Some(self) }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct WardrobeProfile {
    #[serde(default = "default_none")]
    pub cape: String,
    #[serde(default = "default_none")]
    pub back: String,
    #[serde(default = "default_none")]
    pub hat: String,
    #[serde(default = "default_none")]
    pub aura: String,
    #[serde(default = "default_none")]
    pub pet: String,
    #[serde(default = "default_none")]
    pub companion: String,
    #[serde(default = "default_one")]
    pub wing_scale: f64,
    #[serde(default = "default_true")]
    pub cosmetic_glow: bool,
    #[serde(default = "default_true")]
    pub cosmetic_particles: bool,
    #[serde(default)]
    pub slot_transforms: HashMap<String, SlotTransformDto>,
    #[serde(default)]
    pub updated_at_ms: i64,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SyncRequest {
    pub uuid: String,
    #[serde(default = "default_none")]
    pub cape: String,
    #[serde(default = "default_none")]
    pub back: String,
    #[serde(default = "default_none")]
    pub hat: String,
    #[serde(default = "default_none")]
    pub aura: String,
    #[serde(default = "default_none")]
    pub pet: String,
    #[serde(default = "default_none")]
    pub companion: String,
    #[serde(default = "default_one")]
    pub wing_scale: f64,
    #[serde(default = "default_true")]
    pub cosmetic_glow: bool,
    #[serde(default = "default_true")]
    pub cosmetic_particles: bool,
    #[serde(default)]
    pub slot_transforms: HashMap<String, SlotTransformDto>,
}

#[derive(Debug, Deserialize)]
pub struct BatchRequest {
    pub uuids: Vec<String>,
}

#[derive(Debug, Deserialize)]
pub struct LeaveRequest {
    pub uuid: String,
}

fn normalize_uuid(raw: &str) -> Option<String> {
    let clean: String = raw
        .trim()
        .chars()
        .filter(|c| *c != '-')
        .map(|c| c.to_ascii_lowercase())
        .collect();
    if clean.len() == 32 && clean.bytes().all(|b| b.is_ascii_hexdigit()) {
        Some(clean)
    } else {
        None
    }
}

fn sanitize_cosmetic_id(raw: &str) -> String {
    let s = raw.trim();
    if s.is_empty() || s.len() > 64 {
        return "none".to_string();
    }
    if s.bytes().all(|b| b.is_ascii_lowercase() || b.is_ascii_digit() || b == b'_') {
        s.to_string()
    } else {
        "none".to_string()
    }
}

fn clamp_finite(v: f64, lo: f64, hi: f64, fallback: f64) -> f64 {
    if v.is_finite() { v.clamp(lo, hi) } else { fallback }
}

/// POST /api/wardrobe/sync — publish a player's equipped cosmetics & per-slot transforms.
pub async fn sync(State(state): State<Arc<AppState>>, Json(req): Json<SyncRequest>) -> ApiResult<Json<Value>> {
    let uuid = normalize_uuid(&req.uuid).ok_or_else(|| AppError::bad_request("invalid Minecraft UUID"))?;

    let mut clean_transforms = HashMap::new();
    for (slot, tr) in req.slot_transforms {
        if ALLOWED_SLOTS.contains(&slot.as_str()) {
            if let Some(clean) = tr.sanitized() {
                clean_transforms.insert(slot, clean);
            }
        }
    }

    let profile = WardrobeProfile {
        cape: sanitize_cosmetic_id(&req.cape),
        back: sanitize_cosmetic_id(&req.back),
        hat: sanitize_cosmetic_id(&req.hat),
        aura: sanitize_cosmetic_id(&req.aura),
        pet: sanitize_cosmetic_id(&req.pet),
        companion: sanitize_cosmetic_id(&req.companion),
        wing_scale: clamp_finite(if req.wing_scale <= 0.0 { 1.0 } else { req.wing_scale }, 0.6, 1.5, 1.0),
        cosmetic_glow: req.cosmetic_glow,
        cosmetic_particles: req.cosmetic_particles,
        slot_transforms: clean_transforms,
        updated_at_ms: AppState::now_ms(),
    };

    state.wardrobe.insert(uuid, profile).await;
    Ok(Json(json!({ "ok": true, "online": state.wardrobe.entry_count() })))
}

/// POST /api/wardrobe/batch — fetch active Wardrobe profiles for up to 100 nearby players.
pub async fn batch(State(state): State<Arc<AppState>>, Json(req): Json<BatchRequest>) -> ApiResult<Json<Value>> {
    let mut players: HashMap<String, WardrobeProfile> = HashMap::new();
    for raw in req.uuids.iter().take(100) {
        if let Some(uuid) = normalize_uuid(raw) {
            if let Some(profile) = state.wardrobe.get(&uuid).await {
                players.insert(uuid, profile);
            }
        }
    }
    Ok(Json(json!({
        "players": players,
        "online": state.wardrobe.entry_count()
    })))
}

/// POST /api/wardrobe/leave — remove player's live wardrobe presence on disconnect.
pub async fn leave(State(state): State<Arc<AppState>>, Json(req): Json<LeaveRequest>) -> ApiResult<Json<Value>> {
    if let Some(uuid) = normalize_uuid(&req.uuid) {
        state.wardrobe.invalidate(&uuid).await;
    }
    Ok(Json(json!({ "ok": true })))
}

/// GET /api/wardrobe/stats — lightweight status check showing active synced players.
pub async fn stats(State(state): State<Arc<AppState>>) -> Json<Value> {
    Json(json!({
        "ok": true,
        "online": state.wardrobe.entry_count()
    }))
}

