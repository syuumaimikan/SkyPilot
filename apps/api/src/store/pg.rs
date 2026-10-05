//! PostgreSQL / TimescaleDB store (runtime-checked SQLx queries; migrations in /migrations).

use async_trait::async_trait;
use chrono::{DateTime, Utc};
use skypilot_hypixel::BazaarResponse;
use skypilot_pricing::normalize::Sale;
use skypilot_pricing::state::{BazaarPoint, CandidateRecord};
use skypilot_protocol::goals::{AnalyzeResponse, GoalPriority, SavedGoal};
use skypilot_protocol::market::{NotificationRule, PricePoint};
use skypilot_protocol::profile::ProgressSnapshot;
use sqlx::types::Json;
use sqlx::{PgPool, Row};
use uuid::Uuid;

use super::*;

pub struct PgStore {
    pub pool: PgPool,
}

impl PgStore {
    pub async fn connect(url: &str) -> anyhow::Result<Self> {
        let pool = sqlx::postgres::PgPoolOptions::new().max_connections(16).acquire_timeout(std::time::Duration::from_secs(10)).connect(url).await?;
        Ok(PgStore { pool })
    }

    pub async fn migrate(&self, dir: &std::path::Path) -> anyhow::Result<()> {
        let m = sqlx::migrate::Migrator::new(dir).await?;
        m.run(&self.pool).await?;
        Ok(())
    }
}

fn user_from_row(r: &sqlx::postgres::PgRow) -> UserRow {
    UserRow {
        id: r.get("id"),
        login: r.get("login"),
        password_hash: r.get("password_hash"),
        minecraft_uuid: r.get("minecraft_uuid"),
        minecraft_name: r.get("minecraft_name"),
        profile_id: r.get("profile_id"),
        play_style: parse_style(r.get::<String, _>("play_style").as_str()),
        budget: r.get("budget"),
        lang: r.get("lang"),
    }
}

fn device_code_from_row(r: &sqlx::postgres::PgRow) -> DeviceCodeRow {
    DeviceCodeRow {
        device_code_hash: r.get("device_code_hash"),
        user_code: r.get("user_code"),
        device_name: r.get("device_name"),
        expires_at: r.get("expires_at"),
        approved_user_id: r.get("approved_user_id"),
        denied: r.get("denied"),
        last_poll_at: r.get("last_poll_at"),
    }
}

fn token_from_row(r: &sqlx::postgres::PgRow) -> DeviceTokenRow {
    DeviceTokenRow { id: r.get("id"), user_id: r.get("user_id"), name: r.get("name"), created_at: r.get("created_at"), last_used_at: r.get("last_used_at"), revoked: r.get("revoked") }
}

const USER_COLS: &str = "id, login, password_hash, minecraft_uuid, minecraft_name, profile_id, play_style, budget, lang";

