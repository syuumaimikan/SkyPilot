package dev.skypilot.mod.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class WardrobeVfxTest {
    @Test
    void allBuiltinVfxThemesHaveTexturesAndCleanConfigSanitization(@TempDir Path dir) throws Exception {
        assertTrue(WardrobeVfxCatalog.BUILTIN.size() >= 18);
        assertEquals(7, WardrobeVfxCatalog.EVENT_SLOTS.size());

        for (var style : WardrobeVfxCatalog.BUILTIN) {
            assertTrue(WardrobeVfxCatalog.validId(style.id()));
            String p1 = "/assets/skypilot/textures/vfx/" + style.primaryTex() + ".png";
            String p2 = "/assets/skypilot/textures/vfx/" + style.ringTex() + ".png";
            String p3 = "/assets/skypilot/textures/vfx_preview/" + style.id() + ".png";
            try (var in1 = WardrobeVfxCatalog.class.getResourceAsStream(p1);
                 var in2 = WardrobeVfxCatalog.class.getResourceAsStream(p2);
                 var in3 = WardrobeVfxCatalog.class.getResourceAsStream(p3)) {
                assertNotNull(in1, "Missing custom VFX texture: " + p1);
                assertNotNull(in2, "Missing custom VFX ring texture: " + p2);
                assertNotNull(in3, "Missing studio VFX preview texture: " + p3);
            }
        }

        // Verify shared high-res HDR textures exist too
        for (String extra : new String[]{
                "slash_arc", "slash_core", "magic_sigil_outer", "vortex_spiral",
                "glow_soft", "pillar_beam", "ribbon_trail", "pet_beam"
        }) {
            try (var in = WardrobeVfxCatalog.class.getResourceAsStream("/assets/skypilot/textures/vfx/" + extra + ".png")) {
                assertNotNull(in, "Missing core custom VFX texture: " + extra);
            }
        }

        var cfg = new SkyPilotConfig();
        WardrobeVfxCatalog.equip(cfg.appearance.vfx, "attack", "sakura");
        WardrobeVfxCatalog.equip(cfg.appearance.vfx, "move", "butterfly");
        WardrobeVfxCatalog.equip(cfg.appearance.vfx, "kill", "invalid_garbage");
        cfg.appearance.vfx.scale = 9.0;
        cfg.appearance.vfx.intensity = 99;
        cfg.sanitize();

        assertEquals("sakura", cfg.appearance.vfx.attack);
        assertEquals("butterfly", cfg.appearance.vfx.move);
        assertEquals("none", cfg.appearance.vfx.kill);
        assertEquals(2.0, cfg.appearance.vfx.scale, 1e-6);
        assertEquals(3, cfg.appearance.vfx.intensity);
        assertEquals(2, WardrobeVfxCatalog.activeCount(cfg.appearance.vfx));

        // Round-trip through standalone WardrobeConfigStore
        var store = new WardrobeConfigStore(dir);
        store.save(cfg.appearance);
        var loaded = store.load();
        assertEquals("sakura", loaded.vfx.attack);
        assertEquals("butterfly", loaded.vfx.move);
        assertEquals(2.0, loaded.vfx.scale, 1e-6);

        // Search works in Japanese kana & English
        assertTrue(WardrobeVfxCatalog.search("さくら").stream().anyMatch(s -> s.id().equals("sakura")));
        assertTrue(WardrobeVfxCatalog.search("にくきゅう").stream().anyMatch(s -> s.id().equals("paw")));
    }
}

