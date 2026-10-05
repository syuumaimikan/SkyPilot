//! Money Making Advisor: personalised ranking. Every method's coins/hour is computed now
//! from the player's stats and live market data (nothing is a fixed "X M/h").

use skypilot_protocol::advisor::{AdvisorReport, MoneyMethodEstimate, Tone};
use skypilot_protocol::common::{Confidence, DataSource, Metric, Range, RiskLevel};
use skypilot_protocol::market::FlipQuery;

use super::*;
use crate::ctx::{Ctx, fmt_coins};
use crate::optimizer::CandidateAction;

fn risk_of(s: &str) -> RiskLevel {
    match s {
        "VERY_LOW" => RiskLevel::VeryLow,
        "LOW" => RiskLevel::Low,
        "HIGH" => RiskLevel::High,
        "VERY_HIGH" => RiskLevel::VeryHigh,
        _ => RiskLevel::Medium,
    }
}

pub fn rank(ctx: &Ctx) -> Vec<MoneyMethodEstimate> {
    let mut out = Vec::new();
    let liquid = ctx.liquid_coins();
    for m in &ctx.registry.curated.money_methods {
        let mut missing = Vec::new();
        let mut unknown_req = false;
        for r in &m.requirements {
            match ctx.check(r) {
                Some(true) => {}
                Some(false) => missing.push(r.clone()),
                None => unknown_req = true,
            }
        }
        let (range, variable, setup, capital, why, conf): (Option<Range>, bool, Option<f64>, Option<f64>, String, f64) = match m.estimator.as_str() {
            "farming" => match super::farming::best_crop(ctx) {
                Some(c) => {
                    let v = c.bazaar_coins_per_hour.unwrap_or(0.0).max(c.npc_coins_per_hour);
                    (Some(Range { low: v * 0.8, high: v * 1.05 }), false, None, None, ctx.t(format!("{} at {:.0} fortune", c.name, c.fortune), format!("{}（幸運{:.0}）", c.name, c.fortune)), 0.65)
                }
                None => (None, false, None, None, ctx.t("Garden data unavailable", "ガーデンのデータなし"), 0.2),
            },
            "mining" => match super::mining::best_money_target(ctx) {
                Some(t) => {
                    let v = t.effective_coins_per_hour.unwrap_or(0.0);
                    (Some(Range { low: v * 0.7, high: v * 1.05 }), false, None, None, ctx.t(format!("{} with your mining stats", t.name), format!("現在の採掘ステータスで{}", t.name)), 0.55)
                }
                None => (None, false, None, None, ctx.t("No accessible mining target with known prices", "価格が分かる採掘対象がありません"), 0.2),
            },
            "slayer" => {
                let mut best: Option<(String, f64)> = None;
                for def in &ctx.registry.curated.slayers {
                    let (gear, _) = super::slayer::gear_status(ctx, def);
                    for t in 1..=def.max_tier {
                        if !super::slayer::unlocked(ctx, def, t) || super::slayer::readiness(ctx, def, t, gear) < 0.75 {
                            continue;
                        }
                        let e = super::slayer::tier_economics(ctx, def, t, gear);
                        if let Some(n) = e.net_per_hour {
                            if best.as_ref().is_none_or(|b| n > b.1) {
                                best = Some((format!("{} T{t}", def.name), n));
                            }
                        }
                    }
                }
                match best {
                    Some((name, v)) => (Some(Range { low: v * 0.3, high: v * 1.5 }), true, None, None, ctx.t(format!("{name} (expected value incl. rare drops)"), format!("{name}（レアドロップ込みの期待値）")), 0.4),
                    None => (None, true, None, None, ctx.t("No slayer tier you are ready for has priced drops", "準備済みのTierで価格の分かるドロップがありません"), 0.2),
                }
            }
            "dungeons" => (None, true, None, None, ctx.t("Chest profit depends on RNG and chest purchases; not modelled.", "チェスト利益はRNGと購入判断に依存するため未モデル化。"), 0.2),
            "ah_flip" => match ctx.market {
                Some(mk) => {
                    let list = mk.flips(&FlipQuery { max_budget: Some(liquid.max(1.0)), min_profit: None, min_roi: None, min_confidence: Some(0.6), max_risk: Some(RiskLevel::Medium), search: None, limit: Some(30) }, true);
                    let n = list.flips.len();
                    let profit: f64 = list.flips.iter().take(5).map(|f| f.expected_net_profit * f.probability_of_sale).sum();
                    (None, true, None, Some(liquid), ctx.t(format!("{n} live opportunities within your purse; top-5 expected profit {}", fmt_coins(profit)), format!("所持金内のライブ機会{n}件、上位5件の期待利益{}", fmt_coins(profit))), if n > 0 { 0.5 } else { 0.2 })
                }
                None => (None, true, None, None, ctx.t("Market collector not running", "マーケット収集が停止中"), 0.1),
            },
            "bazaar_flip" => match ctx.market {
                Some(mk) => {
                    let list = mk.bazaar_list(liquid.max(1.0));
                    let order: Vec<_> = list.flips.iter().filter(|f| f.profit_per_hour.is_some()).take(3).collect();
                    let v: f64 = order.iter().filter_map(|f| f.profit_per_hour).sum::<f64>() * 0.5;
                    (if v > 0.0 { Some(Range { low: v * 0.3, high: v }) } else { None }, true, None, Some(order.iter().map(|f| f.capital_required).sum()), ctx.t("Top-3 order flips with realistic market share", "現実的なシェアでの上位3件の注文フリップ"), 0.45)
                }
                None => (None, true, None, None, ctx.t("Market collector not running", "マーケット収集が停止中"), 0.1),
            },
            "craft_flip" => match ctx.market {
                Some(mk) => {
                    let (c, _) = mk.craft_flips();
                    let top: Vec<_> = c.iter().filter(|f| f.material_cost <= liquid).take(5).collect();
                    (None, true, None, top.first().map(|f| f.material_cost), ctx.t(format!("{} profitable crafts you can afford", top.len()), format!("購入可能な利益の出るクラフト{}件", top.len())), 0.5)
                }
                None => (None, true, None, None, ctx.t("Market collector not running", "マーケット収集が停止中"), 0.1),
            },
            "forge_flip" | "kat_flip" => match ctx.market {
                Some(mk) => {
                    let (t, _) = mk.time_flips();
                    let kind = if m.estimator == "forge_flip" { skypilot_protocol::market::TimeFlipKind::Forge } else { skypilot_protocol::market::TimeFlipKind::Kat };
                    let best = t.iter().filter(|f| f.kind == kind && f.capital <= liquid).max_by(|a, b| a.profit_per_day.total_cmp(&b.profit_per_day));
                    match best {
                        Some(b) => (Some(Range { low: b.profit_per_day / 24.0 * 0.6, high: b.profit_per_day / 24.0 }), true, None, Some(b.capital), ctx.t(format!("{}: {}/day per slot (passive)", b.name, fmt_coins(b.profit_per_day)), format!("{}: 1枠あたり{}/日（放置）", b.name, fmt_coins(b.profit_per_day))), b.confidence.score),
                        None => (None, true, None, None, ctx.t("No affordable profitable recipe right now", "現在、購入可能で利益の出るレシピがありません"), 0.2),
                    }
                }
                None => (None, true, None, None, ctx.t("Market collector not running", "マーケット収集が停止中"), 0.1),
            },
            "minions" => {
                let best = super::minions::rows(ctx).into_iter().filter_map(|r| r.next_coins_per_day.map(|v| (r.name, v))).max_by(|a, b| a.1.total_cmp(&b.1));
                match best {
                    Some((n, v)) => (Some(Range { low: v / 24.0 * 0.7, high: v / 24.0 }), false, None, None, ctx.t(format!("Best single minion: {n} ({}/day)", fmt_coins(v)), format!("最良のミニオン1体: {n}（{}/日）", fmt_coins(v))), 0.35),
                    None => (None, false, None, None, ctx.t("No priced minion product", "価格の分かるミニオン生産物なし"), 0.1),
                }
            }
            _ => (None, true, None, None, String::new(), 0.1),
        };
        let risk = risk_of(&m.risk);
        let met = missing.is_empty();
        let mid = range.map(|r| (r.low + r.high) / 2.0).unwrap_or(0.0);
        let score = if met { mid * conf / risk.weight() } else { 0.0 };
        out.push(MoneyMethodEstimate {
            id: m.id.clone(),
            name: m.name.clone(),
            category: m.category.clone(),
            coins_per_hour: range,
            variable,
            setup_cost: setup,
            capital_required: capital,
            risk,
            requirements: m.requirements.clone(),
            requirements_met: met && !unknown_req,
            missing_requirements: missing,
            why,
            confidence: Confidence::from_score(conf, vec![]),
            link: Some(m.link.clone()),
            score,
        });
    }
    out.sort_by(|a, b| b.score.total_cmp(&a.score).then(b.confidence.score.total_cmp(&a.confidence.score)));
    out
}

