//! Player Analyzer.
//!
//! Input: the raw `/v2/skyblock/profiles` response (+ optional museum/garden responses).
//! Output: [`PlayerAnalysis`] - every field is either a value derived deterministically from
//! the API/registry, or `Unknown` with the reason. Nothing is inferred by an LLM.

pub mod gear;
pub mod inventory;
pub mod networth;
pub mod progression;
pub mod sections;

use serde_json::Value;
use skypilot_data::Registry;
use skypilot_pricing::PriceOracle;
use skypilot_protocol::common::{Freshness, Known};
use skypilot_protocol::profile::*;
use thiserror::Error;

pub use inventory::Inventories;

#[derive(Debug, Error, PartialEq)]
pub enum AnalyzeError {
    #[error("this player has no SkyBlock profiles")]
    NoProfiles,
    #[error("profile {0} not found for this player")]
    ProfileNotFound(String),
    #[error("player is not a member of the selected profile")]
    NotAMember,
}

pub struct AnalyzerInput<'a> {
    pub player: PlayerRef,
    pub profiles: &'a Value,
    pub profile_id: Option<&'a str>,
    pub museum: Option<&'a Value>,
    pub garden: Option<&'a Value>,
    pub fetched_at_ms: i64,
    pub now_ms: i64,
}

fn undash(s: &str) -> String {
    s.replace('-', "").to_ascii_lowercase()
}

pub fn list_profiles(profiles: &Value) -> Vec<ProfileListEntry> {
    profiles
        .get("profiles")
        .and_then(Value::as_array)
        .map(|a| {
            a.iter()
                .map(|p| ProfileListEntry {
                    profile_id: p.get("profile_id").and_then(Value::as_str).unwrap_or_default().to_string(),
                    cute_name: p.get("cute_name").and_then(Value::as_str).unwrap_or("Unknown").to_string(),
                    selected: p.get("selected").and_then(Value::as_bool).unwrap_or(false),
                    game_mode: p.get("game_mode").and_then(Value::as_str).map(str::to_string),
                    member_count: p.get("members").and_then(Value::as_object).map(|m| m.len() as u32).unwrap_or(0),
                })
                .collect()
        })
        .unwrap_or_default()
}

/// Pick the requested profile, else the selected one, else the first.
pub fn select_profile<'a>(profiles: &'a Value, profile_id: Option<&str>) -> Result<&'a Value, AnalyzeError> {
    let list = profiles.get("profiles").and_then(Value::as_array).filter(|a| !a.is_empty()).ok_or(AnalyzeError::NoProfiles)?;
    if let Some(id) = profile_id {
        return list
            .iter()
            .find(|p| p.get("profile_id").and_then(Value::as_str).map(undash) == Some(undash(id)))
            .ok_or_else(|| AnalyzeError::ProfileNotFound(id.to_string()));
    }
    Ok(list.iter().find(|p| p.get("selected").and_then(Value::as_bool) == Some(true)).unwrap_or(&list[0]))
}

