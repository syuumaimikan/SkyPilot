package dev.skypilot.launcher;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Fabric's version rules, so the launcher can tell which mods fit Minecraft 26.2 before the game
 * starts. Follows Fabric Loader 0.19.5 ({@code SemanticVersionImpl}, {@code VersionPredicateParser},
 * {@code VersionComparisonOperator}):
 * <ul>
 * <li>a predicate is space-separated terms that must all match; "" or "*" matches anything;</li>
 * <li>operators >=, <=, >, <, =, ~ (same major.minor, at least), ^ (same major, at least);</li>
 * <li>a.x means ^a-, a.b.x means ~a.b-, a.b.c.x means >=a.b.c- <a.b.(c+1)-;</li>
 * <li>a pre-release (after "-") sorts below the release, build metadata (after "+") is ignored;</li>
 * <li>versions that are not semantic only compare equal by text.</li>
 * </ul>
 */
public final class FabricVersion implements Comparable<FabricVersion> {
	private static final Pattern NUMBER = Pattern.compile("0|[1-9][0-9]*");
	private static final Pattern UNSIGNED = Pattern.compile("[0-9]+");
	private static final String[] OPERATORS = {">=", "<=", ">", "<", "=", "~", "^"};

	private final String raw;
	private final int[] components;
	/** null = release; "" = the lowest pre-release (used for wildcard bounds). */
	private final String prerelease;
	private final boolean semantic;

	private FabricVersion(String raw, int[] components, String prerelease, boolean semantic) {
		this.raw = raw;
		this.components = components;
		this.prerelease = prerelease;
		this.semantic = semantic;
	}

	public static FabricVersion parse(String text) {
		String s = text == null ? "" : text.strip();
		String core = s;
		int plus = core.indexOf('+');
		if (plus >= 0) core = core.substring(0, plus);
		String pre = null;
		int dash = core.indexOf('-');
		if (dash >= 0) {
			pre = core.substring(dash + 1);
			core = core.substring(0, dash);
		}
		String[] parts = core.split("\\.", -1);
		int[] comps = new int[parts.length];
		for (int i = 0; i < parts.length; i++) {
			if (!NUMBER.matcher(parts[i]).matches() || parts[i].length() > 9) return new FabricVersion(s, new int[0], null, false);
			comps[i] = Integer.parseInt(parts[i]);
		}
		if (pre != null && !pre.isEmpty() && !pre.matches("[0-9A-Za-z.-]+")) return new FabricVersion(s, new int[0], null, false);
		return new FabricVersion(s, comps, pre, true);
	}

	public boolean semantic() {
		return semantic;
	}

	public String text() {
		return raw;
	}

	int component(int i) {
		return i < components.length ? components[i] : 0;
	}

	@Override
	public int compareTo(FabricVersion o) {
		if (!semantic || !o.semantic) return raw.compareTo(o.raw);
		for (int i = 0; i < Math.max(components.length, o.components.length); i++) {
			int c = Integer.compare(component(i), o.component(i));
			if (c != 0) return c;
		}
		if (prerelease == null || o.prerelease == null) {
			if (prerelease == null && o.prerelease == null) return 0;
			return prerelease == null ? 1 : -1;
		}
		String[] a = prerelease.isEmpty() ? new String[0] : prerelease.split("\\.");
		String[] b = o.prerelease.isEmpty() ? new String[0] : o.prerelease.split("\\.");
		for (int i = 0; i < Math.min(a.length, b.length); i++) {
			int c = compareIdentifier(a[i], b[i]);
			if (c != 0) return c;
		}
		return Integer.compare(a.length, b.length);
	}

	private static int compareIdentifier(String a, String b) {
		boolean na = UNSIGNED.matcher(a).matches();
		boolean nb = UNSIGNED.matcher(b).matches();
		if (na && nb) {
			String x = a.replaceFirst("^0+(?=.)", "");
			String y = b.replaceFirst("^0+(?=.)", "");
			return x.length() != y.length() ? Integer.compare(x.length(), y.length()) : x.compareTo(y);
		}
		if (na) return -1;
		if (nb) return 1;
		return a.compareTo(b);
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof FabricVersion v && compareTo(v) == 0;
	}

	@Override
	public int hashCode() {
		return raw.hashCode();
	}

	@Override
	public String toString() {
		return raw;
	}

	// ---------------------------------------------------------------- predicates

	private record Term(String op, FabricVersion version) {
		boolean test(FabricVersion v) {
			if (!v.semantic || !version.semantic) {
				// Text-only versions: inclusive operators mean "the same text", the others never match.
				return (op.equals("=") || op.equals(">=") || op.equals("<=") || op.equals("~") || op.equals("^")) && v.raw.equals(version.raw);
			}
			int c = v.compareTo(version);
			return switch (op) {
				case ">=" -> c >= 0;
				case "<=" -> c <= 0;
				case ">" -> c > 0;
				case "<" -> c < 0;
				case "~" -> c >= 0 && v.component(0) == version.component(0) && v.component(1) == version.component(1);
				case "^" -> c >= 0 && v.component(0) == version.component(0);
				default -> c == 0;
			};
		}
	}

	/** One version range as written in fabric.mod.json; null when it cannot be read. */
	static List<Term> terms(String predicate) {
		List<Term> out = new ArrayList<>();
		for (String part : predicate.strip().split(" ")) {
			String s = part.strip();
			if (s.isEmpty() || s.equals("*")) continue;
			String op = "=";
			for (String o : OPERATORS) {
				if (s.startsWith(o)) {
					op = o;
					s = s.substring(o.length());
					break;
				}
			}
			String[] parts = s.split("\\.", -1);
			String last = parts[parts.length - 1];
			boolean wildcard = parts.length > 1 && (last.equals("x") || last.equals("X") || last.equals("*"));
			if (wildcard) {
				if (!op.equals("=")) return null;
				String base = String.join(".", java.util.Arrays.copyOf(parts, parts.length - 1));
				FabricVersion low = parse(base + "-");
				if (!low.semantic) return null;
				if (parts.length == 2) {
					out.add(new Term("^", low));
				} else if (parts.length == 3) {
					out.add(new Term("~", low));
				} else {
					out.add(new Term(">=", low));
					int[] next = low.components.clone();
					next[next.length - 1]++;
					out.add(new Term("<", new FabricVersion(base, next, "", true)));
				}
				continue;
			}
			FabricVersion v = parse(s);
			if (!v.semantic && !(op.equals("=") || op.equals(">=") || op.equals("<="))) return null;
			out.add(new Term(v.semantic ? op : "=", v));
		}
		return out;
	}

	/** Does {@code version} satisfy one fabric.mod.json version range? Unreadable ranges count as satisfied (Fabric reports them itself). */
	public static boolean matches(String predicate, String version) {
		List<Term> t = terms(predicate == null ? "" : predicate);
		if (t == null) return true;
		FabricVersion v = parse(version);
		for (Term term : t) {
			if (!term.test(v)) return false;
		}
		return true;
	}

	/** Any of several ranges (a JSON array in fabric.mod.json means "one of these"). */
	public static boolean matchesAny(List<String> predicates, String version) {
		if (predicates.isEmpty()) return true;
		for (String p : predicates) {
			if (matches(p, version)) return true;
		}
		return false;
	}
}
