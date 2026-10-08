export function deadlineRate(balance: number, target: number, days: number, hours: number, passive: number, expense: number) {
  const remaining = Math.max(0, target - balance);
  const activeNeed = Math.max(0, remaining + (expense - passive) * days);
  return { remaining, hourly: days > 0 && hours > 0 ? activeNeed / (days * hours) : activeNeed === 0 ? 0 : null };
}
export function upgradePayback(cost: number, before: number, after: number, hours: number) {
  const gain = after - before;
  return { gain, hours: gain > 0 ? cost / gain : null, days: gain > 0 && hours > 0 ? cost / gain / hours : null };
}
export function sessionRate(start: number, end: number, external: number, minutes: number) {
  const net = end - start - external;
  return { net, hourly: minutes > 0 ? net * 60 / minutes : null };
}
