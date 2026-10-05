//! SkyBlock chat translation (EN -> JA).
//!
//! ```text
//! text (client already replaced names/ranks/numbers with ⟦n⟧)
//!   -> protect SkyBlock terms (item names, jargon) as ⟦pN⟧
//!   -> full-message pattern dictionary ("anyone want f7?" -> "F7行く人いる？")
//!   -> slang-only messages assembled from the phrase dictionary
//!   -> cache
//!   -> optional provider (DeepL / LibreTranslate / Claude) with timeout + circuit breaker
//!   -> placeholder validation (never return text that lost a protected token)
//! ```
//! Chat text is never persisted; the cache is in-memory with a short TTL.

pub mod anthropic;
pub mod providers;

use std::sync::Arc;
use std::time::Duration;

use moka::sync::Cache;
use regex::Regex;
use skypilot_hypixel::CircuitBreaker;
use skypilot_protocol::translation::{DictionaryEntry, TranslateRequest, TranslateResponse, TranslationDictionary, TranslationStatus};
use unicode_normalization::UnicodeNormalization;

pub use providers::Provider;

pub struct Pattern {
    pub re: Regex,
    pub template: String,
}

pub struct Dictionary {
    pub version: String,
    /// Longest first.
    pub protected: Vec<String>,
    pub phrases: Vec<(String, String)>,
    pub patterns: Vec<Pattern>,
}

impl Dictionary {
    pub fn new(version: String, mut protected: Vec<String>, phrases: Vec<(String, String)>, patterns: Vec<(String, String)>) -> Self {
        protected.retain(|p| p.chars().count() >= 2);
        protected.sort_by(|a, b| b.len().cmp(&a.len()).then(a.cmp(b)));
        protected.dedup();
        let patterns = patterns
            .into_iter()
            .filter_map(|(re, template)| match Regex::new(&format!("(?i){re}")) {
                Ok(re) => Some(Pattern { re, template }),
                Err(e) => {
                    tracing::warn!(error = %e, "invalid translation pattern skipped");
                    None
                }
            })
            .collect();
        let mut phrases: Vec<(String, String)> = phrases.into_iter().map(|(k, v)| (k.to_lowercase(), v)).collect();
        phrases.sort_by(|a, b| b.0.len().cmp(&a.0.len()));
        Dictionary { version, protected, phrases, patterns }
    }

    pub fn to_protocol(&self) -> TranslationDictionary {
        TranslationDictionary {
            version: self.version.clone(),
            protected_terms: self.protected.clone(),
            phrases: self.phrases.iter().map(|(k, v)| DictionaryEntry { term: k.clone(), translation: Some(v.clone()), category: "phrase".into() }).collect(),
        }
    }
}

const PLACEHOLDER_OPEN: char = '⟦';
const PLACEHOLDER_CLOSE: char = '⟧';

fn placeholders(s: &str) -> Vec<String> {
    let mut out = Vec::new();
    let mut cur = String::new();
    let mut inside = false;
    for c in s.chars() {
        match c {
            PLACEHOLDER_OPEN => {
                inside = true;
                cur.clear();
            }
            PLACEHOLDER_CLOSE if inside => {
                inside = false;
                out.push(cur.clone());
            }
            _ if inside => cur.push(c),
            _ => {}
        }
    }
    out.sort();
    out
}

/// True when the text is already mostly Japanese (kana/kanji) - nothing to do.
pub fn is_japanese(s: &str) -> bool {
    let letters: Vec<char> = s.chars().filter(|c| c.is_alphabetic()).collect();
    if letters.is_empty() {
        return false;
    }
    let ja = letters.iter().filter(|c| matches!(**c as u32, 0x3040..=0x30FF | 0x4E00..=0x9FFF | 0xFF66..=0xFF9F)).count();
    ja * 2 >= letters.len()
}

/// Replace protected terms with ⟦pN⟧ placeholders (case-insensitive, word boundaries).
pub fn protect(text: &str, dict: &Dictionary) -> (String, Vec<(String, String)>) {
    let mut out = text.to_string();
    let mut map = Vec::new();
    let lower = out.to_lowercase();
    for term in &dict.protected {
        let t = term.to_lowercase();
        if !lower.contains(&t) {
            continue;
        }
        let re = match Regex::new(&format!(r"(?i)(^|[^\p{{L}}\p{{N}}]){}($|[^\p{{L}}\p{{N}}])", regex::escape(term))) {
            Ok(r) => r,
            Err(_) => continue,
        };
        while let Some(m) = re.captures(&out) {
            let whole = m.get(0).unwrap();
            let pre = m.get(1).map(|x| x.as_str()).unwrap_or("");
            let post = m.get(2).map(|x| x.as_str()).unwrap_or("");
            let original = &out[whole.start() + pre.len()..whole.end() - post.len()];
            let key = format!("p{}", map.len());
            map.push((key.clone(), original.to_string()));
            let replacement = format!("{pre}{PLACEHOLDER_OPEN}{key}{PLACEHOLDER_CLOSE}{post}");
            out.replace_range(whole.range(), &replacement);
            if map.len() > 64 {
                break;
            }
        }
    }
    (out, map)
}

pub fn unprotect(text: &str, map: &[(String, String)]) -> String {
    let mut out = text.to_string();
    for (k, v) in map {
        out = out.replace(&format!("{PLACEHOLDER_OPEN}{k}{PLACEHOLDER_CLOSE}"), v);
    }
    out
}

