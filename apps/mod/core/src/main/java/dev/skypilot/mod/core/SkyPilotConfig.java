package dev.skypilot.mod.core;

/**
 * User settings, stored as JSON in {@code config/skypilot/config.json}.
 *
 * <p>Everything that sends data off the client is opt-in: chat translation is {@code OFF} by
 * default and session statistics are only uploaded when {@link Session#upload} is enabled.
 */
public final class SkyPilotConfig {
	/** SkyPilot backend (web origin). Plain http is only accepted for localhost. */
	public String apiBaseUrl = "http://localhost:3000";
	/** "auto" (follow the game language), "en" or "ja". */
	public String language = "auto";
	public Hud hud = new Hud();
	public Translation translation = new Translation();
	public Session session = new Session();
	/** Adds a SkyPilot price line to SkyBlock item tooltips (display only). */
	public boolean priceTooltip = true;

	public static final class Hud {
		public boolean enabled = true;
		/** Top-left corner in GUI-scaled pixels. */
		public int x = 4;
		public int y = 4;
		public double scale = 1.0;
		/** Background opacity 0..1. */
		public double opacity = 0.6;
		public boolean showContext = true;
		public boolean showSession = true;
	}

	public static final class Translation {
		/** The original line is always shown; the translation is added below it. */
		public TranslationModeSetting mode = TranslationModeSetting.OFF;
		/** The user has seen the notice that chat text is sent to the SkyPilot server / translator. */
		public boolean disclosureAccepted = false;
	}

	public static final class Session {
		public boolean enabled = true;
		/** Upload session statistics to the linked SkyPilot account when a session ends. */
		public boolean upload = false;
	}

	/** Mirrors the translation modes; kept here so the config has no dependency on the translation module. */
	public enum TranslationModeSetting {
		OFF, MANUAL, NPC_ONLY, PARTY, GUILD, AUTO
	}

	/** Clamp values edited by hand in the JSON file into safe ranges. */
	public SkyPilotConfig sanitize() {
		if (apiBaseUrl == null || apiBaseUrl.isBlank()) apiBaseUrl = "http://localhost:3000";
		apiBaseUrl = apiBaseUrl.strip().replaceAll("/+$", "");
		if (language == null || !(language.equals("en") || language.equals("ja") || language.equals("auto"))) language = "auto";
		if (hud == null) hud = new Hud();
		if (translation == null) translation = new Translation();
		if (session == null) session = new Session();
		if (translation.mode == null) translation.mode = TranslationModeSetting.OFF;
		hud.scale = clamp(hud.scale, 0.5, 2.5);
		hud.opacity = clamp(hud.opacity, 0.0, 1.0);
		hud.x = (int) clamp(hud.x, 0, 10_000);
		hud.y = (int) clamp(hud.y, 0, 10_000);
		// An external translation mode is only active after the disclosure was shown.
		if (!translation.disclosureAccepted && translation.mode != TranslationModeSetting.OFF && translation.mode != TranslationModeSetting.MANUAL) {
			translation.mode = TranslationModeSetting.OFF;
		}
		return this;
	}

	private static double clamp(double v, double lo, double hi) {
		if (Double.isNaN(v)) return lo;
		return Math.max(lo, Math.min(hi, v));
	}
}
