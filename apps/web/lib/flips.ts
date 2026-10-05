import type { FlipOpportunity, NotificationRule, RiskLevel } from "@skypilot/protocol";

export const RISK_ORDER: RiskLevel[] = ["VERY_LOW", "LOW", "MEDIUM", "HIGH", "VERY_HIGH"];

export interface FlipFilter {
  maxBudget: number | null;
  minProfit: number | null;
  minRoi: number | null;
  minConfidence: number | null;
  maxRisk: RiskLevel | null;
  search?: string;
  hideSuspicious?: boolean;
}

/** Same semantics as the server-side filter (services/pricing-engine/src/flips.rs). */
export function matchesFilter(f: FlipOpportunity, q: FlipFilter): boolean {
  if (q.maxBudget !== null && f.buyPrice > q.maxBudget) return false;
  if (q.minProfit !== null && f.expectedNetProfit < q.minProfit) return false;
  if (q.minRoi !== null && f.roi < q.minRoi) return false;
  if (q.minConfidence !== null && f.confidence.score < q.minConfidence) return false;
  if (q.maxRisk !== null && RISK_ORDER.indexOf(f.risk) > RISK_ORDER.indexOf(q.maxRisk)) return false;
  if (q.hideSuspicious && f.manipulation.suspicious) return false;
  const s = q.search?.trim().toLowerCase();
  if (s && !f.itemName.toLowerCase().includes(s) && !f.itemId.toLowerCase().includes(s)) return false;
  return true;
}

/** Alert rules never fire for manipulation-flagged flips (same as Web Push on the server). */
export function ruleMatches(rule: NotificationRule, f: FlipOpportunity): boolean {
  return rule.enabled && !f.manipulation.suspicious && matchesFilter(f, rule);
}
