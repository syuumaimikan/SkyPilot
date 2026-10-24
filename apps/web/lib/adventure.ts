export type Repeat = "once" | "daily" | "weekly";
export type Task = { id: string; title: string; minutes: number; priority: number; repeat: Repeat; completed: string | null };
export type Session = { id: string; title: string; category: string; minutes: number; income: number; cost: number; at: number; note: string };
export type Milestone = { id: string; title: string; unit: string; current: number; target: number; rate: number };
export type Wish = { id: string; title: string; quantity: number; price: number; bought: boolean };
export type Loadout = { id: string; title: string; weapon: string; armor: string; pet: string; utility: string; note: string };
export type Note = { id: string; title: string; body: string; tags: string; pinned: boolean };
export type Reminder = { id: string; title: string; due: number; done: boolean };
export type Focus = { title: string; total: number; remaining: number; end: number | null };
export type Adventure = { version: 1; tasks: Task[]; sessions: Session[]; milestones: Milestone[]; wishes: Wish[]; loadouts: Loadout[]; notes: Note[]; reminders: Reminder[]; focus: Focus | null };
export const limits = { tasks: 100, sessions: 300, milestones: 100, wishes: 100, loadouts: 30, notes: 100, reminders: 100 };
export const blankAdventure = (): Adventure => ({ version: 1, tasks: [], sessions: [], milestones: [], wishes: [], loadouts: [], notes: [], reminders: [], focus: null });

const record = (value: unknown): Record<string, unknown> => {
  if (typeof value !== "object" || value === null || Array.isArray(value)) throw new Error("object");
  return value as Record<string, unknown>;
};
const str = (v: unknown, max: number, empty = false): string => {
  if (typeof v !== "string" || v.length > max || (!empty && !v.trim())) throw new Error("text");
  return v;
};
const num = (v: unknown, max = 1e15, min = 0): number => {
  if (typeof v !== "number" || !Number.isFinite(v) || v < min || v > max) throw new Error("number");
  return v;
};
const bool = (v: unknown): boolean => { if (typeof v !== "boolean") throw new Error("boolean"); return v; };
const id = (v: unknown): string => { const s = str(v, 80); if (!/^[A-Za-z0-9_-]+$/.test(s)) throw new Error("id"); return s; };
const timestamp = (v: unknown) => num(v, 4102444800000);

/** Strict, bounded import: reconstruct fields and reject duplicates, future versions and non-finite values. */
export function validateAdventure(value: unknown): Adventure {
  const data = record(value);
  if (data.version !== 1) throw new Error("version");
  const used = new Set<string>();
  function rows<T>(key: keyof typeof limits, parse: (v: Record<string, unknown>) => T): T[] {
    const list = data[key];
    if (!Array.isArray(list) || list.length > limits[key]) throw new Error("limit");
    return list.map(v => {
      const row = record(v); const rowId = id(row.id);
      if (used.has(rowId)) throw new Error("duplicate");
      used.add(rowId); return parse(row);
    });
  }
  const tasks = rows("tasks", v => {
    if (!["once", "daily", "weekly"].includes(String(v.repeat))) throw new Error("repeat");
    const priority = num(v.priority, 3, 1);
    if (!Number.isInteger(priority)) throw new Error("priority");
    return { id: id(v.id), title: str(v.title, 160), minutes: num(v.minutes, 1440, 1), priority, repeat: v.repeat as Repeat, completed: v.completed === null ? null : str(v.completed, 20) };
  });
  const sessions = rows("sessions", v => ({ id: id(v.id), title: str(v.title, 160), category: str(v.category, 60), minutes: num(v.minutes, 1440, 1 / 60), income: num(v.income), cost: num(v.cost), at: timestamp(v.at), note: str(v.note, 2000, true) }));
  const milestones = rows("milestones", v => ({ id: id(v.id), title: str(v.title, 160), unit: str(v.unit, 40), current: num(v.current), target: num(v.target, 1e15, 1e-9), rate: num(v.rate) }));
  const wishes = rows("wishes", v => {
    const quantity = num(v.quantity, 1000000, 1);
    if (!Number.isInteger(quantity)) throw new Error("quantity");
    return { id: id(v.id), title: str(v.title, 160), quantity, price: num(v.price, 1e15 / quantity), bought: bool(v.bought) };
  });
  const loadouts = rows("loadouts", v => ({ id: id(v.id), title: str(v.title, 160), weapon: str(v.weapon, 300, true), armor: str(v.armor, 300, true), pet: str(v.pet, 300, true), utility: str(v.utility, 300, true), note: str(v.note, 2000, true) }));
  const notes = rows("notes", v => ({ id: id(v.id), title: str(v.title, 160), body: str(v.body, 6000, true), tags: str(v.tags, 200, true), pinned: bool(v.pinned) }));
  const reminders = rows("reminders", v => ({ id: id(v.id), title: str(v.title, 160), due: timestamp(v.due), done: bool(v.done) }));
  let focus: Focus | null = null;
  if (data.focus !== null) {
    const f = record(data.focus); const total = num(f.total, 86400000, 60000);
    focus = { title: str(f.title, 160), total, remaining: num(f.remaining, total), end: f.end === null ? null : timestamp(f.end) };
  }
  return { version: 1, tasks, sessions, milestones, wishes, loadouts, notes, reminders, focus };
}

