package dev.skypilot.wardrobe;

import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player stateful aerodynamic cloth physics for flowing wind-reactive capes (both local
 * player and synced remote players!). Tracks horizontal speed, vertical fall drag, and yaw turn inertia.
 */
final class CapePhysics {
    static final class State {
        double prevYaw;
        double yawTurnRate;
        double smoothedSpeed = 0.04;
        double smoothedVertSpeed;
        long lastTimeMs;
        boolean initialized;
    }

    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();
    private static final State STUDIO = new State();
    private static long lastPruneMs;

    private CapePhysics() {}

    static State update(Player player) {
        if (player == null) {
            STUDIO.smoothedSpeed = 0.04;
            STUDIO.smoothedVertSpeed = 0.0;
            STUDIO.yawTurnRate = 0.0;
            return STUDIO;
        }
        long now = System.currentTimeMillis();
        if (now - lastPruneMs > 15_000) {
            lastPruneMs = now;
            STATES.entrySet().removeIf(e -> now - e.getValue().lastTimeMs > 20_000);
        }

        State st = STATES.computeIfAbsent(player.getUUID(), k -> new State());
        double dt = st.lastTimeMs > 0 ? (now - st.lastTimeMs) / 1000.0 : 0.05;
        st.lastTimeMs = now;
        if (dt <= 0.001 || dt > 0.5) dt = 0.05;

        var vel = player.getDeltaMovement();
        // Remote players often have near-zero deltaMovement on client; derive horizontal velocity from position delta!
        double dx = player.getX() - player.xOld;
        double dy = player.getY() - player.yOld;
        double dz = player.getZ() - player.zOld;
        double posSpeed = Math.hypot(dx, dz);
        double velSpeed = Math.hypot(vel.x, vel.z);
        double targetSpeed = Math.max(velSpeed, posSpeed);
        double targetVert = Math.abs(vel.y) > Math.abs(dy) ? vel.y : dy;

        st.smoothedSpeed += (targetSpeed - st.smoothedSpeed) * Math.min(1.0, dt * 9.0);
        st.smoothedVertSpeed += (targetVert - st.smoothedVertSpeed) * Math.min(1.0, dt * 7.0);

        double currentYaw = player.yBodyRot;
        if (!st.initialized) {
            st.prevYaw = currentYaw;
            st.initialized = true;
        }
        double dyaw = Math.IEEEremainder(currentYaw - st.prevYaw, 360.0);
        st.prevYaw = currentYaw;
        st.yawTurnRate += (dyaw / dt * (Math.PI / 180.0) - st.yawTurnRate) * Math.min(1.0, dt * 6.0);
        return st;
    }

    static State peek(UUID id) {
        if (id == null) return STUDIO;
        return STATES.getOrDefault(id, STUDIO);
    }

    static void clear() {
        STATES.clear();
    }
}