pub struct MoneyProvider;

impl Provider for MoneyProvider {
    fn id(&self) -> &'static str {
        "money"
    }

    fn actions(&self, ctx: &Ctx, goals: &[GoalContext]) -> Vec<CandidateAction> {
        let gap: f64 = goals.iter().filter_map(|g| g.open("money").and_then(|n| Some(n.target? - n.current.unwrap_or(0.0)))).fold(0.0, f64::max);
        if gap <= 0.0 && ctx.play_style != skypilot_protocol::goals::PlayStyle::MoneyFocus {
            return Vec::new();
        }
        let ranked = rank(ctx);
        let Some(best) = ranked.iter().find(|m| m.requirements_met && m.coins_per_hour.is_some_and(|r| r.low > 0.0)) else { return Vec::new() };
        let r = best.coins_per_hour.unwrap();
        let mid = (r.low + r.high) / 2.0;
        let hours_needed = if mid > 0.0 && gap > 0.0 { gap / mid } else { 2.0 };
        let session = hours_needed.min(2.0).max(0.5);
        let mut a = CandidateAction::new(format!("money-{}", best.id), "money", &best.category, ctx.t(format!("Earn coins: {} ({}–{}/h)", best.name, fmt_coins(r.low), fmt_coins(r.high)), format!("金策: {}（{}〜{}/時）", best.name, fmt_coins(r.low), fmt_coins(r.high))));
        a.metrics.push(Metric::value(ctx.t("Coins/hour", "コイン/時"), mid, Some("coins")));
        if gap > 0.0 {
            a.metrics.push(Metric::value(ctx.t("Coins still needed", "不足コイン"), gap, Some("coins")));
            a.metrics.push(Metric::value(ctx.t("Hours to close the gap", "必要時間"), hours_needed, Some("h")));
        }
        a.coin_return_per_hour = mid;
        a.hours = session;
        a.risk = best.risk.weight() * 1.5;
        a.confidence = best.confidence.score;
        a.long_term_value = 0.4;
        a.why = ctx.t(format!("Best money method for your account right now: {}.", best.why), format!("現在のあなたのアカウントで最も効率の良い金策です: {}。", best.why));
        for g in goals {
            if let Some(n) = g.open("money") {
                let need = (n.target.unwrap_or(0.0) - n.current.unwrap_or(0.0)).max(1.0);
                if let Some(i) = g.impact("money", mid * session / need) {
                    a.goal_impacts.insert(g.id.clone(), i);
                }
            }
        }
        a.link = best.link.clone();
        a.sources = vec![DataSource::Market, DataSource::HypixelProfile, DataSource::SkyPilotRegistry];
        vec![a]
    }

    fn report(&self, ctx: &Ctx, _goals: &[GoalContext]) -> AdvisorReport {
        let mut r = report("money", ctx.t("Money Making", "金策"));
        r.summary.push(Metric::value(ctx.t("Liquid coins", "流動資金"), ctx.liquid_coins(), Some("coins")));
        let ranked = rank(ctx);
        let mut s = section("ranking", ctx.t("Best methods for you", "あなたに最適な金策"));
        s.table = Some(table(
            &["#", "Method", "Coins / hour", "Capital", "Risk", "Requirements", "Why", "Confidence"],
            ranked
                .iter()
                .enumerate()
                .map(|(i, m)| {
                    vec![
                        num((i + 1) as f64, None),
                        text(&m.name),
                        match m.coins_per_hour {
                            Some(r) => text(format!("{} – {}", fmt_coins(r.low), fmt_coins(r.high))),
                            None => badge(ctx.t("Variable", "変動"), Tone::Info),
                        },
                        coins_opt(m.capital_required),
                        badge(format!("{:?}", m.risk), match m.risk {
                            RiskLevel::VeryLow | RiskLevel::Low => Tone::Good,
                            RiskLevel::Medium => Tone::Warn,
                            _ => Tone::Bad,
                        }),
                        if m.missing_requirements.is_empty() { badge(ctx.t("Met", "達成"), Tone::Good) } else { badge(m.missing_requirements.join(", "), Tone::Bad) },
                        text(&m.why),
                        progress(m.confidence.score, format!("{:.0}%", m.confidence.score * 100.0)),
                    ]
                })
                .collect(),
        ));
        s.notes.push(ctx.t("Rates are recomputed from your stats and live prices on every request; ranges reflect uptime/market variance.", "効率はリクエストごとにステータスとライブ価格から再計算され、幅は稼働率と市場変動を表します。"));
        s.sources = vec![DataSource::Market, DataSource::HypixelProfile, DataSource::SkyPilotHeuristic];
        r.sections.push(s);
        r.freshness = market_freshness(ctx);
        r
    }
}
