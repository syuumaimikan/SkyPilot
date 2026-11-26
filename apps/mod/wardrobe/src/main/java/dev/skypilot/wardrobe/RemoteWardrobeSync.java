package dev.skypilot.wardrobe;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.skypilot.mod.core.CosmeticCatalog;
import dev.skypilot.mod.core.SkyPilotConfig;
import dev.skypilot.mod.core.WardrobeVfxCatalog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;

import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 100% standalone multiplayer cosmetic &amp; VFX synchronization for {@code skypilot_wardrobe}.
 *
 * <p>Supports three completely self-contained modes without requiring any SkyPilot server:
 * <ul>
 *   <li>{@code "lan"} (default): Built-in zero-config UDP Multicast + Broadcast LAN peer discovery
 *       ({@code 239.255.77.88:47832}). Mod users on the same LAN or machine automatically see each
 *       other's 10-slot cosmetics, transforms, and VFX with zero external server.</li>
 *   <li>{@code "offline"}: Strictly local — zero sockets, zero network traffic.</li>
 *   <li>{@code "relay"}: Optional user-configured external HTTP relay for public internet servers
 *       (e.g. Hypixel lobbies). Still sends ZERO custom Minecraft game packets.</li>
 * </ul>
 */
final class RemoteWardrobeSync {
    private static final String LAN_MAGIC = "SPW1";
    private static final String LAN_GROUP = "239.255.77.88";
    private static final int LAN_PORT = 47832;
    private static final long LAN_PEER_TTL_MS = 18_000L;

    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Wardrobe-Sync");
        t.setDaemon(true);
        return t;
    });

    private static final ConcurrentHashMap<UUID, SkyPilotConfig.Appearance> REMOTE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Long> REMOTE_LAST_SEEN = new ConcurrentHashMap<>();
    private static final AtomicBoolean DIRTY = new AtomicBoolean(true);
    private static final AtomicBoolean PUSH_IN_FLIGHT = new AtomicBoolean(false);
    private static final AtomicBoolean POLL_IN_FLIGHT = new AtomicBoolean(false);
    private static final AtomicBoolean LAN_RUNNING = new AtomicBoolean(false);

    private static volatile MulticastSocket lanSocket;
    private static volatile Thread lanThread;
    private static volatile boolean lanReady;
    private static volatile long lastPushMs;
    private static volatile long lastPollMs;
    private static volatile long lastLanBroadcastMs;
    private static volatile boolean relayConnected;
    private static volatile int serverOnlineCount;
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
        WardrobeRuntime rt = WardrobeRuntime.get();
        if (rt == null) return false;
        String mode = syncMode(rt.appearance());
        if ("lan".equals(mode)) return lanReady;
        if ("relay".equals(mode)) return relayConnected;
        return false;
    }

    static String syncMode(SkyPilotConfig.Appearance a) {
        if (a == null || a.wardrobeSyncMode == null) return "lan";
        return switch (a.wardrobeSyncMode) {
            case "offline", "relay" -> a.wardrobeSyncMode;
            default -> "lan";
        };
    }

    static int nearbySyncedCount() {
        return REMOTE.size();
    }

    static String statusSummary(boolean ja) {
        WardrobeRuntime rt = WardrobeRuntime.get();
        if (rt == null) return "";
        var a = rt.appearance();
        String mode = syncMode(a);
        if ("offline".equals(mode) || (!a.showOtherPlayers && !a.shareWithOthers)) {
            return ja ? "ローカル専用" : "Local Only";
        }
        if ("lan".equals(mode)) {
            int peers = REMOTE.size();
            if (peers > 0) {
                return ja ? "LAN自動共有 (" + peers + "人接続)" : "LAN Sync (" + peers + " peer" + (peers == 1 ? "" : "s") + ")";
            }
            return ja ? "LAN自動共有 (待機中)" : "LAN Auto-Sync (ready)";
        }
        // Optional external relay mode
        String url = validatedBaseUrl(a.wardrobeSyncUrl);
        if (url == null) {
            return ja ? "リレー未設定" : "Relay URL not set";
        }
        if (relayConnected) {
            return ja
                    ? "リレー共有中 (" + REMOTE.size() + "人 / 全体" + serverOnlineCount + "人)"
                    : "Relay (" + REMOTE.size() + " nearby / " + serverOnlineCount + " online)";
        }
        return ja ? "リレー待機中" : "Relay standby";
    }

    static void tick(Minecraft mc) {
        WardrobeRuntime rt = WardrobeRuntime.get();
        if (rt == null || mc.player == null || mc.level == null) return;
        SkyPilotConfig.Appearance a = rt.appearance();
        String mode = syncMode(a);
        UUID selfId = mc.player.getUUID();
        lastLocalUuid = selfId;
        long now = System.currentTimeMillis();

        if ("offline".equals(mode) || (!a.showOtherPlayers && !a.shareWithOthers)) {
            stopLanListener();
            relayConnected = false;
            if (!REMOTE.isEmpty()) {
                REMOTE.clear();
                REMOTE_LAST_SEEN.clear();
            }
            return;
        }

        if ("lan".equals(mode)) {
            relayConnected = false;
            if (a.showOtherPlayers) ensureLanListener();
            else {
                stopLanListener();
                if (!REMOTE.isEmpty()) {
                    REMOTE.clear();
                    REMOTE_LAST_SEEN.clear();
                }
            }

            // Broadcast own cosmetics over LAN UDP on change (debounced 500ms) or every 3.5s
            if (a.shareWithOthers) {
                boolean needSend = (DIRTY.get() && now - lastLanBroadcastMs >= 500) || (now - lastLanBroadcastMs >= 3_500);
                if (needSend) {
                    DIRTY.set(false);
                    lastLanBroadcastMs = now;
                    String payload = LAN_MAGIC + buildSyncJson(selfId, a);
                    EXEC.execute(() -> sendLanPacket(payload));
                }
            }

            // Evict timed-out LAN peers
            if (!REMOTE_LAST_SEEN.isEmpty()) {
                for (var it = REMOTE_LAST_SEEN.entrySet().iterator(); it.hasNext(); ) {
                    var e = it.next();
                    if (now - e.getValue() > LAN_PEER_TTL_MS) {
                        it.remove();
                        REMOTE.remove(e.getKey());
                    }
                }
            }
            return;
        }

        // Optional external HTTP relay mode
        stopLanListener();
        String baseUrl = validatedBaseUrl(a.wardrobeSyncUrl);
        if (baseUrl == null) {
            relayConnected = false;
            return;
        }

        if (a.shareWithOthers) {
            boolean needPush = (DIRTY.get() && now - lastPushMs >= 800) || (now - lastPushMs >= 45_000);
            if (needPush && PUSH_IN_FLIGHT.compareAndSet(false, true)) {
                DIRTY.set(false);
                lastPushMs = now;
                String payload = buildSyncJson(selfId, a);
                EXEC.execute(() -> pushAsync(baseUrl, payload));
            }
        }

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
        REMOTE_LAST_SEEN.clear();
        DIRTY.set(true);
        WardrobeRuntime rt = WardrobeRuntime.get();
        if (rt == null || selfId == null) return;
        String mode = syncMode(rt.appearance());
        if ("lan".equals(mode)) {
            String bye = LAN_MAGIC + "{\"uuid\":\"" + toCompactUuid(selfId) + "\",\"leave\":true}";
            EXEC.execute(() -> sendLanPacket(bye));
        } else if ("relay".equals(mode)) {
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
                    ? "URL無効 (https:// またはローカル http:// を指定)"
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
                    relayConnected = true;
                    serverOnlineCount = online;
                    return ja ? "接続成功！ オンライン: " + online + "人" : "Connected! Online: " + online;
                }
                return "HTTP " + resp.statusCode();
            } catch (Exception e) {
                relayConnected = false;
                return (ja ? "接続失敗: " : "Failed: ") + shortMsg(e);
            }
        }, EXEC);
    }

    static void shutdown() {
        onDisconnect();
        stopLanListener();
        EXEC.shutdown();
    }

    // ---- Built-in Standalone UDP LAN Peer Sync ----

    private static void ensureLanListener() {
        if (!LAN_RUNNING.compareAndSet(false, true)) return;
        Thread t = new Thread(() -> {
            try (MulticastSocket sock = new MulticastSocket(null)) {
                sock.setReuseAddress(true);
                sock.bind(new InetSocketAddress(LAN_PORT));
                InetAddress group = InetAddress.getByName(LAN_GROUP);
                try {
                    sock.joinGroup(new InetSocketAddress(group, LAN_PORT), null);
                } catch (Exception ignored) {
                    // Fallback: broadcast packets still arrive on LAN_PORT even if multicast join is restricted
                }
                lanSocket = sock;
                lanReady = true;
                byte[] buf = new byte[4096];
                while (LAN_RUNNING.get() && !sock.isClosed()) {
                    DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                    sock.receive(pkt);
                    int len = pkt.getLength();
                    if (len <= LAN_MAGIC.length()) continue;
                    String msg = new String(pkt.getData(), pkt.getOffset(), len, StandardCharsets.UTF_8);
                    if (!msg.startsWith(LAN_MAGIC)) continue;
                    handleLanPayload(msg.substring(LAN_MAGIC.length()));
                }
            } catch (Exception ignored) {
                // Socket closed during shutdown or LAN port busy; silently stay offline-safe
            } finally {
                lanSocket = null;
                lanReady = false;
                LAN_RUNNING.set(false);
            }
        }, "Wardrobe-LAN-Listener");
        t.setDaemon(true);
        lanThread = t;
        t.start();
    }

    private static void stopLanListener() {
        LAN_RUNNING.set(false);
        lanReady = false;
        MulticastSocket s = lanSocket;
        lanSocket = null;
        if (s != null) {
            try { s.close(); } catch (Exception ignored) {}
        }
        Thread t = lanThread;
        lanThread = null;
        if (t != null) t.interrupt();
    }

    private static void sendLanPacket(String payload) {
        byte[] data = payload.getBytes(StandardCharsets.UTF_8);
        if (data.length > 3800) return;
        try (DatagramSocket ds = new DatagramSocket()) {
            ds.setBroadcast(true);
            try {
                InetAddress group = InetAddress.getByName(LAN_GROUP);
                ds.send(new DatagramPacket(data, data.length, group, LAN_PORT));
            } catch (Exception ignored) {}
            try {
                InetAddress bcast = InetAddress.getByName("255.255.255.255");
                ds.send(new DatagramPacket(data, data.length, bcast, LAN_PORT));
            } catch (Exception ignored) {}
        } catch (Exception ignored) {}
    }

    private static void handleLanPayload(String json) {
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            if (!obj.has("uuid") || !obj.get("uuid").isJsonPrimitive()) return;
            UUID uid = parseCompactUuid(obj.get("uuid").getAsString());
            if (uid == null || uid.equals(lastLocalUuid)) return;
            if (obj.has("leave") && obj.get("leave").getAsBoolean()) {
                REMOTE.remove(uid);
                REMOTE_LAST_SEEN.remove(uid);
                return;
            }
            SkyPilotConfig.Appearance parsed = parseRemoteAppearance(obj);
            REMOTE.put(uid, parsed);
            REMOTE_LAST_SEEN.put(uid, System.currentTimeMillis());
        } catch (Exception ignored) {}
    }

    // ---- Optional External HTTP Relay Sync ----

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
                relayConnected = true;
                JsonObject obj = JsonParser.parseString(resp.body()).getAsJsonObject();
                if (obj.has("online")) serverOnlineCount = obj.get("online").getAsInt();
            } else {
                relayConnected = false;
            }
        } catch (Exception e) {
            relayConnected = false;
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
                relayConnected = true;
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
                REMOTE.keySet().removeIf(u -> !seen.contains(u));
            } else {
                relayConnected = false;
            }
        } catch (Exception e) {
            relayConnected = false;
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

    // ---- Shared 10-Slot Serialization & Sanitization ----

    private static String buildSyncJson(UUID selfId, SkyPilotConfig.Appearance a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("uuid", toCompactUuid(selfId));
        for (String slot : CosmeticCatalog.SLOTS) {
            m.put(slot, safeBundledId(slot, CosmeticCatalog.equippedId(a, slot)));
        }
        m.put("wingScale", a.wingScale);
        m.put("cosmeticGlow", a.cosmeticGlow);
        m.put("cosmeticParticles", a.cosmeticParticles);
        Map<String, SkyPilotConfig.SlotTransform> cleanTr = new LinkedHashMap<>();
        if (a.slotTransforms != null) {
            for (var e : a.slotTransforms.entrySet()) {
                if (CosmeticCatalog.SLOTS.contains(e.getKey()) && e.getValue() != null && !e.getValue().isIdentity()) {
                    cleanTr.put(e.getKey(), e.getValue());
                }
            }
        }
        if (!cleanTr.isEmpty()) m.put("slotTransforms", cleanTr);
        if (a.vfx != null && WardrobeVfxCatalog.activeCount(a.vfx) > 0) {
            Map<String, Object> vm = new LinkedHashMap<>();
            vm.put("attack", safeBundledVfx(a.vfx.attack));
            vm.put("kill", safeBundledVfx(a.vfx.kill));
            vm.put("move", safeBundledVfx(a.vfx.move));
            vm.put("jump", safeBundledVfx(a.vfx.jump));
            vm.put("dash", safeBundledVfx(a.vfx.dash));
            vm.put("projectile", safeBundledVfx(a.vfx.projectile));
            vm.put("ambient", safeBundledVfx(a.vfx.ambient));
            vm.put("scale", a.vfx.scale);
            vm.put("intensity", a.vfx.intensity);
            vm.put("customColorEnabled", a.vfx.customColorEnabled);
            vm.put("customColor", a.vfx.customColor & 0xFFFFFF);
            m.put("vfx", vm);
        }
        return GSON.toJson(m);
    }

    private static String safeBundledVfx(String id) {
        if (id == null || "none".equals(id) || id.startsWith("custom_")) return "none";
        return WardrobeVfxCatalog.byId(id) != null ? id : "none";
    }

    private static String safeBundledId(String slot, String id) {
        if (id == null || "none".equals(id) || id.startsWith("custom_")) return "none";
        return CosmeticCatalog.valid(slot, id) ? id : "none";
    }

    private static SkyPilotConfig.Appearance parseRemoteAppearance(JsonObject o) {
        SkyPilotConfig.Appearance a = new SkyPilotConfig.Appearance();
        for (String slot : CosmeticCatalog.SLOTS) {
            CosmeticCatalog.equip(a, slot, validSlot(o, slot));
        }
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
        if (o.has("vfx") && o.get("vfx").isJsonObject()) {
            JsonObject vo = o.getAsJsonObject("vfx");
            SkyPilotConfig.VfxSettings vs = new SkyPilotConfig.VfxSettings();
            vs.attack = validVfx(vo, "attack");
            vs.kill = validVfx(vo, "kill");
            vs.move = vo.has("move") ? validVfx(vo, "move") : validVfx(vo, "moveVfx");
            vs.jump = validVfx(vo, "jump");
            vs.dash = validVfx(vo, "dash");
            vs.projectile = validVfx(vo, "projectile");
            vs.ambient = validVfx(vo, "ambient");
            if (vo.has("scale")) vs.scale = vo.get("scale").getAsDouble();
            if (vo.has("intensity")) vs.intensity = vo.get("intensity").getAsInt();
            if (vo.has("customColorEnabled")) vs.customColorEnabled = vo.get("customColorEnabled").getAsBoolean();
            if (vo.has("customColor")) vs.customColor = vo.get("customColor").getAsInt();
            vs.sanitize();
            a.vfx = vs;
        }
        return a;
    }

    private static String validVfx(JsonObject o, String key) {
        JsonElement el = o.get(key);
        if (el == null || !el.isJsonPrimitive()) return "none";
        return safeBundledVfx(el.getAsString());
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

    private static UUID parseCompactUuid(String raw) {
        if (raw == null) return null;
        String s = raw.replace("-", "").trim();
        if (s.length() != 32) return null;
        try {
            long msb = Long.parseUnsignedLong(s.substring(0, 16), 16);
            long lsb = Long.parseUnsignedLong(s.substring(16, 32), 16);
            return new UUID(msb, lsb);
        } catch (Exception e) {
            return null;
        }
    }

    private static String shortMsg(Exception e) {
        String m = e.getMessage();
        if (m == null || m.isBlank()) return e.getClass().getSimpleName();
        return m.length() > 48 ? m.substring(0, 45) + "..." : m;
    }
}
