//! Grounded assistant. Answers are assembled ONLY from structured data (analysis, roadmap,
//! actions, market). When the data does not contain the answer, it says so ("確認できません").
//! The API may pass the produced `facts` to an LLM purely for phrasing.

use serde::Serialize;
use skypilot_protocol::goals::{AnalyzeResponse, NodeStatus};

use crate::ctx::{Lang, fmt_coins, fmt_hours};

#[derive(Debug, Clone, Serialize, PartialEq)]
pub enum Intent {
    WhatNow,
    Progress,
    Why,
    Money,
    Price(String),
    Requirement(String),
    Stat(String),
    Unknown,
}

pub fn classify(q: &str) -> Intent {
    let l = q.to_lowercase();
    let any = |ws: &[&str]| ws.iter().any(|w| l.contains(w));
    if any(&["今何", "何すれば", "what should i do", "what now", "next step", "次は", "次に何"]) {
        return Intent::WhatNow;
    }
    if any(&["なぜ", "why", "理由"]) {
        return Intent::Why;
    }
    if any(&["進捗", "progress", "roadmap", "ロードマップ", "あとどれ"]) {
        return Intent::Progress;
    }
    if any(&["金策", "money", "coins/h", "稼ぎ", "make coins"]) {
        return Intent::Money;
    }
    for marker in ["price of ", "how much is ", "how much are "] {
        if let Some(rest) = l.split(marker).nth(1) {
            return Intent::Price(rest.trim_matches(|c: char| c == '?' || c.is_whitespace()).to_string());
        }
    }
    if let Some(idx) = l.find("いくら") {
        let item = l[..idx].trim_end_matches(['は', 'の', 'っ', 'て']).trim().to_string();
        if !item.is_empty() {
            return Intent::Price(item);
        }
    }
    for marker in ["requirements for ", "requirement for ", "how do i get "] {
        if let Some(rest) = l.split(marker).nth(1) {
            return Intent::Requirement(rest.trim_matches(|c: char| c == '?' || c.is_whitespace()).to_string());
        }
    }
    if let Some(idx) = l.find("の条件") {
        return Intent::Requirement(l[..idx].trim().to_string());
    }
    for stat in ["mp", "magical power", "catacombs", "cata", "hotm", "networth", "skill average"] {
        if l.contains(stat) {
            return Intent::Stat(stat.to_string());
        }
    }
    Intent::Unknown
}

#[derive(Debug, Clone, Serialize)]
pub struct Answer {
    pub intent: Intent,
    pub text: String,
    /// Structured facts the text was built from (also given to the LLM, if enabled).
    pub facts: Vec<String>,
    pub grounded: bool,
}

