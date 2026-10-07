package dev.skypilot.mod.platform.mixin;
import dev.skypilot.mod.platform.VisualEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LivingEntity.class)
abstract class CombatEffectsMixin {@Inject(method="handleDamageEvent",at=@At("HEAD")) private void skypilot$hit(DamageSource damage,CallbackInfo ci){VisualEffects.hurt((LivingEntity)(Object)this,damage);}}
