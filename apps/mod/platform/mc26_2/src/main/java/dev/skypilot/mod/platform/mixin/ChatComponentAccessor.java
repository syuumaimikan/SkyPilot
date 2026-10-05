package dev.skypilot.mod.platform.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

/**
 * Read access to the chat history so click-to-translate can show a line's translation in
 * place of the original (and switch back). Accessors only - no vanilla behaviour is changed.
 */
@Mixin(ChatComponent.class)
public interface ChatComponentAccessor {
	@Accessor("allMessages")
	List<GuiMessage> skypilot$allMessages();

	/** Re-wraps the visible lines from the history without resetting the scroll position. */
	@Invoker("refreshTrimmedMessages")
	void skypilot$refreshTrimmedMessages();
}
