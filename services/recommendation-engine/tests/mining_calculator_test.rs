mod common;

use skypilot_protocol::advisor::MiningCalcInput;
use skypilot_reco::Lang;
use skypilot_reco::providers::mining::{calculate, default_input, player_stats};

#[test]
fn rates_follow_mining_speed_formula() {
    let w = common::world();
    let ctx = common::ctx(w, Lang::En);
    let input = MiningCalcInput { mining_speed: 3000.0, mining_fortune: 200.0, gemstone_fortune: 0.0, dwarven_metal_fortune: 0.0, block_fortune: 0.0, pristine: 0.0, uptime: 1.0 };
    let r = calculate(&ctx, &input, 0.0);
    let ruby = r.targets.iter().find(|t| t.target_id == "ruby").unwrap();
    // round(2300*30/3000) = 23 ticks -> 72000/23 blocks/h
    assert_eq!(ruby.ticks_per_block, 23.0);
    assert!((ruby.blocks_per_hour - 72_000.0 / 23.0).abs() < 1e-6);
    // 4 rough (3-5) x (1 + 200% fortune) per block
    assert!((ruby.drops_per_hour - ruby.blocks_per_hour * 4.0 * 3.0).abs() < 1e-6);
    assert!(ruby.npc_coins_per_hour.unwrap() > 0.0);
}

#[test]
fn more_speed_never_reduces_income() {
    let w = common::world();
    let ctx = common::ctx(w, Lang::En);
    let slow = calculate(&ctx, &MiningCalcInput { mining_speed: 1000.0, mining_fortune: 100.0, gemstone_fortune: 0.0, dwarven_metal_fortune: 0.0, block_fortune: 0.0, pristine: 0.0, uptime: 0.8 }, 0.0);
    let fast = calculate(&ctx, &MiningCalcInput { mining_speed: 5000.0, mining_fortune: 100.0, gemstone_fortune: 0.0, dwarven_metal_fortune: 0.0, block_fortune: 0.0, pristine: 0.0, uptime: 0.8 }, 0.0);
    for t in &slow.targets {
        let f = fast.targets.iter().find(|x| x.target_id == t.target_id).unwrap();
        assert!(f.blocks_per_hour >= t.blocks_per_hour, "{}", t.target_id);
    }
    // fuel cost is subtracted from effective income
    let with_fuel = calculate(&ctx, &MiningCalcInput { mining_speed: 5000.0, mining_fortune: 100.0, gemstone_fortune: 0.0, dwarven_metal_fortune: 0.0, block_fortune: 0.0, pristine: 0.0, uptime: 0.8 }, 50_000.0);
    let a = fast.targets.iter().find(|t| t.target_id == "mithril").unwrap().effective_coins_per_hour.unwrap();
    let b = with_fuel.targets.iter().find(|t| t.target_id == "mithril").unwrap().effective_coins_per_hour.unwrap();
    assert!((a - b - 50_000.0).abs() < 1e-6);
}

#[test]
fn player_stats_come_from_gear_and_hotm() {
    let w = common::world();
    let ctx = common::ctx(w, Lang::En);
    let stats = player_stats(&ctx);
    assert!(stats.get("Mining Speed").copied().unwrap_or(0.0) > 0.0, "late-game sample owns mining gear: {stats:?}");
    let input = default_input(&ctx);
    assert_eq!(input.uptime, 0.75);
    assert!(calculate(&ctx, &input, 0.0).targets.iter().any(|t| t.effective_coins_per_hour.is_some()));
}