#[async_trait]
impl Store for PgStore {
    fn kind(&self) -> &'static str {
        "postgres"
    }

    async fn create_user(&self) -> StoreResult<Uuid> {
        let id = Uuid::new_v4();
        sqlx::query("INSERT INTO users (id) VALUES ($1)").bind(id).execute(&self.pool).await?;
        Ok(id)
    }

    async fn get_user(&self, id: Uuid) -> StoreResult<Option<UserRow>> {
        let r = sqlx::query(&format!("SELECT {USER_COLS} FROM users WHERE id = $1")).bind(id).fetch_optional(&self.pool).await?;
        if r.is_some() {
            let _ = sqlx::query("UPDATE users SET last_seen_at = now() WHERE id = $1").bind(id).execute(&self.pool).await;
        }
        Ok(r.as_ref().map(user_from_row))
    }

    async fn find_user_by_login(&self, login: &str) -> StoreResult<Option<UserRow>> {
        let r = sqlx::query(&format!("SELECT {USER_COLS} FROM users WHERE login = $1")).bind(login).fetch_optional(&self.pool).await?;
        Ok(r.as_ref().map(user_from_row))
    }

    async fn set_credentials(&self, id: Uuid, login: &str, password_hash: &str) -> StoreResult<()> {
        sqlx::query("UPDATE users SET login = $2, password_hash = $3 WHERE id = $1").bind(id).bind(login).bind(password_hash).execute(&self.pool).await?;
        Ok(())
    }

    async fn update_prefs(&self, id: Uuid, mc_uuid: Option<&str>, mc_name: Option<&str>, profile_id: Option<&str>, style: PlayStyle, budget: Option<f64>, lang: &str) -> StoreResult<()> {
        sqlx::query(
            "UPDATE users SET minecraft_uuid = COALESCE($2, minecraft_uuid), minecraft_name = COALESCE($3, minecraft_name), profile_id = COALESCE($4, profile_id), play_style = $5, budget = $6, lang = $7 WHERE id = $1",
        )
        .bind(id)
        .bind(mc_uuid)
        .bind(mc_name)
        .bind(profile_id)
        .bind(style_str(style))
        .bind(budget)
        .bind(lang)
        .execute(&self.pool)
        .await?;
        Ok(())
    }

    async fn create_session(&self, id_hash: &str, user_id: Uuid, csrf: &str, expires_at: DateTime<Utc>) -> StoreResult<()> {
        sqlx::query("INSERT INTO web_sessions (id_hash, user_id, csrf_token, expires_at) VALUES ($1, $2, $3, $4)").bind(id_hash).bind(user_id).bind(csrf).bind(expires_at).execute(&self.pool).await?;
        Ok(())
    }

    async fn get_session(&self, id_hash: &str) -> StoreResult<Option<SessionRow>> {
        let r = sqlx::query("SELECT user_id, csrf_token, expires_at FROM web_sessions WHERE id_hash = $1 AND expires_at > now()").bind(id_hash).fetch_optional(&self.pool).await?;
        Ok(r.map(|r| SessionRow { user_id: r.get("user_id"), csrf_token: r.get("csrf_token"), expires_at: r.get("expires_at") }))
    }

    async fn delete_session(&self, id_hash: &str) -> StoreResult<()> {
        sqlx::query("DELETE FROM web_sessions WHERE id_hash = $1").bind(id_hash).execute(&self.pool).await?;
        Ok(())
    }

    async fn save_goals(&self, user_id: Uuid, goals: &[SavedGoal]) -> StoreResult<()> {
        let mut tx = self.pool.begin().await?;
        sqlx::query("DELETE FROM goals WHERE user_id = $1").bind(user_id).execute(&mut *tx).await?;
        for g in goals {
            sqlx::query("INSERT INTO goals (user_id, id, text, priority, created_at) VALUES ($1, $2, $3, $4, $5)")
                .bind(user_id)
                .bind(&g.id)
                .bind(&g.text)
                .bind(if g.priority == GoalPriority::Primary { "PRIMARY" } else { "SECONDARY" })
                .bind(ms_to_dt(g.created_at_ms))
                .execute(&mut *tx)
                .await?;
        }
        tx.commit().await?;
        Ok(())
    }

    async fn load_goals(&self, user_id: Uuid) -> StoreResult<Vec<SavedGoal>> {
        let rows = sqlx::query("SELECT id, text, priority, created_at FROM goals WHERE user_id = $1 ORDER BY priority, created_at").bind(user_id).fetch_all(&self.pool).await?;
        Ok(rows
            .iter()
            .map(|r| SavedGoal {
                id: r.get("id"),
                text: r.get("text"),
                priority: if r.get::<String, _>("priority") == "PRIMARY" { GoalPriority::Primary } else { GoalPriority::Secondary },
                created_at_ms: r.get::<DateTime<Utc>, _>("created_at").timestamp_millis(),
            })
            .collect())
    }

    async fn save_analysis(&self, user_id: Uuid, resp: &AnalyzeResponse, snapshot: &ProgressSnapshot) -> StoreResult<()> {
        let a = &resp.analysis;
        let taken = ms_to_dt(snapshot.taken_at_ms);
        let mut tx = self.pool.begin().await?;
        sqlx::query("INSERT INTO minecraft_accounts (uuid, username) VALUES ($1, $2) ON CONFLICT (uuid) DO UPDATE SET username = EXCLUDED.username, updated_at = now()")
            .bind(&a.player.uuid)
            .bind(&a.player.username)
            .execute(&mut *tx)
            .await?;
        sqlx::query("INSERT INTO skyblock_profiles (profile_id, cute_name, game_mode) VALUES ($1, $2, $3) ON CONFLICT (profile_id) DO UPDATE SET cute_name = EXCLUDED.cute_name, game_mode = EXCLUDED.game_mode, updated_at = now()")
            .bind(&a.profile_id)
            .bind(&a.cute_name)
            .bind(&a.game_mode)
            .execute(&mut *tx)
            .await?;
        sqlx::query("INSERT INTO profile_snapshots (user_id, minecraft_uuid, profile_id, taken_at, metrics, analysis) VALUES ($1, $2, $3, $4, $5, $6)")
            .bind(user_id)
            .bind(&a.player.uuid)
            .bind(&a.profile_id)
            .bind(taken)
            .bind(Json(snapshot))
            .bind(Json(resp))
            .execute(&mut *tx)
            .await?;
        sqlx::query("INSERT INTO recommendations (user_id, actions) VALUES ($1, $2)").bind(user_id).bind(Json(&resp.actions)).execute(&mut *tx).await?;
        for g in &resp.goals {
            sqlx::query("INSERT INTO roadmaps (user_id, goal_id, title, overall_progress, payload) VALUES ($1, $2, $3, $4, $5) ON CONFLICT (user_id, goal_id) DO UPDATE SET title = EXCLUDED.title, overall_progress = EXCLUDED.overall_progress, payload = EXCLUDED.payload, updated_at = now()")
                .bind(user_id)
                .bind(&g.id)
                .bind(&g.spec.title)
                .bind(g.roadmap.overall_progress)
                .bind(Json(&g.roadmap))
                .execute(&mut *tx)
                .await?;
            sqlx::query("DELETE FROM goal_nodes WHERE user_id = $1 AND goal_id = $2").bind(user_id).bind(&g.id).execute(&mut *tx).await?;
            for n in &g.graph.nodes {
                sqlx::query("INSERT INTO goal_nodes (user_id, goal_id, node_id, payload) VALUES ($1, $2, $3, $4)").bind(user_id).bind(&g.id).bind(&n.id).bind(Json(n)).execute(&mut *tx).await?;
            }
        }
        if let Some(skills) = a.skills.as_ref() {
            for s in skills {
                sqlx::query("INSERT INTO skills (minecraft_uuid, profile_id, taken_at, skill, level, xp) VALUES ($1, $2, $3, $4, $5, $6) ON CONFLICT DO NOTHING")
                    .bind(&a.player.uuid)
                    .bind(&a.profile_id)
                    .bind(taken)
                    .bind(&s.id)
                    .bind(s.level as i32)
                    .bind(s.xp)
                    .execute(&mut *tx)
                    .await?;
            }
        }
        if let Some(m) = a.mining.as_ref() {
            sqlx::query("INSERT INTO mining_profiles (minecraft_uuid, profile_id, taken_at, hotm, mithril_powder, gemstone_powder, glacite_powder, data) VALUES ($1, $2, $3, $4, $5, $6, $7, $8) ON CONFLICT DO NOTHING")
                .bind(&a.player.uuid)
                .bind(&a.profile_id)
                .bind(taken)
                .bind(m.hotm.as_ref().map(|h| h.level as i32))
                .bind(m.mithril_powder.as_ref().map(|p| p.total))
                .bind(m.gemstone_powder.as_ref().map(|p| p.total))
                .bind(m.glacite_powder.as_ref().map(|p| p.total))
                .bind(Json(m))
                .execute(&mut *tx)
                .await?;
            if let Some(nodes) = m.nodes.as_ref() {
                sqlx::query("INSERT INTO hotm_snapshots (minecraft_uuid, profile_id, taken_at, nodes) VALUES ($1, $2, $3, $4) ON CONFLICT DO NOTHING").bind(&a.player.uuid).bind(&a.profile_id).bind(taken).bind(Json(nodes)).execute(&mut *tx).await?;
            }
        }
        if let Some(sl) = a.slayers.as_ref() {
            for s in sl {
                sqlx::query("INSERT INTO slayer_profiles (minecraft_uuid, profile_id, taken_at, slayer, level, xp, kills) VALUES ($1, $2, $3, $4, $5, $6, $7) ON CONFLICT DO NOTHING")
                    .bind(&a.player.uuid)
                    .bind(&a.profile_id)
                    .bind(taken)
                    .bind(&s.id)
                    .bind(s.level.level as i32)
                    .bind(s.level.xp)
                    .bind(Json(&s.kills_by_tier))
                    .execute(&mut *tx)
                    .await?;
            }
        }
        if let Some(g) = a.garden.as_ref() {
            sqlx::query("INSERT INTO garden_profiles (minecraft_uuid, profile_id, taken_at, garden_level, data) VALUES ($1, $2, $3, $4, $5) ON CONFLICT DO NOTHING")
                .bind(&a.player.uuid)
                .bind(&a.profile_id)
                .bind(taken)
                .bind(g.garden_level.as_ref().map(|l| l.level as i32))
                .bind(Json(g))
                .execute(&mut *tx)
                .await?;
        }
        if let Some(d) = a.dungeons.as_ref() {
            sqlx::query("INSERT INTO dungeon_profiles (minecraft_uuid, profile_id, taken_at, catacombs, class_average, data) VALUES ($1, $2, $3, $4, $5, $6) ON CONFLICT DO NOTHING")
                .bind(&a.player.uuid)
                .bind(&a.profile_id)
                .bind(taken)
                .bind(d.catacombs.level_with_progress)
                .bind(d.class_average)
                .bind(Json(d))
                .execute(&mut *tx)
                .await?;
        }
        if let Some(acc) = a.accessories.as_ref() {
            let ids: Vec<&String> = acc.accessories.iter().map(|x| &x.skyblock_id).collect();
            sqlx::query("INSERT INTO accessory_snapshots (minecraft_uuid, profile_id, taken_at, magical_power, accessories) VALUES ($1, $2, $3, $4, $5) ON CONFLICT DO NOTHING")
                .bind(&a.player.uuid)
                .bind(&a.profile_id)
                .bind(taken)
                .bind(acc.magical_power as i32)
                .bind(Json(ids))
                .execute(&mut *tx)
                .await?;
        }
        tx.commit().await?;
        Ok(())
    }

    async fn latest_analysis(&self, user_id: Uuid) -> StoreResult<Option<(AnalyzeResponse, i64)>> {
        let r = sqlx::query("SELECT analysis, taken_at FROM profile_snapshots WHERE user_id = $1 AND analysis IS NOT NULL ORDER BY taken_at DESC LIMIT 1").bind(user_id).fetch_optional(&self.pool).await?;
        Ok(match r {
            Some(r) => {
                let Json(a): Json<AnalyzeResponse> = r.try_get("analysis")?;
                Some((a, r.get::<DateTime<Utc>, _>("taken_at").timestamp_millis()))
            }
            None => None,
        })
    }

    async fn snapshots(&self, minecraft_uuid: &str, profile_id: &str) -> StoreResult<Vec<ProgressSnapshot>> {
        let rows = sqlx::query("SELECT metrics FROM profile_snapshots WHERE minecraft_uuid = $1 AND profile_id = $2 ORDER BY taken_at ASC LIMIT 2000").bind(minecraft_uuid).bind(profile_id).fetch_all(&self.pool).await?;
        Ok(rows.iter().filter_map(|r| r.try_get::<Json<ProgressSnapshot>, _>("metrics").ok().map(|j| j.0)).collect())
    }

    async fn save_raw_profile(&self, user_id: Uuid, raw: &RawProfile) -> StoreResult<()> {
        sqlx::query(
            "INSERT INTO raw_profiles (user_id, minecraft_uuid, username, profile_id, fetched_at, profiles, museum, garden) VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
             ON CONFLICT (user_id) DO UPDATE SET minecraft_uuid = EXCLUDED.minecraft_uuid, username = EXCLUDED.username, profile_id = EXCLUDED.profile_id, fetched_at = EXCLUDED.fetched_at, profiles = EXCLUDED.profiles, museum = EXCLUDED.museum, garden = EXCLUDED.garden",
        )
        .bind(user_id)
        .bind(&raw.minecraft_uuid)
        .bind(&raw.username)
        .bind(&raw.profile_id)
        .bind(ms_to_dt(raw.fetched_at_ms))
        .bind(&raw.profiles)
        .bind(&raw.museum)
        .bind(&raw.garden)
        .execute(&self.pool)
        .await?;
        Ok(())
    }

    async fn load_raw_profile(&self, user_id: Uuid) -> StoreResult<Option<RawProfile>> {
        let r = sqlx::query("SELECT minecraft_uuid, username, profile_id, fetched_at, profiles, museum, garden FROM raw_profiles WHERE user_id = $1").bind(user_id).fetch_optional(&self.pool).await?;
        Ok(r.map(|r| RawProfile {
            minecraft_uuid: r.get("minecraft_uuid"),
            username: r.get("username"),
            profile_id: r.get("profile_id"),
            fetched_at_ms: r.get::<DateTime<Utc>, _>("fetched_at").timestamp_millis(),
            profiles: r.get("profiles"),
            museum: r.get("museum"),
            garden: r.get("garden"),
        }))
    }

    async fn create_device_code(&self, row: &DeviceCodeRow) -> StoreResult<()> {
        sqlx::query("INSERT INTO device_codes (device_code_hash, user_code, device_name, expires_at) VALUES ($1, $2, $3, $4)")
            .bind(&row.device_code_hash)
            .bind(&row.user_code)
            .bind(&row.device_name)
            .bind(row.expires_at)
            .execute(&self.pool)
            .await?;
        sqlx::query("DELETE FROM device_codes WHERE expires_at < now() - interval '1 hour'").execute(&self.pool).await?;
        Ok(())
    }

    async fn device_code_by_user_code(&self, user_code: &str) -> StoreResult<Option<DeviceCodeRow>> {
        let r = sqlx::query("SELECT * FROM device_codes WHERE user_code = $1").bind(user_code).fetch_optional(&self.pool).await?;
        Ok(r.as_ref().map(device_code_from_row))
    }

    async fn device_code(&self, hash: &str) -> StoreResult<Option<DeviceCodeRow>> {
        let r = sqlx::query("SELECT * FROM device_codes WHERE device_code_hash = $1").bind(hash).fetch_optional(&self.pool).await?;
        Ok(r.as_ref().map(device_code_from_row))
    }

    async fn set_device_code_decision(&self, user_code: &str, user_id: Option<Uuid>, denied: bool) -> StoreResult<()> {
        sqlx::query("UPDATE device_codes SET approved_user_id = $2, denied = $3 WHERE user_code = $1").bind(user_code).bind(user_id).bind(denied).execute(&self.pool).await?;
        Ok(())
    }

    async fn touch_device_code(&self, hash: &str) -> StoreResult<()> {
        sqlx::query("UPDATE device_codes SET last_poll_at = now() WHERE device_code_hash = $1").bind(hash).execute(&self.pool).await?;
        Ok(())
    }

    async fn delete_device_code(&self, hash: &str) -> StoreResult<()> {
        sqlx::query("DELETE FROM device_codes WHERE device_code_hash = $1").bind(hash).execute(&self.pool).await?;
        Ok(())
    }

    async fn create_device_token(&self, id: &str, token_hash: &str, user_id: Uuid, name: &str) -> StoreResult<()> {
        sqlx::query("INSERT INTO device_tokens (id, token_hash, user_id, name) VALUES ($1, $2, $3, $4)").bind(id).bind(token_hash).bind(user_id).bind(name).execute(&self.pool).await?;
        Ok(())
    }

    async fn device_token(&self, token_hash: &str) -> StoreResult<Option<DeviceTokenRow>> {
        let r = sqlx::query("SELECT id, user_id, name, created_at, last_used_at, revoked FROM device_tokens WHERE token_hash = $1").bind(token_hash).fetch_optional(&self.pool).await?;
        Ok(r.as_ref().map(token_from_row))
    }

    async fn touch_device_token(&self, id: &str) -> StoreResult<()> {
        sqlx::query("UPDATE device_tokens SET last_used_at = now() WHERE id = $1").bind(id).execute(&self.pool).await?;
        Ok(())
    }

    async fn list_devices(&self, user_id: Uuid) -> StoreResult<Vec<DeviceTokenRow>> {
        let rows = sqlx::query("SELECT id, user_id, name, created_at, last_used_at, revoked FROM device_tokens WHERE user_id = $1 AND NOT revoked ORDER BY created_at DESC").bind(user_id).fetch_all(&self.pool).await?;
        Ok(rows.iter().map(token_from_row).collect())
    }

    async fn revoke_device(&self, user_id: Uuid, id: &str) -> StoreResult<bool> {
        let r = sqlx::query("UPDATE device_tokens SET revoked = true WHERE id = $1 AND user_id = $2").bind(id).bind(user_id).execute(&self.pool).await?;
        Ok(r.rows_affected() > 0)
    }

    async fn store_sales(&self, sales: &[Sale]) -> StoreResult<()> {
        if sales.is_empty() {
            return Ok(());
        }
        let ids: Vec<String> = sales.iter().map(|s| s.auction_id.clone()).collect();
        let items: Vec<String> = sales.iter().map(|s| s.market_id.clone()).collect();
        let keys: Vec<String> = sales.iter().map(|s| s.comparable_key.clone()).collect();
        let prices: Vec<f64> = sales.iter().map(|s| s.unit_price).collect();
        let counts: Vec<i32> = sales.iter().map(|s| s.count as i32).collect();
        let bins: Vec<bool> = sales.iter().map(|s| s.bin).collect();
        let ts: Vec<DateTime<Utc>> = sales.iter().map(|s| ms_to_dt(s.ts_ms)).collect();
        sqlx::query(
            "INSERT INTO auction_sales (auction_id, item_id, price_key, unit_price, count, bin, sold_at)
             SELECT * FROM UNNEST($1::text[], $2::text[], $3::text[], $4::float8[], $5::int4[], $6::bool[], $7::timestamptz[]) ON CONFLICT DO NOTHING",
        )
        .bind(&ids)
        .bind(&items)
        .bind(&keys)
        .bind(&prices)
        .bind(&counts)
        .bind(&bins)
        .bind(&ts)
        .execute(&self.pool)
        .await?;
        sqlx::query(
            "INSERT INTO item_variants (price_key, item_id) SELECT DISTINCT * FROM UNNEST($1::text[], $2::text[]) ON CONFLICT (price_key) DO UPDATE SET last_seen = now()",
        )
        .bind(&keys)
        .bind(&items)
        .execute(&self.pool)
        .await?;
        Ok(())
    }

    async fn store_bazaar(&self, resp: &BazaarResponse) -> StoreResult<()> {
        let ts = ms_to_dt(resp.last_updated);
        let mut ids = Vec::new();
        let mut buy = Vec::new();
        let mut sell = Vec::new();
        let mut bv = Vec::new();
        let mut sv = Vec::new();
        let mut bmw = Vec::new();
        let mut smw = Vec::new();
        let mut bo = Vec::new();
        let mut so = Vec::new();
        for (id, p) in &resp.products {
            ids.push(id.clone());
            buy.push(p.buy_summary.first().map(|o| o.price_per_unit).unwrap_or(p.quick_status.buy_price));
            sell.push(p.sell_summary.first().map(|o| o.price_per_unit).unwrap_or(p.quick_status.sell_price));
            bv.push(p.quick_status.buy_volume);
            sv.push(p.quick_status.sell_volume);
            bmw.push(p.quick_status.buy_moving_week);
            smw.push(p.quick_status.sell_moving_week);
            bo.push(p.quick_status.buy_orders as i32);
            so.push(p.quick_status.sell_orders as i32);
        }
        let tss: Vec<DateTime<Utc>> = vec![ts; ids.len()];
        sqlx::query(
            "INSERT INTO bazaar_snapshots (product_id, ts, buy, sell, buy_volume, sell_volume, buy_moving_week, sell_moving_week, buy_orders, sell_orders)
             SELECT * FROM UNNEST($1::text[], $2::timestamptz[], $3::float8[], $4::float8[], $5::float8[], $6::float8[], $7::float8[], $8::float8[], $9::int4[], $10::int4[])
             ON CONFLICT DO NOTHING",
        )
        .bind(&ids)
        .bind(&tss)
        .bind(&buy)
        .bind(&sell)
        .bind(&bv)
        .bind(&sv)
        .bind(&bmw)
        .bind(&smw)
        .bind(&bo)
        .bind(&so)
        .execute(&self.pool)
        .await?;
        Ok(())
    }

    async fn store_candidates(&self, c: &[CandidateRecord]) -> StoreResult<()> {
        if c.is_empty() {
            return Ok(());
        }
        let ts: Vec<DateTime<Utc>> = c.iter().map(|x| ms_to_dt(x.ts_ms)).collect();
        let keys: Vec<String> = c.iter().map(|x| x.comparable_key.clone()).collect();
        let items: Vec<String> = c.iter().map(|x| x.market_id.clone()).collect();
        let buy: Vec<f64> = c.iter().map(|x| x.buy_price).collect();
        let sell: Vec<f64> = c.iter().map(|x| x.recommended_sell).collect();
        let fair: Vec<f64> = c.iter().map(|x| x.fair_value).collect();
        let roi: Vec<f64> = c.iter().map(|x| x.roi).collect();
        let profit: Vec<f64> = c.iter().map(|x| x.expected_profit).collect();
        let spd: Vec<f64> = c.iter().map(|x| x.sales_per_day).collect();
        let conf: Vec<f64> = c.iter().map(|x| x.confidence).collect();
        sqlx::query(
            "INSERT INTO flip_candidates (ts, price_key, item_id, buy_price, recommended_sell, fair_value, roi, expected_profit, sales_per_day, confidence)
             SELECT * FROM UNNEST($1::timestamptz[], $2::text[], $3::text[], $4::float8[], $5::float8[], $6::float8[], $7::float8[], $8::float8[], $9::float8[], $10::float8[])",
        )
        .bind(&ts)
        .bind(&keys)
        .bind(&items)
        .bind(&buy)
        .bind(&sell)
        .bind(&fair)
        .bind(&roi)
        .bind(&profit)
        .bind(&spd)
        .bind(&conf)
        .execute(&self.pool)
        .await?;
        Ok(())
    }

    async fn load_sales_since(&self, since_ms: i64) -> StoreResult<Vec<Sale>> {
        let rows = sqlx::query("SELECT auction_id, item_id, price_key, unit_price, count, bin, sold_at FROM auction_sales WHERE sold_at >= $1 ORDER BY sold_at ASC LIMIT 2000000")
            .bind(ms_to_dt(since_ms))
            .fetch_all(&self.pool)
            .await?;
        Ok(rows
            .iter()
            .map(|r| Sale {
                auction_id: r.get("auction_id"),
                market_id: r.get("item_id"),
                comparable_key: r.get("price_key"),
                unit_price: r.get("unit_price"),
                count: r.get::<i32, _>("count").max(1) as u32,
                bin: r.get("bin"),
                ts_ms: r.get::<DateTime<Utc>, _>("sold_at").timestamp_millis(),
            })
            .collect())
    }

    async fn load_bazaar_since(&self, since_ms: i64) -> StoreResult<Vec<(String, BazaarPoint)>> {
        // one point per product per 15 minutes is enough to seed trend/volatility
        let rows = sqlx::query(
            "SELECT DISTINCT ON (product_id, date_trunc('hour', ts), (extract(minute from ts)::int / 15)) product_id, ts, buy, sell, buy_volume, sell_volume, buy_moving_week, sell_moving_week
             FROM bazaar_snapshots WHERE ts >= $1 ORDER BY product_id, date_trunc('hour', ts), (extract(minute from ts)::int / 15), ts DESC",
        )
        .bind(ms_to_dt(since_ms))
        .fetch_all(&self.pool)
        .await?;
        let mut out: Vec<(String, BazaarPoint)> = rows
            .iter()
            .map(|r| {
                (
                    r.get("product_id"),
                    BazaarPoint {
                        ts_ms: r.get::<DateTime<Utc>, _>("ts").timestamp_millis(),
                        buy: r.get::<Option<f64>, _>("buy").unwrap_or(0.0),
                        sell: r.get::<Option<f64>, _>("sell").unwrap_or(0.0),
                        buy_volume: r.get::<Option<f64>, _>("buy_volume").unwrap_or(0.0),
                        sell_volume: r.get::<Option<f64>, _>("sell_volume").unwrap_or(0.0),
                        buy_moving_week: r.get::<Option<f64>, _>("buy_moving_week").unwrap_or(0.0),
                        sell_moving_week: r.get::<Option<f64>, _>("sell_moving_week").unwrap_or(0.0),
                    },
                )
            })
            .collect();
        out.sort_by_key(|(_, p)| p.ts_ms);
        Ok(out)
    }

    async fn load_candidates_since(&self, since_ms: i64) -> StoreResult<Vec<CandidateRecord>> {
        let rows = sqlx::query("SELECT ts, price_key, item_id, buy_price, recommended_sell, fair_value, roi, expected_profit, sales_per_day, confidence FROM flip_candidates WHERE ts >= $1 ORDER BY ts ASC LIMIT 500000")
            .bind(ms_to_dt(since_ms))
            .fetch_all(&self.pool)
            .await?;
        Ok(rows
            .iter()
            .map(|r| CandidateRecord {
                ts_ms: r.get::<DateTime<Utc>, _>("ts").timestamp_millis(),
                comparable_key: r.get("price_key"),
                market_id: r.get("item_id"),
                buy_price: r.get("buy_price"),
                recommended_sell: r.get("recommended_sell"),
                fair_value: r.get("fair_value"),
                roi: r.get("roi"),
                expected_profit: r.get("expected_profit"),
                sales_per_day: r.get("sales_per_day"),
                confidence: r.get("confidence"),
            })
            .collect())
    }

    async fn price_history(&self, item_id: &str, since_ms: i64, bucket_ms: i64) -> StoreResult<Vec<PricePoint>> {
        let secs = (bucket_ms / 1000).max(60) as f64;
        // Bazaar products: mid price; AH items: sale percentiles.
        let bz = sqlx::query(
            "SELECT to_timestamp(floor(extract(epoch from ts) / $3) * $3) AS b,
                    percentile_cont(0.5) WITHIN GROUP (ORDER BY (buy + sell) / 2) AS median, min(sell) AS low, max(buy) AS high, avg(buy_moving_week) / 168 AS volume
             FROM bazaar_snapshots WHERE product_id = $1 AND ts >= $2 GROUP BY b ORDER BY b",
        )
        .bind(item_id)
        .bind(ms_to_dt(since_ms))
        .bind(secs)
        .fetch_all(&self.pool)
        .await?;
        let rows = if !bz.is_empty() {
            bz
        } else {
            sqlx::query(
                "SELECT to_timestamp(floor(extract(epoch from sold_at) / $3) * $3) AS b,
                        percentile_cont(0.5) WITHIN GROUP (ORDER BY unit_price) AS median,
                        percentile_cont(0.1) WITHIN GROUP (ORDER BY unit_price) AS low,
                        percentile_cont(0.9) WITHIN GROUP (ORDER BY unit_price) AS high, count(*)::float8 AS volume
                 FROM auction_sales WHERE (item_id = $1 OR price_key = $1) AND sold_at >= $2 GROUP BY b ORDER BY b",
            )
            .bind(item_id)
            .bind(ms_to_dt(since_ms))
            .bind(secs)
            .fetch_all(&self.pool)
            .await?
        };
        Ok(rows
            .iter()
            .map(|r| PricePoint {
                t: r.get::<DateTime<Utc>, _>("b").timestamp_millis(),
                median: r.get("median"),
                low: r.get("low"),
                high: r.get("high"),
                volume: r.get::<Option<f64>, _>("volume").unwrap_or(0.0),
            })
            .collect())
    }

    async fn notification_rules(&self, user_id: Uuid) -> StoreResult<Vec<NotificationRule>> {
        let rows = sqlx::query("SELECT rule FROM notification_rules WHERE user_id = $1").bind(user_id).fetch_all(&self.pool).await?;
        Ok(rows.iter().filter_map(|r| r.try_get::<Json<NotificationRule>, _>("rule").ok().map(|j| j.0)).collect())
    }

    async fn all_notification_rules(&self) -> StoreResult<Vec<(Uuid, NotificationRule)>> {
        let rows = sqlx::query("SELECT user_id, rule FROM notification_rules").fetch_all(&self.pool).await?;
        Ok(rows.iter().filter_map(|r| Some((r.get("user_id"), r.try_get::<Json<NotificationRule>, _>("rule").ok()?.0))).collect())
    }

    async fn save_notification_rule(&self, user_id: Uuid, rule: &NotificationRule) -> StoreResult<()> {
        sqlx::query("INSERT INTO notification_rules (user_id, id, rule) VALUES ($1, $2, $3) ON CONFLICT (user_id, id) DO UPDATE SET rule = EXCLUDED.rule")
            .bind(user_id)
            .bind(&rule.id)
            .bind(Json(rule))
            .execute(&self.pool)
            .await?;
        Ok(())
    }

    async fn delete_notification_rule(&self, user_id: Uuid, id: &str) -> StoreResult<()> {
        sqlx::query("DELETE FROM notification_rules WHERE user_id = $1 AND id = $2").bind(user_id).bind(id).execute(&self.pool).await?;
        Ok(())
    }

    async fn save_push_subscription(&self, user_id: Uuid, sub: &PushSubscription) -> StoreResult<()> {
        sqlx::query("INSERT INTO push_subscriptions (endpoint, user_id, p256dh, auth) VALUES ($1, $2, $3, $4) ON CONFLICT (endpoint) DO UPDATE SET user_id = EXCLUDED.user_id, p256dh = EXCLUDED.p256dh, auth = EXCLUDED.auth")
            .bind(&sub.endpoint)
            .bind(user_id)
            .bind(&sub.p256dh)
            .bind(&sub.auth)
            .execute(&self.pool)
            .await?;
        Ok(())
    }

    async fn push_subscriptions(&self, user_id: Uuid) -> StoreResult<Vec<PushSubscription>> {
        let rows = sqlx::query("SELECT endpoint, p256dh, auth FROM push_subscriptions WHERE user_id = $1").bind(user_id).fetch_all(&self.pool).await?;
        Ok(rows.iter().map(|r| PushSubscription { endpoint: r.get("endpoint"), p256dh: r.get("p256dh"), auth: r.get("auth") }).collect())
    }

    async fn delete_push_subscription(&self, endpoint: &str) -> StoreResult<()> {
        sqlx::query("DELETE FROM push_subscriptions WHERE endpoint = $1").bind(endpoint).execute(&self.pool).await?;
        Ok(())
    }

    async fn save_session_stats(&self, user_id: Uuid, device_id: &str, started_ms: i64, ended_ms: i64, stats: &serde_json::Value) -> StoreResult<()> {
        sqlx::query("INSERT INTO session_stats (user_id, device_id, started_at, ended_at, stats) VALUES ($1, $2, $3, $4, $5)")
            .bind(user_id)
            .bind(device_id)
            .bind(ms_to_dt(started_ms))
            .bind(ms_to_dt(ended_ms))
            .bind(Json(stats))
            .execute(&self.pool)
            .await?;
        Ok(())
    }

    async fn user_settings(&self, user_id: Uuid) -> StoreResult<Option<serde_json::Value>> {
        let row = sqlx::query("SELECT settings FROM user_settings WHERE user_id = $1").bind(user_id).fetch_optional(&self.pool).await?;
        Ok(row.map(|r| r.get::<Json<serde_json::Value>, _>("settings").0))
    }

    async fn save_user_settings(&self, user_id: Uuid, settings: &serde_json::Value) -> StoreResult<()> {
        sqlx::query("INSERT INTO user_settings (user_id, settings, updated_at) VALUES ($1, $2, now()) ON CONFLICT (user_id) DO UPDATE SET settings = EXCLUDED.settings, updated_at = now()")
            .bind(user_id)
            .bind(Json(settings))
            .execute(&self.pool)
            .await?;
        Ok(())
    }
}
