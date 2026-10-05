//! Web Push (RFC 8030 + VAPID RFC 8292) and flip-alert evaluation.
//!
//! Notifications are informational only: they link to the SkyPilot flip page and show the
//! `/viewauction` command for the player to type themselves. Nothing is ever bought or sold.

use std::path::Path;
use std::sync::Arc;
use std::time::Duration;

use base64::Engine;
use base64::engine::general_purpose::URL_SAFE_NO_PAD;
use skypilot_protocol::market::{FlipOpportunity, FlipQuery, NotificationRule};
use uuid::Uuid;
use web_push_native::jwt_simple::algorithms::{ECDSAP256PublicKeyLike, ES256KeyPair};
use web_push_native::{Auth, WebPushBuilder};

use crate::state::AppState;
use crate::store::PushSubscription;

/// Max alerts per user per market cycle, so a burst of listings cannot flood a device.
const MAX_ALERTS_PER_CYCLE: usize = 3;

pub struct WebPush {
    key: ES256KeyPair,
    public_b64: String,
    subject: String,
    http: reqwest::Client,
}

impl WebPush {
    /// VAPID key from VAPID_PRIVATE_KEY (base64url raw 32-byte scalar), otherwise a key
    /// generated once and kept in data/cache (never committed).
    pub fn load(env_key: Option<&str>, cache_dir: &Path, subject: String) -> anyhow::Result<WebPush> {
        let key = match env_key {
            Some(k) => ES256KeyPair::from_bytes(&URL_SAFE_NO_PAD.decode(k.trim())?).map_err(|e| anyhow::anyhow!("invalid VAPID_PRIVATE_KEY: {e}"))?,
            None => {
                let path = cache_dir.join("vapid_private.key");
                match std::fs::read_to_string(&path) {
                    Ok(s) => ES256KeyPair::from_bytes(&URL_SAFE_NO_PAD.decode(s.trim())?).map_err(|e| anyhow::anyhow!("invalid {}: {e}", path.display()))?,
                    Err(_) => {
                        let k = ES256KeyPair::generate();
                        std::fs::create_dir_all(cache_dir)?;
                        std::fs::write(&path, URL_SAFE_NO_PAD.encode(k.to_bytes()))?;
                        tracing::info!(path = %path.display(), "generated VAPID key for Web Push");
                        k
                    }
                }
            }
        };
        let public_b64 = URL_SAFE_NO_PAD.encode(key.public_key().public_key().to_bytes_uncompressed());
        let http = reqwest::Client::builder().timeout(Duration::from_secs(10)).build()?;
        Ok(WebPush { key, public_b64, subject, http })
    }

    pub fn public_key(&self) -> &str {
        &self.public_b64
    }

    /// Sends one message. Returns Ok(false) when the subscription is gone (404/410).
    pub async fn send(&self, sub: &PushSubscription, body: &serde_json::Value) -> anyhow::Result<bool> {
        let endpoint: http::Uri = sub.endpoint.parse()?;
        let ua_public = web_push_native::p256::PublicKey::from_sec1_bytes(&URL_SAFE_NO_PAD.decode(sub.p256dh.trim_end_matches('='))?)?;
        let auth_bytes = URL_SAFE_NO_PAD.decode(sub.auth.trim_end_matches('='))?;
        if auth_bytes.len() != 16 {
            anyhow::bail!("invalid auth secret length");
        }
        let req = WebPushBuilder::new(endpoint, ua_public, Auth::clone_from_slice(&auth_bytes))
            .with_valid_duration(Duration::from_secs(15 * 60))
            .with_vapid(&self.key, &self.subject)
            .build(serde_json::to_vec(body)?)
            .map_err(|e| anyhow::anyhow!("{e}"))?;
        let req = reqwest::Request::try_from(req)?;
        let resp = self.http.execute(req).await?;
        match resp.status().as_u16() {
            200..=299 => Ok(true),
            404 | 410 => Ok(false),
            s => anyhow::bail!("push service returned HTTP {s}"),
        }
    }
}

/// Validates a browser PushSubscription before storing it.
pub fn validate_subscription(sub: &PushSubscription) -> Result<(), &'static str> {
    let Ok(uri) = sub.endpoint.parse::<http::Uri>() else { return Err("endpoint must be an https URL") };
    if sub.endpoint.len() > 1024 || uri.scheme_str() != Some("https") || uri.host().is_none() {
        return Err("endpoint must be an https URL");
    }
    // The VAPID audience is scheme://host, so push services must use the default https port
    // (all browser push services do).
    if uri.port_u16().is_some_and(|p| p != 443) {
        return Err("endpoint must use the default https port");
    }
    let p = URL_SAFE_NO_PAD.decode(sub.p256dh.trim_end_matches('=')).map_err(|_| "invalid p256dh")?;
    web_push_native::p256::PublicKey::from_sec1_bytes(&p).map_err(|_| "invalid p256dh")?;
    let a = URL_SAFE_NO_PAD.decode(sub.auth.trim_end_matches('=')).map_err(|_| "invalid auth")?;
    if a.len() != 16 {
        return Err("invalid auth");
    }
    Ok(())
}

