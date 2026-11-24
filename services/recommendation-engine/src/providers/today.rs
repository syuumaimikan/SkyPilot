//! Today: one guide that combines the latest information for this player — Hypixel's status
//! page (maintenance, incidents), the SkyBlock calendar, the current mayor and minister perks,
//! the best next actions from every other provider, today's flip basket, Hypixel's SkyBlock news
//! and the profile's weakest completion parts. Each line says where it comes from; anything that
//! is not available is listed as unknown instead of being filled in.

use std::collections::BTreeSet;

use skypilot_protocol::advisor::{AdvisorReport, Tone};
use skypilot_protocol::common::{DataSource, Metric};

use super::*;
use crate::ctx::{Ctx, fmt_coins, fmt_hours};
use crate::optimizer::CandidateAction;

pub struct TodayProvider;

pub const ID: &str = "today";

/// Activities a mayor / minister perk can boost, recognised from the perk's own text.
const ACTIVITIES: [(&str, &str, &str, &[&str]); 10] = [
    ("farming", "Farming", "農業", &["farming", "crop", "jacob", "garden", "pest", "visitor"]),
    ("mining", "Mining", "採掘", &["mining", "mithril", "gemstone", "powder", "ore", "fossil", "glacite", "mineshaft"]),
    ("dungeons", "Dungeons", "ダンジョン", &["dungeon", "catacombs", "essence"]),
    ("slayer", "Slayer", "スレイヤー", &["slayer", "boss"]),
    ("fishing", "Fishing", "釣り", &["fishing", "sea creature", "trophy fish", "fish"]),
    ("pets", "Pets", "ペット", &["pet"]),
    ("combat", "Combat", "戦闘", &["combat", "bestiary", "mob"]),
    ("foraging", "Foraging", "伐採", &["foraging", "log", "tree"]),
    ("money", "Coins & market", "コイン・マーケット", &["bazaar", "auction", "bank", "interest", "coins", "tax"]),
    ("skills", "Skill XP", "スキルXP", &["experience", "wisdom", "xp", "skill"]),
];

/// Activity ids a perk text mentions: keywords match at the start of a word ("pet" matches
/// "pets", "ore" does not match "more"), case-insensitively, in the perk name and description.
pub fn perk_activities(name: &str, description: &str) -> Vec<&'static str> {
    let words: String = format!("{name} {description}").to_lowercase().chars().map(|c| if c.is_alphanumeric() { c } else { ' ' }).collect();
    let text = format!(" {} ", words.split_whitespace().collect::<Vec<_>>().join(" "));
    ACTIVITIES.iter().filter(|(_, _, _, keys)| keys.iter().any(|k| text.contains(&format!(" {k}")))).map(|(id, ..)| *id).collect()
}

fn activity_name(ctx: &Ctx, id: &str) -> String {
    ACTIVITIES.iter().find(|a| a.0 == id).map(|a| ctx.t(a.1, a.2)).unwrap_or_else(|| id.to_string())
}

/// Activity ids the player's open goals point at.
fn goal_interest(goals: &[GoalContext]) -> BTreeSet<String> {
    let mut out = BTreeSet::new();
    for g in goals {
        for n in g.open_nodes() {
            let id = n.id.to_ascii_lowercase();
            for (a, ..) in ACTIVITIES {
                if id.contains(a) || (a == "mining" && id.contains("hotm")) || (a == "dungeons" && id.contains("catacombs")) {
                    out.insert(a.to_string());
                }
            }
            if id == "money" {
                out.insert("money".into());
            }
            if id.starts_with("skill:") {
                out.insert("skills".into());
            }
        }
    }
    out
}

/// Completion part id -> activity it belongs to.
fn part_activity(part: &str) -> &'static str {
    match part {
        "catacombs" | "classes" => "dungeons",
        "hotm" => "mining",
        "garden" => "farming",
        "slayers" => "slayer",
        "skills" => "skills",
        "collections" | "minions" => "money",
        _ => "",
    }
}

