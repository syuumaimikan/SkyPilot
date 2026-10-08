package dev.skypilot.mod.platform;

import dev.skypilot.mod.core.Features;
import dev.skypilot.mod.core.PerfRules;
import dev.skypilot.mod.core.SkyPilotConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;

import java.util.Set;

/**
 * Performance features, called from the render mixins. The switches are copied into plain fields
 * once per tick so the per-entity and per-particle checks stay cheap.
 *
 * <p>Everything here only decides what is <i>drawn</i>: hidden entities stay in the world and
 * react to clicks and hits exactly as before, and nothing the player does is cancelled.
 */
public final class Performance {
	private static final Set<ParticleType<?>> FIREWORK = Set.of(ParticleTypes.FIREWORK, ParticleTypes.FLASH);
	private static final Set<ParticleType<?>> EXPLOSION = Set.of(ParticleTypes.EXPLOSION, ParticleTypes.EXPLOSION_EMITTER);
	private static final Set<ParticleType<?>> HIT = Set.of(ParticleTypes.CRIT, ParticleTypes.ENCHANTED_HIT, ParticleTypes.DAMAGE_INDICATOR, ParticleTypes.SWEEP_ATTACK,
			ParticleTypes.HEART);

	private static boolean background, menu, farEntities, farPlayers, damage, falling, fireworks, lightning, explosions, hits, breaking, cap, lite;
	private static int bgFps, menuFps, particlesPerTick;
	private static double entityDist2, playerDist2;
	private static final PerfRules.Budget BUDGET = new PerfRules.Budget();
	/** Armor stand → is a damage number (names of damage numbers never change). */
	private static final java.util.Map<ArmorStand, Boolean> SPLASH = new java.util.WeakHashMap<>();
	private static int ticks;
	private static boolean adaptive, farParticles;
	private static double particleDist2;
	private static int adaptiveCap;
	private static long lastFrame;
	private static boolean wasSampling;
	private static final dev.skypilot.mod.core.FrameSamples FRAMES = new dev.skypilot.mod.core.FrameSamples(300);
	private static String frameStats = "—";

	/** Called once per rendered frame, including frames where the HUD is hidden. */
	public static void frame() {
		Minecraft mc = Minecraft.getInstance();
		SkyPilot sp = SkyPilot.get();
		boolean sampling = sp != null && sp.config().on(Features.PERF_FRAME_STATS) && mc.level != null && mc.isWindowActive() && mc.gui.screen() == null;
		long now = System.nanoTime();
		if (!sampling || !wasSampling) { FRAMES.clear(); frameStats = "—"; lastFrame = 0; }
		if (sampling && lastFrame != 0) FRAMES.add((now - lastFrame) / 1_000_000.0);
		lastFrame = sampling ? now : 0; wasSampling = sampling;
	}

	static String frameStats() { return frameStats; }

	private Performance() {
	}

