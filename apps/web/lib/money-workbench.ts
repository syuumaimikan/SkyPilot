import type { BazaarList, CraftFlip, FlipList, Freshness, IngredientCost, TimeFlip } from "@skypilot/protocol";

export type Opportunity = { id: string; itemId: string; name: string; kind: "BAZAAR" | "NPC" | "CRAFT" | "FORGE" | "KAT" | "AH"; cost: number; profit: number; roi: number; limit: number; confidence: number; stale: boolean; requirement: string | null; href: string };
export type ProductionResponse<T> = { flips: T[]; freshness: Freshness };
const sane = (v: number) => Number.isFinite(v) ? Math.max(0, v) : 0;
const ratio = (v: number) => Math.min(1, sane(v));

/** Cost and profit are per order unit, craft batch, timed slot or auction respectively. */
export function opportunities(bz?: BazaarList, craft?: ProductionResponse<CraftFlip>, time?: ProductionResponse<TimeFlip>, ah?: FlipList): Opportunity[] {
  const rows: Opportunity[] = [];
  for (const f of bz?.flips ?? []) {
    if (f.manipulated || f.kind === "CRAFT_FLIP") continue; // Dedicated recipe source avoids duplicates.
    rows.push({ id: `bz:${f.kind}:${f.productId}`, itemId: f.productId, name: f.name, kind: f.kind === "INSTANT_FLIP" ? "NPC" : "BAZAAR", cost: f.buyPrice, profit: f.profitPerUnit, roi: f.roi, limit: Math.floor(f.suggestedAmount), confidence: f.confidence.score, stale: bz!.freshness.stale, requirement: null, href: `/market/price?item=${encodeURIComponent(f.productId)}` });
  }
  for (const f of craft?.flips ?? []) rows.push({ id: `craft:${f.itemId}`, itemId: f.itemId, name: f.name, kind: "CRAFT", cost: f.materialCost + f.craftCost, profit: f.expectedProfit, roi: f.roi, limit: Math.min(64, Math.floor(f.salesPerDay / Math.max(1, f.outputCount))), confidence: f.confidence.score, stale: craft!.freshness.stale, requirement: f.requirement, href: "/market/craft" });
  for (const f of time?.flips ?? []) rows.push({ id: `${f.kind}:${f.outputId}`, itemId: f.outputId, name: f.name, kind: f.kind, cost: f.capital, profit: f.profit, roi: f.roi, limit: 1, confidence: f.confidence.score, stale: time!.freshness.stale, requirement: f.requirement, href: "/market/craft" });
  for (const f of ah?.flips ?? []) {
    if (f.manipulation.suspicious) continue;
    rows.push({ id: `ah:${f.auctionId}`, itemId: f.itemId, name: f.itemName, kind: "AH", cost: f.buyPrice, profit: f.expectedNetProfit, roi: f.roi, limit: 1, confidence: f.confidence.score, stale: ah!.freshness.stale || !ah!.collectorHealthy, requirement: null, href: "/market/flips" });
  }
  return rows.filter(f => Number.isFinite(f.cost) && f.cost > 0 && Number.isFinite(f.profit) && f.profit > 0 && f.limit > 0);
}

/** A shopping proposal only; shared item ids cannot consume the budget twice. */
export function allocate(rows: Opportunity[], capital: number, reserve: number, maxShare: number, slots: number) {
  const budget = Math.max(0, sane(capital) - sane(reserve));
  let remaining = budget;
  const used = new Set<string>();
  const basket: { row: Opportunity; quantity: number; cost: number; profit: number }[] = [];
  for (const row of [...rows].sort((a, b) => b.roi - a.roi)) {
    if (basket.length >= Math.floor(sane(slots))) break;
    if (row.stale || row.requirement || used.has(row.itemId) || !(row.cost > 0) || !Number.isFinite(row.cost)) continue;
    const quantity = Math.max(0, Math.min(Math.floor(row.limit), Math.floor(Math.min(remaining, budget * ratio(maxShare)) / row.cost)));
    if (!quantity) continue;
    const cost = quantity * row.cost;
    basket.push({ row, quantity, cost, profit: quantity * row.profit });
    remaining -= cost;
    used.add(row.itemId);
  }
  return { basket, spent: budget - remaining, remaining, profit: basket.reduce((sum, b) => sum + b.profit, 0) };
}

