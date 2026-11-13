package za.co.neroland.nerodecor.chroma.net;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import za.co.neroland.nerodecor.NeroDecorCommon;
import za.co.neroland.nerodecor.chroma.ChromaColor;
import za.co.neroland.nerodecor.chroma.ChromaDelta;
import za.co.neroland.nerodecor.chroma.ChromaLightSchedule;
import za.co.neroland.nerodecor.chroma.ChromaPalette;
import za.co.neroland.nerodecor.chroma.ChromaResult;
import za.co.neroland.nerodecor.chroma.ChromaSyncCodec;
import za.co.neroland.nerodecor.chroma.ChromaSyncState;
import za.co.neroland.nerodecor.chroma.server.ChromaConfig;
import za.co.neroland.nerodecor.chroma.server.ChromaNetworkStore;
import za.co.neroland.nerodecor.chroma.server.ChromaNetworks;
import za.co.neroland.nerodecor.chroma.server.HubNetwork;
import za.co.neroland.nerodecor.content.block.ChromaCubeBlock;

/**
 * Server side of Chroma static sync, driven by the server tick:
 *
 * <ul>
 *   <li>Every {@value #SCAN_INTERVAL_TICKS} ticks each active hub's watchers are the players tracking the hub's chunk
 *       ({@code ChunkMap#getPlayers}). A new watcher is sent the hub's snapshot (the shadow every other watcher
 *       holds); a player who stops watching is told to drop the hub.</li>
 *   <li>Store changes ({@link ChromaNetworkStore#drainTouched}) mark the hub dirty; a dirty hub flushes at most twice a
 *       second ({@link ChromaSyncState}), as one coalesced delta, or as snapshot parts when the hub moved or the
 *       delta would not fit.</li>
 *   <li>Chroma cubes' light follows the committed static brightness of their palette entry, at most one light change
 *       per block per second ({@link ChromaLightSchedule}); a block that leaves its hub goes back to 15.</li>
 * </ul>
 *
 * <p>Watcher sets live in memory only and are never logged or saved (POPIA/GDPR).
 */
public final class ChromaSync {

    static final int SCAN_INTERVAL_TICKS = 10;
    private static final int LIGHT_UPDATES_PER_TICK = 256;
    private static final int FULL_LIGHT = 15;

    private static final class Tracked {
        final ChromaSyncState state;
        Set<UUID> watchers = new HashSet<>();

        Tracked(long network) {
            this.state = new ChromaSyncState(network);
        }
    }

    private static final class LevelSync {
        final Map<Long, Tracked> tracked = new HashMap<>();
        /** network id -> absolute positions whose light the network last set. */
        final Map<Long, Set<Long>> lit = new HashMap<>();
        final ChromaLightSchedule light = new ChromaLightSchedule();
    }

    private static final Map<ServerLevel, LevelSync> LEVELS = new WeakHashMap<>();
    private static int ticks;

    private ChromaSync() {
    }

    /** Server tick (end). */
    public static void tick(MinecraftServer server) {
        if (!ChromaConfig.ENABLED.get()) {
            return;
        }
        long now = System.nanoTime();
        boolean scan = ++ticks % SCAN_INTERVAL_TICKS == 0;
        for (ServerLevel level : server.getAllLevels()) {
            tickLevel(level, now, scan);
        }
    }

