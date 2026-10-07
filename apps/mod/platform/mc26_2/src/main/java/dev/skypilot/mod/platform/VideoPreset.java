package dev.skypilot.mod.platform;

import dev.skypilot.mod.core.Lang;
import dev.skypilot.mod.core.SkyPilotConfig;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.server.level.ParticleStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * "Lightweight video settings": lowers Minecraft's own heavier video options (never raises any)
 * when the player presses the button, and remembers the player's values so "Restore" puts them
 * back exactly. Option meanings checked against the 26.2 option tooltips (e.g. see-through
 * leaves: "Disabling improves performance").
 */
final class VideoPreset {
	record Change<T>(String key, String en, String ja, Function<Options, OptionInstance<T>> option, UnaryOperator<T> lighter, Function<String, T> parse) {
		T current(Options o) {
			return option.apply(o).get();
		}

		T target(Options o) {
			return lighter.apply(current(o));
		}

		boolean pending(Options o) {
			return !Objects.equals(current(o), target(o));
		}

		void set(Options o, Object value) {
			@SuppressWarnings("unchecked")
			T v = (T) value;
			option.apply(o).set(v);
		}
	}

	static final List<Change<?>> CHANGES = List.of(
			new Change<Integer>("renderDistance", "Render distance", "描画距離", Options::renderDistance, v -> Math.min(v, 8), Integer::valueOf),
			new Change<Double>("entityDistanceScaling", "Entity distance", "エンティティ描画距離", Options::entityDistanceScaling, v -> Math.min(v, 0.75), Double::valueOf),
			new Change<ParticleStatus>("particles", "Particles", "パーティクル", Options::particles, v -> v == ParticleStatus.ALL ? ParticleStatus.DECREASED : v, ParticleStatus::valueOf),
			new Change<Boolean>("entityShadows", "Entity shadows", "エンティティの影", Options::entityShadows, v -> false, Boolean::valueOf),
			new Change<CloudStatus>("cloudStatus", "Clouds", "雲", Options::cloudStatus, v -> CloudStatus.OFF, CloudStatus::valueOf),
			new Change<Integer>("biomeBlendRadius", "Biome blend", "バイオームの色の補間", Options::biomeBlendRadius, v -> Math.min(v, 1), Integer::valueOf),
			new Change<Boolean>("cutoutLeaves", "See-through leaves", "透けて見える葉", Options::cutoutLeaves, v -> false, Boolean::valueOf),
			new Change<Boolean>("improvedTransparency", "Improved transparency", "改良された透明度", Options::improvedTransparency, v -> false, Boolean::valueOf),
			new Change<Integer>("menuBackgroundBlurriness", "Menu background blur", "メニュー背景のぼかし", Options::menuBackgroundBlurriness, v -> Math.min(v, 2), Integer::valueOf));

	private VideoPreset() {
	}

	static String show(Object v, Lang l) {
		if (v instanceof Boolean b) return b ? l.t("On", "オン") : l.t("Off", "オフ");
		if (v instanceof Double d) return Math.round(d * 100) + "%";
		if (v instanceof Enum<?> e) return e.name().charAt(0) + e.name().substring(1).toLowerCase(Locale.ROOT);
		return String.valueOf(v);
	}

	/** How many options would change. */
	static int pending(Options o) {
		int n = 0;
		for (Change<?> c : CHANGES) if (c.pending(o)) n++;
		return n;
	}

	/** Lower the heavier options; the player's previous values are kept (only the first time). */
	static int apply(Minecraft mc, SkyPilotConfig cfg) {
		Options o = mc.options;
		if (cfg.perf.videoBackup == null) cfg.perf.videoBackup = new LinkedHashMap<>();
		int n = 0;
		for (Change<?> c : CHANGES) {
			if (!c.pending(o)) continue;
			cfg.perf.videoBackup.putIfAbsent(c.key(), String.valueOf(c.current(o)));
			c.set(o, c.target(o));
			n++;
		}
		if (cfg.perf.videoBackup.isEmpty()) cfg.perf.videoBackup = null;
		if (n > 0) o.save();
		return n;
	}


	/** Richer vanilla rendering, with the same reversible backup as the lightweight preset. */
	static int quality(Minecraft mc, SkyPilotConfig cfg) {
		if (cfg.perf.videoBackup == null) cfg.perf.videoBackup = new LinkedHashMap<>();
		java.util.Map<String, Object> values = java.util.Map.of(
				"renderDistance", 12, "entityDistanceScaling", 1.0, "particles", ParticleStatus.ALL,
				"entityShadows", true, "cloudStatus", CloudStatus.FANCY, "biomeBlendRadius", 5,
				"cutoutLeaves", true, "improvedTransparency", true, "menuBackgroundBlurriness", 5);
		int n = 0;
		for (Change<?> c : CHANGES) {
			Object value = values.get(c.key());
			if (Objects.equals(c.current(mc.options), value)) continue;
			cfg.perf.videoBackup.putIfAbsent(c.key(), String.valueOf(c.current(mc.options)));
			c.set(mc.options, value); n++;
		}
		if (cfg.perf.videoBackup.isEmpty()) cfg.perf.videoBackup = null;
		if (n > 0) mc.options.save();
		return n;
	}

	/** Put the player's own values back. */
	static int restore(Minecraft mc, SkyPilotConfig cfg) {
		if (cfg.perf.videoBackup == null) return 0;
		Options o = mc.options;
		int n = 0;
		for (Change<?> c : CHANGES) {
			String saved = cfg.perf.videoBackup.get(c.key());
			if (saved == null) continue;
			try {
				c.set(o, c.parse().apply(saved));
				n++;
			} catch (RuntimeException e) {
				// a value from another Minecraft version: leave the option as it is
			}
		}
		cfg.perf.videoBackup = null;
		o.save();
		return n;
	}

	/** One line per option: name, current value, value after "apply" (or the saved value). */
	static List<String[]> rows(Options o, SkyPilotConfig cfg, Lang l) {
		List<String[]> out = new ArrayList<>();
		for (Change<?> c : CHANGES) {
			String saved = cfg.perf.videoBackup == null ? null : cfg.perf.videoBackup.get(c.key());
			Object savedValue = null;
			if (saved != null) {
				try {
					savedValue = c.parse().apply(saved);
				} catch (RuntimeException e) {
					savedValue = saved;
				}
			}
			out.add(new String[] {l.t(c.en(), c.ja()), show(c.current(o), l), c.pending(o) ? show(c.target(o), l) : "", savedValue == null ? "" : show(savedValue, l)});
		}
		return out;
	}
}
