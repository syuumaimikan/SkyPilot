//! In-process store (used when DATABASE_URL is not set). Bounded so it cannot grow forever.

use std::collections::{HashMap, VecDeque};

use async_trait::async_trait;
use chrono::{DateTime, Utc};
use parking_lot::RwLock;
use skypilot_hypixel::BazaarResponse;
use skypilot_pricing::normalize::Sale;
use skypilot_pricing::state::{BazaarPoint, CandidateRecord};
use skypilot_protocol::goals::{AnalyzeResponse, PlayStyle, SavedGoal};
use skypilot_protocol::market::{NotificationRule, PricePoint};
use skypilot_protocol::profile::ProgressSnapshot;
use uuid::Uuid;

use super::*;

const MAX_SALES: usize = 500_000;
const MAX_BAZAAR_POINTS: usize = 1_000_000;

#[derive(Default)]
struct Inner {
    users: HashMap<Uuid, UserRow>,
    sessions: HashMap<String, SessionRow>,
    goals: HashMap<Uuid, Vec<SavedGoal>>,
    analyses: HashMap<Uuid, (AnalyzeResponse, i64)>,
    snapshots: HashMap<(String, String), Vec<ProgressSnapshot>>,
    device_codes: HashMap<String, DeviceCodeRow>,
    tokens: HashMap<String, (String, DeviceTokenRow)>,
    sales: VecDeque<Sale>,
    bazaar: VecDeque<(String, BazaarPoint)>,
    candidates: VecDeque<CandidateRecord>,
    rules: HashMap<Uuid, Vec<NotificationRule>>,
    push: HashMap<String, (Uuid, PushSubscription)>,
    sessions_stats: Vec<(Uuid, String, i64, i64, serde_json::Value)>,
    raw: HashMap<Uuid, RawProfile>,
}

#[derive(Default)]
pub struct MemoryStore {
    inner: RwLock<Inner>,
}

