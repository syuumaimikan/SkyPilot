package dev.skypilot.mod.platform.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import dev.skypilot.mod.platform.SkinPixels;
import net.minecraft.client.renderer.texture.SkinTextureDownloader;
import net.minecraft.core.ClientAsset;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.concurrent.CompletableFuture;

@Mixin(SkinTextureDownloader.class)
abstract class SkinTextureMixin {
    @Inject(method="registerTextureInManager",at=@At("HEAD"))
    private void skypilot$rememberPixels(ClientAsset.Texture texture,NativeImage image,CallbackInfoReturnable<CompletableFuture<ClientAsset.Texture>> cir){
        SkinPixels.capture(texture,image);
    }
}
