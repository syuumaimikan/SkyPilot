package dev.skypilot.mod.platform.mixin;

import dev.skypilot.mod.platform.SlotDecor;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws SkyPilot's slot decorations (rarity background, small numbers, search dimming) around the
 * vanilla slot drawing. Rendering only: slot handling and clicks are untouched.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class SlotRenderMixin {
	@Inject(method = "extractSlot", at = @At("HEAD"))
	private void skypilot$behindSlot(GuiGraphicsExtractor g, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
		SlotDecor.behind((AbstractContainerScreen<?>) (Object) this, g, slot);
	}

	@Inject(method = "extractSlot", at = @At("TAIL"))
	private void skypilot$overSlot(GuiGraphicsExtractor g, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
		SlotDecor.over((AbstractContainerScreen<?>) (Object) this, g, slot);
	}
}