pub fn analyze(input: &AnalyzerInput, registry: &Registry, oracle: &dyn PriceOracle) -> Result<(PlayerAnalysis, Inventories), AnalyzeError> {
    let profile = select_profile(input.profiles, input.profile_id)?;
    let uuid = undash(&input.player.uuid);
    let members = profile.get("members").and_then(Value::as_object).ok_or(AnalyzeError::NotAMember)?;
    let member = members.iter().find(|(k, _)| undash(k) == uuid).map(|(_, v)| v).ok_or(AnalyzeError::NotAMember)?;
    let profile_id = profile.get("profile_id").and_then(Value::as_str).unwrap_or_default().to_string();

    let inv = inventory::load(member, registry);
    let mut warnings = Vec::new();
    if !inv.available {
        warnings.push("Inventory API is disabled: gear, accessories and networth are unavailable. Enable it in SkyBlock Settings > API Settings.".into());
    }
    if !inv.decode_errors.is_empty() {
        warnings.push(format!("{} inventory blob(s) could not be decoded", inv.decode_errors.len()));
    }
    if member.get("profile").and_then(|p| p.get("deletion_notice")).is_some() {
        warnings.push("This member was removed from the profile.".into());
    }

    let (skills, skill_average) = sections::skills(member, registry);
    if !skills.is_known() {
        warnings.push("Skills API is disabled.".into());
    }
    let dungeons = sections::dungeons(member, registry);
    let slayers = sections::slayers(member, registry);
    let mining = sections::mining(member, registry, &inv);
    let garden = sections::garden(member, input.garden, registry, &inv);
    let accessories = sections::accessories(member, registry, &inv);
    let pets = sections::pets(member, registry);
    let gear = if inv.available { Known::known(gear::gear_info(&inv, registry)) } else { Known::unknown("Inventory API disabled") };
    let banking = profile.get("banking");
    let economy = EconomyInfo {
        purse: sections::f(member, &["currencies", "coin_purse"]).unwrap_or(0.0),
        bank: banking.and_then(|b| sections::f(b, &["balance"])).map(Known::known).unwrap_or_else(|| Known::unknown("Banking API disabled")),
        personal_bank: sections::f(member, &["profile", "bank_account"]).map(Known::known).unwrap_or_else(|| Known::unknown("Not available from API")),
        motes: sections::f(member, &["currencies", "motes_purse"]),
        essence: sections::get(member, &["currencies", "essence"])
            .and_then(Value::as_object)
            .map(|m| m.iter().filter_map(|(k, v)| v.get("current").and_then(Value::as_u64).map(|n| (k.clone(), n))).collect())
            .unwrap_or_default(),
    };
    if !economy.bank.is_known() {
        warnings.push("Banking API is disabled: bank balance is excluded from networth.".into());
    }
    let pet_list = pets.as_ref().cloned().unwrap_or_default();
    let nw = networth::compute(&inv, &pet_list, &economy, registry, oracle);
    if let Some(total) = nw.breakdown.as_ref().map(|b| b.total).filter(|t| *t > 0.0) {
        let low: f64 = nw.low_evidence.iter().map(|(_, v)| v).sum();
        if low / total >= 0.1 {
            // (name, count, total value), largest first
            let mut names: Vec<(String, usize, f64)> = Vec::new();
            for (n, v) in &nw.low_evidence {
                match names.iter_mut().find(|(x, _, _)| x == n) {
                    Some((_, c, t)) => {
                        *c += 1;
                        *t += v;
                    }
                    None => names.push((n.clone(), 1, *v)),
                }
            }
            names.sort_by(|a, b| b.2.total_cmp(&a.2));
            let list = names.iter().take(4).map(|(n, c, _)| if *c > 1 { format!("{n} ×{c}") } else { n.clone() }).collect::<Vec<_>>().join(", ");
            warnings.push(format!(
                "{:.0}% of networth ({}) is valued from auction listings with few or no recorded sales ({list}); treat it as an upper bound.",
                low / total * 100.0,
                short_coins(low)
            ));
        }
    }

    // attach values to gear summaries
    let gear = gear.map(|mut g| {
        let lookup = |s: &mut ItemSummary| {
            if let Some((_, _, v, c)) = nw.values.iter().find(|(loc, id, _, _)| *loc == s.location && *id == s.skyblock_id) {
                s.estimated_value = Some(*v);
                s.value_confidence = Some(*c);
            }
        };
        g.armor.iter_mut().for_each(lookup);
        g.equipment.iter_mut().for_each(lookup);
        g.weapons.iter_mut().for_each(lookup);
        g.tools.iter_mut().for_each(lookup);
        g
    });

    let museum = sections::museum(input.museum, &uuid);
    let kuudra = sections::kuudra(member);
    let rift = sections::rift(member, &slayers);
    let fresh = Freshness::compute("Hypixel profile", input.fetched_at_ms, input.now_ms, registry.curated.market.freshness.profile_stale_after);
    let mut analysis = PlayerAnalysis {
        player: input.player.clone(),
        profile_id,
        cute_name: profile.get("cute_name").and_then(Value::as_str).unwrap_or("Unknown").to_string(),
        game_mode: profile.get("game_mode").and_then(Value::as_str).map(str::to_string),
        fetched: fresh,
        api_access: ApiAccess {
            inventory: inv.available,
            banking: banking.is_some(),
            collections: member.get("collection").is_some(),
            skills: skills.is_known(),
            museum: museum.is_known(),
            garden: garden.is_known(),
        },
        skyblock_level: sections::f(member, &["leveling", "experience"]).map(|xp| Known::known(xp / 100.0)).unwrap_or_else(|| Known::unknown("Not available from API")),
        skills,
        skill_average,
        dungeons,
        slayers,
        mining,
        garden,
        accessories,
        pets,
        gear,
        economy,
        networth: nw.breakdown,
        collections: sections::collections(member, registry),
        museum,
        kuudra,
        rift,
        chocolate_factory: sections::chocolate(member),
        fishing: sections::fishing(member, registry, &inv),
        foraging: sections::foraging(member, registry, &inv),
        minions: sections::minions(member, profile),
        bestiary_kills: sections::get(member, &["bestiary", "kills"]).and_then(Value::as_object).map(|m| m.iter().filter(|(k, _)| *k != "last_killed_mob").filter_map(|(_, v)| v.as_u64()).sum()),
        fairy_souls: sections::u(member, &["fairy_soul", "total_collected"]).map(|v| v as u32),
        sacks: if inv.available { Known::known(inv.sacks.clone()) } else { Known::unknown("Inventory API disabled") },
        progression: ProgressionScores { areas: vec![], overall: 0.0, stage: GameStage::EarlyGame, strong_areas: vec![], weak_areas: vec![] },
        warnings,
    };
    let pet_score = sections::f(member, &["leveling", "highest_pet_score"]);
    analysis.progression = progression::score(&analysis, registry, pet_score);
    Ok((analysis, inv))
}

