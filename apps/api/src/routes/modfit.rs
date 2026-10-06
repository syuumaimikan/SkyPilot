//! In-game planning panels: class / floor / slayer fit and the path to each goal, tiered builds
//! (loadouts), money methods with where to do them, and the dungeon puzzle rules. Everything is
//! computed from the player's last analysis, the curated registry and live prices; heuristic
//! scores are labelled as such.

use std::sync::Arc;

use axum::Json;
use axum::extract::{Query, State};
use axum::http::HeaderMap;
use axum_extra::extract::cookie::CookieJar;
use skypilot_protocol::mod_api::*;
use skypilot_reco::providers;

use crate::auth;
use crate::error::ApiResult;
use crate::routes::modapi::{GuidesQuery, icon_of, place, plan_action};
use crate::state::AppState;

const CLASSES: [&str; 5] = ["Healer", "Mage", "Berserk", "Archer", "Tank"];

fn analyse_first(lang: skypilot_reco::ctx::Lang) -> String {
    lang.t("Analyse your profile on the SkyPilot web first.", "先にSkyPilotのWebでプロフィールを分析してください。")
}

/// Which dungeon class a weapon points to, from Hypixel's item data (category and stats):
/// bows → Archer, wands → Healer, swords with more Intelligence than Strength → Mage, other
/// swords → Berserk.
fn weapon_class(def: &skypilot_data::hypixel::ItemDef) -> Option<&'static str> {
    let cat = def.category.as_deref()?;
    let stat = |k: &str| def.stats.get(k).copied().unwrap_or(0.0);
    match cat {
        "BOW" => Some("Archer"),
        "WAND" => Some("Healer"),
        "SWORD" | "LONGSWORD" => Some(if stat("INTELLIGENCE") >= 100.0 && stat("INTELLIGENCE") > stat("STRENGTH") { "Mage" } else { "Berserk" }),
        _ => None,
    }
}

