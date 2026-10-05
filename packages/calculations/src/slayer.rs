//! Slayer economics. Drop chances come from NEU-REPO drop tables; prices from the
//! market engine. Results are *expected values* - never guaranteed profit.

#[derive(Debug, Clone, PartialEq)]
pub struct DropInput {
    pub chance: f64,
    pub amount: f64,
    pub unit_price: Option<f64>,
}

#[derive(Debug, Clone, Copy, PartialEq)]
pub struct BossEconomics {
    /// Sum of priced drop EVs.
    pub expected_drop_value: f64,
    /// EV contributed by drops with chance < `rare_threshold`.
    pub rare_drop_ev: f64,
    pub expected_net: f64,
    /// Share of the drop table (by chance) that could be priced.
    pub priced_share: f64,
}

/// Parse "21.26%" / "0.0565%" / "100%" into a probability.
pub fn parse_chance(text: &str) -> Option<f64> {
    let t = text.trim().trim_end_matches('%').trim();
    let v: f64 = t.parse().ok()?;
    (v.is_finite() && v >= 0.0).then(|| (v / 100.0).min(1.0))
}

/// Parse NEU drop amount hints like "x6-8", "§eDrop Count: §7x50-64", "x1" to an average.
pub fn parse_amount(extra: &[String]) -> f64 {
    for e in extra {
        let clean: String = strip_formatting(e);
        if let Some(idx) = clean.find('x') {
            let rest = &clean[idx + 1..];
            let nums: Vec<f64> = rest
                .split(|c: char| c == '-' || c.is_whitespace())
                .filter_map(|p| p.replace(',', "").parse::<f64>().ok())
                .take(2)
                .collect();
            match nums.as_slice() {
                [a, b] => return (a + b) / 2.0,
                [a] => return *a,
                _ => {}
            }
        }
    }
    1.0
}

pub fn strip_formatting(s: &str) -> String {
    let mut out = String::with_capacity(s.len());
    let mut chars = s.chars();
    while let Some(c) = chars.next() {
        if c == '§' {
            chars.next();
        } else {
            out.push(c);
        }
    }
    out
}

pub fn boss_economics(drops: &[DropInput], boss_cost: f64, rare_threshold: f64) -> BossEconomics {
    let mut ev = 0.0;
    let mut rare = 0.0;
    let mut priced = 0.0;
    let mut total = 0.0;
    for d in drops {
        if !d.chance.is_finite() || d.chance <= 0.0 {
            continue;
        }
        total += d.chance;
        if let Some(p) = d.unit_price.filter(|p| p.is_finite() && *p >= 0.0) {
            let amount = if d.amount.is_finite() && d.amount > 0.0 { d.amount } else { 1.0 };
            let e = d.chance * amount * p;
            if e.is_finite() {
                ev += e;
                priced += d.chance;
                if d.chance < rare_threshold {
                    rare += e;
                }
            }
        }
    }
    let cost = if boss_cost.is_finite() { boss_cost.max(0.0) } else { 0.0 };
    BossEconomics {
        expected_drop_value: ev,
        rare_drop_ev: rare,
        expected_net: ev - cost,
        priced_share: if total > 0.0 { (priced / total).clamp(0.0, 1.0) } else { 0.0 },
    }
}

/// Bosses needed to gain `xp_remaining`, given XP per boss and an XP multiplier (e.g. Aatrox +25%).
pub fn bosses_needed(xp_remaining: f64, xp_per_boss: f64, multiplier: f64) -> Option<f64> {
    if !xp_remaining.is_finite() || xp_remaining <= 0.0 {
        return Some(0.0);
    }
    let m = if multiplier.is_finite() && multiplier > 0.0 { multiplier } else { 1.0 };
    let per = xp_per_boss * m;
    if !per.is_finite() || per <= 0.0 {
        return None;
    }
    Some((xp_remaining / per).ceil())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parsing() {
        assert_eq!(parse_chance("21.26%"), Some(0.2126));
        assert_eq!(parse_chance("bad"), None);
        assert_eq!(parse_amount(&["x6-8".into()]), 7.0);
        assert_eq!(parse_amount(&["§eDrop Count: §7x50-64".into()]), 57.0);
        assert_eq!(parse_amount(&[]), 1.0);
    }

    #[test]
    fn economics() {
        let drops = vec![
            DropInput { chance: 1.0, amount: 100.0, unit_price: Some(10.0) },
            DropInput { chance: 0.001, amount: 1.0, unit_price: Some(1_000_000.0) },
            DropInput { chance: 0.5, amount: 1.0, unit_price: None },
        ];
        let e = boss_economics(&drops, 50_000.0, 0.01);
        assert!((e.expected_drop_value - 2000.0).abs() < 1e-9);
        assert!((e.rare_drop_ev - 1000.0).abs() < 1e-9);
        assert!((e.expected_net + 48_000.0).abs() < 1e-9);
        assert_eq!(bosses_needed(1000.0, 500.0, 1.0), Some(2.0));
        assert_eq!(bosses_needed(1000.0, 0.0, 1.0), None);
    }
}