#[async_trait]
impl Store for MemoryStore {
    fn kind(&self) -> &'static str {
        "memory"
    }

    async fn create_user(&self) -> StoreResult<Uuid> {
        let id = Uuid::new_v4();
        self.inner.write().users.insert(
            id,
            UserRow { id, login: None, password_hash: None, minecraft_uuid: None, minecraft_name: None, profile_id: None, play_style: PlayStyle::Fastest, budget: None, lang: "ja".into() },
        );
        Ok(id)
    }

    async fn get_user(&self, id: Uuid) -> StoreResult<Option<UserRow>> {
        Ok(self.inner.read().users.get(&id).cloned())
    }

    async fn find_user_by_login(&self, login: &str) -> StoreResult<Option<UserRow>> {
        Ok(self.inner.read().users.values().find(|u| u.login.as_deref() == Some(login)).cloned())
    }

    async fn set_credentials(&self, id: Uuid, login: &str, password_hash: &str) -> StoreResult<()> {
        let mut g = self.inner.write();
        if g.users.values().any(|u| u.login.as_deref() == Some(login) && u.id != id) {
            anyhow::bail!("login already taken");
        }
        if let Some(u) = g.users.get_mut(&id) {
            u.login = Some(login.to_string());
            u.password_hash = Some(password_hash.to_string());
        }
        Ok(())
    }

    async fn update_prefs(&self, id: Uuid, mc_uuid: Option<&str>, mc_name: Option<&str>, profile_id: Option<&str>, style: PlayStyle, budget: Option<f64>, lang: &str) -> StoreResult<()> {
        if let Some(u) = self.inner.write().users.get_mut(&id) {
            if mc_uuid.is_some() {
                u.minecraft_uuid = mc_uuid.map(str::to_string);
                u.minecraft_name = mc_name.map(str::to_string);
            }
            u.profile_id = profile_id.map(str::to_string).or(u.profile_id.take());
            u.play_style = style;
            u.budget = budget;
            u.lang = lang.to_string();
        }
        Ok(())
    }

    async fn create_session(&self, id_hash: &str, user_id: Uuid, csrf: &str, expires_at: DateTime<Utc>) -> StoreResult<()> {
        self.inner.write().sessions.insert(id_hash.to_string(), SessionRow { user_id, csrf_token: csrf.to_string(), expires_at });
        Ok(())
    }

    async fn get_session(&self, id_hash: &str) -> StoreResult<Option<SessionRow>> {
        Ok(self.inner.read().sessions.get(id_hash).cloned().filter(|s| s.expires_at > Utc::now()))
    }

    async fn delete_session(&self, id_hash: &str) -> StoreResult<()> {
        self.inner.write().sessions.remove(id_hash);
        Ok(())
    }

    async fn save_goals(&self, user_id: Uuid, goals: &[SavedGoal]) -> StoreResult<()> {
        self.inner.write().goals.insert(user_id, goals.to_vec());
        Ok(())
    }

    async fn load_goals(&self, user_id: Uuid) -> StoreResult<Vec<SavedGoal>> {
        Ok(self.inner.read().goals.get(&user_id).cloned().unwrap_or_default())
    }

    async fn save_analysis(&self, user_id: Uuid, resp: &AnalyzeResponse, snapshot: &ProgressSnapshot) -> StoreResult<()> {
        let mut g = self.inner.write();
        g.analyses.insert(user_id, (resp.clone(), snapshot.taken_at_ms));
        let key = (resp.analysis.player.uuid.clone(), resp.analysis.profile_id.clone());
        let list = g.snapshots.entry(key).or_default();
        list.push(snapshot.clone());
        if list.len() > 2000 {
            list.remove(0);
        }
        Ok(())
    }

    async fn latest_analysis(&self, user_id: Uuid) -> StoreResult<Option<(AnalyzeResponse, i64)>> {
        Ok(self.inner.read().analyses.get(&user_id).cloned())
    }

    async fn snapshots(&self, minecraft_uuid: &str, profile_id: &str) -> StoreResult<Vec<ProgressSnapshot>> {
        Ok(self.inner.read().snapshots.get(&(minecraft_uuid.to_string(), profile_id.to_string())).cloned().unwrap_or_default())
    }

    async fn save_raw_profile(&self, user_id: Uuid, raw: &RawProfile) -> StoreResult<()> {
        self.inner.write().raw.insert(user_id, raw.clone());
        Ok(())
    }

    async fn load_raw_profile(&self, user_id: Uuid) -> StoreResult<Option<RawProfile>> {
        Ok(self.inner.read().raw.get(&user_id).cloned())
    }

    async fn create_device_code(&self, row: &DeviceCodeRow) -> StoreResult<()> {
        self.inner.write().device_codes.insert(row.device_code_hash.clone(), row.clone());
        Ok(())
    }

    async fn device_code_by_user_code(&self, user_code: &str) -> StoreResult<Option<DeviceCodeRow>> {
        Ok(self.inner.read().device_codes.values().find(|d| d.user_code == user_code).cloned())
    }

    async fn device_code(&self, hash: &str) -> StoreResult<Option<DeviceCodeRow>> {
        Ok(self.inner.read().device_codes.get(hash).cloned())
    }

    async fn set_device_code_decision(&self, user_code: &str, user_id: Option<Uuid>, denied: bool) -> StoreResult<()> {
        if let Some(d) = self.inner.write().device_codes.values_mut().find(|d| d.user_code == user_code) {
            d.approved_user_id = user_id;
            d.denied = denied;
        }
        Ok(())
    }

    async fn touch_device_code(&self, hash: &str) -> StoreResult<()> {
        if let Some(d) = self.inner.write().device_codes.get_mut(hash) {
            d.last_poll_at = Some(Utc::now());
        }
        Ok(())
    }

    async fn delete_device_code(&self, hash: &str) -> StoreResult<()> {
        self.inner.write().device_codes.remove(hash);
        Ok(())
    }

    async fn create_device_token(&self, id: &str, token_hash: &str, user_id: Uuid, name: &str) -> StoreResult<()> {
        self.inner.write().tokens.insert(
            token_hash.to_string(),
            (token_hash.to_string(), DeviceTokenRow { id: id.to_string(), user_id, name: name.to_string(), created_at: Utc::now(), last_used_at: None, revoked: false }),
        );
        Ok(())
    }

    async fn device_token(&self, token_hash: &str) -> StoreResult<Option<DeviceTokenRow>> {
        Ok(self.inner.read().tokens.get(token_hash).map(|(_, r)| r.clone()))
    }

    async fn touch_device_token(&self, id: &str) -> StoreResult<()> {
        if let Some((_, r)) = self.inner.write().tokens.values_mut().find(|(_, r)| r.id == id) {
            r.last_used_at = Some(Utc::now());
        }
        Ok(())
    }

    async fn list_devices(&self, user_id: Uuid) -> StoreResult<Vec<DeviceTokenRow>> {
        Ok(self.inner.read().tokens.values().map(|(_, r)| r.clone()).filter(|r| r.user_id == user_id && !r.revoked).collect())
    }

    async fn revoke_device(&self, user_id: Uuid, id: &str) -> StoreResult<bool> {
        let mut g = self.inner.write();
        match g.tokens.values_mut().find(|(_, r)| r.id == id && r.user_id == user_id) {
            Some((_, r)) => {
                r.revoked = true;
                Ok(true)
            }
            None => Ok(false),
        }
    }

    async fn store_sales(&self, sales: &[Sale]) -> StoreResult<()> {
        let mut g = self.inner.write();
        g.sales.extend(sales.iter().cloned());
        while g.sales.len() > MAX_SALES {
            g.sales.pop_front();
        }
        Ok(())
    }

    async fn store_bazaar(&self, resp: &BazaarResponse) -> StoreResult<()> {
        let mut g = self.inner.write();
        for (id, p) in &resp.products {
            g.bazaar.push_back((
                id.clone(),
                BazaarPoint {
                    ts_ms: resp.last_updated,
                    buy: p.buy_summary.first().map(|o| o.price_per_unit).unwrap_or(p.quick_status.buy_price),
                    sell: p.sell_summary.first().map(|o| o.price_per_unit).unwrap_or(p.quick_status.sell_price),
                    buy_volume: p.quick_status.buy_volume,
                    sell_volume: p.quick_status.sell_volume,
                    buy_moving_week: p.quick_status.buy_moving_week,
                    sell_moving_week: p.quick_status.sell_moving_week,
                },
            ));
        }
        while g.bazaar.len() > MAX_BAZAAR_POINTS {
            g.bazaar.pop_front();
        }
        Ok(())
    }

    async fn store_candidates(&self, c: &[CandidateRecord]) -> StoreResult<()> {
        let mut g = self.inner.write();
        g.candidates.extend(c.iter().cloned());
        while g.candidates.len() > 200_000 {
            g.candidates.pop_front();
        }
        Ok(())
    }

    async fn load_sales_since(&self, since_ms: i64) -> StoreResult<Vec<Sale>> {
        Ok(self.inner.read().sales.iter().filter(|s| s.ts_ms >= since_ms).cloned().collect())
    }

    async fn load_bazaar_since(&self, since_ms: i64) -> StoreResult<Vec<(String, BazaarPoint)>> {
        Ok(self.inner.read().bazaar.iter().filter(|(_, p)| p.ts_ms >= since_ms).cloned().collect())
    }

    async fn load_candidates_since(&self, since_ms: i64) -> StoreResult<Vec<CandidateRecord>> {
        Ok(self.inner.read().candidates.iter().filter(|c| c.ts_ms >= since_ms).cloned().collect())
    }

    async fn price_history(&self, _item_id: &str, _since_ms: i64, _bucket_ms: i64) -> StoreResult<Vec<PricePoint>> {
        // The engine's in-memory history covers this case.
        Ok(Vec::new())
    }

    async fn notification_rules(&self, user_id: Uuid) -> StoreResult<Vec<NotificationRule>> {
        Ok(self.inner.read().rules.get(&user_id).cloned().unwrap_or_default())
    }

    async fn all_notification_rules(&self) -> StoreResult<Vec<(Uuid, NotificationRule)>> {
        Ok(self.inner.read().rules.iter().flat_map(|(u, rs)| rs.iter().map(move |r| (*u, r.clone()))).collect())
    }

    async fn save_notification_rule(&self, user_id: Uuid, rule: &NotificationRule) -> StoreResult<()> {
        let mut g = self.inner.write();
        let rs = g.rules.entry(user_id).or_default();
        rs.retain(|r| r.id != rule.id);
        rs.push(rule.clone());
        Ok(())
    }

    async fn delete_notification_rule(&self, user_id: Uuid, id: &str) -> StoreResult<()> {
        if let Some(rs) = self.inner.write().rules.get_mut(&user_id) {
            rs.retain(|r| r.id != id);
        }
        Ok(())
    }

    async fn save_push_subscription(&self, user_id: Uuid, sub: &PushSubscription) -> StoreResult<()> {
        self.inner.write().push.insert(sub.endpoint.clone(), (user_id, sub.clone()));
        Ok(())
    }

    async fn push_subscriptions(&self, user_id: Uuid) -> StoreResult<Vec<PushSubscription>> {
        Ok(self.inner.read().push.values().filter(|(u, _)| *u == user_id).map(|(_, s)| s.clone()).collect())
    }

    async fn delete_push_subscription(&self, endpoint: &str) -> StoreResult<()> {
        self.inner.write().push.remove(endpoint);
        Ok(())
    }

    async fn save_session_stats(&self, user_id: Uuid, device_id: &str, started_ms: i64, ended_ms: i64, stats: &serde_json::Value) -> StoreResult<()> {
        let mut g = self.inner.write();
        g.sessions_stats.push((user_id, device_id.to_string(), started_ms, ended_ms, stats.clone()));
        if g.sessions_stats.len() > 10_000 {
            g.sessions_stats.remove(0);
        }
        Ok(())
    }
}
