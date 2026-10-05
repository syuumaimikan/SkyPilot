//! Time-based flips: Forge processes and Kat pet upgrades. Value is created by waiting,
//! so the key metric is profit per day of processing time (per slot).

use skypilot_data::Registry;
use skypilot_data::neu::Recipe;
use skypilot_protocol::common::Confidence;
use skypilot_protocol::market::{IngredientCost, TimeFlip, TimeFlipKind};

use crate::craft::CraftCalculator;
use crate::oracle::{PriceOracle, PriceSide};

pub fn time_flips(registry: &Registry, oracle: &dyn PriceOracle, bazaar_tax: f64, ah_fee: &dyn Fn(f64) -> f64) -> Vec<TimeFlip> {
    let mut calc = CraftCalculator::new(registry, oracle);
    let mut out = Vec::new();
    for (out_id, recipes) in &registry.neu.recipes {
        for r in recipes {
            let (kind, inputs, coins, seconds, output_count, input_summary) = match r {
                Recipe::Forge { output, inputs, duration_secs } => {
                    let summary = inputs.iter().map(|s| format!("{}x {}", s.amount, registry.display_name(&s.id))).collect::<Vec<_>>().join(", ");
                    (TimeFlipKind::Forge, inputs.clone(), 0.0, *duration_secs, output.amount, summary)
                }
                Recipe::Kat { input, coins, duration_secs, items, .. } => {
                    let mut all = vec![skypilot_data::neu::Stack { id: input.clone(), amount: 1.0 }];
                    all.extend(items.iter().cloned());
                    (TimeFlipKind::Kat, all, *coins, *duration_secs, 1.0, format!("{} + {:.0} coins", registry.display_name(input), coins))
                }
                _ => continue,
            };
            if seconds <= 0.0 {
                continue;
            }
            let Some(sale) = oracle.quote(out_id, PriceSide::Sell) else { continue };
            let mut capital = coins;
            let mut ingredients = Vec::new();
            let mut complete = true;
            let mut conf = sale.confidence;
            for st in &inputs {
                if st.id == "SKYBLOCK_COIN" {
                    capital += st.amount;
                    continue;
                }
                match calc.unit_cost(&st.id) {
                    Some(c) if c.unit_price.is_some() => {
                        let unit = c.unit_price.unwrap_or(0.0);
                        capital += unit * st.amount;
                        if let Some(q) = oracle.quote(&st.id, PriceSide::Buy) {
                            conf = conf.min(q.confidence.max(0.3));
                        }
                        ingredients.push(IngredientCost { amount: st.amount, total: Some(unit * st.amount), ..c });
                    }
                    _ => {
                        complete = false;
                        break;
                    }
                }
            }
            if !complete || capital <= 0.0 {
                continue;
            }
            let final_value = sale.price * output_count;
            let fees = if oracle.is_bazaar(out_id) { final_value * bazaar_tax } else { ah_fee(final_value) };
            let profit = final_value - fees - capital;
            if profit <= 0.0 {
                continue;
            }
            let hours = seconds / 3600.0;
            let profit_per_day = profit / (hours / 24.0).max(1.0 / 24.0);
            out.push(TimeFlip {
                kind,
                output_id: out_id.clone(),
                name: registry.display_name(out_id),
                input_summary,
                capital,
                processing_hours: hours,
                final_value,
                fees,
                profit,
                profit_per_day,
                roi: profit / capital,
                ingredients,
                confidence: Confidence::from_score(conf, vec![]),
                requirement: match kind {
                    TimeFlipKind::Forge => Some("Forge slot (Dwarven Mines); some recipes need HOTM tiers".into()),
                    TimeFlipKind::Kat => Some("Kat (Pet Sitter) in the Hub".into()),
                },
            });
        }
    }
    out.sort_by(|a, b| (b.profit_per_day * b.confidence.score).total_cmp(&(a.profit_per_day * a.confidence.score)));
    out
}
