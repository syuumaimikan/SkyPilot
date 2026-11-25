package dev.skypilot.mod.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CosmeticCatalogTest {
    @Test
    void collectionHas160PlusUnique3dBbmodelsWithTexturesAndBoundedGeometry() throws Exception {
        var ids = new HashSet<String>();
        assertTrue(CosmeticCatalog.ITEMS.size() >= 160, "Expected 160+ cosmetics, found " + CosmeticCatalog.ITEMS.size());
        for (String slot : CosmeticCatalog.SLOTS) {
            assertTrue(CosmeticCatalog.bySlot(slot).size() >= 20, "Slot " + slot + " should have 20+ items");
        }

        for (var item : CosmeticCatalog.ITEMS) {
            assertTrue(ids.add(item.id()), "Duplicate ID: " + item.id());
            var a = new SkyPilotConfig.Appearance();
            item.equip(a);
            assertTrue(item.equipped(a));

            CosmeticRig.Model rig = CosmeticRig.load(item.id());
            assertNotNull(rig, "Missing .bbmodel for " + item.id());
            assertTrue(rig.totalCubes() > 0 && rig.totalCubes() < 200, "Unexpected cube count for " + item.id() + ": " + rig.totalCubes());
            verifyAntiClipping(item, rig.bones);
            try (var in = rig.openTextureStream()) {
                assertNotNull(in, "Missing texture PNG for " + item.id() + ": " + rig.texture);
                assertTrue(in.readAllBytes().length > 100);
            }
            if (rig.emissiveTexture != null && !rig.emissiveTexture.isEmpty()) {
                try (var ein = rig.openEmissiveStream()) {
                    assertNotNull(ein, "Missing emissive glow PNG for " + item.id());
                    assertTrue(ein.readAllBytes().length > 100);
                }
            }

            var voxels = CosmeticMesh.build(a);
            assertFalse(voxels.isEmpty(), "Empty mesh projection for " + item.id());
            for (var v : voxels) {
                assertTrue(Double.isFinite(v.x() + v.y() + v.z()));
                assertTrue(v.size() > 0 && v.size() < 0.25);
                assertTrue(Math.abs(v.x()) < 3.0 && v.y() >= -0.2 && v.y() < 3.5, "Out of bounds voxel for " + item.id() + ": " + v);
            }
        }

        // Verify 100% unique 3D cube geometry for every non-cape cosmetic (zero reused models!)
        for (String slot : List.of("back", "hat", "aura", "pet", "companion")) {
            var sigs = new HashSet<String>();
            for (var item : CosmeticCatalog.bySlot(slot)) {
                CosmeticRig.Model rig = CosmeticRig.load(item.id());
                StringBuilder sb = new StringBuilder();
                appendGeomSig(rig.bones, sb);
                assertTrue(sigs.add(sb.toString()), "Duplicate 3D geometry found in slot " + slot + " for " + item.id());
            }
        }
    }

    private static void appendGeomSig(List<CosmeticRig.Bone> bones, StringBuilder sb) {
        for (var b : bones) {
            for (var c : b.cubes) {
                sb.append(Math.round(c.from[0] * 100)).append(',').append(Math.round(c.from[1] * 100)).append(',').append(Math.round(c.from[2] * 100)).append(':')
                        .append(Math.round(c.to[0] * 100)).append(',').append(Math.round(c.to[1] * 100)).append(',').append(Math.round(c.to[2] * 100)).append(':')
                        .append(Math.round(c.rot[0])).append(',').append(Math.round(c.rot[1])).append(',').append(Math.round(c.rot[2])).append(';');
            }
            appendGeomSig(b.children, sb);
        }
    }

    private static void verifyAntiClipping(CosmeticCatalog.Item item, List<CosmeticRig.Bone> bones) {
        for (var b : bones) {
            for (var c : b.cubes) {
                switch (item.slot()) {
                    case "cape" -> assertTrue(c.from[2] >= 2.55, item.id() + " cape cube clips back: z0=" + c.from[2]);
                    case "back" -> {
                        double mx = Math.abs((c.from[0] + c.to[0]) * 0.5);
                        if (mx < 5.5 && c.from[1] < 25.0 && c.to[1] > 10.0) {
                            assertTrue(c.from[2] >= 2.95, item.id() + " back cube clips torso: z0=" + c.from[2]);
                        }
                    }
                    case "hat" -> {
                        boolean inHead = c.to[0] > -4.45 && c.from[0] < 4.45
                                && c.to[1] > 23.6 && c.from[1] < 32.45
                                && c.to[2] > -4.45 && c.from[2] < 4.45;
                        assertFalse(inHead, item.id() + " hat cube clips head: " + c.name);
                    }
                    case "companion" -> assertTrue(c.from[1] >= -0.01, item.id() + " companion sinks below ground: y0=" + c.from[1]);
                }
            }
            verifyAntiClipping(item, b.children);
        }
    }

    @Test
    void allSixSlotsCombineAndLegacySettingsMigrateCleanly() {
        var c = new SkyPilotConfig();
        c.appearance.cape = "eclipse";
        c.appearance.hat = "horns";
        c.appearance.accessory = "crown";
        c.appearance.back = "dragon";
        c.appearance.aura = "thunder_cloud_aura";
        c.appearance.pet = "pet_bumblebee";
        c.appearance.companion = "comp_cyber_tiger";
        c.appearance.wingScale = 1.25;

        c.sanitize();
        assertEquals("horns", c.appearance.hat);
        assertEquals("none", c.appearance.accessory);
        assertEquals("thunder_cloud_aura", c.appearance.aura);
        assertEquals("pet_bumblebee", c.appearance.pet);
        assertEquals("comp_cyber_tiger", c.appearance.companion);
        assertEquals(1.25, c.appearance.wingScale, 1e-6);
        assertTrue(c.appearance.firstPersonPet);
        c.appearance.firstPersonPet = false;
        c.sanitize();
        assertFalse(c.appearance.firstPersonPet);
        assertEquals(6, WardrobeTools.equippedCount(c.appearance));

        int combined = CosmeticMesh.build(c.appearance).size();
        c.appearance.companion = "none";
        assertTrue(combined > CosmeticMesh.build(c.appearance).size());

        c.appearance.hat = "none";
        c.appearance.accessory = "halo";
        c.sanitize();
        assertEquals("halo", c.appearance.hat);

        c.appearance.cape = null;
        c.appearance.hat = null;
        c.appearance.back = "invalid";
        c.appearance.aura = "invalid";
        c.appearance.pet = "invalid";
        c.appearance.companion = "invalid";
        c.sanitize();
        assertTrue(CosmeticMesh.build(c.appearance).isEmpty());
    }

    @Test
    void customUserBbmodelAndPngCloakImportExportAndKeyframePlayback(@TempDir Path dir) throws Exception {
        try {
            // 1. Export all 6 Blockbench starter templates
            Path tplDir = CustomCosmetics.exportTemplates(dir);
            for (String slot : CosmeticCatalog.SLOTS) {
                Path tpl = tplDir.resolve("template_" + slot + ".bbmodel");
                assertTrue(Files.isRegularFile(tpl), "Missing template for slot " + slot);
                assertTrue(Files.size(tpl) > 500);
            }

            // 2. Author a standalone custom Blockbench .bbmodel with embedded PNG & keyframe animation
            // Minimal valid 1x1 PNG
            byte[] png1x1 = Base64.getDecoder().decode(
                    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+ip1sAAAAASUVORK5CYII=");
            String b64 = Base64.getEncoder().encodeToString(png1x1);
            String customBb = """
                    {
                      "meta": {"format_version": "4.10", "model_format": "bedrock", "box_uv": false},
                      "name": "My Custom Crown",
                      "resolution": {"width": 16, "height": 16},
                      "elements": [
                        {
                          "name": "gem",
                          "from": [-2, 33, -2],
                          "to": [2, 37, 2],
                          "origin": [0, 35, 0],
                          "rotation": [0, 45, 0],
                          "faces": {
                            "north": {"uv": [0, 0, 16, 16]},
                            "south": {"uv": [0, 0, 16, 16]},
                            "east": {"uv": [0, 0, 16, 16]},
                            "west": {"uv": [0, 0, 16, 16]},
                            "up": {"uv": [0, 0, 16, 16]},
                            "down": {"uv": [0, 0, 16, 16]}
                          },
                          "uuid": "cube-1"
                        }
                      ],
                      "outliner": [
                        {
                          "name": "spin_root",
                          "origin": [0, 35, 0],
                          "rotation": [0, 0, 0],
                          "uuid": "bone-1",
                          "children": ["cube-1"]
                        }
                      ],
                      "textures": [
                        {"name": "custom_tex.png", "source": "data:image/png;base64,%s"}
                      ],
                      "animations": [
                        {
                          "name": "animation.custom.idle",
                          "loop": "loop",
                          "length": 2.0,
                          "animators": {
                            "bone-1": {
                              "name": "spin_root",
                              "keyframes": [
                                {"channel": "rotation", "time": 0.0, "data_points": [{"x": 0, "y": 0, "z": 0}]},
                                {"channel": "rotation", "time": 2.0, "data_points": [{"x": 0, "y": 360, "z": 0}]}
                              ]
                            }
                          }
                        }
                      ]
                    }
                    """.formatted(b64);

            Path srcBb = dir.resolve("my_crown.bbmodel");
            Files.writeString(srcBb, customBb, StandardCharsets.UTF_8);

            var imported = CustomCosmetics.importModel(dir, srcBb, "hat", "My Custom Crown");
            assertNotNull(imported);
            assertTrue(imported.custom());
            assertEquals("hat", imported.slot());
            assertTrue(CosmeticCatalog.valid("hat", imported.id()));

            CosmeticRig.Model rig = CosmeticRig.load(imported.id());
            assertNotNull(rig);
            assertEquals(1, rig.totalCubes());
            assertNotNull(rig.embeddedTextureBytes);
            assertEquals(1, rig.clips.size());
            // Verify keyframe interpolation at t = 1.0 -> y rotation = 180 deg!
            var tr = rig.evaluateBone(rig.bones.get(0), 1.0, 0, false, false, false, 0, 0);
            assertEquals(180.0, tr.ryDeg(), 1e-3);

            // 3. Import a custom PNG cloak
            Path srcPng = dir.resolve("my_cape.png");
            Files.write(srcPng, png1x1);
            var cloak = CustomCosmetics.importModel(dir, srcPng, "cape", "My PNG Cloak");
            assertNotNull(cloak);
            assertEquals("cape", cloak.slot());
            assertNotNull(CosmeticRig.load(cloak.id()));

            // 4. Delete custom model cleanly
            assertTrue(CustomCosmetics.deleteCustom(dir, imported.id()));
            assertNull(CosmeticCatalog.byId(imported.id()));
            assertTrue(CustomCosmetics.deleteCustom(dir, cloak.id()));
        } finally {
            CustomCosmetics.reload(null);
        }
    }
}