	/** Every client tick. */
	static void tick(Minecraft mc) {
		SkyPilot sp = SkyPilot.get();
		if (sp == null) return;
		SkyPilotConfig c = sp.config();
		background = c.on(Features.PERF_BACKGROUND_FPS);
		menu = c.on(Features.PERF_MENU_FPS);
		farEntities = c.on(Features.PERF_FAR_ENTITIES);
		farPlayers = c.on(Features.PERF_FAR_PLAYERS);
		damage = c.on(Features.PERF_DAMAGE_SPLASH);
		falling = c.on(Features.PERF_FALLING_BLOCKS);
		fireworks = c.on(Features.PERF_FIREWORKS);
		lightning = c.on(Features.PERF_LIGHTNING);
		explosions = c.on(Features.PERF_EXPLOSIONS);
		hits = c.on(Features.PERF_HIT_PARTICLES);
		breaking = c.on(Features.PERF_BREAK_PARTICLES);
		cap = c.on(Features.PERF_PARTICLE_CAP);
		lite = c.on(Features.PERF_LITE);
		bgFps = c.perf.backgroundFps;
		menuFps = c.perf.menuFps;
		particlesPerTick = c.perf.particlesPerTick;
		adaptive = c.on(Features.PERF_ADAPTIVE);
		farParticles = c.on(Features.PERF_FAR_PARTICLES);
		particleDist2 = (double)c.perf.particleDistance * c.perf.particleDistance;
		if (!adaptive || adaptiveCap == 0 || mc.level == null) adaptiveCap = particlesPerTick;
		adaptiveCap = Math.min(adaptiveCap, particlesPerTick);
		if (ticks % 20 == 0) {
			if (adaptive && mc.level != null && mc.isWindowActive() && mc.gui.screen() == null)
				adaptiveCap = PerfRules.adaptiveBudget(adaptiveCap, particlesPerTick, mc.getFps(), c.perf.targetFps);
			frameStats = FRAMES.count() < 30 ? "—" : String.format(java.util.Locale.ROOT, "p95 %.1f ms · p99 %.1f ms · p99 FPS %.0f", FRAMES.percentile(.95), FRAMES.percentile(.99), FRAMES.slowFrameFps());
		}
		if (adaptive) particlesPerTick = adaptiveCap;
		entityDist2 = (double) c.perf.entityDistance * c.perf.entityDistance;
		playerDist2 = (double) c.perf.playerDistance * c.perf.playerDistance;
		BUDGET.reset();
		if (++ticks % 200 == 0 || mc.level == null) SPLASH.clear();
	}

	static boolean lite() {
		return lite;
	}

	/** Should this entity be left out of this frame? */
	public static boolean hide(Entity e, double camX, double camY, double camZ) {
		if (!(farEntities || farPlayers || damage || falling || fireworks || lightning)) return false;
		Minecraft mc = Minecraft.getInstance();
		if (e == mc.player || e == mc.getCameraEntity()) return false;
		if (e instanceof Player) return farPlayers && e.distanceToSqr(camX, camY, camZ) > playerDist2;
		if (damage && e instanceof ArmorStand stand && stand.hasCustomName() && splash(stand)) return true;
		if (falling && e instanceof FallingBlockEntity) return true;
		if (fireworks && e instanceof FireworkRocketEntity) return true;
		if (lightning && e instanceof LightningBolt) return true;
		return farEntities && e.distanceToSqr(camX, camY, camZ) > entityDist2;
	}

	private static boolean splash(ArmorStand stand) {
		Boolean known = SPLASH.get(stand);
		if (known != null) return known;
		var name = stand.getCustomName();
		boolean is = name != null && PerfRules.isDamageSplash(name.getString(), stand.tickCount);
		SPLASH.put(stand, is);
		return is;
	}

	/** May this particle be created? */
	public static boolean allowParticle(ParticleOptions options) {
		if (!(fireworks || explosions || hits || cap || adaptive)) return true;
		ParticleType<?> type = options.getType();
		if (fireworks && FIREWORK.contains(type)) return false;
		if (explosions && EXPLOSION.contains(type)) return false;
		if (hits && HIT.contains(type)) return false;
		return !(cap || adaptive) || BUDGET.take(particlesPerTick);
	}

	public static boolean allowParticle(ParticleOptions options, double x, double y, double z) {
		var camera = Minecraft.getInstance().getCameraEntity();
		if (farParticles && camera != null && camera.distanceToSqr(x, y, z) > particleDist2) return false;
		return allowParticle(options);
	}

	/** Block break and mining crumbs. */
	public static boolean hideBreakParticles() {
		return breaking;
	}

	/** Minecraft's frame-rate limit with SkyPilot's caps. */
	public static int fpsLimit(int vanilla) {
		vanilla = Afk.fpsLimit(vanilla);
		if (!background && !menu) return vanilla;
		Minecraft mc = Minecraft.getInstance();
		boolean menuOpen = mc.level != null && mc.gui.screen() != null;
		return PerfRules.fpsLimit(vanilla, mc.isWindowActive(), menuOpen, background ? bgFps : 0, menu ? menuFps : 0);
	}
}