export function orderScenario(buy: number, sell: number, count: number, tax: number, buyRaise: number, sellCut: number) {
  const purchase = sane(buy) + sane(buyRaise), sale = Math.max(0, sane(sell) - sane(sellCut));
  const quantity = Math.floor(sane(count)), rate = ratio(tax);
  const cost = purchase * quantity, proceeds = sale * quantity * (1 - rate);
  return { purchase, sale, cost, proceeds, profit: proceeds - cost, breakEven: rate < 1 ? purchase / (1 - rate) : null, roi: cost > 0 ? (proceeds - cost) / cost : null };
}
export function volumeCap(buyWeek: number, sellWeek: number, hours: number, share: number, cost: number, budget: number) {
  const count = Math.floor(Math.min(sane(buyWeek), sane(sellWeek)) / 168 * sane(hours) * ratio(share));
  return cost > 0 && Number.isFinite(cost) ? Math.max(0, Math.min(count, Math.floor(sane(budget) / cost))) : 0;
}
export function stressTrade(cost: number, sale: number, count: number, tax: number, fall: number, increase: number, fee: number) {
  const qty = Math.floor(sane(count));
  const spend = sane(cost) * (1 + ratio(increase)) * qty + sane(fee);
  const proceeds = sane(sale) * (1 - ratio(fall)) * qty * (1 - ratio(tax));
  return { net: proceeds - spend, drawdown: spend > 0 ? Math.max(0, spend - proceeds) / spend : 0 };
}
export function exitPrices(cost: number, count: number, fee: number, tax: number, target: number, loss: number) {
  const qty = Math.floor(sane(count)), rate = ratio(tax), invested = sane(cost) * qty + sane(fee);
  const denominator = qty * (1 - rate);
  return denominator > 0 ? { breakEven: invested / denominator, takeProfit: (invested + sane(target)) / denominator, stopLoss: Math.max(0, invested - sane(loss)) / denominator } : null;
}
export function shoppingList(ingredients: IngredientCost[], batches: number) {
  const count = Math.floor(sane(batches));
  // Each top-level ingredient is an alternative to crafting its children; never sum both.
  return ingredients.map(i => ({ id: i.itemId, name: i.name, amount: i.amount * count, source: i.source, cost: i.total == null ? null : i.total * count }));
}
export function productionPlan(capitalPerSlot: number, profitPerSlot: number, hoursPerCycle: number, slots: number, days: number, available: number) {
  const hours = sane(hoursPerCycle), duration = sane(days) * 24;
  const affordable = capitalPerSlot > 0 && Number.isFinite(capitalPerSlot) ? Math.floor(sane(available) / capitalPerSlot) : 0;
  const activeSlots = Math.min(Math.floor(sane(slots)), affordable);
  const cycles = hours > 0 ? Math.floor(duration / hours) : 0;
  return { slots: activeSlots, cycles, capital: activeSlots * sane(capitalPerSlot), profit: activeSlots * cycles * (Number.isFinite(profitPerSlot) ? profitPerSlot : 0) };
}
export function fuelReturn(baseHourly: number, boost: number, durationHours: number, fuelCost: number, workers: number) {
  const count = Math.floor(sane(workers)), duration = sane(durationHours);
  const extra = sane(baseHourly) * sane(boost) * duration * count;
  const cost = sane(fuelCost) * count;
  return { extra, cost, net: extra - cost, maxFuelPrice: sane(baseHourly) * sane(boost) * duration };
}
export function lootPlan(price: number, probability: number, runs: number, costPerRun: number, baseNetPerRun: number, minutesPerRun: number) {
  const p = ratio(probability), n = Math.floor(sane(runs));
  const perRun = sane(price) * p + (Number.isFinite(baseNetPerRun) ? baseNetPerRun : 0) - sane(costPerRun);
  const minutes = sane(minutesPerRun);
  return { perRun, net: perRun * n, hourly: minutes > 0 ? perRun * 60 / minutes : null, noDrop: (1 - p) ** n, runsFor95: p <= 0 ? null : p >= 1 ? 1 : Math.ceil(Math.log(0.05) / Math.log1p(-p)), bankroll: sane(costPerRun) * n };
}
export function harvestIncome(quantity: number, sellPrice: number | null, tax: number, hourlyCost: number) {
  if (sellPrice == null || !Number.isFinite(sellPrice) || sellPrice < 0) return null;
  return sane(quantity) * sellPrice * (1 - ratio(tax)) - sane(hourlyCost);
}
