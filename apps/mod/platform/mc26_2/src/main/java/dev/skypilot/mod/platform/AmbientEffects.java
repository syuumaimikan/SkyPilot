package dev.skypilot.mod.platform;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.LeavesBlock;

/** Nearby, client-only accents. Never scans an area or reads unloaded terrain. */
final class AmbientEffects {
    private static boolean grounded;
    private static int ticks;
    private static net.minecraft.client.multiplayer.ClientLevel previousLevel;
    static void tick(Minecraft mc) {
        var p = mc.player;
        if (p == null || mc.level == null) { previousLevel = null; ticks = 0; return; }
        if (previousLevel != mc.level) { previousLevel = mc.level; grounded = p.onGround(); ticks = 0; }
        var a = SkyPilot.get().config().visuals;
        boolean landed = !grounded && p.onGround(); grounded = p.onGround();
        if (!mc.isWindowActive() || mc.gui.screen() != null || p.isSpectator()) return;
        if (landed && a.landingDust && !p.isInWater()) {
            for (int i = 0; i < 4 * a.ambientDensity; i++) {
                double angle = i * Math.PI * 2 / (4 * a.ambientDensity);
                mc.level.addParticle(ParticleTypes.POOF, p.getX() + Math.cos(angle)*.4, p.getY()+.05, p.getZ()+Math.sin(angle)*.4, Math.cos(angle)*.025,.02,Math.sin(angle)*.025);
            }
        }
        if (++ticks % 5 != 0) return;
        if (a.sprintTrail && p.isSprinting() && p.onGround() && !p.isInWater())
            mc.level.addParticle(ParticleTypes.CLOUD,p.getX(),p.getY()+.1,p.getZ(),0,.005,0);
        if (!(a.rainSplash || a.fireflies || a.caveDust || a.underwaterBubbles)) return;
        var random = java.util.concurrent.ThreadLocalRandom.current();
        for (int i = 0; i < a.ambientDensity; i++) {
            double x=p.getX()+(random.nextDouble()-.5)*10, z=p.getZ()+(random.nextDouble()-.5)*10, y=p.getY()+random.nextDouble()*3;
            BlockPos pos=BlockPos.containing(x,y,z);
            if (!mc.level.hasChunkAt(pos)) continue;
            if (a.underwaterBubbles && p.isUnderWater() && mc.level.getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER))
                mc.level.addParticle(ParticleTypes.BUBBLE,x,y,z,0,.015,0);
            if (a.caveDust && !p.isInWater() && !mc.level.canSeeSky(pos) && mc.level.getBlockState(pos).isAir())
                mc.level.addParticle(ParticleTypes.ASH,x,y,z,0,-.003,0);
            long time=Math.floorMod(mc.level.getDefaultClockTime(),24000);
            if (a.fireflies && time>13000 && time<23000 && !mc.level.isRaining() && mc.level.getBlockState(pos).isAir()) {
                boolean foliage = mc.level.getBlockState(pos.below()).getBlock() instanceof LeavesBlock
                        || mc.level.getBlockState(pos.above()).getBlock() instanceof LeavesBlock;
                if (foliage) mc.level.addParticle(ParticleTypes.FIREFLY,x,y,z,0,.002,0);
            }
            BlockPos floor=BlockPos.containing(x,p.getY()-.2,z);
            if (a.rainSplash && mc.level.isRainingAt(floor.above()) && mc.level.getBlockState(floor).isSolidRender() && mc.level.getBlockState(floor.above()).isAir())
                mc.level.addParticle(ParticleTypes.SPLASH,x,floor.getY()+1.03,z,0,0,0);
        }
    }
}
