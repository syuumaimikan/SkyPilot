package dev.skypilot.mod.network;

import java.util.List;

/** JSON shapes exchanged with the SkyPilot API (mirrors packages/protocol mod_api / auth / translation). */
public final class Dto {
	private Dto() {
	}

	public record HudLine(String label, String value) {
	}

	public record HudContextBlock(String context, String title, List<HudLine> lines) {
	}

	public record HudState(boolean linked, String username, String goalTitle, Integer stepIndex, Integer stepCount, String stepTitle, Double stepProgress,
			Double stepCurrent, Double stepTarget, String stepUnit, String nextTitle, Double estimatedCost, Double overallProgress, HudContextBlock context,
			List<String> actions, long updatedAtMs, boolean stale, String message) {
	}

	public record PriceResponse(String skyblockId, String name, Double estimatedValue, Double median, Double lowestBin, Double bazaarBuy, Double bazaarSell,
			Double salesPerDay, Double confidence, String confidenceLabel, String priceKey, Long ageSeconds) {
	}

	public record TextResponse(String title, List<HudLine> lines) {
	}

	public record DeviceCodeResponse(String deviceCode, String userCode, String verificationUri, String verificationUriComplete, long expiresIn, int interval) {
	}

	public record DeviceTokenResponse(String status, String accessToken, String scope) {
	}

	public record TranslateResponse(String translated, String provider, boolean cached, boolean unchanged) {
	}

	public record DictionaryEntry(String term, String translation, String category) {
	}

	public record TranslationDictionary(String version, List<String> protectedTerms, List<DictionaryEntry> phrases) {
	}

	// ---------------------------------------------------------------- in-game GUI

	/** Vanilla item id plus head texture / leather colour / glint used to draw a SkyBlock item. */
	public record ItemIcon(String item, String texture, Integer color, boolean glint) {
	}

	/** An AH or Bazaar flip. {@code command} is only ever put into the chat box, never sent. */
	public record FlipRow(String id, String kind, String itemId, String name, String rarity, List<String> modifiers, double buy, double sell, double profit,
			double roi, Double profitPerHour, Double amount, double confidence, String confidenceLabel, String risk, String sellTime, boolean doNotFlip,
			List<String> notes, String command, ItemIcon icon) {
	}

	public record MarketPanel(String kind, String title, List<FlipRow> rows, long ageSeconds, boolean stale, String note) {
	}

	public record Alerts(long nowMs, List<FlipRow> rows, List<String> rules) {
	}

	public record ItemRef(String itemId, String name, Double price, ItemIcon icon) {
	}

	public record PlanAction(String title, String category, String why, List<String> steps, List<HudLine> metrics, double confidence, Double cost, Double hours,
			List<ItemRef> items) {
	}

	public record PlanStep(String title, String detail, String status, double progress, Double cost, Double hours) {
	}

	public record PlanGoal(String title, double progress, Double remainingCost, Double remainingHours, List<PlanStep> steps) {
	}

	public record Plan(String profile, long analyzedAtMs, List<PlanGoal> goals, List<PlanAction> actions, List<String> warnings, String message) {
	}

	/** Advisor report (same JSON as the web); table cells stay generic ({@code kind} + fields). */
	public record Metric(String label, Double before, Double after, Double value, String unit, String text) {
	}

	public record ReportTable(List<String> columns, List<List<com.google.gson.JsonObject>> rows) {
	}

	public record ReportSection(String id, String title, String description, List<Metric> metrics, ReportTable table, List<String> bullets, List<String> notes) {
	}

	public record Report(String domain, String title, List<Metric> summary, List<ReportSection> sections, List<String> unknowns) {
	}
}
