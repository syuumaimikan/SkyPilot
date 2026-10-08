pub mod advisor;
pub mod analyze;
pub mod auth_routes;
pub mod launcher;
pub mod market;
pub mod orders;
pub mod misc;
pub mod modapi;
pub mod modfit;
pub mod modlist;
pub mod settings;
pub mod status_page;

use std::net::SocketAddr;
use std::sync::Arc;
use std::time::Duration;

use axum::Router;
use axum::extract::{ConnectInfo, Request, State};
use axum::http::{HeaderName, HeaderValue, Method, header};
use axum::middleware::{self, Next};
use axum::response::{IntoResponse, Response};
use axum::routing::{delete, get, post, put};
use tower_http::compression::CompressionLayer;
use tower_http::cors::CorsLayer;
use tower_http::limit::RequestBodyLimitLayer;
use tower_http::set_header::SetResponseHeaderLayer;
use tower_http::trace::TraceLayer;

use crate::auth;
use crate::error::AppError;
use crate::state::AppState;

async fn rate_limit(State(state): State<Arc<AppState>>, ConnectInfo(peer): ConnectInfo<SocketAddr>, req: Request, next: Next) -> Response {
    let ip = auth::client_ip(&state, req.headers(), peer);
    if state.limiters.general.check_key(&ip).is_err() {
        return AppError::too_many().into_response();
    }
    next.run(req).await
}

