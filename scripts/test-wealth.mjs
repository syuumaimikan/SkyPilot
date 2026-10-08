import assert from "node:assert/strict";
import { bankInterest, projectWealth, BANK_TIERS } from "../apps/web/lib/wealth.ts";

assert.equal(bankInterest(10e6, 0, 0), 200000);
assert.equal(bankInterest(15e6, 0, 0), 250000);
assert.equal(bankInterest(50e6, 0, 0), 250000);
assert.equal(bankInterest(15e6, 0, 30), 400000);
assert.equal(bankInterest(20e6, 1, 0), 300000);
assert.equal(bankInterest(-1, 0, 0), 0);
assert.equal(bankInterest(NaN, 0, 0), 0);
const input = { bank: 10e6, purse: 0, tier: 0, museum: 0, days: 3, passivePerDay: 0, activePerDay: 0, expensesPerDay: 0, firstPayoutHours: 31, reinvest: true };
const projected = projectWealth(input);
assert.equal(projected.at(-1).interest, 402000); // 200k at 31h, 202k at 62h.
assert.equal(projected.at(-1).total, 10402000);
assert.equal(projectWealth({ ...input, reinvest: false }).at(-1).interest, 400000);
const capped = projectWealth({ ...input, bank: BANK_TIERS[0].capacity });
assert.equal(capped.at(-1).total, 50e6 + 500000); // Overflow remains in purse.
const depleted = projectWealth({ ...input, bank: 0, purse: 100, expensesPerDay: 1e6 });
assert.equal(depleted.at(-1).total, 0);
assert.equal(depleted.at(-1).spent, 100);
const income = projectWealth({ ...input, bank: 0, passivePerDay: 24000, activePerDay: 24000 });
assert.equal(income.at(-1).earned, 144000);
assert.ok(Math.abs(income.at(-1).total - (income.at(-1).earned + income.at(-1).interest)) < .001);
assert.equal(projectWealth({ ...input, days: 0 }).length, 1);
console.log("Wealth projection: brackets, caps, compounding, income and depletion passed.");