pub async fn fit(State(state): State<Arc<AppState>>, jar: CookieJar, headers: HeaderMap, Query(q): Query<GuidesQuery>) -> ApiResult<Json<ModFit>> {
    let user = auth::any_user(&state, &jar, &headers).await?;
    let lang = AppState::lang_of(&user, q.lang.as_deref());
    let Some((resp, _)) = state.store.latest_analysis(user.id).await? else {
        return Ok(Json(ModFit {
            classes: Vec::new(),
            recommended_class: None,
            floors: Vec::new(),
            slayers: Vec::new(),
            goals: Vec::new(),
            notes: Vec::new(),
            message: Some(analyse_first(lang)),
        }));
    };
    let player = state.player(user.id).await?;
    let registry = state.registry();
    let fit = tokio::task::block_in_place(|| {
        let ctx = state.ctx(&registry, &player, &user, lang);
        // --- classes: level (profile) + the most valuable weapon of each class the player owns
        let dungeons = ctx.analysis.dungeons.as_ref();
        let mut best_weapon: std::collections::HashMap<&str, (String, f64)> = std::collections::HashMap::new();
        for o in ctx.inv.all_items() {
            let Some(id) = o.item.skyblock_id.as_deref() else { continue };
            let Some(def) = registry.item(id) else { continue };
            let Some(class) = weapon_class(def) else { continue };
            let value = ctx.buy(id).map(|q| q.price).unwrap_or(0.0);
            let e = best_weapon.entry(class).or_insert((String::new(), -1.0));
            if value > e.1 {
                *e = (registry.display_name(id), value);
            }
        }
        let max_value = best_weapon.values().map(|v| v.1).fold(0.0_f64, f64::max);
        let mut classes: Vec<ModClassFit> = CLASSES
            .iter()
            .map(|&c| {
                let info = dungeons.and_then(|d| d.classes.iter().find(|l| l.name.eq_ignore_ascii_case(c) || l.id.eq_ignore_ascii_case(c)));
                let level = info.map(|l| l.level).unwrap_or(0);
                let mut reasons = vec![lang.t(format!("{c} level {level}"), format!("{c} レベル {level}"))];
                let weapon = best_weapon.get(c);
                let weapon_score = match weapon {
                    Some((name, v)) if max_value > 0.0 => {
                        reasons.push(lang.t(format!("owns {name}"), format!("{name} を所持")));
                        (v / max_value).clamp(0.0, 1.0)
                    }
                    Some((name, _)) => {
                        reasons.push(lang.t(format!("owns {name}"), format!("{name} を所持")));
                        0.5
                    }
                    None => 0.0,
                };
                ModClassFit {
                    class: c.to_string(),
                    level,
                    progress: info.map(|l| l.progress).unwrap_or(0.0),
                    score: (0.6 * (level as f64 / 50.0).min(1.0) + 0.4 * weapon_score).clamp(0.0, 1.0),
                    reasons,
                    selected: dungeons.and_then(|d| d.selected_class.as_deref()).is_some_and(|s| s.eq_ignore_ascii_case(c)),
                }
            })
            .collect();
        classes.sort_by(|a, b| b.score.total_cmp(&a.score));
        let recommended_class = classes.first().filter(|c| c.score > 0.0).map(|c| c.class.clone());
        // --- floors (curated readiness model)
        let floors = providers::dungeons::all_readiness(&ctx)
            .into_iter()
            .map(|f| ModFloorFit {
                floor: f.floor,
                readiness: f.readiness,
                unlocked: f.unlocked,
                recommended: f.recommended,
                completions: f.completions,
                missing: f.missing.iter().map(|m| format!("{}: {}", m.label, m.text.clone().unwrap_or_else(|| m.value.map(|v| format!("{v:.0}")).unwrap_or_default()))).collect(),
            })
            .collect();
        // --- slayers: highest startable tier and the readiness of the best tier to do now
        let slayers = registry
            .curated
            .slayers
            .iter()
            .map(|def| {
                let (gear, _) = providers::slayer::gear_status(&ctx, def);
                let unlocked_tier = (1..=def.max_tier).rev().find(|t| providers::slayer::unlocked(&ctx, def, *t)).unwrap_or(0);
                let tier = unlocked_tier.max(1);
                ModSlayerFit {
                    id: def.id.clone(),
                    name: def.name.clone(),
                    unlocked_tier,
                    tier,
                    readiness: providers::slayer::readiness(&ctx, def, tier, gear),
                    gear_tier: gear.and_then(|g| ["REQUIRED", "RECOMMENDED", "LUXURY"].get(g.saturating_sub(1)).map(|s| s.to_string())),
                }
            })
            .collect();
        // --- goals: what is left and the action that moves each goal forward most
        let goals = resp
            .goals
            .iter()
            .map(|g| {
                let next = resp.actions.iter().find(|a| a.goal_ids.contains(&g.id));
                ModGoalPath {
                    title: g.roadmap.title.clone(),
                    progress: g.roadmap.overall_progress,
                    remaining_cost: g.roadmap.remaining_cost,
                    remaining_hours: g.roadmap.remaining_hours,
                    next_steps: g
                        .roadmap
                        .steps
                        .iter()
                        .filter(|s| crate::routes::modapi::enum_name(&s.status) != "DONE")
                        .take(4)
                        .map(|s| ModPlanStep {
                            title: s.title.clone(),
                            detail: s.detail.clone(),
                            status: crate::routes::modapi::enum_name(&s.status),
                            progress: s.progress,
                            cost: s.estimated_cost,
                            hours: s.estimated_hours,
                        })
                        .collect(),
                    next_action: next.map(|a| plan_action(&registry, a, lang)),
                }
            })
            .collect();
        ModFit {
            classes,
            recommended_class,
            floors,
            slayers,
            goals,
            notes: vec![lang.t(
                "Class fit is a SkyPilot heuristic: 60% class level, 40% the most valuable weapon of that class you own (bows → Archer, wands → Healer, Intelligence swords → Mage, other swords → Berserk; from Hypixel's item data).",
                "クラス適性はSkyPilotの推定です: クラスレベル60%、そのクラス向けで最も価値の高い所持武器40%（弓→アーチャー、ワンド→ヒーラー、知性の剣→メイジ、その他の剣→バーサーク。Hypixelのアイテムデータより）。",
            )],
            message: None,
        }
    });
    Ok(Json(fit))
}