pub fn answer(q: &str, resp: Option<&AnalyzeResponse>, lang: Lang, price_lookup: &dyn Fn(&str) -> Option<String>, requirement_lookup: &dyn Fn(&str) -> Option<String>) -> Answer {
    let intent = classify(q);
    let unknown = |intent: Intent, why: String| Answer { intent, text: lang.t(format!("I can't confirm that from the available data. {why}"), format!("確認できません。{why}")), facts: vec![], grounded: false };
    match &intent {
        Intent::Price(item) => {
            return match price_lookup(item) {
                Some(f) => Answer { intent: intent.clone(), text: f.clone(), facts: vec![f], grounded: true },
                None => unknown(intent.clone(), lang.t("No market data for that item.", "そのアイテムの市場データがありません。")),
            };
        }
        Intent::Requirement(item) => {
            return match requirement_lookup(item) {
                Some(f) => Answer { intent: intent.clone(), text: f.clone(), facts: vec![f], grounded: true },
                None => unknown(intent.clone(), lang.t("Item not found in the Hypixel items list.", "Hypixelのアイテム一覧に見つかりません。")),
            };
        }
        _ => {}
    }
    let Some(r) = resp else {
        return unknown(intent, lang.t("Run an analysis first (username + goal).", "先にユーザー名と目標で分析してください。"));
    };
    let goal = r.goals.first();
    let mut facts = Vec::new();
    if let Some(g) = goal {
        facts.push(format!("goal={} progress={:.0}%", g.spec.title, g.roadmap.overall_progress * 100.0));
        if let Some(c) = g.roadmap.remaining_cost {
            facts.push(format!("remaining_cost={}", fmt_coins(c)));
        }
    }
    let text = match &intent {
        Intent::WhatNow => match r.actions.first() {
            Some(a) => {
                facts.push(format!("top_action={} why={}", a.title, a.why));
                let then = r.actions.get(1).map(|b| b.title.clone());
                let mut t = match goal {
                    Some(g) => lang.t(format!("Your goal is {}. ", g.spec.title), format!("現在の目標は{}です。", g.spec.title)),
                    None => String::new(),
                };
                t += &lang.t(format!("Top priority: {}. {}", a.title, a.why), format!("最優先は「{}」です。{}", a.title, a.why));
                if let Some(c) = a.estimated_cost {
                    t += &lang.t(format!(" Estimated cost: {}.", fmt_coins(c)), format!(" 推定費用は{}です。", fmt_coins(c)));
                }
                if let Some(n) = then {
                    t += &lang.t(format!(" After that: {n}."), format!(" その後は「{n}」を進めてください。"));
                }
                t
            }
            None => return unknown(intent, lang.t("No actions were generated for your goals.", "目標に対するアクションがありません。")),
        },
        Intent::Progress => match goal {
            Some(g) => {
                let open: Vec<_> = g.roadmap.steps.iter().filter(|s| s.status != NodeStatus::Satisfied).take(3).map(|s| s.title.clone()).collect();
                lang.t(
                    format!("{}: {:.0}% complete. Remaining: {}. Est. cost {}, time {}.", g.spec.title, g.roadmap.overall_progress * 100.0, open.join(" → "), g.roadmap.remaining_cost.map(fmt_coins).unwrap_or_else(|| "?".into()), g.roadmap.remaining_hours.map(fmt_hours).unwrap_or_else(|| "?".into())),
                    format!("{}: 進捗{:.0}%。残り: {}。推定費用{}、推定時間{}。", g.spec.title, g.roadmap.overall_progress * 100.0, open.join(" → "), g.roadmap.remaining_cost.map(fmt_coins).unwrap_or_else(|| "?".into()), g.roadmap.remaining_hours.map(fmt_hours).unwrap_or_else(|| "?".into())),
                )
            }
            None => return unknown(intent, lang.t("No goal set.", "目標が設定されていません。")),
        },
        Intent::Why => match r.actions.first() {
            Some(a) => {
                facts.push(format!("priority={:.3} impact={:.2} cost={} hours={:.2}", a.score.priority, a.score.goal_impact, a.score.cost, a.score.time_hours));
                lang.t(format!("{} — {} (goal impact {:.0}%, cost {}, ~{}).", a.title, a.why, a.score.goal_impact * 100.0, fmt_coins(a.score.cost), fmt_hours(a.score.time_hours)), format!("{} — {}（目標への寄与{:.0}%、費用{}、約{}）", a.title, a.why, a.score.goal_impact * 100.0, fmt_coins(a.score.cost), fmt_hours(a.score.time_hours)))
            }
            None => return unknown(intent, String::new()),
        },
        Intent::Money => match r.actions.iter().find(|a| a.score.coin_return_per_hour > 0.0) {
            Some(a) => lang.t(format!("{} (~{}/h). {}", a.title, fmt_coins(a.score.coin_return_per_hour), a.why), format!("{}（約{}/時）。{}", a.title, fmt_coins(a.score.coin_return_per_hour), a.why)),
            None => lang.t("Open Money Making for the full personalised ranking.", "金策ページで個別ランキングを確認してください。"),
        },
        Intent::Stat(s) => {
            let a = &r.analysis;
            let v = match s.as_str() {
                "mp" | "magical power" => a.accessories.as_ref().map(|x| format!("Magical Power {}", x.magical_power)),
                "catacombs" | "cata" => a.dungeons.as_ref().map(|d| format!("Catacombs {:.2}", d.catacombs.level_with_progress)),
                "hotm" => a.mining.as_ref().and_then(|m| m.hotm.as_ref()).map(|h| format!("HOTM {}", h.level)),
                "networth" => a.networth.as_ref().map(|n| format!("Networth {}", fmt_coins(n.total))),
                "skill average" => a.skill_average.as_ref().map(|v| format!("Skill average {v:.2}")),
                _ => None,
            };
            match v {
                Some(v) => v,
                None => return unknown(intent, lang.t("Not available from API.", "APIから取得できません。")),
            }
        }
        Intent::Unknown => return unknown(intent, lang.t("Try: \"What should I do now?\", \"How much is Hyperion?\", \"Progress\".", "例: 「今何すればいい？」「Hyperionはいくら？」「進捗」")),
        Intent::Price(_) | Intent::Requirement(_) => unreachable!(),
    };
    Answer { intent, text, facts, grounded: true }
}