pub fn rule_query(rule: &NotificationRule) -> FlipQuery {
    FlipQuery { max_budget: rule.max_budget, min_profit: rule.min_profit, min_roi: rule.min_roi, min_confidence: rule.min_confidence, max_risk: rule.max_risk, search: None, limit: Some(20) }
}

/// Flips that a rule should alert on. Manipulation-flagged flips never alert.
pub fn matching<'a>(rule: &NotificationRule, flips: &'a [FlipOpportunity]) -> Vec<&'a FlipOpportunity> {
    if !rule.enabled {
        return vec![];
    }
    skypilot_pricing::flips::filter(flips, &rule_query(rule)).into_iter().filter(|f| !f.manipulation.suspicious).collect()
}

pub fn alert_body(rule: &NotificationRule, f: &FlipOpportunity, lang_ja: bool) -> serde_json::Value {
    let profit = skypilot_reco::ctx::fmt_coins(f.expected_net_profit);
    let buy = skypilot_reco::ctx::fmt_coins(f.buy_price);
    let body = if lang_ja {
        format!("{}: 購入 {} → 期待純利益 {}（ROI {:.0}%、信頼度 {:?}）。自分で確認してください: {}", f.item_name, buy, profit, f.roi * 100.0, f.confidence.level, f.view_command)
    } else {
        format!("{}: buy {} → expected net profit {} (ROI {:.0}%, confidence {:?}). Check it yourself: {}", f.item_name, buy, profit, f.roi * 100.0, f.confidence.level, f.view_command)
    };
    serde_json::json!({
        "title": format!("SkyPilot · {}", rule.name),
        "body": body,
        "url": format!("/market/flips?focus={}", f.auction_id),
        "tag": format!("flip-{}", f.auction_id),
    })
}