/// Full-message patterns. Returns the translated text when one matches.
pub fn match_pattern(text: &str, dict: &Dictionary) -> Option<String> {
    let t = text.trim();
    for p in &dict.patterns {
        if let Some(c) = p.re.captures(t) {
            let mut out = p.template.clone();
            for i in 1..c.len() {
                let v = c.get(i).map(|m| m.as_str()).unwrap_or("");
                let v = if v.len() <= 3 { v.to_uppercase() } else { v.to_string() };
                out = out.replace(&format!("{{{i}}}"), &v);
            }
            return Some(out);
        }
    }
    None
}

/// Messages made only of slang/phrases, placeholders and punctuation.
pub fn phrase_only(text: &str, dict: &Dictionary) -> Option<String> {
    let mut rest = text.trim().to_lowercase();
    if rest.is_empty() {
        return None;
    }
    let mut out = Vec::new();
    while !rest.is_empty() {
        rest = rest.trim_start_matches(|c: char| c.is_whitespace() || ",.!?~".contains(c)).to_string();
        if rest.is_empty() {
            break;
        }
        if rest.starts_with(PLACEHOLDER_OPEN) {
            let end = rest.find(PLACEHOLDER_CLOSE)? + PLACEHOLDER_CLOSE.len_utf8();
            out.push(rest[..end].to_string());
            rest = rest[end..].to_string();
            continue;
        }
        let hit = dict.phrases.iter().find(|(k, _)| rest.starts_with(k.as_str()) && rest[k.len()..].chars().next().is_none_or(|c| !c.is_alphanumeric()));
        match hit {
            Some((k, v)) => {
                out.push(v.clone());
                rest = rest[k.len()..].to_string();
            }
            None => return None,
        }
    }
    (!out.is_empty()).then(|| out.join(" "))
}

pub struct Translator {
    dict: Arc<Dictionary>,
    cache: Cache<String, String>,
    provider: Option<Arc<dyn Provider>>,
    breaker: CircuitBreaker,
    timeout: Duration,
}

impl Translator {
    pub fn new(dict: Dictionary, provider: Option<Arc<dyn Provider>>) -> Self {
        Translator {
            dict: Arc::new(dict),
            cache: Cache::builder().max_capacity(20_000).time_to_live(Duration::from_secs(6 * 3600)).build(),
            provider,
            breaker: CircuitBreaker::new(4, Duration::from_secs(60)),
            timeout: Duration::from_millis(3500),
        }
    }

    pub fn dictionary(&self) -> &Dictionary {
        &self.dict
    }

    pub fn status(&self) -> TranslationStatus {
        TranslationStatus {
            external_provider: self.provider.as_ref().map(|p| p.name().to_string()),
            external_enabled: self.provider.is_some(),
            circuit: self.breaker.state().as_str().to_string(),
            privacy_notice: "SkyPilot does not store chat. Dictionary/cached translations stay on the server in memory; messages are sent to the configured external provider only when no dictionary match exists.".into(),
        }
    }

    pub async fn translate(&self, req: &TranslateRequest) -> TranslateResponse {
        let text: String = req.text.nfc().collect();
        let unchanged = |provider: &str| TranslateResponse { translated: text.clone(), provider: provider.into(), cached: false, unchanged: true };
        if text.trim().is_empty() || text.chars().count() > 600 || req.target_lang != "ja" || is_japanese(&text) {
            return unchanged("passthrough");
        }
        if let Some(t) = match_pattern(&text, &self.dict) {
            return TranslateResponse { translated: t, provider: "dictionary".into(), cached: false, unchanged: false };
        }
        let (protected, map) = protect(&text, &self.dict);
        if let Some(t) = phrase_only(&protected, &self.dict) {
            return TranslateResponse { translated: unprotect(&t, &map), provider: "dictionary".into(), cached: false, unchanged: false };
        }
        // nothing left to translate besides placeholders/numbers
        let residue: String = protected.chars().filter(|c| c.is_alphabetic()).collect();
        if residue.chars().count() < 2 {
            return unchanged("passthrough");
        }
        let key = format!("{}|{}", req.target_lang, protected);
        if let Some(hit) = self.cache.get(&key) {
            return TranslateResponse { translated: unprotect(&hit, &map), provider: "cache".into(), cached: true, unchanged: false };
        }
        let Some(provider) = &self.provider else { return unchanged("passthrough") };
        if self.breaker.allow().is_err() {
            return unchanged("circuit-open");
        }
        match tokio::time::timeout(self.timeout, provider.translate(&protected, &req.source_lang, &req.target_lang)).await {
            Ok(Ok(out)) => {
                // Every placeholder must survive, otherwise names/items could be garbled.
                if placeholders(&out) != placeholders(&protected) || out.trim().is_empty() {
                    self.breaker.record_success();
                    return unchanged("passthrough");
                }
                self.breaker.record_success();
                self.cache.insert(key, out.clone());
                TranslateResponse { translated: unprotect(&out, &map), provider: provider.name().into(), cached: false, unchanged: false }
            }
            Ok(Err(e)) => {
                tracing::warn!(error = %e, provider = provider.name(), "translation provider failed");
                self.breaker.record_failure();
                unchanged("passthrough")
            }
            Err(_) => {
                self.breaker.record_failure();
                unchanged("timeout")
            }
        }
    }
}
