//! Local SkyPilot Web Dashboard and root router handler.

use std::sync::Arc;
use axum::extract::State;
use axum::http::{HeaderMap, header};
use axum::response::{Html, IntoResponse, Response};
use serde_json::json;

use crate::state::AppState;

pub async fn root_handler(headers: HeaderMap, State(state): State<Arc<AppState>>) -> Response {
    let accept = headers
        .get(header::ACCEPT)
        .and_then(|v| v.to_str().ok())
        .unwrap_or("");

    // If client specifically asks for JSON without HTML, return JSON
    if accept.contains("application/json") && !accept.contains("text/html") {
        return axum::Json(json!({
            "ok": true,
            "service": "skypilot-api",
            "version": env!("CARGO_PKG_VERSION"),
            "status": "online",
            "dashboard": "/",
            "endpoints": {
                "health": "/api/health",
                "status": "/api/status",
                "settings_apis": "/api/settings/apis",
                "hypixel_app": "/api/settings/hypixel-app",
                "market_status": "/api/market/status"
            }
        })).into_response();
    }

    // Default to the Web Dashboard HTML
    dashboard_handler(State(state)).await.into_response()
}

pub async fn dashboard_handler(State(state): State<Arc<AppState>>) -> Html<String> {
    let reg = state.registry();
    let (remaining, limit) = state.hypixel.key_budget();
    let remaining_str = remaining.map(|r| r.to_string()).unwrap_or_else(|| "N/A".to_string());
    let limit_str = limit.map(|l| l.to_string()).unwrap_or_else(|| "N/A".to_string());
    let has_hypixel = state.hypixel.has_key();
    let breaker = state.hypixel.breaker_state().as_str().to_string();
    let trans_status = state.translator.status();
    let llm_enabled = state.llm.read().unwrap().is_some();
    let llm_model = state.llm.read().unwrap().as_ref().map(|c| c.model.clone()).unwrap_or_else(|| "none".to_string());
    let items_count = reg.items.len();
    let version = env!("CARGO_PKG_VERSION");
    let api_port = state.config.bind.port();

    let html = format!(r#"<!DOCTYPE html>
<html lang="ja">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>SkyPilot Server Dashboard</title>
    <style>
        :root {{
            --bg: #0c0f17;
            --surface: #141a29;
            --surface-hover: #1b2337;
            --border: #232d44;
            --primary: #3b82f6;
            --primary-glow: rgba(59, 130, 246, 0.25);
            --success: #10b981;
            --warning: #f59e0b;
            --danger: #ef4444;
            --text: #f3f4f6;
            --text-dim: #9ca3af;
        }}
        * {{ box-sizing: border-box; margin: 0; padding: 0; }}
        body {{
            background: var(--bg);
            color: var(--text);
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", sans-serif;
            line-height: 1.5;
            padding: 2rem 1.5rem;
        }}
        .container {{
            max-width: 1000px;
            margin: 0 auto;
        }}
        header {{
            display: flex;
            justify-content: space-between;
            align-items: center;
            padding-bottom: 1.5rem;
            margin-bottom: 2rem;
            border-bottom: 1px solid var(--border);
        }}
        .brand {{
            display: flex;
            align-items: center;
            gap: 0.75rem;
        }}
        .logo-icon {{
            width: 40px;
            height: 40px;
            background: linear-gradient(135deg, #2563eb, #60a5fa);
            border-radius: 10px;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 20px;
            box-shadow: 0 4px 12px var(--primary-glow);
        }}
        h1 {{
            font-size: 1.5rem;
            font-weight: 700;
            letter-spacing: -0.02em;
        }}
        .version-badge {{
            font-size: 0.75rem;
            background: var(--surface);
            border: 1px solid var(--border);
            padding: 2px 8px;
            border-radius: 9999px;
            color: var(--text-dim);
            margin-left: 0.5rem;
        }}
        .status-pill {{
            display: inline-flex;
            align-items: center;
            gap: 6px;
            background: rgba(16, 185, 129, 0.1);
            color: var(--success);
            border: 1px solid rgba(16, 185, 129, 0.3);
            padding: 6px 14px;
            border-radius: 9999px;
            font-size: 0.875rem;
            font-weight: 600;
        }}
        .status-dot {{
            width: 8px;
            height: 8px;
            border-radius: 50%;
            background: var(--success);
            box-shadow: 0 0 8px var(--success);
        }}
        .grid {{
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
            gap: 1.25rem;
            margin-bottom: 2rem;
        }}
        .card {{
            background: var(--surface);
            border: 1px solid var(--border);
            border-radius: 12px;
            padding: 1.25rem;
            transition: border-color 0.2s;
        }}
        .card:hover {{
            border-color: var(--primary);
        }}
        .card-header {{
            display: flex;
            align-items: center;
            justify-content: space-between;
            margin-bottom: 1rem;
        }}
        .card-title {{
            font-size: 1rem;
            font-weight: 600;
            display: flex;
            align-items: center;
            gap: 0.5rem;
        }}
        .stat-value {{
            font-size: 1.5rem;
            font-weight: 700;
            margin-bottom: 0.25rem;
        }}
        .stat-sub {{
            font-size: 0.85rem;
            color: var(--text-dim);
        }}
        .badge {{
            display: inline-block;
            font-size: 0.75rem;
            padding: 2px 8px;
            border-radius: 6px;
            font-weight: 600;
        }}
        .badge-success {{ background: rgba(16, 185, 129, 0.15); color: var(--success); }}
        .badge-warning {{ background: rgba(245, 158, 11, 0.15); color: var(--warning); }}
        .badge-primary {{ background: rgba(59, 130, 246, 0.15); color: var(--primary); }}
        .section-title {{
            font-size: 1.15rem;
            font-weight: 600;
            margin: 1.5rem 0 1rem;
            display: flex;
            align-items: center;
            gap: 0.5rem;
        }}
        .endpoint-list {{
            display: flex;
            flex-direction: column;
            gap: 0.5rem;
        }}
        .endpoint-item {{
            display: flex;
            justify-content: space-between;
            align-items: center;
            padding: 0.75rem 1rem;
            background: var(--surface);
            border: 1px solid var(--border);
            border-radius: 8px;
            text-decoration: none;
            color: var(--text);
            transition: all 0.2s;
        }}
        .endpoint-item:hover {{
            background: var(--surface-hover);
            border-color: var(--primary);
            color: #fff;
        }}
        .method {{
            font-size: 0.75rem;
            font-weight: 700;
            color: var(--primary);
            margin-right: 0.75rem;
        }}
        .btn {{
            display: inline-flex;
            align-items: center;
            gap: 0.5rem;
            padding: 0.6rem 1.2rem;
            border-radius: 8px;
            font-weight: 600;
            font-size: 0.875rem;
            cursor: pointer;
            text-decoration: none;
            border: none;
            transition: all 0.2s;
        }}
        .btn-primary {{
            background: var(--primary);
            color: #fff;
        }}
        .btn-primary:hover {{
            opacity: 0.9;
        }}
        .btn-secondary {{
            background: var(--surface);
            border: 1px solid var(--border);
            color: var(--text);
        }}
        .btn-secondary:hover {{
            background: var(--surface-hover);
        }}
        .actions {{
            display: flex;
            gap: 0.75rem;
            flex-wrap: wrap;
            margin-top: 1rem;
        }}
        .info-box {{
            background: rgba(59, 130, 246, 0.08);
            border: 1px solid rgba(59, 130, 246, 0.25);
            border-radius: 10px;
            padding: 1rem 1.25rem;
            margin-bottom: 1.5rem;
        }}
        .info-box h3 {{
            font-size: 0.95rem;
            color: var(--primary);
            margin-bottom: 0.4rem;
        }}
        .info-box p {{
            font-size: 0.85rem;
            color: var(--text-dim);
        }}
        footer {{
            margin-top: 3rem;
            padding-top: 1.5rem;
            border-top: 1px solid var(--border);
            display: flex;
            justify-content: space-between;
            align-items: center;
            font-size: 0.85rem;
            color: var(--text-dim);
        }}
    </style>
</head>
<body>
    <div class="container">
        <header>
            <div class="brand">
                <div class="logo-icon">✈</div>
                <div>
                    <h1>SkyPilot Server <span class="version-badge">v{version}</span></h1>
                    <p style="font-size: 0.85rem; color: var(--text-dim);">Standalone Native Server for Hypixel SkyBlock Copilot</p>
                </div>
            </div>
            <div class="status-pill">
                <div class="status-dot"></div>
                <span>Server Online</span>
            </div>
        </header>

        <div class="info-box">
            <h3>🎮 Minecraft MOD &amp; ランチャー 接続情報</h3>
            <p>本サーバーはローカルで正常に稼働しています。Minecraft ゲーム内MODやSkyPilotランチャーは Web UI（<code>http://localhost:3000</code>）または API（<code>http://localhost:{api_port}/api</code>）経由で連携します。</p>
        </div>

        <div class="grid">
            <!-- Hypixel API -->
            <div class="card">
                <div class="card-header">
                    <span class="card-title">⚔ Hypixel API</span>
                    <span class="badge {hypixel_badge}">{hypixel_badge_text}</span>
                </div>
                <div class="stat-value">{hypixel_stat}</div>
                <div class="stat-sub">Budget: {remaining} / {limit} requests &bull; Circuit: {breaker}</div>
                <div class="actions">
                    <a href="/api/settings/hypixel-app" class="btn btn-secondary" style="font-size: 0.8rem; padding: 0.4rem 0.8rem;">申請ガイド</a>
                </div>
            </div>

            <!-- Translation -->
            <div class="card">
                <div class="card-header">
                    <span class="card-title">🌐 チャット翻訳 (Translation)</span>
                    <span class="badge badge-primary">{trans_provider}</span>
                </div>
                <div class="stat-value">{trans_name}</div>
                <div class="stat-sub">Circuit: {trans_breaker} &bull; Japanese &harr; English</div>
                <div class="actions">
                    <a href="/api/translate/status" class="btn btn-secondary" style="font-size: 0.8rem; padding: 0.4rem 0.8rem;">翻訳ステータス</a>
                </div>
            </div>

            <!-- AI Copilot -->
            <div class="card">
                <div class="card-header">
                    <span class="card-title">🤖 AI Copilot (LLM)</span>
                    <span class="badge {llm_badge}">{llm_badge_text}</span>
                </div>
                <div class="stat-value">{llm_stat}</div>
                <div class="stat-sub">Model: {llm_model} &bull; Deterministic Fallback Active</div>
                <div class="actions">
                    <a href="/api/settings/apis" class="btn btn-secondary" style="font-size: 0.8rem; padding: 0.4rem 0.8rem;">API設定</a>
                </div>
            </div>

            <!-- Registry & Items -->
            <div class="card">
                <div class="card-header">
                    <span class="card-title">📦 SkyBlock アイテム辞書</span>
                    <span class="badge badge-success">Loaded</span>
                </div>
                <div class="stat-value">{items_count} <span style="font-size: 1rem; font-weight: normal;">items</span></div>
                <div class="stat-sub">Hypixel + NEU-REPO Sync Active</div>
                <div class="actions">
                    <a href="/api/mod/items" class="btn btn-secondary" style="font-size: 0.8rem; padding: 0.4rem 0.8rem;">アイテムAPI</a>
                </div>
            </div>
        </div>

        <div class="section-title">🔗 主な API エンドポイント</div>
        <div class="endpoint-list">
            <a href="/api/status" class="endpoint-item" target="_blank">
                <div><span class="method">GET</span>/api/status</div>
                <div style="color: var(--text-dim); font-size: 0.85rem;">サーバー全体ステータス (JSON) &rarr;</div>
            </a>
            <a href="/api/health" class="endpoint-item" target="_blank">
                <div><span class="method">GET</span>/api/health</div>
                <div style="color: var(--text-dim); font-size: 0.85rem;">ヘルスチェック (Health Check) &rarr;</div>
            </a>
            <a href="/api/settings/apis" class="endpoint-item" target="_blank">
                <div><span class="method">GET</span>/api/settings/apis</div>
                <div style="color: var(--text-dim); font-size: 0.85rem;">外部API設定 (Hypixel, DeepL, OpenAI, Claude) &rarr;</div>
            </a>
            <a href="/api/settings/hypixel-app" class="endpoint-item" target="_blank">
                <div><span class="method">GET</span>/api/settings/hypixel-app</div>
                <div style="color: var(--text-dim); font-size: 0.85rem;">Hypixel Developers 永続API申請テンプレート &rarr;</div>
            </a>
            <a href="/api/market/status" class="endpoint-item" target="_blank">
                <div><span class="method">GET</span>/api/market/status</div>
                <div style="color: var(--text-dim); font-size: 0.85rem;">マーケット / バザー最新同期状況 &rarr;</div>
            </a>
            <a href="/api/advisor" class="endpoint-item" target="_blank">
                <div><span class="method">GET</span>/api/advisor</div>
                <div style="color: var(--text-dim); font-size: 0.85rem;">SkyBlock アドバイザードメイン &rarr;</div>
            </a>
        </div>

        <footer>
            <div>SkyPilot Project &bull; Information Copilot Only (Fair Play)</div>
            <div>API Port: {api_port} &bull; Web UI: http://localhost:3000 &bull; Local Native Server</div>
        </footer>
    </div>
</body>
</html>"#,
        version = version,
        api_port = api_port,
        hypixel_badge = if has_hypixel { "badge-success" } else { "badge-warning" },
        hypixel_badge_text = if has_hypixel { "Active" } else { "Unset" },
        hypixel_stat = if has_hypixel { "Ready" } else { "Key 未設定" },
        remaining = remaining_str,
        limit = limit_str,
        breaker = breaker,
        trans_provider = trans_status.external_provider.clone().unwrap_or_else(|| "builtin".to_string()),
        trans_name = trans_status.external_provider.unwrap_or_else(|| "SkyBlock Dictionary".to_string()),
        trans_breaker = trans_status.circuit,
        llm_badge = if llm_enabled { "badge-success" } else { "badge-secondary" },
        llm_badge_text = if llm_enabled { "Active" } else { "Disabled" },
        llm_stat = if llm_enabled { "Enabled" } else { "Disabled" },
        llm_model = llm_model,
        items_count = items_count
    );

    Html(html)
}
