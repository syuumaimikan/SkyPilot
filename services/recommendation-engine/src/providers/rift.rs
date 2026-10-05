//! Rift Advisor.

use skypilot_protocol::advisor::AdvisorReport;
use skypilot_protocol::common::{DataSource, Metric};

use super::*;
use crate::ctx::Ctx;
use crate::optimizer::CandidateAction;

const ROUTE: [(&str, &str, &str); 8] = [
    ("Wizard Tower", "Finish the Wizard Tower questline (Rift Time upgrades).", "ウィザードタワーのクエストを完了（Rift Time強化）。"),
    ("West Village glyphs", "Complete the glyph puzzles in West Village.", "西の村のグリフを完了。"),
    ("Village Plaza (murder)", "Solve the Village Plaza murder mystery.", "村の広場の殺人事件を解決。"),
    ("Black Lagoon", "Deliver the science paper at the Black Lagoon.", "ブラックラグーンで研究論文を届ける。"),
    ("Wyld Woods", "Finish the bug hunter chain in Wyld Woods.", "ワイルドの森の虫ハンターを完了。"),
    ("Mirrorverse", "Clear the Mirrorverse rooms for the reward.", "ミラーバースを攻略して報酬を受け取る。"),
    ("Dreadfarm", "Progress Shania at the Dreadfarm.", "ドレッドファームでShaniaを進める。"),
    ("Castle", "Complete the Castle fairy steps.", "城の妖精ステップを完了。"),
];

pub struct RiftProvider;

impl Provider for RiftProvider {
    fn id(&self) -> &'static str {
        "rift"
    }

    fn actions(&self, _ctx: &Ctx, _goals: &[GoalContext]) -> Vec<CandidateAction> {
        Vec::new()
    }

    fn report(&self, ctx: &Ctx, _goals: &[GoalContext]) -> AdvisorReport {
        let mut r = report("rift", ctx.t("The Rift", "リフト"));
        let Some(rift) = ctx.analysis.rift.as_ref() else {
            r.unknowns.push(ctx.t("The Rift has not been visited", "リフト未訪問"));
            return r;
        };
        r.summary.push(Metric::value(ctx.t("Motes", "モート"), rift.motes_purse.unwrap_or(0.0), None));
        r.summary.push(Metric::value(ctx.t("Enigma souls found", "エニグマソウル"), rift.enigma_souls as f64, None));
        r.summary.push(Metric::value(ctx.t("Timecharms secured", "タイムチャーム"), rift.timecharms.len() as f64, None));
        if let Some(v) = rift.vampire_slayer_level {
            r.summary.push(Metric::value(ctx.t("Vampire Slayer", "ヴァンパイアスレイヤー"), v as f64, None));
        }
        let mut route = section("route", ctx.t("Progression route", "進行ルート"));
        route.table = Some(table(
            &["Area", "Done", "Next step"],
            ROUTE.iter().map(|(area, en, ja)| {
                let done = rift.completed_areas.iter().any(|a| a == area);
                vec![text(*area), yes_no(ctx, done), text(if done { String::new() } else { ctx.t(*en, *ja) })]
            }).collect(),
        ));
        route.notes.push(ctx.t("Completion is inferred from quest flags present in the API; areas without flags are shown as not done.", "完了状況はAPIのクエストフラグから判定。フラグがない場合は未完了表示になります。"));
        route.sources = vec![DataSource::HypixelProfile];
        r.sections.push(route);
        let mut tc = section("timecharms", ctx.t("Timecharms", "タイムチャーム"));
        tc.bullets = rift.timecharms.clone();
        tc.bullets.push(format!("{}: {}", ctx.t("Dead cats found", "見つけた猫"), rift.dead_cats_found));
        tc.bullets.push(format!("Montezuma: {}", if rift.montezuma_unlocked { "✔" } else { "✘" }));
        r.sections.push(tc);
        r
    }
}
