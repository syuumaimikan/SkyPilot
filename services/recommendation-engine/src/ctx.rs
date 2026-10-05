//! Shared context passed to the planner and every provider.

use skypilot_data::Registry;
use skypilot_pricing::{MarketEngine, PriceOracle, PriceSide, Quote};
use skypilot_profile::Inventories;
use skypilot_protocol::goals::PlayStyle;
use skypilot_protocol::profile::{LevelInfo, PlayerAnalysis};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
pub enum Lang {
    #[default]
    En,
    Ja,
}

impl Lang {
    pub fn parse(s: Option<&str>) -> Lang {
        match s.map(|x| x.to_ascii_lowercase()) {
            Some(x) if x.starts_with("ja") => Lang::Ja,
            _ => Lang::En,
        }
    }
    /// Pick the English or Japanese variant.
    pub fn t(self, en: impl Into<String>, ja: impl Into<String>) -> String {
        match self {
            Lang::En => en.into(),
            Lang::Ja => ja.into(),
        }
    }
}

pub struct Ctx<'a> {
    pub registry: &'a Registry,
    pub oracle: &'a dyn PriceOracle,
    pub market: Option<&'a MarketEngine>,
    pub analysis: &'a PlayerAnalysis,
    pub inv: &'a Inventories,
    pub play_style: PlayStyle,
    pub budget: Option<f64>,
    pub now_ms: i64,
    pub lang: Lang,
}

impl<'a> Ctx<'a> {
    pub fn t(&self, en: impl Into<String>, ja: impl Into<String>) -> String {
        self.lang.t(en, ja)
    }

    /// Purse + bank (+ personal bank) - coins usable right now.
    pub fn liquid_coins(&self) -> f64 {
        let e = &self.analysis.economy;
        e.purse + e.bank.as_ref().copied().unwrap_or(0.0) + e.personal_bank.as_ref().copied().unwrap_or(0.0)
    }

    /// Spendable budget: explicit budget if given, else liquid coins.
    pub fn spendable(&self) -> f64 {
        self.budget.unwrap_or_else(|| self.liquid_coins())
    }

    pub fn skill(&self, id: &str) -> Option<&LevelInfo> {
        self.analysis.skills.as_ref().and_then(|s| s.iter().find(|x| x.id.eq_ignore_ascii_case(id)))
    }

    pub fn skill_level(&self, id: &str) -> Option<u32> {
        self.skill(id).map(|s| s.level)
    }

    pub fn catacombs_level(&self) -> Option<u32> {
        self.analysis.dungeons.as_ref().map(|d| d.catacombs.level)
    }

    pub fn hotm_level(&self) -> Option<u32> {
        self.analysis.mining.as_ref().and_then(|m| m.hotm.as_ref()).map(|h| h.level)
    }

    pub fn magical_power(&self) -> Option<u32> {
        self.analysis.accessories.as_ref().map(|a| a.magical_power)
    }

    pub fn slayer(&self, id: &str) -> Option<&skypilot_protocol::profile::SlayerInfo> {
        self.analysis.slayers.as_ref().and_then(|s| s.iter().find(|x| x.id == id))
    }

    pub fn buy(&self, id: &str) -> Option<Quote> {
        self.oracle.quote(id, PriceSide::Buy)
    }

    pub fn sell(&self, id: &str) -> Option<Quote> {
        self.oracle.quote(id, PriceSide::Sell)
    }

    /// True if the player owns the item anywhere (inventories, accessories, pets as PET:TYPE).
    pub fn owns(&self, id: &str) -> bool {
        let pets = self.analysis.pets.as_ref().map(Vec::as_slice).unwrap_or(&[]);
        skypilot_profile::gear::owns_any(self.inv, pets, &[id.to_string()])
    }

    pub fn display_name(&self, id: &str) -> String {
        self.registry.display_name(id)
    }

    /// Evaluate a requirement mini-expression ("skill:MINING>=12", "catacombs>=1", "hotm>=1",
    /// "garden>=1", "purse>=5000000", "slayer:enderman>=3"). Unknown data => None.
    pub fn check(&self, expr: &str) -> Option<bool> {
        let expr = expr.trim();
        if expr.is_empty() {
            return Some(true);
        }
        let (lhs, rhs) = expr.split_once(">=")?;
        let target: f64 = rhs.trim().parse().ok()?;
        let value: Option<f64> = match lhs.trim() {
            l if l.starts_with("skill:") => self.skill_level(&l[6..]).map(f64::from),
            l if l.starts_with("slayer:") => self.slayer(&l[7..]).map(|s| s.level.level as f64),
            "catacombs" => self.catacombs_level().map(f64::from),
            "hotm" => self.hotm_level().map(f64::from).or(Some(0.0)),
            "garden" => self.analysis.garden.as_ref().and_then(|g| g.garden_level.as_ref()).map(|l| l.level as f64).or(Some(0.0)),
            "purse" => Some(self.liquid_coins()),
            "mp" => self.magical_power().map(f64::from),
            _ => None,
        };
        value.map(|v| v >= target)
    }
}

pub fn fmt_coins(v: f64) -> String {
    let a = v.abs();
    let s = if a >= 1e9 {
        format!("{:.2}B", v / 1e9)
    } else if a >= 1e6 {
        format!("{:.1}M", v / 1e6)
    } else if a >= 1e3 {
        format!("{:.1}K", v / 1e3)
    } else {
        format!("{:.0}", v)
    };
    s.replace(".0M", "M").replace(".0K", "K")
}

pub fn fmt_hours(h: f64) -> String {
    if !h.is_finite() {
        return "?".into();
    }
    if h < 1.0 {
        format!("{}m", (h * 60.0).round() as i64)
    } else if h < 48.0 {
        let hh = h.floor() as i64;
        let mm = ((h - hh as f64) * 60.0).round() as i64;
        if mm == 0 { format!("{hh}h") } else { format!("{hh}h {mm}m") }
    } else {
        format!("{:.1}d", h / 24.0)
    }
}
