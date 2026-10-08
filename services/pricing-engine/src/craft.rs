//! Recursive recipe costing and craft flips (Bazaar/AH ingredients -> product).

use std::collections::{HashMap, HashSet};

use skypilot_data::Registry;
use skypilot_data::neu::{Recipe, Stack};
use skypilot_protocol::common::Confidence;
use skypilot_protocol::market::{CraftFlip, IngredientCost};

use crate::oracle::{PriceOracle, PriceSide};

const MAX_DEPTH: usize = 6;

pub struct CraftCalculator<'a> {
    pub registry: &'a Registry,
    pub oracle: &'a dyn PriceOracle,
    memo: HashMap<String, Option<IngredientCost>>,
}

impl<'a> CraftCalculator<'a> {
    pub fn new(registry: &'a Registry, oracle: &'a dyn PriceOracle) -> Self {
        CraftCalculator { registry, oracle, memo: HashMap::new() }
    }

    fn crafting_recipes(&self, id: &str) -> Vec<(&Stack, &Vec<Stack>)> {
        self.registry
            .neu
            .recipes
            .get(id)
            .map(|rs| {
                rs.iter()
                    .filter_map(|r| match r {
                        Recipe::Crafting { output, inputs, .. } => Some((output, inputs)),
                        _ => None,
                    })
                    .collect()
            })
            .unwrap_or_default()
    }

    /// Cheapest way to obtain one unit of `id`: buy it or craft it (recursively).
    pub fn unit_cost(&mut self, id: &str) -> Option<IngredientCost> {
        let mut stack = HashSet::new();
        self.unit_cost_inner(id, 0, &mut stack)
    }

    fn unit_cost_inner(&mut self, id: &str, depth: usize, visiting: &mut HashSet<String>) -> Option<IngredientCost> {
        if id == "SKYBLOCK_COIN" {
            return Some(leaf(id, "Coins", 1.0, Some(1.0), "Coins"));
        }
        if let Some(m) = self.memo.get(id).filter(|_|depth==0) {
            return m.clone();
        }
        if !visiting.insert(id.to_string()) {
            return None; // cycle
        }
        let name = self.registry.display_name(id);
        let bought = self.oracle.quote(id, PriceSide::Buy).filter(|q|q.price.is_finite()&&q.price>0.0).map(|q| leaf(id, &name, 1.0, Some(q.price), q.source));
        let mut best = bought;
        if depth < MAX_DEPTH {
            let recipes: Vec<(Stack, Vec<Stack>)> = self.crafting_recipes(id).into_iter().map(|(o, i)| (o.clone(), i.clone())).collect();
            for (output, inputs) in recipes {
                if !valid_recipe(&output,&inputs) {continue;}
                let mut children = Vec::new();
                let mut total = 0.0;
                let mut complete = true;
                for input in &inputs {
                    match self.unit_cost_inner(&input.id, depth + 1, visiting) {
                        Some(c) => {
                            let unit = c.unit_price.unwrap_or(0.0);
                            total += unit * input.amount;
                            children.push(scaled(c,input.amount/output.amount));
                        }
                        None => {
                            complete = false;
                            children.push(leaf(&input.id, &self.registry.display_name(&input.id), input.amount, None, "Unknown"));
                        }
                    }
                }
                if !complete || !total.is_finite() {
                    continue;
                }
                let per_unit = total / output.amount.max(1.0);
                if best.as_ref().and_then(|b| b.unit_price).is_none_or(|b| per_unit < b) {
                    best = Some(IngredientCost {
                        item_id: id.to_string(),
                        name: name.clone(),
                        amount: 1.0,
                        unit_price: Some(per_unit),
                        total: Some(per_unit),
                        source: "Craft".into(),
                        children,
                    });
                }
            }
        }
        visiting.remove(id);
        if depth==0 {self.memo.insert(id.to_string(), best.clone());}
        best
    }