/// Compact metrics stored on every user-initiated analysis (never polled).
pub fn snapshot(a: &PlayerAnalysis, taken_at_ms: i64) -> ProgressSnapshot {
    ProgressSnapshot {
        taken_at_ms,
        networth: a.networth.as_ref().map(|n| n.total),
        skyblock_level: a.skyblock_level.as_ref().copied(),
        skill_average: a.skill_average.as_ref().copied(),
        magical_power: a.accessories.as_ref().map(|x| x.magical_power as f64),
        catacombs: a.dungeons.as_ref().map(|d| d.catacombs.level_with_progress),
        slayer_xp_total: a.slayers.as_ref().map(|s| s.iter().map(|x| x.level.xp).sum()),
        mithril_powder_total: a.mining.as_ref().and_then(|m| m.mithril_powder.as_ref()).map(|p| p.total),
        gemstone_powder_total: a.mining.as_ref().and_then(|m| m.gemstone_powder.as_ref()).map(|p| p.total),
        hotm: a.mining.as_ref().and_then(|m| m.hotm.as_ref()).map(|h| h.level_with_progress),
        garden_level: a.garden.as_ref().and_then(|g| g.garden_level.as_ref()).map(|l| l.level_with_progress),
    }
}

/// 12_345_678 -> "12.3M" (for warning texts).
fn short_coins(v: f64) -> String {
    let a = v.abs();
    if a >= 1e9 {
        format!("{:.1}B", v / 1e9)
    } else if a >= 1e6 {
        format!("{:.1}M", v / 1e6)
    } else if a >= 1e3 {
        format!("{:.1}K", v / 1e3)
    } else {
        format!("{v:.0}")
    }
}