pub async fn builds(State(state): State<Arc<AppState>>, jar: CookieJar, headers: HeaderMap, Query(q): Query<GuidesQuery>) -> ApiResult<Json<ModBuilds>> {
    let user = auth::any_user(&state, &jar, &headers).await?;
    let lang = AppState::lang_of(&user, q.lang.as_deref());
    let analysed = state.store.latest_analysis(user.id).await?;
    if analysed.is_none() {
        return Ok(Json(ModBuilds { builds: Vec::new(), message: Some(analyse_first(lang)) }));
    }
    let player = state.player(user.id).await?;
    let registry = state.registry();
    let builds = tokio::task::block_in_place(|| {
        let ctx = state.ctx(&registry, &player, &user, lang);
        let pets = ctx.analysis.pets.as_ref().map(Vec::as_slice).unwrap_or(&[]);
        let goal_slayers: Vec<String> =
            analysed.as_ref().map(|(r, _)| r.goals.iter().flat_map(|g| g.graph.nodes.iter().filter_map(|n| n.id.strip_prefix("slayer:").map(str::to_string))).collect()).unwrap_or_default();
        let mut builds: Vec<ModBuild> = registry
            .curated
            .slayers
            .iter()
            .map(|def| {
                let tiers = ["REQUIRED", "RECOMMENDED", "LUXURY"]
                    .iter()
                    .enumerate()
                    .map(|(i, tier)| {
                        let slots: Vec<ModBuildSlot> = def
                            .gear
                            .iter()
                            .map(|slot| {
                                let list = [&slot.required, &slot.recommended, &slot.luxury][i];
                                let items: Vec<ModGearItem> = list
                                    .iter()
                                    .map(|id| ModGearItem {
                                        id: id.clone(),
                                        name: registry.display_name(id),
                                        icon: icon_of(&registry, id),
                                        slot: slot.slot.clone(),
                                        tier: tier.to_string(),
                                        owned: skypilot_profile::gear::owns_any(ctx.inv, pets, std::slice::from_ref(id)),
                                        price: if id.starts_with("PET:") { None } else { ctx.buy(id).map(|q| q.price) },
                                        note: None,
                                    })
                                    .collect();
                                let done = items.iter().any(|it| it.owned);
                                let cost = if done { None } else { items.iter().filter_map(|it| it.price).reduce(f64::min) };
                                ModBuildSlot { slot: slot.slot.clone(), items, done, cost }
                            })
                            .collect();
                        let complete = slots.iter().all(|s| s.done);
                        let unpriced = slots.iter().any(|s| !s.done && s.cost.is_none());
                        let cost_to_complete = if complete { Some(0.0) } else if unpriced { None } else { Some(slots.iter().filter_map(|s| s.cost).sum()) };
                        ModBuildTier { tier: tier.to_string(), slots, complete, cost_to_complete }
                    })
                    .collect();
                ModBuild {
                    id: def.id.clone(),
                    title: lang.t(format!("{} build", def.name), format!("{} の装備構成", def.name)),
                    tiers,
                    goal: goal_slayers.contains(&def.id),
                    source: "data/registry/slayers.toml (Hypixel SkyBlock Wiki)".into(),
                }
            })
            .collect();
        builds.sort_by_key(|b| !b.goal);
        builds
    });
    Ok(Json(ModBuilds { builds, message: None }))
}

