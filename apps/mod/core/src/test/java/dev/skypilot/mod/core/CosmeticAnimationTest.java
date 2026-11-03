package dev.skypilot.mod.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CosmeticAnimationTest {
    @Test
    void capePitchReactsToMovementAndSneak() {
        double idlePitch = CosmeticAnimation.capePitch(0.0, 15, 5, 0.0, false, 0.0);
        double movingPitch = CosmeticAnimation.capePitch(0.0, 15, 5, 0.3, false, 0.0);
        double sneakingPitch = CosmeticAnimation.capePitch(0.0, 15, 5, 0.0, true, 0.0);
        double fallingPitch = CosmeticAnimation.capePitch(0.0, 15, 5, 0.0, false, -0.8);

        assertTrue(idlePitch > 0, "Idle cape should hang with slight base pitch");
        assertTrue(movingPitch > idlePitch, "Moving player should induce aerodynamic cape lift");
        assertTrue(sneakingPitch > idlePitch, "Sneaking player should tilt cape away from legs");
        assertTrue(fallingPitch > idlePitch, "Falling player should experience upward air resistance");

        for (int r = 0; r < 16; r++) {
            for (int c = 0; c < 10; c++) {
                double p = CosmeticAnimation.capePitch(1.5, r, c, 0.25, false, 0.0);
                assertTrue(Double.isFinite(p));
                assertTrue(p >= -Math.PI && p <= Math.PI);
            }
        }
    }

    @Test
    void capeRollAndWaveZAreFiniteAndBounded() {
        for (double t = 0.0; t < 10.0; t += 0.5) {
            double roll = CosmeticAnimation.capeRoll(t, 15, 5, 0.2);
            double waveZ = CosmeticAnimation.capeWaveZ(t, 15, 5, 0.2);
            assertTrue(Double.isFinite(roll));
            assertTrue(Double.isFinite(waveZ));
            assertTrue(Math.abs(roll) < Math.PI / 2.0);
            assertTrue(Math.abs(waveZ) < 0.2, "Wave deflection should remain within subtle cloth bounds");
        }
    }

    @Test
    void animatedColorsAreValidAcrossAllCosmetics() {
        for (var item : CosmeticCatalog.ITEMS) {
            for (double t = 0.0; t < 5.0; t += 1.0) {
                int col = CosmeticAnimation.animatedColor(item.id(), item.color(), 5, 5, t);
                assertTrue(col >= 0 && col <= 0xFFFFFF, "Color should be valid 24-bit RGB");
            }
        }
    }

    @Test
    void haloAndWingPhysicsAreSmoothAndBounded() {
        for (double t = 0.0; t < 10.0; t += 0.25) {
            double bob = CosmeticAnimation.haloBob(t);
            double spin = CosmeticAnimation.haloSpin(t, false);
            double twitchL = CosmeticAnimation.earTwitch(t, -1);
            double twitchR = CosmeticAnimation.earTwitch(t, 1);
            double idleFlap = CosmeticAnimation.wingFlap(t, 0.0, false, false);
            double sprintFlap = CosmeticAnimation.wingFlap(t, 0.35, false, false);
            double glideFlap = CosmeticAnimation.wingFlap(t, 0.0, true, false);

            assertTrue(Math.abs(bob) <= 0.05);
            assertTrue(Double.isFinite(spin));
            assertTrue(Double.isFinite(twitchL));
            assertTrue(Double.isFinite(twitchR));
            assertTrue(Math.abs(idleFlap) < Math.PI / 2.0);
            assertTrue(Math.abs(sprintFlap) < Math.PI / 2.0);
            assertTrue(glideFlap >= Math.toRadians(20.0), "Glide posture should extend wings wide");
        }
    }
}
