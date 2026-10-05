use serde::{Deserialize, Serialize};
use ts_rs::TS;

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct Me {
    pub user_id: String,
    /// Anonymous workspaces have no login name.
    pub login: Option<String>,
    pub anonymous: bool,
    pub csrf_token: String,
    pub linked_devices: Vec<LinkedDevice>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct LinkedDevice {
    pub id: String,
    pub name: String,
    #[ts(type = "number")]
    pub created_at_ms: i64,
    #[ts(type = "number | null")]
    pub last_used_at_ms: Option<i64>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct Credentials {
    pub login: String,
    pub password: String,
}

/// RFC 8628 style device authorization response (mod -> backend).
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct DeviceCodeResponse {
    pub device_code: String,
    pub user_code: String,
    pub verification_uri: String,
    pub verification_uri_complete: String,
    pub expires_in: u32,
    pub interval: u32,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct DeviceCodeRequest {
    pub device_name: String,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct DeviceTokenRequest {
    pub device_code: String,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct DeviceTokenResponse {
    /// "authorization_pending" | "slow_down" | "expired_token" | "access_denied" | "ok"
    pub status: String,
    pub access_token: Option<String>,
    pub scope: Option<String>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct DeviceApproveRequest {
    pub user_code: String,
    pub approve: bool,
}
