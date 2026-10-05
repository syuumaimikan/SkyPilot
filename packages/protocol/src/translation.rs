use serde::{Deserialize, Serialize};
use ts_rs::TS;

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct TranslateRequest {
    /// Text with protected tokens already replaced by `⟦0⟧`, `⟦1⟧` placeholders by the client.
    pub text: String,
    #[serde(default = "default_source")]
    pub source_lang: String,
    #[serde(default = "default_target")]
    pub target_lang: String,
    /// "PARTY" | "GUILD" | "PRIVATE" | "PLAYER" | "NPC" | "SYSTEM"
    pub channel: Option<String>,
}

fn default_source() -> String {
    "en".into()
}
fn default_target() -> String {
    "ja".into()
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct TranslateResponse {
    pub translated: String,
    /// "dictionary" | "cache" | provider name | "passthrough"
    pub provider: String,
    pub cached: bool,
    /// True when no translation was necessary/possible and the text is returned unchanged.
    pub unchanged: bool,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct DictionaryEntry {
    pub term: String,
    /// Some(translation) for phrase entries, None for protected (never translated) terms.
    pub translation: Option<String>,
    pub category: String,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct TranslationDictionary {
    pub version: String,
    pub protected_terms: Vec<String>,
    pub phrases: Vec<DictionaryEntry>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(rename_all = "camelCase")]
#[ts(export)]
pub struct TranslationStatus {
    pub external_provider: Option<String>,
    pub external_enabled: bool,
    pub circuit: String,
    pub privacy_notice: String,
}
