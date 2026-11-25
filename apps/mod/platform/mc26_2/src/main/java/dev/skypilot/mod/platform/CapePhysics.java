package dev.skypilot.mod.platform;

import dev.skypilot.mod.core.CosmeticAnimation;
import net.minecraft.world.entity.player.Player;

/**
 * Stateful physics simulation for flowing, wind-reactive capes.
 * Tracks player velocity, aerodynamic drag, sneak angle offsets, and centrifugal turning roll.
 */
final class CapePhysics {
    private static double prevYaw;
    private static double yawTurnRate;
    private static double smoothedSpeed;
    private static double smoothedVertSpeed;
    private static long lastTimeMs;

    private CapePhysics() {}

    /**
     * Updates physics state for the active player.
     * In the wardrobe preview studio, sets realistic ambient wind parameters.
     */
    static void update(Player player) {
        long now = System.currentTimeMillis();
        double dt = lastTimeMs > 0 ? (now - lastTimeMs) / 1000.0 : 0.05;
        lastTimeMs = now;
        if (dt <= 0 || dt > 0.5) dt = 0.05;

        if (player != null) {
            var vel = player.getDeltaMovement();
            double targetSpeed = Math.hypot(vel.x, vel.z);
            double targetVert = vel.y;

            // Exponential moving average for smooth cloth inertia
            smoothedSpeed += (targetSpeed - smoothedSpeed) * Math.min(1.0, dt * 9.0);
            smoothedVertSpeed += (targetVert - smoothedVertSpeed) * Math.min(1.0, dt * 7.0);

            double currentYaw = player.getYRot();
            double dyaw = Math.IEEEremainder(currentYaw - prevYaw, 360.0);
            prevYaw = currentYaw;
            yawTurnRate += (dyaw / dt * (Math.PI / 180.0) - yawTurnRate) * Math.min(1.0, dt * 6.0);
        } else {
            // Studio mannequin preview: gentle studio breeze
            smoothedSpeed = 0.04;
            smoothedVertSpeed = 0.0;
            yawTurnRate = 0.0;
        }
    }

    /**
     * Pitch angle for root segment 0 at shoulders (radians).
     */
    static float getSegmentPitch(double time, int segment, boolean sneaking) {
        int row = segment * 4 + 2;
        return (float) CosmeticAnimation.capePitch(time, row, 5, smoothedSpeed, sneaking, smoothedVertSpeed);
    }

    /**
     * Incremental angle for chained segments 1..3 relative to the preceding segment.
     */
    static float getDeltaPitch(double time, int segment, boolean sneaking) {
        double pPrev = CosmeticAnimation.capePitch(time, (segment - 1) * 4 + 2, 5, smoothedSpeed, sneaking, smoothedVertSpeed);
        double pCurr = CosmeticAnimation.capePitch(time, segment * 4 + 2, 5, smoothedSpeed, sneaking, smoothedVertSpeed);
        return (float) (pCurr - pPrev);
    }

    /**
     * Lateral roll angle for segment (radians).
     */
    static float getSegmentRoll(double time, int segment) {
        int row = segment * 4 + 2;
        return (float) CosmeticAnimation.capeRoll(time, row, 5, yawTurnRate);
    }

    static double getSmoothedSpeed() {
        return smoothedSpeed;
    }

    static double getSmoothedVertSpeed() {
        return smoothedVertSpeed;
    }

    static double getYawTurnRate() {
        return yawTurnRate;
    }
}
