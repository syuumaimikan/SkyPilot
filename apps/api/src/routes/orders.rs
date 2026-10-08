use crate::{
    auth,
    error::{ApiResult, AppError},
    state::AppState,
};
use axum::{Json, extract::State, http::HeaderMap};
use axum_extra::extract::cookie::CookieJar;
use serde::{Deserialize, Serialize};
use serde_json::json;
use std::sync::Arc;

#[derive(Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Order {
    pub name: String,
    pub side: String,
    pub price: f64,
    pub status: String,
}
#[derive(Deserialize)]
pub struct Snapshot {
    pub source: String,
    pub orders: Vec<Order>,
}

fn valid(s: &Snapshot) -> bool {
    ["ah", "bazaar"].contains(&s.source.as_str())
        && s.orders.len() <= 100
        && s.orders.iter().all(|o| {
            !o.name.trim().is_empty()
                && o.name.len() <= 256
                && ["BUY", "SELL"].contains(&o.side.as_str())
                && o.price.is_finite()
                && o.price >= 0.0
                && o.price <= 1e15
                && ["ACTIVE", "FILLED", "SOLD", "EXPIRED", "UNKNOWN"].contains(&o.status.as_str())
        })
}
/// Only a linked device can upload observations; identity always comes from its token.
pub async fn save(
    State(state): State<Arc<AppState>>,
    headers: HeaderMap,
    Json(s): Json<Snapshot>,
) -> ApiResult<Json<serde_json::Value>> {
    let (_, user) = auth::bearer(&state, &headers)
        .await?
        .ok_or_else(AppError::unauthorized)?;
    if !valid(&s) {
        return Err(AppError::bad_request("Invalid order snapshot"));
    }
    let at = chrono::Utc::now().timestamp_millis();
    state
        .store
        .save_orders(
            user.id,
            &s.source,
            &json!({"orders":s.orders,"observedAtMs":at,"source":"MOD_MENU","partial":true}),
        )
        .await?;
    Ok(Json(json!({"observedAtMs":at})))
}
pub async fn get(
    State(state): State<Arc<AppState>>,
    jar: CookieJar,
    headers: HeaderMap,
) -> ApiResult<Json<serde_json::Value>> {
    let user = auth::any_user(&state, &jar, &headers).await?;
    let mut ah = state.store.orders(user.id, "ah").await?;
    let now = chrono::Utc::now().timestamp_millis();
    let mut ah_error = None;
    if let Some(uuid) = &user.minecraft_uuid {
        if ah
            .as_ref()
            .and_then(|v| v["observedAtMs"].as_i64())
            .is_none_or(|at| now - at > 60_000)
        {
            match state.hypixel.player_auctions(uuid).await {
                Ok(v) => {
                    let rows:Vec<_>=v["auctions"].as_array().into_iter().flatten().take(100).map(|a|{
                        let end=a["end"].as_i64().unwrap_or(i64::MAX);
                        let bid=a["highest_bid_amount"].as_f64().unwrap_or(0.0);
                        let status=if end<=now {if bid>0.0 {"SOLD"}else{"EXPIRED"}}else{"ACTIVE"};
                        json!({"name":a["item_name"].as_str().unwrap_or("Unknown"),"side":"SELL","price":a["starting_bid"].as_f64().unwrap_or(0.0),"status":status})
                    }).collect();
                    let snapshot = json!({"orders":rows,"observedAtMs":now,"source":"HYPIXEL_API"});
                    state.store.save_orders(user.id, "ah", &snapshot).await?;
                    ah = Some(snapshot);
                }
                Err(_) => ah_error = Some("AH API unavailable; showing last observation"),
            }
        }
    }
    Ok(Json(
        json!({"ah":ah,"bazaar":state.store.orders(user.id,"bazaar").await?,"ahError":ah_error}),
    ))
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn rejects_invalid_prices_and_status() {
        let mut s = Snapshot {
            source: "bazaar".into(),
            orders: vec![Order {
                name: "Melon".into(),
                side: "SELL".into(),
                price: 2.0,
                status: "ACTIVE".into(),
            }],
        };
        assert!(valid(&s));
        s.orders[0].price = f64::NAN;
        assert!(!valid(&s));
        s.orders[0].price = 2.0;
        s.orders[0].status = "claimed".into();
        assert!(!valid(&s));
    }
    #[tokio::test]
    async fn observations_are_isolated_by_user_and_market() {
        use crate::store::{Store, memory::MemoryStore};
        let store = MemoryStore::default();
        let a = store.create_user().await.unwrap();
        let b = store.create_user().await.unwrap();
        store
            .save_orders(a, "bazaar", &json!({"orders":[{"name":"Melon"}]}))
            .await
            .unwrap();
        assert!(store.orders(a, "bazaar").await.unwrap().is_some());
        assert!(store.orders(b, "bazaar").await.unwrap().is_none());
        assert!(store.orders(a, "ah").await.unwrap().is_none());
    }
}
