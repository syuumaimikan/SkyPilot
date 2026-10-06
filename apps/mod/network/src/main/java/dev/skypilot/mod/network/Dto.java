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
			Double salesPerDay, Double confidence, String confidenceLabel, String priceKey, Long ageSeconds, Double npcSell, Double craftCost, Double bazaarTaxRate) {
	}

	/** A sea creature and the chat line announcing it (SkyHanni-REPO data, via the SkyPilot server). */
	public record SeaCreature(String name, String area, String message, String rarity, Double fishingXp) {
	}

	public record SeaCreatures(List<SeaCreature> creatures, String attribution) {
	}

	/** SkyBlock calendar constants (from the server's curated registry) and the next events. */
	public record Calendar(long epochMs, long dayMs, int daysPerMonth, List<String> months, List<CalendarEvent> events, long nowMs) {
	}

	public record CalendarEvent(String id, String name, long startMs, long endMs, boolean active, Integer everyDays, Integer offsetDays, Integer durationDays) {
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

	/** Live Bazaar prices of one product (by its display name). */
	public record BazaarQuote(String name, String productId, Double instantBuy, Double instantSell) {
	}

	public record BazaarQuotes(List<BazaarQuote> quotes, long ageSeconds, boolean stale) {
	}

	public record ItemRef(String itemId, String name, Double price, ItemIcon icon) {
	}

	public record PlanAction(String title, String category, String why, List<String> steps, List<HudLine> metrics, double confidence, Double cost, Double hours,
			List<ItemRef> items, Place place) {
	}

	/** Where something is done: a public place, the closest warp (only put into the chat box) and a note. */
	public record Place(Waypoint waypoint, String warp, String note) {
	}

	// ---------------------------------------------------------------- fit, builds, money, puzzles

	public record ClassFit(@com.google.gson.annotations.SerializedName("class") String className, int level, double progress, double score, List<String> reasons,
			boolean selected) {
	}

	public record FloorFit(String floor, double readiness, boolean unlocked, boolean recommended, long completions, List<String> missing) {
	}

	public record SlayerFit(String id, String name, int unlockedTier, int tier, double readiness, String gearTier) {
	}

	public record GoalPath(String title, double progress, Double remainingCost, Double remainingHours, List<PlanStep> nextSteps, PlanAction nextAction) {
	}

	public record Fit(List<ClassFit> classes, String recommendedClass, List<FloorFit> floors, List<SlayerFit> slayers, List<GoalPath> goals, List<String> notes,
			String message) {
	}

	public record BuildSlot(String slot, List<GearItem> items, boolean done, Double cost) {
	}

	public record BuildTier(String tier, List<BuildSlot> slots, boolean complete, Double costToComplete) {
	}

	public record Build(String id, String title, List<BuildTier> tiers, boolean goal, String source) {
	}

	public record Builds(List<Build> builds, String message) {
	}

	public record MoneyMethod(String id, String name, String category, Double lowPerHour, Double highPerHour, Double setupCost, String risk, boolean requirementsMet,
			List<String> missing, String why, double confidence, Place place) {
	}

	public record Money(List<MoneyMethod> methods, String message) {
	}

	public record Perk(String name, String description) {
	}

	public record Mayor(String name, List<Perk> perks) {
	}

	public record FireSale(String itemId, String name, long startMs, long endMs, Long amount, Long price) {
	}

	/** One SkyBlock patch-notes post (link to hypixel.net). */
	public record NewsItem(String title, String date, String link) {
	}

	public record News(List<NewsItem> items, String source) {
	}

	/** Current mayor / minister and fire sales (Hypixel resources, via the SkyPilot server). */
	public record SkyblockNow(Mayor mayor, Mayor minister, Integer year, List<FireSale> fireSales) {
	}

	/** Latest Modrinth version of one mod file (by SHA-1); information only. */
	public record ModUpdate(String hash, String projectId, String versionId, String versionNumber, String name, String published, boolean upToDate, String url) {
	}

	public record ModUpdates(List<ModUpdate> updates, long checkedAtMs, String source) {
	}

	/** A dungeon puzzle's general rules (wiki), never a solution. */
	public record Puzzle(String name, String floors, String rule, boolean canFail, String source) {
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

	// ---------------------------------------------------------------- item browser & values

	public record ItemEntry(String id, String name, String rarity, ItemIcon icon, Double value) {
	}

	public record ItemPage(int total, int offset, List<ItemEntry> items) {
	}

	public record ItemStackInfo(String id, String name, double amount, ItemIcon icon, Double unitPrice) {
	}

	/** {@code kind}: CRAFTING (with a 3×3 {@code grid}), FORGE, KAT or NPC_SHOP. */
	public record Recipe(String kind, ItemStackInfo output, List<ItemStackInfo> inputs, List<ItemStackInfo> grid, Double durationSecs, String npc, Double coins,
			Double cost) {
	}

	public record DropSource(String source, double chance, double amount) {
	}

	public record ItemDetail(ItemEntry item, String category, List<String> lore, String valueSource, Double lowestBin, Double salesPerDay, Double bazaarBuy,
			Double bazaarSell, Double npcSell, Double craftCost, List<Recipe> recipes, List<DropSource> drops, List<ItemEntry> usedIn, String wiki) {
	}

	public record Value(String skyblockId, String name, Double value, Double confidence) {
	}

	public record Values(List<Value> values, double total) {
	}

	// ---------------------------------------------------------------- user settings (server side)

	/** Whether a key is stored on the server; only its last characters are ever returned. */
	public record KeyInfo(boolean configured, String hint) {
	}

	/** {@code provider}: "server" | "deepl" | "claude" | "none". */
	public record TranslationSettings(String provider, KeyInfo deepl, KeyInfo anthropic, String serverProvider, List<String> providers) {
	}

	public record UserSettings(TranslationSettings translation) {
	}

	public record TranslationTest(boolean ok, String provider, String translated, String message) {
	}

	// ---------------------------------------------------------------- guides, gear, farms

	/** A fixed public place (SkyHanni-REPO island graphs); {@code island} is e.g. "DWARVEN_MINES". */
	public record Waypoint(String island, String name, double x, double y, double z) {
	}

	public record GuideStep(String title, String text, Boolean done, Waypoint waypoint) {
	}

	public record Guide(String id, String island, String title, String source, List<GuideStep> steps) {
	}

	public record Guides(List<Guide> guides, String attribution) {
	}

	public record GearItem(String id, String name, ItemIcon icon, String slot, String tier, boolean owned, Double price, String note) {
	}

	public record GearSection(String title, List<GearItem> items) {
	}

	public record Gear(List<GearSection> sections, String message) {
	}

	public record FarmSummary(String id, String name, List<String> crops, String description, List<Integer> size) {
	}

	public record FarmBlock(int x, int y, int z, String block) {
	}

	public record FarmStep(int index, String title, String detail, Integer layerY, java.util.Map<String, Integer> blocks) {
	}

	public record FarmDesign(FarmSummary summary, List<FarmBlock> blocks, java.util.Map<String, Integer> blockCounts, List<Integer> layers, List<String> movement,
			String harvestDirection, List<String> waterNotes, List<String> lightingNotes, List<FarmStep> buildSteps, double estimatedBuildMinutes,
			Double estimatedMaterialCost) {
	}
}