pub async fn money(State(state): State<Arc<AppState>>, jar: CookieJar, headers: HeaderMap, Query(q): Query<GuidesQuery>) -> ApiResult<Json<ModMoney>> {
    let user = auth::any_user(&state, &jar, &headers).await?;
    let lang = AppState::lang_of(&user, q.lang.as_deref());
    if state.store.latest_analysis(user.id).await?.is_none() {
        return Ok(Json(ModMoney { methods: Vec::new(), message: Some(analyse_first(lang)) }));
    }
    let player = state.player(user.id).await?;
    let registry = state.registry();
    let methods = tokio::task::block_in_place(|| {
        let ctx = state.ctx(&registry, &player, &user, lang);
        providers::money::rank(&ctx)
            .into_iter()
            .map(|m| ModMoneyMethod {
                place: place(&registry, &format!("money-{}", m.id), lang),
                id: m.id,
                name: m.name,
                category: m.category,
                low_per_hour: m.coins_per_hour.as_ref().map(|r| r.low),
                high_per_hour: m.coins_per_hour.as_ref().map(|r| r.high),
                setup_cost: m.setup_cost,
                risk: crate::routes::modapi::enum_name(&m.risk),
                requirements_met: m.requirements_met,
                missing: m.missing_requirements,
                why: m.why,
                confidence: m.confidence.score,
            })
            .collect()
    });
    Ok(Json(ModMoney { methods, message: None }))
}

/// Mayor, minister and fire sales (public Hypixel resources; fire sales cached 5 minutes).
pub async fn skyblock_now(State(state): State<Arc<AppState>>) -> ApiResult<Json<ModSkyblockNow>> {
    use std::sync::Mutex;
    static FIRE_SALES: std::sync::OnceLock<Mutex<(i64, serde_json::Value)>> = std::sync::OnceLock::new();
    let now = AppState::now_ms();
    let cache = FIRE_SALES.get_or_init(|| Mutex::new((0, serde_json::Value::Null)));
    let cached = {
        let c = cache.lock().expect("fire sale cache");
        (now - c.0 < 300_000).then(|| c.1.clone())
    };
    let sales_json = match cached {
        Some(v) => v,
        None => match state.hypixel.firesales().await {
            Ok(v) => {
                *cache.lock().expect("fire sale cache") = (now, v.clone());
                v
            }
            // keep serving the last answer (or none) when Hypixel is unavailable or rate limits
            Err(e) => {
                tracing::warn!(error = %e, "fire sales not refreshed");
                cache.lock().expect("fire sale cache").1.clone()
            }
        },
    };
    let registry = state.registry();
    let mayor_of = |m: &skypilot_data::hypixel::Mayor| ModMayor {
        name: m.name.clone(),
        perks: m.perks.iter().map(|p| ModPerk { name: p.name.clone(), description: p.description.clone() }).collect(),
    };
    let election = registry.election.as_ref();
    let fire_sales = sales_json
        .get("sales")
        .and_then(|s| s.as_array())
        .map(|list| {
            list.iter()
                .filter_map(|s| {
                    let id = s.get("item_id")?.as_str()?.to_string();
                    let end = s.get("end")?.as_i64()?;
                    (end > now).then(|| ModFireSale {
                        name: registry.display_name(&id),
                        item_id: id,
                        start_ms: s.get("start").and_then(|v| v.as_i64()).unwrap_or(0),
                        end_ms: end,
                        amount: s.get("amount").and_then(|v| v.as_u64()),
                        price: s.get("price").and_then(|v| v.as_u64()),
                    })
                })
                .collect()
        })
        .unwrap_or_default();
    Ok(Json(ModSkyblockNow {
        mayor: election.and_then(|e| e.mayor.as_ref()).map(mayor_of),
        minister: election.and_then(|e| e.minister.as_ref()).map(mayor_of),
        year: election.and_then(|e| e.year),
        fire_sales,
    }))
}

/// Dungeon puzzle rules (public, curated from the wiki).
pub async fn puzzles(State(state): State<Arc<AppState>>, Query(q): Query<GuidesQuery>) -> ApiResult<Json<Vec<ModPuzzle>>> {
    let registry = state.registry();
    let ja = q.lang.as_deref() == Some("ja");
    let p = &registry.curated.puzzles;
    Ok(Json(
        p.puzzles
            .iter()
            .map(|d| ModPuzzle { name: d.name.clone(), floors: d.floors.clone(), rule: if ja { d.rule_ja.clone() } else { d.rule.clone() }, can_fail: d.can_fail, source: p.source.clone() })
            .collect(),
    ))
}