impl Provider for TodayProvider {
    fn id(&self) -> &'static str {
        ID
    }

    /// The best next actions of every other provider (the planner skips this provider in plans,
    /// so nothing is counted twice).
    fn actions(&self, ctx: &Ctx, goals: &[GoalContext]) -> Vec<CandidateAction> {
        all().into_iter().filter(|p| p.id() != ID).flat_map(|p| p.actions(ctx, goals)).collect()
    }

    fn report(&self, ctx: &Ctx, goals: &[GoalContext]) -> AdvisorReport {
        let mut r = report(ID, ctx.t("Today's guide", "今日のガイド"));
        let now = ctx.now_ms;
        let interest = goal_interest(goals);
        let mut focus: Vec<(u32, String)> = Vec::new();
        let mut alerts: Vec<String> = Vec::new();

        // ---- Hypixel status page
        match ctx.live.and_then(|l| l.server.as_ref()).filter(|s| s.available) {
            Some(s) => {
                let sb = s.services.iter().find(|v| v.name == "SkyBlock").map(|v| v.status.as_str());
                let running = s.maintenances.iter().find(|m| m.skyblock && matches!(m.status.as_str(), "in_progress" | "verifying"));
                let next = s.maintenances.iter().filter(|m| m.skyblock && m.status == "scheduled").filter_map(|m| m.starts_ms.map(|t| (t, m))).filter(|(t, _)| *t > now).min_by_key(|(t, _)| *t);
                let state = if let Some(m) = running {
                    alerts.push(match m.ends_ms.filter(|e| *e > now) {
                        Some(e) => ctx.t(format!("SkyBlock is in maintenance ({}), planned to end in {}.", m.name, fmt_hours((e - now) as f64 / 3_600_000.0)), format!("SkyBlockはメンテナンス中です（{}）。終了予定まで {}。", m.name, fmt_hours((e - now) as f64 / 3_600_000.0))),
                        None => ctx.t(format!("SkyBlock is in maintenance ({}).", m.name), format!("SkyBlockはメンテナンス中です（{}）。", m.name)),
                    });
                    ctx.t("Maintenance", "メンテナンス中")
                } else {
                    match sb {
                        Some("operational") => ctx.t("Online", "稼働中"),
                        Some("degraded_performance") => ctx.t("Slow", "遅延"),
                        Some("partial_outage") | Some("major_outage") => ctx.t("Outage", "障害"),
                        Some("under_maintenance") => ctx.t("Maintenance", "メンテナンス中"),
                        _ => ctx.t("Unknown", "不明"),
                    }
                };
                if let Some((t, m)) = next {
                    let h = (t - now) as f64 / 3_600_000.0;
                    if h <= 24.0 {
                        alerts.push(ctx.t(
                            format!("SkyBlock maintenance in {} ({}): finish long runs and claim AH sales before it.", fmt_hours(h), m.name),
                            format!("{}後にSkyBlockメンテナンス（{}）。長い周回やAHの回収はその前に。", fmt_hours(h), m.name),
                        ));
                    }
                }
                for i in s.incidents.iter().filter(|i| i.skyblock || i.services.iter().any(|v| v.contains("Authentication") || v.contains("Minecraft Server"))) {
                    alerts.push(ctx.t(format!("Hypixel incident: {} ({})", i.name, i.status), format!("Hypixelの障害: {}（{}）", i.name, i.status)));
                }
                r.summary.push(Metric::text("SkyBlock", state));
            }
            None => r.unknowns.push(ctx.t("Hypixel's status page is not available right now.", "Hypixelのステータスページを今は取得できません。")),
        }
        if ctx.live.is_some_and(|l| l.key_rejected) {
            alerts.push(ctx.t(
                "Hypixel rejected the server's API key (403): profile updates stop until a new Personal API Key is set in Settings.",
                "HypixelがサーバーのAPIキーを拒否しました（403）。設定で新しい Personal API Key を入れるまでプロフィールは更新されません。",
            ));
        }
        if let Some(m) = ctx.market {
            let (ah, _, bz) = m.freshness();
            if ah.stale || bz.stale {
                alerts.push(ctx.t("Market data is outdated: re-check prices in game before buying.", "マーケットのデータが古くなっています。購入前にゲーム内で価格を確認してください。"));
            }
        }

        // ---- calendar
        let cal = &ctx.registry.curated.calendar;
        let mut events_now = 0;
        let mut rows = Vec::new();
        for (e, start, end, active) in cal.upcoming(now) {
            if !active && start - now > 24 * 3_600_000 {
                continue;
            }
            let relevant = e.progression.iter().any(|p| interest.iter().any(|i| p.contains(i.as_str()) || i.contains(p.as_str())));
            if active {
                events_now += 1;
            }
            if relevant {
                focus.push((
                    if active { 90 } else { 60 },
                    if active {
                        ctx.t(format!("{} is on now (ends in {}) and fits your goals: {}", e.name, fmt_hours((end - now) as f64 / 3_600_000.0), e.value), format!("{} が開催中（残り {}）で、目標に合っています: {}", e.name, fmt_hours((end - now) as f64 / 3_600_000.0), e.value))
                    } else {
                        ctx.t(format!("{} starts in {} and fits your goals: {}", e.name, fmt_hours((start - now) as f64 / 3_600_000.0), e.value), format!("{} があと {} で始まり、目標に合っています: {}", e.name, fmt_hours((start - now) as f64 / 3_600_000.0), e.value))
                    },
                ));
            }
            rows.push(vec![
                text(&e.name),
                if active { badge(ctx.t("Now", "開催中"), Tone::Good) } else { text(ctx.t(format!("in {}", fmt_hours((start - now) as f64 / 3_600_000.0)), format!("あと {}", fmt_hours((start - now) as f64 / 3_600_000.0)))) },
                text(&e.value),
                if relevant { badge(ctx.t("Your goals", "目標に関係"), Tone::Info) } else { text("") },
            ]);
        }
        r.summary.push(Metric::value(ctx.t("Events now", "開催中のイベント"), events_now as f64, None));

        // ---- mayor / minister
        let mut boosted: BTreeSet<&'static str> = BTreeSet::new();
        let mut perk_lines = Vec::new();
        if let Some(e) = &ctx.registry.election {
            for (role, m) in [(ctx.t("Mayor", "市長"), e.mayor.as_ref()), (ctx.t("Minister", "大臣"), e.minister.as_ref())] {
                let Some(m) = m else { continue };
                for p in &m.perks {
                    let desc = skypilot_items::text::strip_formatting(&p.description);
                    let acts = perk_activities(&p.name, &desc);
                    boosted.extend(acts.iter().copied());
                    let tag = if acts.is_empty() { String::new() } else { format!(" [{}]", acts.iter().map(|a| activity_name(ctx, a)).collect::<Vec<_>>().join(", ")) };
                    perk_lines.push(format!("{role} {}: {}{tag} — {desc}", m.name, p.name));
                }
            }
            for a in &boosted {
                if interest.contains(*a) {
                    focus.push((80, ctx.t(format!("The current mayor/minister boosts {} — a good day for it.", activity_name(ctx, a)), format!("今の市長・大臣は{}を強化しています。今日やると効率的です。", activity_name(ctx, a)))));
                }
            }
        } else {
            r.unknowns.push(ctx.t("Election data not loaded.", "選挙データを読み込めていません。"));
        }

        // ---- completion
        let completion = skypilot_profile::completion::compute(ctx.analysis, ctx.registry);
        if let Some(o) = completion.overall {
            r.summary.push(Metric::value(ctx.t("Completion", "達成率"), o, Some("%")));
        }
        let mut weakest = completion.parts.clone();
        weakest.sort_by(|a, b| a.percent.total_cmp(&b.percent));
        weakest.retain(|p| p.percent < 100.0);
        if let Some(w) = weakest.first() {
            let a = part_activity(&w.id);
            let boost = !a.is_empty() && boosted.contains(a);
            focus.push((
                if boost { 85 } else { 40 },
                if boost {
                    ctx.t(format!("{} is your lowest part ({:.1}%) and is boosted right now.", w.name, w.percent), format!("{} がいちばん低い分野（{:.1}%）で、今は強化中です。", w.name, w.percent))
                } else {
                    ctx.t(format!("Lowest completion part: {} at {:.1}% ({}).", w.name, w.percent, w.basis), format!("いちばん低い分野: {} {:.1}%（{}）。", w.name, w.percent, w.basis))
                },
            ));
        }

        // ---- money: today's flip basket
        let mut money = section("money", ctx.t("Money today (AH flips)", "今日の金策（AHフリップ）"));
        if let Some(m) = ctx.market {
            let capital = ctx.spendable();
            if capital >= 100_000.0 {
                let plan = m.snowball(capital, 2.0, 7);
                if plan.picks.is_empty() {
                    money.bullets.push(ctx.t("No flip fits your coins right now.", "今の所持金に合うフリップはありません。"));
                } else {
                    r.summary.push(Metric::text(ctx.t("Flip EV / cycle", "フリップ期待値/回"), fmt_coins(plan.expected_profit_per_cycle)));
                    money.metrics.push(Metric::text(ctx.t("Basket", "バスケット"), format!("{} · {}", plan.picks.len(), fmt_coins(plan.deployed))));
                    money.metrics.push(Metric::text(ctx.t("Expected profit per cycle", "1サイクルの期待利益"), fmt_coins(plan.expected_profit_per_cycle)));
                    if let Some(last) = plan.projection.last() {
                        money.metrics.push(Metric::text(ctx.t("In 7 days if reinvested (projection)", "7日後・再投資した場合（見通し）"), fmt_coins(last.capital)));
                    }
                    for p in plan.picks.iter().take(3) {
                        let record = p.track_record.as_ref().map(|t| format!(" · {}/{}", t.hits, t.trials)).unwrap_or_default();
                        money.bullets.push(format!("{} — {} → {} (+{}, P {:.0}%{record}) · {}", p.item_name, fmt_coins(p.buy_price), fmt_coins(p.recommended_sell), fmt_coins(p.expected_net_profit), p.probability_of_sale * 100.0, p.view_command));
                    }
                    focus.push((50, ctx.t(format!("Flip basket for your coins: about {} expected per cycle (see the snowball plan).", fmt_coins(plan.expected_profit_per_cycle)), format!("所持金でのフリップ: 1サイクルあたり期待値 約{}（雪だるまプラン参照）。", fmt_coins(plan.expected_profit_per_cycle)))));
                }
                money.notes.push(ctx.t("SkyPilot never buys or lists: paste the command in game yourself and re-check the price.", "SkyPilotは購入・出品を行いません。コマンドはゲーム内で自分で貼り付け、価格を再確認してください。"));
            } else {
                money.bullets.push(ctx.t("Less than 100k coins available: build coins with the money guide first.", "使えるコインが10万未満です。まずは金策ガイドで資金を作りましょう。"));
            }
            money.sources = vec![DataSource::Market];
        }

        // ---- sections in reading order
        let mut s = section("alerts", ctx.t("Before you play", "プレイ前に"));
        if alerts.is_empty() {
            s.bullets.push(ctx.t("Nothing to watch out for right now.", "今のところ注意点はありません。"));
        }
        s.bullets.extend(alerts);
        s.sources = vec![DataSource::HypixelStatusPage, DataSource::Market];
        r.sections.push(s);

        focus.sort_by(|a, b| b.0.cmp(&a.0));
        let mut s = section("focus", ctx.t("Today's focus", "今日の重点"));
        s.description = Some(ctx.t("Combined from events, the mayor, your goals, your completion and the market.", "イベント・市長・目標・達成率・マーケットを組み合わせた提案です。"));
        if focus.is_empty() {
            s.bullets.push(ctx.t("Set a goal on the dashboard to get a focus for today.", "ダッシュボードで目標を設定すると、今日の重点を提案します。"));
        }
        s.bullets.extend(focus.into_iter().take(5).map(|(_, t)| t));
        s.sources = vec![DataSource::SkyPilotRegistry, DataSource::HypixelResources, DataSource::HypixelProfile, DataSource::SkyPilotHeuristic];
        r.sections.push(s);

        let mut s = section("events", ctx.t("Events now and in the next 24h", "開催中・24時間以内のイベント"));
        if rows.is_empty() {
            s.bullets.push(ctx.t("No event in the next 24 hours.", "24時間以内のイベントはありません。"));
        } else {
            s.table = Some(table(&["Event", "When", "Value", ""], rows));
        }
        s.sources = vec![DataSource::SkyPilotRegistry];
        r.sections.push(s);

        if !perk_lines.is_empty() {
            let mut s = section("mayor", ctx.t("Mayor & minister perks", "市長・大臣のパーク"));
            s.bullets = perk_lines;
            s.notes.push(ctx.t("Boosted activities are recognised from the perk text.", "強化される分野はパークの説明文から判定しています。"));
            s.sources = vec![DataSource::HypixelResources];
            r.sections.push(s);
        }

        if !money.bullets.is_empty() || !money.metrics.is_empty() {
            r.sections.push(money);
        }

        if !weakest.is_empty() {
            let mut s = section("completion", ctx.t("Lowest completion", "達成率の低い分野"));
            s.table = Some(table(&["Part", "Done", ""], weakest.iter().take(3).map(|p| vec![text(&p.name), progress(p.percent / 100.0, format!("{:.1}%", p.percent)), text(&p.basis)]).collect()));
            s.sources = vec![DataSource::HypixelProfile, DataSource::NeuRepo];
            r.sections.push(s);
        }

        match ctx.live.map(|l| &l.news) {
            Some(news) if !news.is_empty() => {
                let mut s = section("news", ctx.t("Latest SkyBlock news", "SkyBlockの最新ニュース"));
                for n in news.iter().take(3) {
                    s.bullets.push(format!("{} ({}) — {}", n.title, n.date, n.link));
                }
                s.sources = vec![DataSource::HypixelResources];
                r.sections.push(s);
            }
            _ => r.unknowns.push(ctx.t("SkyBlock news not available right now.", "SkyBlockのニュースを今は取得できません。")),
        }
        r.freshness = market_freshness(ctx);
        r
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn perks_are_classified_from_their_text() {
        assert_eq!(perk_activities("Pelt-pocalypse", "Gain more Trevor rewards"), Vec::<&str>::new());
        assert_eq!(perk_activities("Prospection", "Mining minions work 25% faster"), vec!["mining"]);
        assert_eq!(perk_activities("Mining Fiesta", "Schedules a Mining Fiesta with double mithril powder"), vec!["mining"]);
        assert!(perk_activities("Farming Simulator", "+25 Farming Fortune and more Jacob's crops").contains(&"farming"));
        assert!(perk_activities("Shopping Spree", "Increase daily NPC buy limits by 10x").is_empty());
        assert!(perk_activities("Benevolence", "+25% more bank interest").contains(&"money"));
    }
}