export function decodeAdventure(raw: string): Adventure {
  if (!raw) return blankAdventure();
  if (raw.length > 2000000) throw new Error("size");
  return validateAdventure(JSON.parse(raw));
}

/** Checklists reset using the device's calendar, independent of Hypixel's reset schedule. */
export function periodKey(repeat: Repeat, now: number): string {
  if (repeat === "once") return "once";
  const local = new Date(now);
  const day = new Date(Date.UTC(local.getFullYear(), local.getMonth(), local.getDate()));
  if (repeat === "weekly") day.setUTCDate(day.getUTCDate() - (day.getUTCDay() + 6) % 7);
  return `${repeat}:${day.toISOString().slice(0, 10)}`;
}
export const isDone = (task: Task, now: number) => task.completed === periodKey(task.repeat, now);
export function taskPlan(tasks: Task[], available: number, now: number) {
  let remaining = Math.max(0, available);
  const selected: Task[] = [], deferred: Task[] = [];
  for (const task of tasks.filter(t => !isDone(t, now)).sort((a, b) => b.priority - a.priority || a.minutes - b.minutes || a.id.localeCompare(b.id))) {
    if (task.minutes <= remaining) { selected.push(task); remaining -= task.minutes; }
    else deferred.push(task);
  }
  return { selected, deferred, used: Math.max(0, available) - remaining, remaining };
}
export function focusRemaining(focus: Focus | null, now: number): number {
  if (!focus) return 0;
  return focus.end === null ? focus.remaining : Math.max(0, Math.min(focus.total, focus.end - now));
}
export function milestoneProgress(goal: Milestone) {
  const remaining = Math.max(0, goal.target - goal.current);
  return { remaining, fraction: Math.min(1, Math.max(0, goal.current / goal.target)), hours: remaining === 0 ? 0 : goal.rate > 0 ? remaining / goal.rate : null };
}
export function sessionSummary(sessions: Session[], from: number, to: number) {
  const rows = sessions.filter(s => s.at >= from && s.at <= to);
  const income = rows.reduce((a, s) => a + s.income, 0), cost = rows.reduce((a, s) => a + s.cost, 0);
  const minutes = rows.reduce((a, s) => a + s.minutes, 0);
  return { count: rows.length, income, cost, net: income - cost, minutes, perHour: minutes > 0 ? (income - cost) * 60 / minutes : null };
}
export function categorySummary(sessions: Session[]) {
  return [...new Set(sessions.map(s => s.category))].map(category => ({ category, ...sessionSummary(sessions.filter(s => s.category === category), 0, 4102444800000) })).sort((a, b) => (b.perHour ?? 0) - (a.perHour ?? 0));
}
export const wishTotal = (wishes: Wish[]) => wishes.filter(w => !w.bought).reduce((sum, w) => sum + w.price * w.quantity, 0);
