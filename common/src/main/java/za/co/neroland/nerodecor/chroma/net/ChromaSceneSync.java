package za.co.neroland.nerodecor.chroma.net;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.nerodecor.chroma.ChromaResult;
import za.co.neroland.nerodecor.chroma.SceneSlots;
import za.co.neroland.nerodecor.chroma.SceneSyncCodec;
import za.co.neroland.nerodecor.chroma.server.ChromaAnimationConfig;
import za.co.neroland.nerodecor.chroma.server.ChromaNetworkStore;
import za.co.neroland.nerodecor.chroma.server.ChromaScenes;
import za.co.neroland.nerodecor.chroma.server.HubNetwork;

/**
 * Server side of scene sync (Stage 4), driven from {@link ChromaSync} with the same watcher sets (players tracking the
 * hub's chunk). A hub's scene state goes out once when it changes ({@link SceneSlots#revision()}) or when a player
 * starts watching: one small packet, never per block or per tick. The zone map (which zone each linked block is in)
 * follows only while a scene plays, at most twice a second, and only when the layout actually changed.
 *
 * <p>Watcher sets are the static sync's, held in memory only; nothing here is saved or logged.
 */
public final class ChromaSceneSync {

    private static final long ZONE_INTERVAL_NANOS = 500_000_000L;

    private static final class Sent {
        boolean initialised;
        int revision = Integer.MIN_VALUE;
        boolean animationsOff;
        boolean zonesDirty = true;
        boolean zonesSent;
        long zoneHash;
        long lastZoneSend = Long.MIN_VALUE / 2;
    }

    private static final Map<ServerLevel, Map<Long, Sent>> LEVELS = new WeakHashMap<>();

    private ChromaSceneSync() {
    }

    private static Map<Long, Sent> sent(ServerLevel level) {
        return LEVELS.computeIfAbsent(level, ignored -> new HashMap<>());
    }

    /** The network's linked blocks, zones, anchor or activity may have changed. */
    static void touched(ServerLevel level, long id) {
        Sent s = sent(level).get(id);
        if (s != null) {
            s.zonesDirty = true;
            s.revision = Integer.MIN_VALUE; // the anchor may have moved: resend the state too
        }
    }

    /** New watchers get the current scene state, and the zone map while a scene plays. */
    static void onNewcomers(ServerLevel level, HubNetwork network, Collection<ServerPlayer> players) {
        if (players.isEmpty()) {
            return;
        }
        List<CustomPacketPayload> packets = new ArrayList<>();
        CustomPacketPayload state = state(network);
        if (state != null) {
            packets.add(state);
        }
        if (playing(network)) {
            packets.addAll(zoneMap(network));
        }
        for (ServerPlayer player : players) {
            packets.forEach(p -> ChromaNet.sendToPlayer(player, p));
        }
    }

    /**
     * One level's scene step: triggers first, then per watched hub the scene state (on change) and the zone map
     * (rate-limited, on change).
     */
    static void tick(ServerLevel level, ChromaNetworkStore store, Map<Long, Set<UUID>> watched, long now) {
        ChromaScenes.tick(level, false);
        Map<Long, Sent> sentMap = sent(level);
        sentMap.keySet().retainAll(watched.keySet());
        boolean off = !ChromaAnimationConfig.ANIMATIONS.get();
        for (Map.Entry<Long, Set<UUID>> e : watched.entrySet()) {
            HubNetwork network = store.get(e.getKey());
            Set<UUID> watchers = e.getValue();
            if (network == null || watchers.isEmpty()) {
                continue;
            }
            Sent s = sentMap.computeIfAbsent(network.id(), ignored -> new Sent());
            if (!s.initialised) {
                s.initialised = true;
                // First sight of this hub here: newcomers were already served by onNewcomers.
                s.revision = network.scenes().revision();
                s.animationsOff = off;
                if (playing(network)) {
                    s.zoneHash = zoneHash(network);
                    s.zonesSent = true;
                    s.zonesDirty = false;
                    s.lastZoneSend = now;
                }
                continue;
            }
            if (s.revision != network.scenes().revision() || s.animationsOff != off) {
                s.revision = network.scenes().revision();
                s.animationsOff = off;
                CustomPacketPayload state = state(network);
                if (state != null) {
                    send(level, watchers, List.of(state));
                }
                if (!s.zonesSent) {
                    s.zonesDirty = true;
                }
            }
            if (playing(network) && s.zonesDirty && now - s.lastZoneSend >= ZONE_INTERVAL_NANOS) {
                long hash = zoneHash(network);
                s.zonesDirty = false;
                if (!s.zonesSent || hash != s.zoneHash) {
                    s.zoneHash = hash;
                    s.zonesSent = true;
                    s.lastZoneSend = now;
                    send(level, watchers, zoneMap(network));
                }
            }
        }
    }

    private static boolean playing(HubNetwork network) {
        return network.active() && network.scenes().activeScene() != null
                && ChromaAnimationConfig.ANIMATIONS.get();
    }

    private static CustomPacketPayload state(HubNetwork network) {
        boolean off = !ChromaAnimationConfig.ANIMATIONS.get();
        SceneSlots slots = network.scenes();
        SceneSyncCodec.SceneState state = new SceneSyncCodec.SceneState(network.id(), network.anchorX(),
                network.anchorY(), network.anchorZ(), off ? SceneSyncCodec.FLAG_ANIMATIONS_OFF : 0,
                off ? SceneSlots.STATIC : slots.active(), slots.startTick(), ChromaScenes.seed(network),
                off ? null : slots.activeScene());
        ChromaResult<byte[]> bytes = SceneSyncCodec.encodeScene(state);
        return bytes.isOk() ? new ChromaScenePayloads.SceneState(bytes.value()) : null;
    }

    private static List<CustomPacketPayload> zoneMap(HubNetwork network) {
        List<Integer> offsets = network.linkedOffsets();
        int[] rel = new int[offsets.size()];
        int[] zones = new int[rel.length];
        for (int i = 0; i < rel.length; i++) {
            rel[i] = offsets.get(i);
            zones[i] = Math.max(0, network.zoneAt(rel[i]));
        }
        List<CustomPacketPayload> out = new ArrayList<>();
        for (byte[] part : SceneSyncCodec.encodeZoneMap(network.id(), rel, zones)) {
            out.add(new ChromaScenePayloads.ZoneMap(part));
        }
        return out;
    }

    /** Order-independent hash of (offset, zone) pairs plus the anchor. */
    static long zoneHash(HubNetwork network) {
        long h = 0x9E3779B97F4A7C15L * (network.linkedOffsets().size() + 1);
        for (int rel : network.linkedOffsets()) {
            long v = (long) rel << 8 | network.zoneAt(rel) & 0xFF;
            v *= 0xBF58476D1CE4E5B9L;
            h += v ^ (v >>> 31);
        }
        return h ^ (31L * network.anchorX() + 17L * network.anchorY() + network.anchorZ());
    }

    private static void send(ServerLevel level, Set<UUID> players, List<CustomPacketPayload> payloads) {
        for (UUID id : players) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player != null) {
                payloads.forEach(p -> ChromaNet.sendToPlayer(player, p));
            }
        }
    }
}
