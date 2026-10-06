package dev.skypilot.mod.core;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bazaar order helper: reads one of your order items in Hypixel's "Your Bazaar Orders" window
 * (name "BUY Enchanted Diamond" / "SELL …", lore "Price per unit: 1,234.5 coins") and compares
 * it with the live top of the book. Reading only - orders are never clicked, claimed or changed.
 * Lines that do not match are ignored (nothing is guessed).
 */
public final class BazaarOrderRules {
	public enum Status {
		/** Your price is the best on its side of the book. */
		BEST,
		/** Someone has a better price: your order waits behind theirs. */
		OUTBID,
		/** Completely filled: ready to claim. */
		FILLED,
		/** No live price for this product. */
		UNKNOWN
	}

	/** {@code buy}: a buy order (false = sell offer); {@code price}: coins per unit. */
	public record Order(boolean buy, String product, double price, boolean filled) {
	}

	public record Verdict(Status status, double gap) {
	}

	private static final Pattern NAME = Pattern.compile("^(BUY|SELL) (.+)$");
	private static final Pattern PRICE = Pattern.compile("^Price per unit: ([0-9][0-9,]*(?:\\.[0-9]+)?) coins?$");
	private static final Pattern FILLED = Pattern.compile("^Filled: .*100%");
	/** Prices are shown to 0.1 coins. */
	private static final double EPS = 0.05;

	private BazaarOrderRules() {
	}

	/** The orders window ("Your Bazaar Orders", or the co-op one). */
	public static boolean isOrdersWindow(String title) {
		String t = Text.strip(title == null ? "" : title).strip();
		return t.equals("Your Bazaar Orders") || t.equals("Co-op Bazaar Orders");
	}

	/** One order from an item's name and lore, or null when it is not an order item. */
	public static Order parse(String name, List<String> lore) {
		if (name == null || lore == null) return null;
		Matcher n = NAME.matcher(Text.strip(name).strip());
		if (!n.matches()) return null;
		Double price = null;
		boolean filled = false;
		for (String raw : lore) {
			String line = Text.strip(raw).strip();
			Matcher p = PRICE.matcher(line);
			if (p.matches()) price = Double.parseDouble(p.group(1).replace(",", ""));
			if (FILLED.matcher(line).find()) filled = true;
		}
		if (price == null || !(price > 0)) return null;
		return new Order(n.group(1).equals("BUY"), n.group(2).strip(), price, filled);
	}

	/**
	 * {@code instantBuy}: lowest sell offer; {@code instantSell}: highest buy order (either may
	 * be null). The gap is how far a better price is from yours (coins per unit).
	 */
	public static Verdict judge(Order o, Double instantBuy, Double instantSell) {
		if (o.filled()) return new Verdict(Status.FILLED, 0);
		Double best = o.buy() ? instantSell : instantBuy;
		if (best == null || !(best > 0)) return new Verdict(Status.UNKNOWN, 0);
		double gap = o.buy() ? best - o.price() : o.price() - best;
		return gap > EPS ? new Verdict(Status.OUTBID, gap) : new Verdict(Status.BEST, 0);
	}

	/** "outbid by 12.3 (0.8%)". */
	public static String describe(Verdict v, Order o, Lang l) {
		return switch (v.status()) {
			case BEST -> l.t("best price", "最良価格");
			case FILLED -> l.t("filled - claim it", "約定済み - 受け取れます");
			case UNKNOWN -> l.t("no live price", "現在価格なし");
			case OUTBID -> l.t("outbid by ", "上回られています: ") + Text.coins(v.gap())
					+ String.format(Locale.ROOT, " (%.1f%%)", v.gap() / o.price() * 100);
		};
	}
}