pub fn router(state: Arc<AppState>) -> Router {
    let api = Router::new()
        .route("/health", get(misc::health))
        .route("/status", get(misc::status))
        // auth & devices
        .route("/auth/me", get(auth_routes::me))
        .route("/auth/register", post(auth_routes::register))
        .route("/auth/login", post(auth_routes::login))
        .route("/auth/logout", post(auth_routes::logout))
        .route("/devices/{id}", delete(auth_routes::revoke_device))
        .route("/device/code", post(auth_routes::device_code))
        .route("/device/token", post(auth_routes::device_token))
        .route("/device/lookup", get(auth_routes::device_lookup))
        .route("/device/approve", post(auth_routes::device_approve))
        // analysis & workspace
        .route("/players/{name}/profiles", get(analyze::profiles))
        .route("/analyze", post(analyze::analyze_handler))
        .route("/workspace", get(analyze::workspace))
        .route("/workspace/goals", put(analyze::set_goals))
        .route("/workspace/prefs", put(analyze::prefs))
        .route("/snapshots", get(analyze::snapshots))
        // advisors
        .route("/advisor", get(advisor::domains))
        .route("/advisor/report/{domain}", get(advisor::report))
        .route("/advisor/mining/calc", get(advisor::mining_calc))
        .route("/advisor/mining/hotm", get(advisor::hotm))
        .route("/advisor/mining/powder", get(advisor::powder))
        .route("/advisor/gear", get(advisor::gear))
        .route("/advisor/slayer/plan", get(advisor::slayer_plan))
        .route("/advisor/money", get(advisor::money))
        .route("/advisor/dungeons/floors", get(advisor::floors))
        .route("/advisor/farming/crops", get(advisor::crops))
        .route("/advisor/liquidation", get(advisor::liquidation))
        .route("/farms", get(advisor::farms))
        .route("/farms/{id}", get(advisor::farm))
        // market
        .route("/market/status", get(market::status))
        .route("/market/orders", get(orders::get))
        .route("/mod/orders", post(orders::save))
        .route("/market/flips", get(market::flips))
        .route("/market/bazaar", get(market::bazaar))
        .route("/market/craft", get(market::craft))
        .route("/market/time", get(market::time_flips))
        .route("/market/search", get(market::search))
        .route("/market/items/{id}", get(market::item))
        .route("/market/backtest", post(market::backtest))
        .route("/ws/market", get(market::ws))
        // notifications
        .route("/notifications/rules", get(misc::rules).put(misc::save_rule))
        .route("/notifications/rules/{id}", delete(misc::delete_rule))
        .route("/notifications/push/key", get(misc::push_key))
        .route("/notifications/push/subscribe", post(misc::push_subscribe))
        .route("/notifications/push/unsubscribe", post(misc::push_unsubscribe))
        .route("/notifications/push/test", post(misc::push_test))
        // assistant & translation
        .route("/assistant/chat", post(misc::chat))
        .route("/me/settings", get(settings::get))
        .route("/me/settings/translation", put(settings::put_translation))
        .route("/me/settings/translation/test", post(settings::test_translation))
        .route("/translate", post(misc::translate))
        .route("/translate/dictionary", get(misc::dictionary))
        .route("/translate/status", get(misc::translation_status))
        // mod
        .route("/mod/hud", get(modapi::hud_state))
        .route("/mod/price", post(modapi::price))
        .route("/mod/text/{kind}", get(modapi::text))
        .route("/mod/session", post(modapi::session))
        .route("/mod/market", get(modapi::market))
        .route("/mod/bazaar/quotes", get(modapi::bazaar_quotes))
        .route("/launcher/latest", get(launcher::latest))
        .route("/launcher/files/{name}", get(launcher::file))
        .route("/mod/plan", get(modapi::plan))
        .route("/mod/alerts", get(modapi::alerts))
        .route("/mod/icons", get(modapi::icons))
        .route("/mod/items", get(modapi::items))
        .route("/mod/item/{id}", get(modapi::item))
        .route("/mod/values", post(modapi::values))
        .route("/mod/guides", get(modapi::guides))
        .route("/mod/locations", get(modapi::locations))
        .route("/mod/gear", get(modapi::gear))
        .route("/mod/calendar", get(modapi::calendar))
        .route("/mod/sea-creatures", get(modapi::sea_creatures))
        .route("/mod/fit", get(modfit::fit))
        .route("/mod/builds", get(modfit::builds))
        .route("/mod/money", get(modfit::money))
        .route("/mod/puzzles", get(modfit::puzzles))
        .route("/mod/skyblock", get(modfit::skyblock_now))
        .route("/mod/news", get(modfit::news))
        .route("/mod/server-status", get(status_page::server_status))
        .route("/mod/mod-updates", post(modlist::mod_updates))
        .layer(middleware::from_fn_with_state(state.clone(), rate_limit));

    let origin: HeaderValue = state.config.public_web_url.trim_end_matches('/').parse().unwrap_or(HeaderValue::from_static("http://localhost:3000"));
    let cors = CorsLayer::new()
        .allow_origin(origin)
        .allow_credentials(true)
        .allow_methods([Method::GET, Method::POST, Method::PUT, Method::DELETE])
        .allow_headers([header::CONTENT_TYPE, header::AUTHORIZATION, HeaderName::from_static(auth::CSRF_HEADER)])
        .max_age(Duration::from_secs(600));

    Router::new()
        .nest("/api", api)
        .layer(RequestBodyLimitLayer::new(256 * 1024))
        .layer(CompressionLayer::new())
        .layer(cors)
        .layer(SetResponseHeaderLayer::overriding(header::X_CONTENT_TYPE_OPTIONS, HeaderValue::from_static("nosniff")))
        .layer(SetResponseHeaderLayer::overriding(header::X_FRAME_OPTIONS, HeaderValue::from_static("DENY")))
        .layer(SetResponseHeaderLayer::overriding(header::REFERRER_POLICY, HeaderValue::from_static("no-referrer")))
        .layer(SetResponseHeaderLayer::overriding(header::CONTENT_SECURITY_POLICY, HeaderValue::from_static("default-src 'none'; frame-ancestors 'none'")))
        .layer(SetResponseHeaderLayer::if_not_present(header::CACHE_CONTROL, HeaderValue::from_static("no-store")))
        .layer(TraceLayer::new_for_http())
        .with_state(state)
}
