// Banking brackets from the archived Hypixel Banking reference, checked 2026-10-08.
// The simulator assumes regular logins to collect every 31-hour payout.
export const BANK_TIERS = [
  { name: "Starter", capacity: 50e6, ceiling: 15e6 },
  { name: "Gold", capacity: 100e6, ceiling: 20e6 },
  { name: "Deluxe", capacity: 250e6, ceiling: 30e6 },
  { name: "Super Deluxe", capacity: 500e6, ceiling: 50e6 },
  { name: "Premier", capacity: 1e9, ceiling: 160e6 },
  { name: "Luxurious", capacity: 6e9, ceiling: 5160e6 },
  { name: "Palatial", capacity: 60e9, ceiling: 55160e6 },
];
const BRACKETS = [[10e6, .02], [20e6, .01], [30e6, .005], [50e6, .002], [160e6, .001], [5160e6, .0001], [55160e6, .00001]];
const safe = (value: number, maximum = 1e15) => Number.isFinite(value) ? Math.max(0, Math.min(maximum, value)) : 0;
export function bankInterest(balance: number, tier: number, museum: number) {
  const account = BANK_TIERS[Math.max(0, Math.min(BANK_TIERS.length - 1, Math.floor(safe(tier))))];
  const eligible = Math.min(safe(balance), account.ceiling);
  let interest = 0, previous = 0;
  for (const [limit, rate] of BRACKETS) {
    interest += Math.max(0, Math.min(eligible, limit) - previous) * rate;
    previous = limit;
  }
  return interest * (1 + Math.floor(safe(museum, 30)) * .02);
}
export interface WealthInput {
  bank: number; purse: number; tier: number; museum: number; days: number;
  passivePerDay: number; activePerDay: number; expensesPerDay: number;
  firstPayoutHours: number; reinvest: boolean;
}
export interface WealthPoint { day: number; total: number; baseline: number; interest: number; earned: number; spent: number }
export function projectWealth(input: WealthInput): WealthPoint[] {
  const tier = Math.floor(safe(input.tier, BANK_TIERS.length - 1));
  const cap = BANK_TIERS[tier].capacity;
  let bank = Math.min(safe(input.bank), cap), purse = safe(input.purse) + Math.max(0, safe(input.bank) - cap);
  const initial = bank + purse;
  let interest = 0, earned = 0, spent = 0;
  const result: WealthPoint[] = [{ day: 0, total: initial, baseline: initial, interest, earned, spent }];
  const first = Math.max(1, Math.ceil(safe(input.firstPayoutHours, 31)));
  for (let hour = 1; hour <= Math.floor(safe(input.days, 365)) * 24; hour++) {
    const income = (safe(input.passivePerDay) + safe(input.activePerDay)) / 24;
    const expense = Math.min(bank + purse + income, safe(input.expensesPerDay) / 24);
    purse += income; earned += income; spent += expense;
    const fromPurse = Math.min(purse, expense); purse -= fromPurse; bank -= expense - fromPurse;
    if (input.reinvest) { const deposit = Math.min(purse, cap - bank); bank += deposit; purse -= deposit; }
    const payout = hour >= first && (hour - first) % 31 === 0;
    if (payout) {
      const gain = bankInterest(bank, tier, input.museum); interest += gain;
      if (input.reinvest) { const deposited = Math.min(gain, cap - bank); bank += deposited; purse += gain - deposited; }
      else purse += gain;
    }
    if (hour % 24 === 0 || payout) result.push({ day: hour / 24, total: bank + purse, baseline: Math.max(0, initial + earned - spent), interest, earned, spent });
  }
  return result;
}