/// Evaluate every user's rules against the latest flips and deliver new matches.
pub async fn evaluate_rules(state: Arc<AppState>) {
    let Some(push) = state.push.as_ref() else { return };
    let rules = match state.store.all_notification_rules().await {
        Ok(r) => r,
        Err(e) => {
            tracing::warn!(error = %e, "notification rules unavailable");
            return;
        }
    };
    if rules.is_empty() {
        return;
    }
    let flips = state.market.state.read().flips.clone();
    let mut per_user: std::collections::HashMap<Uuid, Vec<serde_json::Value>> = Default::default();
    for (user, rule) in &rules {
        for f in matching(rule, &flips) {
            if per_user.get(user).is_some_and(|v| v.len() >= MAX_ALERTS_PER_CYCLE) {
                break;
            }
            if !state.bus.claim_once(&format!("{user}:{}:{}", rule.id, f.auction_id), Duration::from_secs(6 * 3600)).await {
                continue;
            }
            let ja = state.store.get_user(*user).await.ok().flatten().is_some_and(|u| u.lang == "ja");
            per_user.entry(*user).or_default().push(alert_body(rule, f, ja));
        }
    }
    for (user, bodies) in per_user {
        let subs = state.store.push_subscriptions(user).await.unwrap_or_default();
        for sub in subs {
            for b in &bodies {
                match push.send(&sub, b).await {
                    Ok(true) => {}
                    Ok(false) => {
                        let _ = state.store.delete_push_subscription(&sub.endpoint).await;
                        break;
                    }
                    Err(e) => {
                        tracing::debug!(error = %e, "web push delivery failed");
                        break;
                    }
                }
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn subscription_validation() {
        let ok = PushSubscription {
            endpoint: "https://push.example.net/abc".into(),
            p256dh: "BLn9b-VR0ca83knDNZ32dCHGyjJp-1riX9ZTN40MqV8K_LpQmLqxC_DoHvqvFXO_nGdAB4W9dogZb_sM-uV4JbY".into(),
            auth: "_ordMnz7uTCmrpBTeUV4Bw".into(),
        };
        assert!(validate_subscription(&ok).is_ok());
        assert!(validate_subscription(&PushSubscription { endpoint: "http://x".into(), ..ok.clone() }).is_err());
        assert!(validate_subscription(&PushSubscription { endpoint: "https://push.example.net:8443/abc".into(), ..ok.clone() }).is_err());
        assert!(validate_subscription(&PushSubscription { endpoint: "https://fcm.googleapis.com:443/fcm/send/x".into(), ..ok.clone() }).is_ok());
        assert!(validate_subscription(&PushSubscription { p256dh: "AAAA".into(), ..ok.clone() }).is_err());
        assert!(validate_subscription(&PushSubscription { auth: "AAAA".into(), ..ok }).is_err());
    }

    /// Full delivery against a local push service playing the browser's role: the request
    /// carries a valid VAPID JWT for the endpoint origin and an RFC 8291 aes128gcm payload that
    /// decrypts with the subscription's private key; 410 Gone reports the subscription as dead.
    #[tokio::test]
    async fn delivers_encrypted_payload_with_valid_vapid() {
        use axum::body::Bytes;
        use axum::http::{HeaderMap, StatusCode};
        use rand::RngCore;
        use web_push_native::jwt_simple::algorithms::ES256PublicKey;
        use web_push_native::jwt_simple::claims::{Audiences, NoCustomClaims};
        use web_push_native::p256::SecretKey;
        use web_push_native::p256::elliptic_curve::sec1::ToEncodedPoint;

        // Browser side: subscription key pair + auth secret.
        let mut seed = [0u8; 32];
        rand::thread_rng().fill_bytes(&mut seed);
        let ua_secret = SecretKey::from_slice(&seed).expect("valid scalar");
        let ua_public = ua_secret.public_key().to_encoded_point(false);
        let mut auth = [0u8; 16];
        rand::thread_rng().fill_bytes(&mut auth);

        // Local push service recording what it receives.
        let (tx, mut rx) = tokio::sync::mpsc::unbounded_channel::<(HeaderMap, Vec<u8>)>();
        let app = axum::Router::new()
            .route(
                "/push/{id}",
                axum::routing::post(move |axum::extract::Path(id): axum::extract::Path<String>, headers: HeaderMap, body: Bytes| {
                    let tx = tx.clone();
                    async move {
                        let _ = tx.send((headers, body.to_vec()));
                        if id == "gone" { StatusCode::GONE } else { StatusCode::CREATED }
                    }
                }),
            );
        let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
        let addr = listener.local_addr().unwrap();
        tokio::spawn(async move { axum::serve(listener, app).await.unwrap() });

        let dir = std::env::temp_dir().join(format!("skypilot-push-{}", crate::auth::random_token(4)));
        let push = WebPush::load(None, &dir, "mailto:alerts@skypilot.test".into()).unwrap();
        let sub = PushSubscription {
            endpoint: format!("http://{addr}/push/live"),
            p256dh: URL_SAFE_NO_PAD.encode(ua_public.as_bytes()),
            auth: URL_SAFE_NO_PAD.encode(auth),
        };
        let body = serde_json::json!({"title": "SkyPilot · test", "body": "Hyperion: buy 800M → +12M", "url": "/market/flips?focus=abc"});
        assert!(push.send(&sub, &body).await.unwrap(), "201 Created = delivered");

        let (headers, payload) = rx.recv().await.unwrap();
        assert_eq!(headers.get("content-encoding").unwrap(), "aes128gcm");
        assert!(headers.get("ttl").is_some());
        // Authorization: vapid t=<JWT>, k=<application server public key>
        let authz = headers.get("authorization").unwrap().to_str().unwrap().to_string();
        let t = authz.split("t=").nth(1).unwrap().split(',').next().unwrap().trim();
        let k = authz.split("k=").nth(1).unwrap().trim();
        assert_eq!(k, push.public_key(), "k is the advertised VAPID public key");
        let vapid_pk = ES256PublicKey::from_bytes(&URL_SAFE_NO_PAD.decode(k).unwrap()).unwrap();
        let claims = vapid_pk.verify_token::<NoCustomClaims>(t, None).expect("VAPID JWT signature verifies");
        // aud = scheme://host of the endpoint (push services listen on the default port).
        match claims.audiences.expect("aud") {
            Audiences::AsString(a) => assert_eq!(a, "http://127.0.0.1"),
            Audiences::AsSet(s) => assert!(s.contains("http://127.0.0.1")),
        }
        assert_eq!(claims.subject.as_deref(), Some("mailto:alerts@skypilot.test"));
        // Only the subscription's private key can read the notification.
        let plain = web_push_native::decrypt(payload, &ua_secret, &web_push_native::Auth::clone_from_slice(&auth)).expect("payload decrypts");
        assert_eq!(serde_json::from_slice::<serde_json::Value>(&plain).unwrap(), body);

        let gone = PushSubscription { endpoint: format!("http://{addr}/push/gone"), ..sub };
        assert!(!push.send(&gone, &body).await.unwrap(), "410 Gone = subscription expired");
        let _ = std::fs::remove_dir_all(dir);
    }

    #[test]
    fn vapid_key_roundtrip() {
        let dir = std::env::temp_dir().join(format!("skypilot-vapid-{}", crate::auth::random_token(4)));
        let a = WebPush::load(None, &dir, "mailto:test@example.org".into()).unwrap();
        let b = WebPush::load(None, &dir, "mailto:test@example.org".into()).unwrap();
        assert_eq!(a.public_key(), b.public_key());
        assert_eq!(URL_SAFE_NO_PAD.decode(a.public_key()).unwrap().len(), 65);
        let _ = std::fs::remove_dir_all(dir);
    }
}
