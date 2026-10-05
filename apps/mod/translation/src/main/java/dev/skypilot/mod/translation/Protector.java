package dev.skypilot.mod.translation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Replaces text that must survive translation unchanged (SkyBlock terms, player names, numbers,
 * commands) with {@code ⟦n⟧} placeholders, and restores them afterwards.
 */
public final class Protector {
	public record Protected(String text, List<String> tokens) {
	}

	private static final Pattern NUMBER = Pattern.compile("(?<![\\p{L}\\d⟦])\\d[\\d,.]*[kKmMbB]?(?![\\p{L}\\d⟧])");
	private static final Pattern COMMAND = Pattern.compile("(?<!\\S)/[a-zA-Z][\\w ]{0,40}?(?=$|[.,!?])");
	private static final Pattern PLACEHOLDER = Pattern.compile("⟦(\\d+)⟧");

	private final List<Pattern> terms = new ArrayList<>();

	/** {@code protectedTerms}: SkyBlock vocabulary (item, area, mob names...) plus known player names. */
	public Protector(Collection<String> protectedTerms) {
		protectedTerms.stream()
				.filter(t -> t != null && t.strip().length() >= 2)
				.map(String::strip)
				.distinct()
				.sorted(Comparator.comparingInt(String::length).reversed())
				.forEach(t -> terms.add(Pattern.compile("(?<![\\p{L}\\d_⟦])" + Pattern.quote(t) + "(?![\\p{L}\\d_⟧])", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)));
	}

	public Protected protect(String text) {
		List<String> tokens = new ArrayList<>();
		String out = text;
		for (Pattern p : terms) out = replace(out, p, tokens);
		out = replace(out, COMMAND, tokens);
		out = replace(out, NUMBER, tokens);
		return new Protected(out, tokens);
	}

	/** Restores placeholders. Returns null when the translation lost or invented a placeholder. */
	public static String restore(String translated, List<String> tokens) {
		Matcher m = PLACEHOLDER.matcher(translated);
		StringBuilder sb = new StringBuilder();
		int seen = 0;
		while (m.find()) {
			int idx = Integer.parseInt(m.group(1));
			if (idx >= tokens.size()) return null;
			m.appendReplacement(sb, Matcher.quoteReplacement(tokens.get(idx)));
			seen++;
		}
		m.appendTail(sb);
		return seen == tokens.size() ? sb.toString() : null;
	}

	private static String replace(String text, Pattern p, List<String> tokens) {
		Matcher m = p.matcher(text);
		StringBuilder sb = new StringBuilder();
		while (m.find()) {
			// never nest placeholders
			if (m.group().contains("⟦")) continue;
			tokens.add(m.group());
			m.appendReplacement(sb, Matcher.quoteReplacement("⟦" + (tokens.size() - 1) + "⟧"));
		}
		m.appendTail(sb);
		return sb.toString();
	}

	/** True when the text already contains Japanese (kana or CJK ideographs). */
	public static boolean isJapanese(String s) {
		return s.codePoints().anyMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.HIRAGANA
				|| Character.UnicodeScript.of(c) == Character.UnicodeScript.KATAKANA
				|| Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN);
	}

	/** Lines with fewer than three letters (e.g. "gg", "?", "1") or already in Japanese are skipped. */
	public static boolean worthTranslating(String body) {
		long letters = body.codePoints().filter(Character::isLetter).count();
		return letters >= 3 && !isJapanese(body);
	}

	static String normalizePhrase(String s) {
		return s.toLowerCase(Locale.ROOT).replaceAll("[\\s]+", " ").replaceAll("[!?.~]+$", "").strip();
	}
}
