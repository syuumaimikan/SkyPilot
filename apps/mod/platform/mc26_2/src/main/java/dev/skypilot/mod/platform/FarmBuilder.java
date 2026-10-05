package dev.skypilot.mod.platform;

import dev.skypilot.mod.core.Lang;
import dev.skypilot.mod.network.Dto;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Farm build guide in the world: the chosen SkyPilot farm design is anchored at the block the
 * player stood on (rotated to where they faced) and the current layer's blocks that are still
 * missing or wrong are outlined, coloured by block type. It reads only blocks the player can
 * see and never places anything.
 */
final class FarmBuilder {
	private static final int RADIUS = 28;
	private static final int MAX_BOXES = 1500;
	private static Dto.FarmDesign design;
	private static BlockPos origin;
	private static Direction facing = Direction.SOUTH;
	private static int layerIndex;
	private static int remaining;
	private static int layerTotal;

	private FarmBuilder() {
	}

	static boolean active() {
		return design != null && origin != null;
	}

	static Dto.FarmDesign design() {
		return design;
	}

	/** Anchor the design at the player's feet, facing where they look. */
	static void start(Dto.FarmDesign d) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		start(d, mc.player.blockPosition(), mc.player.getDirection());
	}

	static void start(Dto.FarmDesign d, BlockPos at, Direction face) {
		design = d;
		origin = at;
		facing = face.getAxis().isHorizontal() ? face : Direction.SOUTH;
		layerIndex = 0;
		missing = List.of();
		ticks = 4;
		Lang l = SkyPilot.get().lang();
		McText.chat(McText.prefix().append(Component.literal(l.t("Farm guide: ", "ファーム建築ガイド: ") + d.summary().name()).withStyle(ChatFormatting.GOLD)));
		McText.chat(Component.literal(l.t("  Anchored where you stand, extending forward and to the right. Next layer: /skypilot farm next · stop: /skypilot farm stop",
				"  今立っている位置から前方・右方向に配置します。次の層: /skypilot farm next · 終了: /skypilot farm stop")).withStyle(ChatFormatting.GRAY));
	}

	static void stop() {
		design = null;
		origin = null;
	}

	static void next() {
		if (design != null && layerIndex + 1 < design.layers().size()) {
			layerIndex++;
			missing = List.of();
			ticks = 4;
		}
	}

	static void prev() {
		if (design != null && layerIndex > 0) {
			layerIndex--;
			missing = List.of();
			ticks = 4;
		}
	}

	static int layer() {
		return design == null || design.layers().isEmpty() ? 0 : design.layers().get(Math.min(layerIndex, design.layers().size() - 1));
	}

	/** Design (x, y, z) → world position: design +z goes "forward", +x goes "right". */
	static BlockPos world(int x, int y, int z) {
		Direction right = facing.getClockWise();
		return origin.offset(facing.getStepX() * z + right.getStepX() * x, y, facing.getStepZ() * z + right.getStepZ() * x);
	}

	static int colorOf(String block) {
		return switch (block) {
			case "water" -> 0xFF3F76E4;
			case "sand", "red_sand" -> 0xFFE0D8A0;
			case "farmland", "dirt", "soul_sand", "mycelium", "podzol" -> 0xFF8B5A2B;
			case "glass", "glass_pane" -> 0xFFB0E0FF;
			case "jungle_log" -> 0xFF6B4F2A;
			default -> block.contains("stone") || block.contains("brick") ? 0xFF9A9A9A : 0xFF55FF55;
		};
	}

	/** True when the world already has the block (by id; "water" also accepts flowing water). */
	static boolean placed(BlockState state, String block) {
		String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		return id.equals(block) || (block.equals("water") && state.getFluidState().isSource());
	}

	private static List<BlockPos> missing = List.of();
	private static Map<BlockPos, String> wanted = Map.of();
	private static int ticks;

	/** Every 5 client ticks: which blocks of the current layer are still missing (nearest first). */
	static void tick(Minecraft mc) {
		if (!active() || mc.player == null || mc.level == null || ++ticks % 5 != 0) return;
		int y = layer();
		BlockPos me = mc.player.blockPosition();
		List<BlockPos> list = new ArrayList<>();
		Map<BlockPos, String> want = new HashMap<>();
		int total = 0;
		for (Dto.FarmBlock b : design.blocks()) {
			if (b.y() != y) continue;
			total++;
			BlockPos p = world(b.x(), b.y(), b.z());
			if (placed(mc.level.getBlockState(p), b.block())) continue;
			list.add(p);
			want.put(p, b.block());
		}
		list.sort((a, b) -> Double.compare(a.distSqr(me), b.distSqr(me)));
		layerTotal = total;
		remaining = list.size();
		missing = list;
		wanted = want;
	}

	/** {@code LevelRenderEvents.BEFORE_GIZMOS}. */
	@SuppressWarnings("try")
	static void render() {
		Minecraft mc = Minecraft.getInstance();
		if (!active() || mc.player == null) return;
		try (var collection = mc.levelRenderer.collectPerFrameRenderThreadGizmos()) {
			BlockPos me = mc.player.blockPosition();
			int drawn = 0;
			for (BlockPos p : missing) {
				if (drawn >= MAX_BOXES || p.distSqr(me) > RADIUS * RADIUS) continue;
				int c = colorOf(wanted.getOrDefault(p, ""));
				Gizmos.cuboid(new AABB(p).deflate(0.04), GizmoStyle.strokeAndFill(c, 1.5f, (c & 0xFFFFFF) | 0x30000000));
				drawn++;
			}
			// origin marker
			Gizmos.cuboid(new AABB(origin).inflate(0.06), GizmoStyle.stroke(0xFFFF5555, 3f));
		}
	}

	/** HUD line: layer, blocks still to place and the step text. */
	static void hud(GuiGraphicsExtractor g, Font font, int screenWidth, int y) {
		if (!active()) return;
		Lang l = SkyPilot.get().lang();
		String text = l.t("Farm ", "ファーム ") + design.summary().name() + " · " + l.t("layer Y+", "Y+") + layer() + l.t("", "層") + " (" + (layerIndex + 1) + "/" + design.layers().size() + ") · "
				+ l.t("left ", "残り ") + remaining + "/" + layerTotal;
		int w = font.width(text) + 10;
		int x = screenWidth / 2 - w / 2;
		g.fill(x, y, x + w, y + 13, 0xA0101018);
		g.text(font, text, x + 5, y + 3, remaining == 0 ? 0xFF55FF55 : 0xFFFFE08A, false);
	}

	/** For tests. */
	static int remaining() {
		return remaining;
	}
}
