//! Optional external translation providers. Configured server-side only; the mod never
//! talks to them directly and never holds their keys.

use std::time::Duration;

use async_trait::async_trait;
use serde_json::{Value, json};

use crate::anthropic::AnthropicClient;

#[async_trait]
pub trait Provider: Send + Sync {
    fn name(&self) -> &'static str;
    async fn translate(&self, text: &str, source: &str, target: &str) -> Result<String, String>;
}

/// DeepL (free keys end with ":fx").
pub struct DeepL {
    http: reqwest::Client,
    key: String,
}

impl DeepL {
    pub fn new(key: String) -> Self {
        DeepL { http: reqwest::Client::new(), key }
    }
}

#[async_trait]
impl Provider for DeepL {
    fn name(&self) -> &'static str {
        "deepl"
    }
    async fn translate(&self, text: &str, source: &str, target: &str) -> Result<String, String> {
        let host = if self.key.ends_with(":fx") { "https://api-free.deepl.com" } else { "https://api.deepl.com" };
        let body = json!({"text": [text], "source_lang": source.to_uppercase(), "target_lang": target.to_uppercase()});
        let resp = self
            .http
            .post(format!("{host}/v2/translate"))
            .header("Authorization", format!("DeepL-Auth-Key {}", self.key))
            .json(&body)
            .send()
            .await
            .map_err(|e| e.to_string())?;
        if !resp.status().is_success() {
            return Err(format!("deepl HTTP {}", resp.status()));
        }
        let v: Value = resp.json().await.map_err(|e| e.to_string())?;
        v.pointer("/translations/0/text").and_then(Value::as_str).map(str::to_string).ok_or_else(|| "deepl: no text".into())
    }
}

/// Self-hosted LibreTranslate.
pub struct LibreTranslate {
    http: reqwest::Client,
    url: String,
    key: Option<String>,
}

impl LibreTranslate {
    pub fn new(url: String, key: Option<String>) -> Self {
        LibreTranslate { http: reqwest::Client::new(), url: url.trim_end_matches('/').to_string(), key }
    }
}

#[async_trait]
impl Provider for LibreTranslate {
    fn name(&self) -> &'static str {
        "libretranslate"
    }
    async fn translate(&self, text: &str, source: &str, target: &str) -> Result<String, String> {
        let mut body = json!({"q": text, "source": source, "target": target, "format": "text"});
        if let Some(k) = &self.key {
            body["api_key"] = json!(k);
        }
        let resp = self.http.post(format!("{}/translate", self.url)).json(&body).send().await.map_err(|e| e.to_string())?;
        if !resp.status().is_success() {
            return Err(format!("libretranslate HTTP {}", resp.status()));
        }
        let v: Value = resp.json().await.map_err(|e| e.to_string())?;
        v.get("translatedText").and_then(Value::as_str).map(str::to_string).ok_or_else(|| "libretranslate: no text".into())
    }
}

/// Claude as translator (grounded: the system prompt forbids adding content and requires
/// placeholders to be preserved; output is validated by the Translator anyway).
pub struct Claude {
    client: AnthropicClient,
}

impl Claude {
    pub fn new(client: AnthropicClient) -> Self {
        Claude { client }
    }
}

const SYSTEM: &str = "You translate short Hypixel SkyBlock Minecraft chat messages from English into natural, casual Japanese as used by Japanese SkyBlock players. Rules: output only the translation, no quotes or notes; keep every token of the form ⟦...⟧ exactly as is and exactly once; keep SkyBlock jargon such as item names, F7, M5, T4, HOTM, MP, AH, BZ in English; never add information that is not in the message.";

#[async_trait]
impl Provider for Claude {
    fn name(&self) -> &'static str {
        "claude"
    }
    async fn translate(&self, text: &str, _source: &str, _target: &str) -> Result<String, String> {
        self.client.complete(SYSTEM, text, 1024, "low", Duration::from_secs(20)).await.map(|s| s.trim().to_string()).map_err(|e| e.to_string())
    }
}

/// Pick a provider from environment configuration (first match wins).
pub fn from_env() -> Option<std::sync::Arc<dyn Provider>> {
    let provider = std::env::var("TRANSLATION_PROVIDER").unwrap_or_default().to_lowercase();
    let deepl = std::env::var("DEEPL_API_KEY").ok().filter(|k| !k.is_empty());
    let libre = std::env::var("LIBRETRANSLATE_URL").ok().filter(|k| !k.is_empty());
    match provider.as_str() {
        "none" | "off" => None,
        "deepl" => deepl.map(|k| std::sync::Arc::new(DeepL::new(k)) as _),
        "libretranslate" => libre.map(|u| std::sync::Arc::new(LibreTranslate::new(u, std::env::var("LIBRETRANSLATE_API_KEY").ok())) as _),
        "claude" => AnthropicClient::from_env().map(|c| std::sync::Arc::new(Claude::new(c)) as _),
        _ => deepl
            .map(|k| std::sync::Arc::new(DeepL::new(k)) as std::sync::Arc<dyn Provider>)
            .or_else(|| libre.map(|u| std::sync::Arc::new(LibreTranslate::new(u, std::env::var("LIBRETRANSLATE_API_KEY").ok())) as _)),
    }
}
