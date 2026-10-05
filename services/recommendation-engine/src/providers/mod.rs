//! Content providers. Each provider is independent: it proposes candidate actions for the
//! optimizer and renders its advisor report. New SkyBlock content = new provider (plus data),
//! without touching the core planner.

pub mod accessories;
pub mod collections;
pub mod combat;
pub mod daily;
pub mod dungeons;
pub mod events;
pub mod farming;
pub mod fishing;
pub mod foraging;
pub mod gear;
pub mod hotm;
pub mod kuudra;
pub mod liquidation;
pub mod minions;
pub mod mining;
pub mod money;
pub mod museum;
pub mod pets;
pub mod rift;
pub mod slayer;

use skypilot_protocol::advisor::{AdvisorReport, AdvisorSection, Cell, Table, Tone};
use skypilot_protocol::common::{DataSource, Freshness, Metric};
use skypilot_protocol::goals::{GoalGraph, GoalNode, GoalPriority, GoalSpec, NodeStatus};

use crate::ctx::Ctx;
use crate::optimizer::CandidateAction;

/// A goal with its built graph, visible to providers when proposing actions.
#[derive(Debug, Clone)]
pub struct GoalContext {
    pub id: String,
    pub spec: GoalSpec,
    pub priority: GoalPriority,
    pub graph: GoalGraph,
}

impl GoalContext {
    pub fn node(&self, id: &str) -> Option<&GoalNode> {
        self.graph.nodes.iter().find(|n| n.id == id)
    }
    /// Unsatisfied node by id.
    pub fn open(&self, id: &str) -> Option<&GoalNode> {
        self.node(id).filter(|n| !matches!(n.status, NodeStatus::Satisfied))
    }
    pub fn open_nodes(&self) -> impl Iterator<Item = &GoalNode> {
        self.graph.nodes.iter().filter(|n| !matches!(n.status, NodeStatus::Satisfied))
    }
    pub fn open_count(&self) -> usize {
        self.open_nodes().count().max(1)
    }
    /// Share of this goal's remaining work represented by closing `fraction` of node `id`.
    pub fn impact(&self, id: &str, fraction: f64) -> Option<f64> {
        self.open(id).map(|_| (fraction.clamp(0.0, 1.0) / self.open_count() as f64).max(0.0))
    }
}

pub trait Provider: Send + Sync {
    fn id(&self) -> &'static str;
    fn actions(&self, ctx: &Ctx, goals: &[GoalContext]) -> Vec<CandidateAction>;
    fn report(&self, ctx: &Ctx, goals: &[GoalContext]) -> AdvisorReport;
}

pub fn all() -> Vec<Box<dyn Provider>> {
    vec![
        Box::new(accessories::AccessoriesProvider),
        Box::new(slayer::SlayerProvider),
        Box::new(dungeons::DungeonsProvider),
        Box::new(mining::MiningProvider),
        Box::new(farming::FarmingProvider),
        Box::new(fishing::FishingProvider),
        Box::new(foraging::ForagingProvider),
        Box::new(kuudra::KuudraProvider),
        Box::new(rift::RiftProvider),
        Box::new(minions::MinionsProvider),
        Box::new(collections::CollectionsProvider),
        Box::new(museum::MuseumProvider),
        Box::new(pets::PetsProvider),
        Box::new(gear::GearProvider),
        Box::new(combat::CombatProvider),
        Box::new(money::MoneyProvider),
        Box::new(daily::DailyProvider),
        Box::new(events::EventsProvider),
        Box::new(liquidation::LiquidationProvider),
    ]
}

pub fn find(domain: &str) -> Option<Box<dyn Provider>> {
    all().into_iter().find(|p| p.id() == domain)
}

// ---------------------------------------------------------------- report helpers

pub fn report(domain: &str, title: impl Into<String>) -> AdvisorReport {
    AdvisorReport { domain: domain.to_string(), title: title.into(), summary: Vec::new(), sections: Vec::new(), actions: Vec::new(), unknowns: Vec::new(), freshness: Vec::new() }
}

pub fn section(id: &str, title: impl Into<String>) -> AdvisorSection {
    AdvisorSection::new(id, title)
}

pub fn table(columns: &[&str], rows: Vec<Vec<Cell>>) -> Table {
    Table { columns: columns.iter().map(|c| c.to_string()).collect(), rows }
}

pub fn text(s: impl Into<String>) -> Cell {
    Cell::Text { text: s.into() }
}
pub fn coins(v: f64) -> Cell {
    Cell::Coins { value: v }
}
pub fn coins_opt(v: Option<f64>) -> Cell {
    v.map(coins).unwrap_or(Cell::Unknown { reason: "No market data".into() })
}
pub fn num(v: f64, unit: Option<&str>) -> Cell {
    Cell::Number { value: v, unit: unit.map(str::to_string), digits: Some(if v.fract() == 0.0 { 0 } else { 1 }) }
}
pub fn pct(v: f64) -> Cell {
    Cell::Percent { value: v }
}
pub fn hours(h: f64) -> Cell {
    Cell::Duration { hours: h }
}
pub fn badge(t: impl Into<String>, tone: Tone) -> Cell {
    Cell::Badge { text: t.into(), tone }
}
pub fn item(ctx: &Ctx, id: &str) -> Cell {
    Cell::Item { item_id: id.to_string(), name: ctx.display_name(id), rarity: ctx.registry.item(id).and_then(|i| i.tier.clone()) }
}
pub fn progress(v: f64, label: impl Into<String>) -> Cell {
    Cell::Progress { value: v.clamp(0.0, 1.0), label: Some(label.into()) }
}
pub fn unknown(reason: &str) -> Cell {
    Cell::Unknown { reason: reason.to_string() }
}

pub fn yes_no(ctx: &Ctx, ok: bool) -> Cell {
    if ok { badge(ctx.t("Yes", "はい"), Tone::Good) } else { badge(ctx.t("No", "いいえ"), Tone::Bad) }
}

pub fn metric(label: impl Into<String>, v: f64, unit: Option<&str>) -> Metric {
    Metric::value(label, v, unit)
}

pub fn market_freshness(ctx: &Ctx) -> Vec<Freshness> {
    ctx.market.map(|m| {
        let (a, _, b) = m.freshness();
        vec![a, b]
    })
    .unwrap_or_default()
}

pub fn sources(list: &[DataSource]) -> Vec<DataSource> {
    list.to_vec()
}
