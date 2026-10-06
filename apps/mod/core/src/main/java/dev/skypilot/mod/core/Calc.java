package dev.skypilot.mod.core;

import java.util.Locale;

/**
 * Calculator for {@code /skypilot calc}: + - * / ^, parentheses, "x" for multiply, thousands
 * separators and the k / m / b / t suffixes players use for coins ("1.5m*3+200k").
 */
public final class Calc {
	private final String s;
	private int pos;

	private Calc(String s) {
		this.s = s;
	}

	public static double eval(String expression) {
		if (expression == null || expression.length() > 200) throw new IllegalArgumentException("expression too long");
		String cleaned = expression.toLowerCase(Locale.ROOT).replace(",", "").replace(" ", "").replace('×', '*').replace('÷', '/');
		Calc c = new Calc(cleaned);
		double v = c.sum();
		if (c.pos != cleaned.length()) throw new IllegalArgumentException("unexpected '" + cleaned.charAt(c.pos) + "'");
		if (Double.isNaN(v) || Double.isInfinite(v)) throw new IllegalArgumentException("not a finite number");
		return v;
	}

	private double sum() {
		double v = product();
		while (pos < s.length()) {
			char c = s.charAt(pos);
			if (c == '+') {
				pos++;
				v += product();
			} else if (c == '-') {
				pos++;
				v -= product();
			} else {
				break;
			}
		}
		return v;
	}

	private double product() {
		double v = power();
		while (pos < s.length()) {
			char c = s.charAt(pos);
			if (c == '*' || c == 'x') {
				pos++;
				v *= power();
			} else if (c == '/') {
				pos++;
				double d = power();
				if (d == 0) throw new IllegalArgumentException("division by zero");
				v /= d;
			} else {
				break;
			}
		}
		return v;
	}

	private double power() {
		double base = unary();
		if (pos < s.length() && s.charAt(pos) == '^') {
			pos++;
			return Math.pow(base, power());
		}
		return base;
	}

	private double unary() {
		if (pos < s.length() && s.charAt(pos) == '-') {
			pos++;
			return -unary();
		}
		if (pos < s.length() && s.charAt(pos) == '+') {
			pos++;
			return unary();
		}
		return atom();
	}

	private double atom() {
		if (pos < s.length() && s.charAt(pos) == '(') {
			pos++;
			double v = sum();
			if (pos >= s.length() || s.charAt(pos) != ')') throw new IllegalArgumentException("missing ')'");
			pos++;
			return suffix(v);
		}
		int start = pos;
		while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.')) pos++;
		if (start == pos) throw new IllegalArgumentException(pos < s.length() ? "unexpected '" + s.charAt(pos) + "'" : "number expected");
		double v;
		try {
			v = Double.parseDouble(s.substring(start, pos));
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("bad number " + s.substring(start, pos));
		}
		return suffix(v);
	}

	private double suffix(double v) {
		if (pos >= s.length()) return v;
		double m = switch (s.charAt(pos)) {
			case 'k' -> 1e3;
			case 'm' -> 1e6;
			case 'b' -> 1e9;
			case 't' -> 1e12;
			default -> 1;
		};
		if (m != 1) pos++;
		return v * m;
	}

	/** "1,234,567.5" (two decimals at most, trailing zeros removed). */
	public static String plain(double v) {
		String r = String.format(Locale.ROOT, "%,.2f", v);
		if (r.contains(".")) r = r.replaceAll("0+$", "").replaceAll("\\.$", "");
		return r;
	}
}
