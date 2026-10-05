package dev.skypilot.mod.core;

/** UI language for mod messages. */
public enum Lang {
	EN, JA;

	public String t(String en, String ja) {
		return this == JA ? ja : en;
	}

	public String code() {
		return this == JA ? "ja" : "en";
	}

	/** Resolve the configured language ("auto" follows the Minecraft language code, e.g. "ja_jp"). */
	public static Lang resolve(String configured, String gameLanguage) {
		if ("ja".equals(configured)) return JA;
		if ("en".equals(configured)) return EN;
		return gameLanguage != null && gameLanguage.toLowerCase().startsWith("ja") ? JA : EN;
	}
}
