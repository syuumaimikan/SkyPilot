package dev.skypilot.mod.platform;

import dev.skypilot.mod.platform.mixin.PlayerTabOverlayAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Reads the sidebar scoreboard and tab list exactly as the vanilla client displays them. */
final class Sidebar {
	private Sidebar() {
	}

	static String title(Minecraft mc) {
		Objective o = objective(mc);
		return o == null ? "" : o.getDisplayName().getString();
	}

	static List<String> lines(Minecraft mc) {
		Objective o = objective(mc);
		if (o == null) return List.of();
		Scoreboard sb = mc.level.getScoreboard();
		List<String> out = new ArrayList<>();
		sb.listPlayerScores(o).stream()
				.filter(e -> !e.isHidden())
				.sorted(Comparator.comparingInt(PlayerScoreEntry::value).reversed())
				.limit(15)
				.forEach(e -> {
					PlayerTeam team = sb.getPlayersTeam(e.owner());
					Component name = e.display() != null ? e.display() : PlayerTeam.formatNameForTeam(team, e.ownerName());
					out.add(name.getString());
				});
		return out;
	}

	/** Tab list cells in the order the tab list draws them (column by column, top to bottom). */
	static List<String> tabLines(Minecraft mc) {
		ClientPacketListener conn = mc.getConnection();
		if (conn == null) return List.of();
		List<String> out = new ArrayList<>();
		for (PlayerInfo info : ((PlayerTabOverlayAccessor) mc.gui.hud.getTabList()).skypilot$playerInfos()) {
			Component c = info.getTabListDisplayName();
			if (c != null) out.add(c.getString());
			if (out.size() > 80) break;
		}
		return out;
	}

	/** Names of players in the tab list (protected from translation). */
	static List<String> playerNames(Minecraft mc) {
		ClientPacketListener conn = mc.getConnection();
		if (conn == null) return List.of();
		List<String> out = new ArrayList<>();
		for (PlayerInfo info : conn.getOnlinePlayers()) {
			String n = info.getProfile().name();
			if (n != null && n.matches("[A-Za-z0-9_]{3,16}")) out.add(n);
			if (out.size() > 120) break;
		}
		return out;
	}

	private static Objective objective(Minecraft mc) {
		if (mc.level == null) return null;
		return mc.level.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
	}
}
