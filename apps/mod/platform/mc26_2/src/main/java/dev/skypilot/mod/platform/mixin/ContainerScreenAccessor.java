package dev.skypilot.mod.platform.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Where a container window sits, so SkyPilot's panel can be drawn beside it (read only). */
@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenAccessor {
	@Accessor("leftPos")
	int skypilot$leftPos();

	@Accessor("topPos")
	int skypilot$topPos();

	@Accessor("imageWidth")
	int skypilot$imageWidth();

	@Accessor("imageHeight")
	int skypilot$imageHeight();

	@Accessor("hoveredSlot")
	net.minecraft.world.inventory.Slot skypilot$hoveredSlot();
}