    /** One level's sync step (public for GameTests). */
    public static void tickLevel(ServerLevel level, long now, boolean scan) {
        ChromaNetworkStore store = ChromaNetworks.store(level);
        LevelSync sync = LEVELS.computeIfAbsent(level, ignored -> new LevelSync());
        for (long id : store.drainTouched()) {
            Tracked tracked = sync.tracked.get(id);
            if (tracked != null) {
                tracked.state.markDirty();
            }
            relight(level, sync, id, store.get(id));
        }
        if (scan) {
            scan(level, store, sync);
        }
        flush(level, store, sync, now);
        for (ChromaLightSchedule.Update update : sync.light.drain(now, LIGHT_UPDATES_PER_TICK)) {
            BlockPos pos = BlockPos.of(update.pos());
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof ChromaCubeBlock && state.getValue(ChromaCubeBlock.LIGHT) != update.level()) {
                level.setBlock(pos, state.setValue(ChromaCubeBlock.LIGHT, update.level()), Block.UPDATE_CLIENTS);
            }
        }
    }

    // --- watchers -----------------------------------------------------------------------------------------------

    private static void scan(ServerLevel level, ChromaNetworkStore store, LevelSync sync) {
        Set<Long> live = new HashSet<>();
        for (HubNetwork network : store.all()) {
            if (!network.active()) {
                continue;
            }
            long id = network.id();
            List<ServerPlayer> players = level.getChunkSource().chunkMap.getPlayers(
                    new ChunkPos(network.anchorX() >> 4, network.anchorZ() >> 4), false);
            Tracked tracked = sync.tracked.get(id);
            if (players.isEmpty()) {
                if (tracked != null) {
                    sendTo(level, tracked.watchers, clear(id));
                    sync.tracked.remove(id);
                }
                continue;
            }
            live.add(id);
            if (tracked == null) {
                tracked = new Tracked(id);
                sync.tracked.put(id, tracked);
            }
            Set<UUID> now = new HashSet<>();
            List<ServerPlayer> newcomers = new ArrayList<>();
            for (ServerPlayer player : players) {
                now.add(player.getUUID());
                if (!tracked.watchers.contains(player.getUUID())) {
                    newcomers.add(player);
                }
            }
            Set<UUID> leavers = new HashSet<>(tracked.watchers);
            leavers.removeAll(now);
            sendTo(level, leavers, clear(id));
            tracked.watchers = now;
            if (tracked.state.hasSent() && !newcomers.isEmpty()) {
                List<CustomPacketPayload> parts = snapshotParts(tracked.state);
                for (ServerPlayer player : newcomers) {
                    parts.forEach(part -> ChromaNet.sendToPlayer(player, part));
                }
            }
        }
        Iterator<Map.Entry<Long, Tracked>> it = sync.tracked.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Tracked> e = it.next();
            if (!live.contains(e.getKey())) {
                sendTo(level, e.getValue().watchers, clear(e.getKey()));
                it.remove();
            }
        }
    }

    private static void flush(ServerLevel level, ChromaNetworkStore store, LevelSync sync, long now) {
        Iterator<Map.Entry<Long, Tracked>> it = sync.tracked.entrySet().iterator();
        while (it.hasNext()) {
            Tracked tracked = it.next().getValue();
            if (tracked.watchers.isEmpty() || !tracked.state.due(now)) {
                continue;
            }
            HubNetwork network = store.get(tracked.state.network());
            if (network == null || !network.active()) {
                sendTo(level, tracked.watchers, clear(tracked.state.network()));
                it.remove();
                continue;
            }
            ChromaSyncState.Flush flush = tracked.state.flush(now, network.anchorX(), network.anchorY(),
                    network.anchorZ(), layout(network), network.palette().toPacked());
            switch (flush.kind()) {
                case SNAPSHOT -> sendTo(level, tracked.watchers, snapshotParts(tracked.state));
                case DELTA -> {
                    ChromaResult<byte[]> bytes = ChromaSyncCodec.encodeDelta(flush.delta());
                    if (bytes.isOk()) {
                        sendTo(level, tracked.watchers, List.of(new ChromaPayloads.Delta(bytes.value())));
                    } else {
                        sendTo(level, tracked.watchers, snapshotParts(tracked.state));
                    }
                }
                default -> {
                }
            }
        }
    }

    /** The hub's current static layout: packed relative offset to palette index. */
    static Map<Integer, Integer> layout(HubNetwork network) {
        Map<Integer, Integer> out = new HashMap<>();
        for (int rel : network.linkedOffsets()) {
            out.put(rel, network.paletteIndexAt(rel));
        }
        return out;
    }

    private static List<CustomPacketPayload> snapshotParts(ChromaSyncState state) {
        int[][] blocks = state.shadowBlocks();
        ChromaResult<List<byte[]>> parts = ChromaSyncCodec.encodeSnapshotParts(state.network(), state.anchorX(),
                state.anchorY(), state.anchorZ(), state.shadowPalette(), blocks[0], blocks[1]);
        if (!parts.isOk()) {
            NeroDecorCommon.LOGGER.warn("[NeroDecor] Chroma snapshot could not be encoded ({})", parts.error());
            return List.of();
        }
        List<CustomPacketPayload> out = new ArrayList<>();
        for (byte[] part : parts.value()) {
            out.add(new ChromaPayloads.Snapshot(part));
        }
        return out;
    }

    private static List<CustomPacketPayload> clear(long network) {
        return List.of(new ChromaPayloads.Delta(ChromaSyncCodec.encodeDelta(ChromaDelta.clear(network)).value()));
    }

    private static void sendTo(ServerLevel level, Set<UUID> players, List<CustomPacketPayload> payloads) {
        if (players.isEmpty() || payloads.isEmpty()) {
            return;
        }
        for (UUID id : players) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player != null) {
                payloads.forEach(p -> ChromaNet.sendToPlayer(player, p));
            }
        }
    }

    // --- light ------------------------------------------------------------------------------------------------------

    /** Queues light changes for the network's chroma cubes after a store change. */
    private static void relight(ServerLevel level, LevelSync sync, long id, HubNetwork network) {
        Set<Long> previous = sync.lit.getOrDefault(id, Set.of());
        Map<Long, Integer> targets = new HashMap<>();
        if (network != null && network.active()) {
            ChromaPalette palette = network.palette();
            for (int rel : network.linkedOffsets()) {
                int index = network.paletteIndexAt(rel);
                targets.put(network.absolute(rel), palette.isValidIndex(index) ? palette.get(index).brightness()
                        : ChromaColor.MAX_BRIGHTNESS);
            }
        }
        for (long pos : previous) {
            if (!targets.containsKey(pos)) {
                request(level, sync, pos, FULL_LIGHT);
            }
        }
        for (Map.Entry<Long, Integer> e : targets.entrySet()) {
            request(level, sync, e.getKey(), e.getValue());
        }
        if (targets.isEmpty()) {
            sync.lit.remove(id);
        } else {
            sync.lit.put(id, new HashSet<>(targets.keySet()));
        }
    }

    private static void request(ServerLevel level, LevelSync sync, long packed, int light) {
        BlockPos pos = BlockPos.of(packed);
        if (level.isLoaded(pos) && level.getBlockState(pos).getBlock() instanceof ChromaCubeBlock) {
            sync.light.request(packed, light);
        }
    }
}
