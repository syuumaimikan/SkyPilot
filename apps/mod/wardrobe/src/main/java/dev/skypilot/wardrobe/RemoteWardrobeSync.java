package dev.skypilot.wardrobe;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.skypilot.mod.core.CosmeticCatalog;
import dev.skypilot.mod.core.SkyPilotConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Hypixel-safe multiplayer cosmetic synchronization over lightweight HTTP REST calls
 * ({@code /api/wardrobe/sync}, {@code /api/wardrobe/batch}, {@code /api/wardrobe/leave}).
 *
 * <p>Sends ZERO custom Minecraft server packets. Runs completely asynchronously off the render
 * thread with short timeouts and silent graceful fallback when the sync server is offline.
 */
final class RemoteWardrobeSync {
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SkyPilot-WardrobeSync");
        t.setDaemon(true);
        return t;
    });

    private static final ConcurrentHashMap<UUID, SkyPilotConfig.Appearance> REMOTE = new ConcurrentHashMap<>();
    private static final AtomicBoolean DIRTY = new AtomicBoolean(true);
    private static final AtomicBoolean PUSH_IN_FLIGHT = new AtomicBoolean(false);
    private static final AtomicBoolean POLL_IN_FLIGHT = new AtomicBoolean(false);

    private static volatile long lastPushMs;
    private static volatile long lastPollMs;
    private static volatile boolean connected;
    private static volatile int serverOnlineCount;
    private static volatile String lastError = "";
    private static volatile UUID lastLocalUuid;

    private RemoteWardrobeSync() {}

    static void markDirty() {
        DIRTY.set(true);
    }

    static SkyPilotConfig.Appearance getRemote(UUID uuid) {
        return uuid == null ? null : REMOTE.get(uuid);
    }

    static Map<UUID, SkyPilotConfig.Appearance> allRemotes() {
        return REMOTE;
    }

    static boolean isConnected() {
        return connected;
    }

    static int serverOnlineCount() {
        return serverOnlineCount;
    }

    static int nearbySyncedCount() {
        return REMOTE.size();
    }

    static String statusSummary(boolean ja) {
        WardrobeRuntime rt = WardrobeRuntime.get();
        if (rt == null) return "";
        var a = rt.appearance();
        if (!a.showOtherPlayers && !a.shareWithOthers) {
            return ja ? "同期OFF" : "Sync OFF";
        }
        if (connected) {
            return ja
                    ? "同期中 (" + REMOTE.size() + "人表示 / 全体" + serverOnlineCount + "人)"
                    : "Synced (" + REMOTE.size() + " nearby / " + serverOnlineCount + " online)";
        }
        return ja ? "オフライン (サーバー待機中)" : "Offline (waiting for sync server)";
    }

    static void tick(Minecraft mc) {
        WardrobeRuntime rt = WardrobeRuntime.get();
        if (rt == null || mc.player == null || mc.level == null) return;
        SkyPilotConfig.Appearance a = rt.appearance();
        String baseUrl = validatedBaseUrl(a.wardrobeSyncUrl);
        if (baseUrl == null) {
            connected = false;
            return;
        }

        UUID selfId = mc.player.getUUID();
        lastLocalUuid = selfId;
        long now = System.currentTimeMillis();

        // 1. Push own cosmetics when changed (debounced 800ms) or heartbeat every 45s
        if (a.shareWithOthers) {
            boolean needPush = (DIRTY.get() && now - lastPushMs >= 800) || (now - lastPushMs >= 45_000);
            if (needPush && PUSH_IN_FLIGHT.compareAndSet(false, true)) {
                DIRTY.set(false);
                lastPushMs = now;
                String payload = buildSyncJson(selfId, a);
                EXEC.execute(() -> pushAsync(baseUrl, payload));
            }
        }

        // 2. Poll visible nearby players every 4s
        if (a.showOtherPlayers && now - lastPollMs >= 4_000 && POLL_IN_FLIGHT.compareAndSet(false, true)) {
            lastPollMs = now;
            List<UUID> nearby = collectNearbyUuids(mc, selfId);
            if (nearby.isEmpty()) {
                REMOTE.clear();
                POLL_IN_FLIGHT.set(false);
            } else {
                EXEC.execute(() -> pollBatchAsync(baseUrl, nearby));
            }
        } else if (!a.showOtherPlayers && !REMOTE.isEmpty()) {
            REMOTE.clear();
        }
    }

    static void onDisconnect() {
        UUID selfId = lastLocalUuid;
        lastLocalUuid = null;
        REMOTE.clear();
        DIRTY.set(true);
        WardrobeRuntime rt = WardrobeRuntime.get();
        if (rt != null && selfId != null) {
            String baseUrl = validatedBaseUrl(rt.appearance().wardrobeSyncUrl);
            if (baseUrl != null) {
                String body = "{\"uuid\":\"" + toCompactUuid(selfId) + "\"}";
                EXEC.execute(() -> postQuietly(baseUrl + "/api/wardrobe/leave", body));
            }
        }
    }

    static CompletableFuture<String> testConnectionAsync(String rawUrl, boolean ja) {
        String base = validatedBaseUrl(rawUrl);
        if (base == null) {
            return CompletableFuture.completedFuture(ja
                    ? "URL無効 (https:// またはローカル http:// を指定してください)"
                    : "Invalid URL (use https:// or local/LAN http://)");
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(base + "/api/wardrobe/stats"))
                        .timeout(Duration.ofSeconds(3))
                        .GET()
                        .build();
                HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    JsonObject obj = JsonParser.parseString(resp.body()).getAsJsonObject();
                    int online = obj.has("online") ? obj.get("online").getAsInt() : 0;
                    connected = true;
                    serverOnlineCount = online;
                    lastError = "";
                    return ja ? "接続成功！ オンライン: " + online + "人" : "Connected! Online: " + online;
                }
                return "HTTP " + resp.statusCode();
            } catch (Exception e) {
                connected = false;
                return (ja ? "接続失敗: " : "Failed: ") + shortMsg(e);
            }
        }, EXEC);
    }

    static void shutdown() {
        onDisconnect();
        EXEC.shutdown();
    }

    private static List<UUID> collectNearbyUuids(Minecraft mc, UUID selfId) {
        List<UUID> ids = new ArrayList<>();
        if (mc.level == null || mc.player == null) return ids;
        for (AbstractClientPlayer p : mc.level.players()) {
            if (p == null || p.getUUID().equals(selfId)) continue;
            if (p.distanceToSqr(mc.player) > 96.0 * 96.0) continue;
            ids.add(p.getUUID());
            if (ids.size() >= 100) break;
        }
        return ids;
    }

    private static void pushAsync(String baseUrl, String payload) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/api/wardrobe/sync"))
                    .timeout(Duration.ofSeconds(2))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                connected = true;
                lastError = "";
                JsonObject obj = JsonParser.parseString(resp.body()).getAsJsonObject();
                if (obj.has("online")) serverOnlineCount = obj.get("online").getAsInt();
            } else {
                connected = false;
                lastError = "HTTP " + resp.statusCode();
            }
        } catch (Exception e) {
            connected = false;
            lastError = shortMsg(e);
        } finally {
            PUSH_IN_FLIGHT.set(false);
        }
    }

    private static void pollBatchAsync(String baseUrl, List<UUID> nearby) {
        try {
            Map<String, UUID> byCompact = new HashMap<>();
            List<String> list = new ArrayList<>(nearby.size());
            for (UUID id : nearby) {
                String c = toCompactUuid(id);
                byCompact.put(c, id);
                list.add(c);
            }
            String body = GSON.toJson(Map.of("uuids", list));
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/api/wardrobe/batch"))
                    .timeout(Duration.ofSeconds(2))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                connected = true;
                lastError = "";
                JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
                if (root.has("online")) serverOnlineCount = root.get("online").getAsInt();
                Set<UUID> seen = new HashSet<>();
                if (root.has("players") && root.get("players").isJsonObject()) {
                    for (var entry : root.getAsJsonObject("players").entrySet()) {
                        UUID uid = byCompact.get(entry.getKey().toLowerCase(Locale.ROOT));
                        if (uid != null && entry.getValue().isJsonObject()) {
                            SkyPilotConfig.Appearance parsed = parseRemoteAppearance(entry.getValue().getAsJsonObject());
                            REMOTE.put(uid, parsed);
                            seen.add(uid);
                        }
                    }
                }
                // Evict players who left range or unequipped sync
                REMOTE.keySet().removeIf(u -> !seen.contains(u));
            } else {
                connected = false;
            }
        } catch (Exception e) {
            connected = false;
            lastError = shortMsg(e);
        } finally {
            POLL_IN_FLIGHT.set(false);
        }
    }

    private static void postQuietly(String url, String body) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(2))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HTTP.send(req, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {}
    }

    private static String buildSyncJson(UUID selfId, SkyPilotConfig.Appearance a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("uuid", toCompactUuid(selfId));
        m.put("cape", safeBundledId("cape", a.cape));
        m.put("back", safeBundledId("back", a.back));
        m.put("hat", safeBundledId("hat", a.hat));
        m.put("aura", safeBundledId("aura", a.aura));
        m.put("pet", safeBundledId("pet", a.pet));
        m.put("companion", safeBundledId("companion", a.companion));
        m.put("wingScale", a.wingScale);
        m.put("cosmeticGlow", a.cosmeticGlow);
        m.put("cosmeticParticles", a.cosmeticParticles);
        Map<String, SkyPilotConfig.SlotTransform> cleanTr = new LinkedHashMap<>();
        if (a.slotTransforms != null) {
            for (var e : a.slotTransforms.entrySet()) {
                if (e.getValue() != null && !e.getValue().isIdentity()) {
                    cleanTr.put(e.getKey(), e.getValue());
                }
            }
        }
        m.put("slotTransforms", cleanTr);
        return GSON.toJson(m);
    }

    private static String safeBundledId(String slot, String id) {
        if (id == null || "none".equals(id) || id.startsWith("custom_")) return "none";
        return CosmeticCatalog.valid(slot, id) ? id : "none";
    }

    private static SkyPilotConfig.Appearance parseRemoteAppearance(JsonObject o) {
        SkyPilotConfig.Appearance a = new SkyPilotConfig.Appearance();
        a.cape = validSlot(o, "cape");
        a.back = validSlot(o, "back");
        a.hat = validSlot(o, "hat");
        a.aura = validSlot(o, "aura");
        a.pet = validSlot(o, "pet");
        a.companion = validSlot(o, "companion");
        a.wingScale = o.has("wingScale") ? Math.max(0.6, Math.min(1.5, o.get("wingScale").getAsDouble())) : 1.0;
        a.cosmeticGlow = !o.has("cosmeticGlow") || o.get("cosmeticGlow").getAsBoolean();
        a.cosmeticParticles = !o.has("cosmeticParticles") || o.get("cosmeticParticles").getAsBoolean();
        if (o.has("slotTransforms") && o.get("slotTransforms").isJsonObject()) {
            for (var e : o.getAsJsonObject("slotTransforms").entrySet()) {
                if (!CosmeticCatalog.SLOTS.contains(e.getKey()) || !e.getValue().isJsonObject()) continue;
                try {
                    SkyPilotConfig.SlotTransform tr = GSON.fromJson(e.getValue(), SkyPilotConfig.SlotTransform.class);
                    if (tr != null) {
                        tr.sanitize();
                        if (!tr.isIdentity()) a.slotTransforms.put(e.getKey(), tr);
                    }
                } catch (Exception ignored) {}
            }
        }
        return a;
    }

    private static String validSlot(JsonObject o, String slot) {
        JsonElement el = o.get(slot);
        if (el == null || !el.isJsonPrimitive()) return "none";
        String id = el.getAsString();
        return CosmeticCatalog.valid(slot, id) ? id : "none";
    }

    static String validatedBaseUrl(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.strip().replaceAll("/+$", "");
        try {
            URI u = URI.create(s);
            String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase(Locale.ROOT);
            String host = u.getHost() == null ? "" : u.getHost().toLowerCase(Locale.ROOT);
            if ("https".equals(scheme) && !host.isEmpty()) return s;
            if ("http".equals(scheme) && (host.equals("localhost") || host.equals("127.0.0.1") || host.equals("::1")
                    || host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("100.")
                    || host.startsWith("172."))) {
                return s;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static String toCompactUuid(UUID id) {
        return id.toString().replace("-", "").toLowerCase(Locale.ROOT);
    }

    private static String shortMsg(Exception e) {
        String m = e.getMessage();
        if (m == null || m.isBlank()) return e.getClass().getSimpleName();
        return m.length() > 48 ? m.substring(0, 45) + "..." : m;
    }
}

