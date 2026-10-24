/** Planning from measured inputs only; no game mechanics, drop rates or stats are inferred. */
export function combatPlan(hp: number, hit: number, hitsPerSecond: number, incoming: number, healing: number, mana: number, spellCost: number) {
  const hits = hp === 0 ? 0 : hit > 0 ? Math.ceil(hp / hit) : null;
  const dps = hit * hitsPerSecond;
  const survival = incoming <= healing ? null : hp / (incoming - healing);
  return { hits, dps, seconds: hp === 0 ? 0 : dps > 0 ? hp / dps : null, survival, casts: spellCost > 0 ? Math.floor(mana / spellCost) : null };
}
export function runPlan(minutes: number, breakMinutes: number, available: number, income: number, cost: number, fixed: number, players: number) {
  const cycle = minutes + breakMinutes;
  const runs = minutes > 0 && cycle > 0 ? Math.floor((available + breakMinutes) / cycle) : 0;
  const used = runs > 0 ? runs * minutes + (runs - 1) * breakMinutes : 0;
  const net = runs > 0 ? runs * (income - cost) - fixed : 0;
  return { runs, used, spare: Math.max(0, available - used), bankroll: runs > 0 ? fixed + runs * cost : 0, net, perPlayer: net / players, hourly: used > 0 ? net * 60 / used : null };
}
export type Supply = { id: string; name: string; quantity: number; stack: number; price: number; owned: number; perRun: number };
export function inventoryPlan(rows: Supply[], slots: number, runs: number) {
  const items = rows.map(row => {
    const required = row.quantity + row.perRun * runs;
    const missing = Math.max(0, required - row.owned);
    return { ...row, required, missing, cost: missing * row.price, slots: Math.ceil(required / row.stack) };
  });
  const used = items.reduce((sum, row) => sum + row.slots, 0);
  return { items, used, free: Math.max(0, slots - used), overflow: Math.max(0, used - slots), cost: items.reduce((sum, row) => sum + row.cost, 0), unpriced: items.filter(row => row.missing > 0 && row.price === 0).length, trips: used === 0 ? 0 : slots > 0 ? Math.ceil(used / slots) : null };
}
export function upgradePlan(before: number, after: number, price: number, beforeMinutes: number, afterMinutes: number, runs: number, valuePerHour: number) {
  const gain = after - before;
  const savedMinutes = (beforeMinutes - afterMinutes) * runs;
  const savedValue = savedMinutes * valuePerHour / 60;
  const valuePerRun = (beforeMinutes - afterMinutes) * valuePerHour / 60;
  return { gain, percent: before > 0 ? gain / before * 100 : null, pricePerGain: gain > 0 ? price / gain : null, savedMinutes, savedValue, paybackRuns: price === 0 ? 0 : valuePerRun > 0 ? Math.ceil(price / valuePerRun) : null };
}

export type LabState = { version: 1; combat: number[]; run: number[]; upgrade: number[]; slots: number; plannedRuns: number; supplies: Supply[] };
export const blankLab = (): LabState => ({ version: 1, combat: [0, 0, 0, 0, 0, 0, 0], run: [5, 1, 60, 0, 0, 0, 1], upgrade: [0, 0, 0, 5, 4, 10, 0], slots: 36, plannedRuns: 10, supplies: [] });
const number = (v: unknown, max = 1e12) => { if (typeof v !== 'number' || !Number.isFinite(v) || v < 0 || v > max) throw new Error('number'); return v; };
export function decodeLab(raw: string): LabState {
  if (!raw) return blankLab();
  if (raw.length > 100000) throw new Error('size');
  const d = JSON.parse(raw);
  if (!d || d.version !== 1 || !Array.isArray(d.supplies) || d.supplies.length > 60) throw new Error('version or limit');
  const vector = (v: unknown) => { if (!Array.isArray(v) || v.length !== 7) throw new Error('fields'); return v.map(x => number(x)); };
  const combat = vector(d.combat), run = vector(d.run), upgrade = vector(d.upgrade);
  if (run[0] <= 0 || run[6] < 1 || run[6] > 100 || !Number.isInteger(run[6])) throw new Error('run');
  const ids = new Set<string>();
  const supplies = d.supplies.map((row: Supply) => {
    if (!row || typeof row.name !== 'string' || !row.name.trim() || row.name.length > 160 || typeof row.id !== 'string' || !/^[a-zA-Z0-9_-]{1,80}$/.test(row.id) || ids.has(row.id)) throw new Error('item');
    ids.add(row.id);
    const stack = number(row.stack, 9999), quantity = number(row.quantity, 1e7), owned = number(row.owned, 1e7), perRun = number(row.perRun, 1e5);
    if (stack < 1 || ![stack,quantity,owned,perRun].every(Number.isInteger)) throw new Error('integer');
    return { id: row.id, name: row.name, quantity, stack, owned, perRun, price: number(row.price) };
  });
  const slots = number(d.slots, 100000), plannedRuns = number(d.plannedRuns, 100000);
  if (![slots, plannedRuns].every(Number.isInteger)) throw new Error('integer');
  return { version: 1, combat, run, upgrade, slots, plannedRuns, supplies };
}
