//! Server half of translation_component_test: the text pipeline must never lose a
//! protected token (names/ranks/items arrive as ⟦n⟧ placeholders from the mod) and must
//! fail open (return the original) when a provider misbehaves.

use std::sync::Arc;
use std::sync::atomic::{AtomicU32, Ordering};

use async_trait::async_trait;
use proptest::prelude::*;
use skypilot_protocol::translation::TranslateRequest;
use skypilot_translation::{Dictionary, Provider, Translator, is_japanese, match_pattern, phrase_only, protect, unprotect};

fn dict() -> Dictionary {
    Dictionary::new(
        "test".into(),
        vec!["Hyperion".into(), "Terminator".into(), "Magical Power".into(), "MP".into(), "F7".into()],
        vec![("ty".into(), "ありがとう".into()), ("gg".into(), "GG".into()), ("lf".into(), "募集".into())],
        vec![(r"^(?:anyone|any1|who)\s+(?:want|wanna|doing)\s+([fm][1-7]|kuudra)\s*\??$".into(), "{1}行く人いる？".into())],
    )
}

fn req(text: &str) -> TranslateRequest {
    TranslateRequest { text: text.into(), source_lang: "en".into(), target_lang: "ja".into(), channel: Some("PARTY".into()) }
}

struct Echo(&'static str, AtomicU32);
#[async_trait]
impl Provider for Echo {
    fn name(&self) -> &'static str {
        "echo"
    }
    async fn translate(&self, text: &str, _: &str, _: &str) -> Result<String, String> {
        self.1.fetch_add(1, Ordering::SeqCst);
        Ok(format!("{}{}", self.0, text))
    }
}

struct Garbler;
#[async_trait]
impl Provider for Garbler {
    fn name(&self) -> &'static str {
        "garbler"
    }
    async fn translate(&self, _text: &str, _: &str, _: &str) -> Result<String, String> {
        Ok("翻訳されたが名前が消えた".into())
    }
}

struct Failing;
#[async_trait]
impl Provider for Failing {
    fn name(&self) -> &'static str {
        "failing"
    }
    async fn translate(&self, _: &str, _: &str, _: &str) -> Result<String, String> {
        Err("down".into())
    }
}

#[tokio::test]
async fn spec_example_party_message() {
    let t = Translator::new(dict(), None);
    let r = t.translate(&req("anyone want f7?")).await;
    assert_eq!(r.translated, "F7行く人いる？");
    assert_eq!(r.provider, "dictionary");
}

#[tokio::test]
async fn slang_only_messages_use_the_dictionary() {
    let t = Translator::new(dict(), None);
    assert_eq!(t.translate(&req("ty ⟦0⟧!")).await.translated, "ありがとう ⟦0⟧");
    assert_eq!(phrase_only("gg", &dict()).as_deref(), Some("GG"));
    assert!(phrase_only("ty for the carry", &dict()).is_none(), "partial matches go to the provider");
}

#[tokio::test]
async fn protected_terms_survive_provider_round_trip() {
    let echo = Arc::new(Echo("訳:", AtomicU32::new(0)));
    let t = Translator::new(dict(), Some(echo.clone()));
    let r = t.translate(&req("selling my Hyperion for cheap ⟦0⟧")).await;
    assert!(r.translated.contains("Hyperion"));
    assert!(r.translated.contains("⟦0⟧"));
    assert_eq!(r.provider, "echo");
    // second call is cached
    let r2 = t.translate(&req("selling my Hyperion for cheap ⟦0⟧")).await;
    assert!(r2.cached);
    assert_eq!(echo.1.load(Ordering::SeqCst), 1);
}

#[tokio::test]
async fn providers_that_drop_placeholders_are_rejected() {
    let t = Translator::new(dict(), Some(Arc::new(Garbler)));
    let r = t.translate(&req("need help with ⟦0⟧ please")).await;
    assert!(r.unchanged);
    assert_eq!(r.translated, "need help with ⟦0⟧ please");
}

#[tokio::test]
async fn failing_provider_opens_the_circuit_and_fails_open() {
    let t = Translator::new(dict(), Some(Arc::new(Failing)));
    for i in 0..6 {
        let r = t.translate(&req(&format!("random sentence number {i} here"))).await;
        assert!(r.unchanged);
    }
    assert_eq!(t.status().circuit, "OPEN");
}

#[test]
fn japanese_detection_and_protection() {
    assert!(is_japanese("F7行く人いる？"));
    assert!(!is_japanese("anyone want f7"));
    let (p, map) = protect("my magical power is 900 and Hyperion", &dict());
    assert!(!p.to_lowercase().contains("hyperion"));
    assert_eq!(unprotect(&p, &map), "my magical power is 900 and Hyperion");
    // "MP" protected only as a whole word
    let (p2, _) = protect("temple MP", &dict());
    assert!(p2.starts_with("temple "));
    assert!(match_pattern("who doing kuudra", &dict()).is_some());
}

proptest! {
    #[test]
    fn protect_unprotect_roundtrip(s in "[a-zA-Z0-9 ,.!?]{0,80}", insert in proptest::bool::ANY) {
        let text = if insert { format!("{s} Hyperion {s}") } else { s.clone() };
        let d = dict();
        let (p, map) = protect(&text, &d);
        prop_assert_eq!(unprotect(&p, &map), text);
    }
}
