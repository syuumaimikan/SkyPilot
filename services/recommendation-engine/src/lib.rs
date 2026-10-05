//! SkyPilot recommendation engine.
//!
//! ```text
//! natural language goal -> GoalParser -> GoalSpec
//!   -> GraphBuilder (requirements, readiness, acquisition, money) -> GoalGraph
//!   -> providers propose CandidateActions -> ScoringStrategy -> "What should I do now?"
//!   -> Roadmap (ordered steps, progress, remaining cost/time)
//! ```
//! Every number comes from structured data (profile, registry, market). The optional LLM in
//! the API only rephrases these results.

pub mod assistant;
pub mod ctx;
pub mod goal_parser;
pub mod graph;
pub mod hud;
pub mod market_forms;
pub mod optimizer;
pub mod providers;
pub mod requirements;
pub mod roadmap;

use std::collections::BTreeMap;

use skypilot_protocol::advisor::AdvisorReport;
use skypilot_protocol::goals::{GoalParseResult, GoalPriority, GoalResult, GoalSpec, RecommendedAction};

pub use ctx::{Ctx, Lang};
pub use providers::{GoalContext, Provider};

use crate::optimizer::{DefaultStrategy, ScoringInput, ScoringStrategy};

pub struct PlanOutput {
    pub parse: GoalParseResult,
    pub goals: Vec<GoalResult>,
    pub actions: Vec<RecommendedAction>,
}

pub struct Planner {
    providers: Vec<Box<dyn Provider>>,
    strategy: Box<dyn ScoringStrategy>,
}

impl Default for Planner {
    fn default() -> Self {
        Planner { providers: providers::all(), strategy: Box::new(DefaultStrategy) }
    }
}

fn goal_id(spec: &GoalSpec) -> String {
    let raw = serde_json::to_string(&spec.kind).unwrap_or_default();
    let mut h: u64 = 1469598103934665603;
    for b in raw.bytes() {
        h ^= b as u64;
        h = h.wrapping_mul(1099511628211);
    }
    format!("g{:x}", h & 0xffff_ffff)
}

impl Planner {
    pub fn with_strategy(mut self, s: Box<dyn ScoringStrategy>) -> Self {
        self.strategy = s;
        self
    }

    /// Parse goal texts (first = primary) into specs.
    pub fn parse(&self, ctx: &Ctx, texts: &[(String, GoalPriority)]) -> (GoalParseResult, Vec<(GoalSpec, GoalPriority)>) {
        let parser = goal_parser::GoalParser::new(ctx.registry);
        let mut all = GoalParseResult { goals: Vec::new(), unmatched: Vec::new(), suggestions: Vec::new() };
        let mut specs = Vec::new();
        for (text, prio) in texts {
            let r = parser.parse(text);
            for g in &r.goals {
                if !specs.iter().any(|(s, _): &(GoalSpec, GoalPriority)| s.kind == g.kind) {
                    specs.push((g.clone(), *prio));
                }
            }
            all.goals.extend(r.goals);
            all.unmatched.extend(r.unmatched);
            all.suggestions.extend(r.suggestions);
        }
        (all, specs)
    }

    pub fn contexts(&self, ctx: &Ctx, specs: &[(GoalSpec, GoalPriority)]) -> Vec<GoalContext> {
        specs
            .iter()
            .map(|(spec, prio)| GoalContext { id: goal_id(spec), spec: spec.clone(), priority: *prio, graph: graph::GraphBuilder::build(ctx, spec) })
            .collect()
    }

    pub fn plan(&self, ctx: &Ctx, texts: &[(String, GoalPriority)], action_limit: usize) -> PlanOutput {
        let (parse, specs) = self.parse(ctx, texts);
        let goal_ctx = self.contexts(ctx, &specs);
        let mut candidates = Vec::new();
        for p in &self.providers {
            candidates.extend(p.actions(ctx, &goal_ctx));
        }
        let priorities: BTreeMap<String, GoalPriority> = goal_ctx.iter().map(|g| (g.id.clone(), g.priority)).collect();
        let coin_gap = goal_ctx.iter().filter_map(|g| g.open("money").and_then(|n| Some(n.target? - n.current.unwrap_or(0.0)))).fold(0.0, f64::max);
        let input = ScoringInput { style: ctx.play_style, liquid_coins: ctx.liquid_coins(), goal_priorities: &priorities, coin_gap };
        let actions = optimizer::finalize(candidates, self.strategy.as_ref(), &input, action_limit);
        let goals = goal_ctx
            .into_iter()
            .map(|g| {
                let roadmap = roadmap::build(&g.id, &g.spec.title, &g.graph, &actions);
                GoalResult { id: g.id, spec: g.spec, priority: g.priority, graph: graph::prune(g.graph), roadmap }
            })
            .collect();
        PlanOutput { parse, goals, actions }
    }

    pub fn report(&self, ctx: &Ctx, domain: &str, texts: &[(String, GoalPriority)]) -> Option<AdvisorReport> {
        let provider = self.providers.iter().find(|p| p.id() == domain)?;
        let (_, specs) = self.parse(ctx, texts);
        let goal_ctx = self.contexts(ctx, &specs);
        let mut report = provider.report(ctx, &goal_ctx);
        let priorities: BTreeMap<String, GoalPriority> = goal_ctx.iter().map(|g| (g.id.clone(), g.priority)).collect();
        let input = ScoringInput { style: ctx.play_style, liquid_coins: ctx.liquid_coins(), goal_priorities: &priorities, coin_gap: 0.0 };
        report.actions = optimizer::finalize(provider.actions(ctx, &goal_ctx), self.strategy.as_ref(), &input, 10);
        Some(report)
    }

    pub fn domains(&self) -> Vec<&'static str> {
        self.providers.iter().map(|p| p.id()).collect()
    }
}
