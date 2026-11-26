package dev.skypilot.mod.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WardrobeSearchTest {
    @Test
    void kanaFoldingMultiKeywordAndSemanticSearchWorkCleanly() {
        var a = new SkyPilotConfig.Appearance();
        // Hiragana matches Katakana (うーぱー -> ウーパールーパー)
        var axolotls = WardrobeSearch.filterAndSort(
                CosmeticCatalog.ITEMS, a, "all", "all", false, false, false, "うーぱー", WardrobeSearch.SortOrder.DEFAULT);
        assertTrue(axolotls.stream().anyMatch(i -> i.id().equals("pet_axolotl")), "Expected hiragana 'うーぱー' to match pet_axolotl");

        // Multi-keyword AND with full-width Japanese space
        var sakuraWings = WardrobeSearch.filterAndSort(
                CosmeticCatalog.ITEMS, a, "all", "all", false, false, false, "桜　翼", WardrobeSearch.SortOrder.DEFAULT);
        assertFalse(sakuraWings.isEmpty());
        assertTrue(sakuraWings.stream().allMatch(i -> i.slot().equals("back")));

        // Exact Eclipse query must remain tightly scoped (preserves WardrobePageTest!)
        var eclipseOnly = WardrobeSearch.filterAndSort(
                CosmeticCatalog.ITEMS, a, "all", "all", false, false, false, "EcLiPsE", WardrobeSearch.SortOrder.DEFAULT);
        assertEquals(List.of("eclipse"), eclipseOnly.stream().map(CosmeticCatalog.Item::id).toList());

        // Rarity filter
        var mythics = WardrobeSearch.filterAndSort(
                CosmeticCatalog.ITEMS, a, "all", "mythic", false, false, false, "", WardrobeSearch.SortOrder.RARITY_DESC);
        assertFalse(mythics.isEmpty());
        assertTrue(mythics.stream().allMatch(i -> "mythic".equals(i.rarity())));
    }

    @Test
    void frameIndexHandlesEpoch2026TimestampsWithoutIntOverflow() {
        // Year 2026 epoch seconds (~1.7956e9): 1.7956e9 * 8 = 1.436e10 > Integer.MAX_VALUE!
        double epoch2026 = 1_795_600_000.0;
        int fps = 8;
        int frames = 10;
        int first = CosmeticAnimation.frameIndex(epoch2026, fps, frames);
        int distinct = 0;
        boolean[] seen = new boolean[frames];
        for (int step = 0; step < frames; step++) {
            int f = CosmeticAnimation.frameIndex(epoch2026 + step * (1.0 / fps) + 0.001, fps, frames);
            assertTrue(f >= 0 && f < frames);
            if (!seen[f]) {
                seen[f] = true;
                distinct++;
            }
        }
        assertEquals(frames, distinct, "Animated cloak must cycle through all frames even at 2026 epoch time");
        assertNotEquals(first, CosmeticAnimation.frameIndex(epoch2026 + 0.25, fps, frames));
    }

    @Test
    void slotTransformsAndGranularFirstPersonPersistThroughSanitizeAndStore(@TempDir Path dir) throws Exception {
        var cfg = new SkyPilotConfig();
        var tr = cfg.appearance.editTransform("aura");
        tr.offsetX = 12.5; // clamped to 8.0
        tr.offsetY = -2.5;
        tr.scale = 1.35;
        tr.rotX = 25.0;
        tr.rotY = 90.0;
        tr.rotZ = -15.0;
        cfg.appearance.firstPersonAura = true;
        cfg.appearance.firstPersonWings = true;
        cfg.appearance.firstPersonCape = false;
        cfg.appearance.firstPersonHat = true;
        cfg.appearance.showOtherPlayers = true;
        cfg.appearance.shareWithOthers = true;
        cfg.appearance.wardrobeSyncUrl = "http://localhost:8080///";

        WardrobeTools.remember(cfg.appearance, "Custom Fit");
        cfg.sanitize();

        assertEquals(8.0, cfg.appearance.transformOf("aura").offsetX, 1e-6);
        assertEquals(1.35, cfg.appearance.transformOf("aura").scale, 1e-6);
        assertEquals("http://localhost:8080", cfg.appearance.wardrobeSyncUrl);
        assertTrue(cfg.appearance.firstPersonFor("aura"));
        assertTrue(cfg.appearance.firstPersonFor("back"));
        assertFalse(cfg.appearance.firstPersonFor("cape"));
        assertTrue(cfg.appearance.firstPersonFor("hat"));

        // Save & reload via standalone WardrobeConfigStore
        var store = new WardrobeConfigStore(dir);
        store.save(cfg.appearance);
        var reloaded = store.load();
        assertEquals(8.0, reloaded.transformOf("aura").offsetX, 1e-6);
        assertEquals(90.0, reloaded.transformOf("aura").rotY, 1e-6);
        assertTrue(reloaded.firstPersonAura);
        assertTrue(reloaded.firstPersonWings);
        assertEquals(1, reloaded.outfits.size());
        assertEquals(1.35, reloaded.outfits.get(0).slotTransforms.get("aura").scale, 1e-6);
    }
}

