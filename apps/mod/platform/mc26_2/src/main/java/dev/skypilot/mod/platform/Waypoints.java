package dev.skypilot.mod.platform;

import dev.skypilot.mod.core.Islands;
import dev.skypilot.mod.core.Lang;
import dev.skypilot.mod.network.Dto;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;

/**
 * Guide waypoints: a box and beam at a fixed public place (NPC, area, warp from SkyHanni-REPO),
 * its name and distance, and a straight guide line from the player. Drawn only on the island
 * the place belongs to. It points the way; the player walks - nothing is pathfound, nothing
 * moves the player.
 */
final class Waypoints {
	private static final int COLOR = 0xFFB07CFF;
	private static final double ARRIVE = 4.0;
	private static Dto.Waypoint target;
	private static Dto.Guide guide;
	private static int stepIndex = -1;
	private static boolean arrived;
	/** Tests run in a world without Hypixel's tab list. */
	private static String islandOverride;

	private Waypoints() {
	}

	static Dto.Waypoint target() {
		return target;
	}

	static Dto.Guide guide() {
		return guide;
	}

	static int stepIndex() {
		return stepIndex;
	}

	static void set(Dto.Waypoint w) {
		target = w;
		arrived = false;
	}

	/** Follow a guide from step {@code index} (its waypoint becomes the target). */
	static void follow(Dto.Guide g, int index) {
		guide = g;
		stepIndex = Math.max(0, Math.min(index, g.steps().size() - 1));
		set(g.steps().get(stepIndex).waypoint());
		Lang l = SkyPilot.get().lang();
		Dto.GuideStep s = g.steps().get(stepIndex);
		McText.chat(McText.prefix().append(Component.literal(l.t("Guide ", "ガイド ") + (stepIndex + 1) + "/" + g.steps().size() + ": ").withStyle(ChatFormatting.GOLD))
				.append(Component.literal(s.title()).withStyle(ChatFormatting.WHITE)));
		McText.chat(Component.literal("  " + s.text()).withStyle(ChatFormatting.GRAY));
	}

	static void next() {
		if (guide != null && stepIndex + 1 < guide.steps().size()) follow(guide, stepIndex + 1);
	}

	static void prev() {
		if (guide != null && stepIndex > 0) follow(guide, stepIndex - 1);
	}

	static void clear() {
		target = null;
		guide = null;
		stepIndex = -1;
	}

	static void setIslandOverride(String island) {
		islandOverride = island;
	}

	static String currentIsland() {
		if (islandOverride != null) return islandOverride;
		return SkyPilot.get().context().islandId();
	}

	static boolean onTargetIsland() {
		return target != null && ("*".equals(target.island()) || target.island().equals(currentIsland()));
	}

	/** {@code LevelRenderEvents.BEFORE_GIZMOS}: collect this frame's gizmos. */
	@SuppressWarnings("try")
	static void render() {
		Minecraft mc = Minecraft.getInstance();
		if (target == null || mc.player == null || !onTargetIsland()) return;
		try (var collection = mc.levelRenderer.collectPerFrameRenderThreadGizmos()) {
			draw(mc);
		}
	}

	private static void draw(Minecraft mc) {
		Vec3 eye = mc.player.getEyePosition();
		Vec3 base = new Vec3(target.x(), target.y(), target.z());
		double dist = eye.distanceTo(base);
		if (!arrived && dist < ARRIVE) {
			arrived = true;
			Lang l = SkyPilot.get().lang();
			McText.info(l.t("Arrived: ", "到着: ") + target.name() + (guide != null && stepIndex + 1 < guide.steps().size() ? l.t(" - /skypilot guide next", " - 次へ: /skypilot guide next") : ""));
		}
		// box + beam at the place
		Gizmos.cuboid(new AABB(base.x - 0.5, base.y, base.z - 0.5, base.x + 0.5, base.y + 2, base.z + 0.5), GizmoStyle.strokeAndFill(COLOR, 2f, 0x40B07CFF)).setAlwaysOnTop();
		Gizmos.cuboid(new AABB(base.x - 0.12, base.y + 2, base.z - 0.12, base.x + 0.12, base.y + 40, base.z + 0.12), GizmoStyle.fill(0x55B07CFF));
		Gizmos.billboardText(target.name() + "  " + String.format(Locale.ROOT, "%.0fm", dist), base.add(0, 2.6, 0), TextGizmo.Style.forColorAndCentered(0xFFFFFFFF)).setAlwaysOnTop();
		// straight guide line from just in front of the player (not a path - walk it yourself)
		if (dist > ARRIVE) {
			Vec3 dir = base.add(0, 1, 0).subtract(eye).normalize();
			Vec3 from = eye.add(0, -0.6, 0).add(dir.scale(1.5));
			Vec3 to = dist > 96 ? from.add(dir.scale(94)) : base.add(0, 1, 0);
			Gizmos.line(from, to, COLOR, 3f).setAlwaysOnTop();
		}
	}

	/** Small HUD line: target, distance and direction, or which island it is on. */
	static void hud(GuiGraphicsExtractor g, Font font, int screenWidth) {
		Minecraft mc = Minecraft.getInstance();
		if (target == null || mc.player == null) return;
		Lang l = SkyPilot.get().lang();
		String text;
		if (onTargetIsland()) {
			Vec3 p = mc.player.position();
			double dx = target.x() - p.x;
			double dz = target.z() - p.z;
			double dist = Math.sqrt(dx * dx + dz * dz + Math.pow(target.y() - p.y, 2));
			text = "➜ " + target.name() + "  " + String.format(Locale.ROOT, "%.0fm", dist) + " " + arrow(mc.player.getYRot(), dx, dz) + heightHint(target.y() - p.y, l);
		} else {
			text = "➜ " + target.name() + " · " + l.t("on ", "") + Islands.name(target.island(), l) + l.t("", "にあります");
		}
		if (guide != null) text = "[" + (stepIndex + 1) + "/" + guide.steps().size() + "] " + text;
		int w = font.width(text) + 10;
		int x = screenWidth / 2 - w / 2;
		g.fill(x, 2, x + w, 15, 0xA0101018);
		g.text(font, text, x + 5, 5, 0xFFE0D0FF, false);
	}

	private static String heightHint(double dy, Lang l) {
		if (dy > 6) return l.t(" (above)", "（上）");
		if (dy < -6) return l.t(" (below)", "（下）");
		return "";
	}

	/** Arrow for the target direction relative to where the player looks. */
	static String arrow(float yaw, double dx, double dz) {
		double target = Math.toDegrees(Math.atan2(-dx, dz));
		double rel = ((target - yaw) % 360 + 540) % 360 - 180;
		String[] arrows = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
		int i = (int) Math.floorMod(Math.round(rel / 45.0), 8L);
		return arrows[i];
	}

	/** For tests. */
	static List<Dto.GuideStep> steps() {
		return guide == null ? List.of() : guide.steps();
	}
}
