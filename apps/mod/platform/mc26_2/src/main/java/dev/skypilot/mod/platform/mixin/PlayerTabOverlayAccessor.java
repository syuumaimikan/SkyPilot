package dev.skypilot.mod.platform.mixin;

import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

/** The tab list entries in the order the vanilla tab list draws them (read only). */
@Mixin(PlayerTabOverlay.class)
public interface PlayerTabOverlayAccessor {
	@Invoker("getPlayerInfos")
	List<PlayerInfo> skypilot$playerInfos();
}