    /// Cost of crafting `id` from ingredients (ignoring the option to buy `id` itself).
    pub fn craft_cost(&mut self, id: &str) -> Option<(f64, f64, Vec<IngredientCost>)> {
        let recipes: Vec<(Stack, Vec<Stack>)> = self.crafting_recipes(id).into_iter().map(|(o, i)| (o.clone(), i.clone())).collect();
        let mut best: Option<(f64, f64, Vec<IngredientCost>)> = None;
        for (output, inputs) in recipes {
            if !valid_recipe(&output,&inputs) {continue;}
            let mut total = 0.0;
            let mut children = Vec::new();
            let mut ok = true;
            for input in &inputs {
                match self.unit_cost(&input.id) {
                    Some(c) if c.unit_price.is_some() => {
                        let unit = c.unit_price.unwrap_or(0.0);
                        total += unit * input.amount;
                        children.push(scaled(c,input.amount));
                    }
                    _ => {
                        ok = false;
                        break;
                    }
                }
            }
            if ok && total.is_finite() && best.as_ref().is_none_or(|b| total / output.amount < b.0 / b.1) {
                best = Some((total, output.amount.max(1.0), children));
            }
        }
        best
    }
}

fn valid_recipe(output:&Stack,inputs:&[Stack])->bool {
    output.amount.is_finite()&&output.amount>=1.0&&!inputs.is_empty()&&inputs.iter().all(|i|i.amount.is_finite()&&i.amount>0.0)
}
fn scaled(mut c:IngredientCost,factor:f64)->IngredientCost {
    c.amount*=factor;c.total=c.total.map(|v|v*factor);
    c.children=c.children.into_iter().map(|child|scaled(child,factor)).collect();c
}
fn ingredient_confidence(c:&IngredientCost,oracle:&dyn PriceOracle)->f64 {
    if c.item_id=="SKYBLOCK_COIN" {return 1.0;}
    if c.children.is_empty() {return oracle.quote(&c.item_id,PriceSide::Buy).map(|q|q.confidence).filter(|c|c.is_finite()).unwrap_or(0.0).clamp(0.0,1.0);}
    c.children.iter().map(|i|ingredient_confidence(i,oracle)).fold(1.0,f64::min)
}

fn leaf(id: &str, name: &str, amount: f64, unit: Option<f64>, source: &str) -> IngredientCost {
    IngredientCost {
        item_id: id.to_string(),
        name: name.to_string(),
        amount,
        unit_price: unit,
        total: unit.map(|u| u * amount),
        source: source.to_string(),
        children: Vec::new(),
    }
}

/// Evaluate craft flips for every craftable item that has a market price.
pub fn craft_flips(registry: &Registry, oracle: &dyn PriceOracle, bazaar_tax: f64, fee_for_ah: &dyn Fn(f64) -> f64, min_profit: f64) -> Vec<CraftFlip> {
    let mut calc = CraftCalculator::new(registry, oracle);
    let mut out = Vec::new();
    let mut ids: Vec<String> = registry
        .neu
        .recipes
        .iter()
        .filter(|(_, rs)| rs.iter().any(|r| matches!(r, Recipe::Crafting { .. })))
        .map(|(id, _)| id.clone())
        .collect();
    ids.sort();
    for id in ids {
        let Some(sale) = oracle.quote(&id, PriceSide::Sell) else { continue };
        if !sale.price.is_finite() || !sale.confidence.is_finite() || !sale.sales_per_day.is_finite() || sale.price <= 0.0 || sale.confidence < 0.3 {
            continue;
        }
        let Some((material_cost, output_count, ingredients)) = calc.craft_cost(&id) else { continue };
        let sale_total = sale.price * output_count;
        let fees = if oracle.is_bazaar(&id) { sale_total * bazaar_tax } else { fee_for_ah(sale_total) };
        let profit = sale_total - fees - material_cost;
        if !profit.is_finite() || !fees.is_finite() || fees<0.0 || profit < min_profit || material_cost <= 0.0 {
            continue;
        }
        // Liquidity of every ingredient bounds confidence (thin ingredients = unreliable cost).
        let mut conf = sale.confidence;
        for ing in &ingredients {
            conf=conf.min(ingredient_confidence(ing,oracle));
        }
        let item = registry.item(&id);
        let requirement = item.and_then(|i| i.requirements.first()).map(|r| r.describe());
        out.push(CraftFlip {
            item_id: id.clone(),
            name: registry.display_name(&id),
            output_count,
            material_cost,
            craft_cost: 0.0,
            sale_price: sale_total,
            sale_source: sale.source.to_string(),
            fees,
            expected_profit: profit,
            roi: profit / material_cost,
            sales_per_day: sale.sales_per_day,
            ingredients,
            confidence: Confidence::from_score(conf, if sale.sales_per_day < 1.0 { vec!["Product sells slowly".into()] } else { vec![] }),
            requirement,
        });
    }
    out.sort_by(|a, b| (b.expected_profit * b.confidence.score).total_cmp(&(a.expected_profit * a.confidence.score)));
    out
}
