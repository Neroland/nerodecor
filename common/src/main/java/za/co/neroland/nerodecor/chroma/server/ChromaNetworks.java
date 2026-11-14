package za.co.neroland.nerodecor.chroma.server;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

import za.co.neroland.nerolandcore.data.PlayerDataErasure;

import za.co.neroland.nerodecor.NeroDecorCommon;
import za.co.neroland.nerodecor.chroma.RelativePos;
import za.co.neroland.nerodecor.chroma.server.privacy.ChromaPrivacy;
import za.co.neroland.nerodecor.content.block.ChromaHubBlockEntity;

/**
 * The game-side face of Chroma hub networks: hub placement and removal, linking (single, box, flood, auto-adopt),
 * lookups for the renderer, and erasure. Every edit runs the pure {@link ChromaValidator} rules, the per-player
 * {@link ChromaRateLimiter}, and {@link ChromaGuard} (vanilla + loader protection) before the store changes.
 *
 * <p>Logging: nothing here logs players, UUIDs or positions.
 */
public final class ChromaNetworks {

    /** Result of one block's link state as the renderer will see it. */
    public record Applied(long network, int paletteIndex, int zone) {
    }

    /** Outcome of a multi-block selection: how many joined, and the first refusal (OK if none). */
    public record Selection(int linked, int refused, ChromaVerdict verdict) {
    }

    /** A network and the dimension whose store holds it. */
    public record Located(ServerLevel level, HubNetwork network) {
    }

    private static final long SWEEP_INTERVAL_MS = 10L * 60L * 1000L;

    private static volatile ChromaRateLimiter limiter;

    private ChromaNetworks() {
    }

    /** Registers the erasure hook with Core. Called once from common init. */
    public static void init() {
        PlayerDataErasure.register(ChromaPrivacy::erase);
    }

    // --- stores ---------------------------------------------------------------------------------------------------

    /** This dimension's store (through Core's recovery guard), with a throttled retention sweep. */
    public static ChromaNetworkStore store(ServerLevel level) {
        ChromaNetworkData data = ChromaNetworkData.get(level);
        long now = ChromaPrivacy.now();
        if (data.dueForSweep(now, SWEEP_INTERVAL_MS)) {
            ChromaPrivacy.sweep(level, data, now); // broken-hub (90 d) and orphaned (30 d) retention
        }
        return data.store();
    }

    /** The shared limiter, rebuilt if the configured rates change. */
    public static ChromaRateLimiter limiter() {
        int eps = ChromaConfig.EDITS_PER_SECOND.get();
        int opm = ChromaConfig.BLOCK_OPS_PER_MINUTE.get();
        ChromaRateLimiter current = limiter;
        if (current == null || current.editsPerSecond() != eps || current.opsPerMinute() != opm) {
            synchronized (ChromaNetworks.class) {
                current = limiter;
                if (current == null || current.editsPerSecond() != eps || current.opsPerMinute() != opm) {
                    current = new ChromaRateLimiter(eps, opm);
                    limiter = current;
                }
            }
        }
        return current;
    }

    public static Optional<Located> find(MinecraftServer server, long id) {
        if (id == 0L) {
            return Optional.empty();
        }
        for (ServerLevel level : server.getAllLevels()) {
            HubNetwork network = store(level).get(id);
            if (network != null) {
                return Optional.of(new Located(level, network));
            }
        }
        return Optional.empty();
    }

    /** Networks {@code player} owns across every dimension. */
    public static int ownedCount(MinecraftServer server, UUID player) {
        int n = 0;
        for (ServerLevel level : server.getAllLevels()) {
            n += store(level).ownedBy(player);
        }
        return n;
    }

    private static long newId(MinecraftServer server) {
        while (true) {
            long id = ThreadLocalRandom.current().nextLong();
            if (id != 0L && find(server, id).isEmpty()) {
                return id;
            }
        }
    }

    // --- hub lifecycle --------------------------------------------------------------------------------------------

    /** Whether {@code player} may start a new network now (hub cap, master switch). */
    public static ChromaVerdict canStartNetwork(ServerPlayer player) {
        return ChromaValidator.checkNewHub(ownedCount(((ServerLevel) player.level()).getServer(), player.getUUID()),
                ChromaLimits.fromConfig());
    }

    /**
     * A hub block was placed. A hub item that carries a network re-links it here (moving it between dimensions if
     * needed, dropping stored blocks that are no longer capable or that another hub now controls); otherwise a new
     * network is started for the placer.
     *
     * @return the network id the hub block entity should hold (0 = inert hub)
     */
    public static long hubPlaced(ServerLevel level, BlockPos pos, ServerPlayer placer, long carried) {
        ChromaLimits limits = ChromaLimits.fromConfig();
        if (!limits.enabled()) {
            return carried; // keep the data with the hub; nothing is activated while Chroma is off
        }
        MinecraftServer server = level.getServer();
        if (carried != 0L) {
            Optional<Located> found = find(server, carried);
            if (found.isPresent()) {
                Located at = found.get();
                HubNetwork network = at.network();
                boolean duplicate = network.active() && hubPresent(at.level(), network)
                        && !(at.level() == level && network.anchorX() == pos.getX()
                        && network.anchorY() == pos.getY() && network.anchorZ() == pos.getZ());
                if (!duplicate) {
                    if (placer != null && !network.roleOf(placer.getUUID()).atLeast(ChromaRole.ADMIN)) {
                        tell(placer, ChromaVerdict.NO_PERMISSION);
                        return carried; // stays with the hub, inactive, until its owner places it
                    }
                    if (at.level() != level) {
                        HubNetwork moved = store(at.level()).remove(network.id());
                        store(level).adopt(moved);
                    }
                    store(level).activate(network.id(), pos.getX(), pos.getY(), pos.getZ(),
                            packed -> keepOnRelink(level, packed));
                    return network.id();
                }
            }
        }
        if (placer == null) {
            return 0L;
        }
        ChromaVerdict verdict = ChromaValidator.checkNewHub(ownedCount(server, placer.getUUID()), limits);
        if (!verdict.isOk()) {
            tell(placer, verdict);
            return 0L;
        }
        long id = newId(server);
        store(level).create(id, placer.getUUID(), pos.getX(), pos.getY(), pos.getZ(), limits.volumeX(),
                limits.volumeY(), limits.volumeZ());
        return id;
    }

    /** A hub block was removed: its network stops controlling blocks (they show their own colour again). */
    public static void hubRemoved(ServerLevel level, BlockPos pos, long id) {
        if (id == 0L) {
            return;
        }
        ChromaNetworkStore store = store(level);
        HubNetwork network = store.get(id);
        if (network != null && network.anchorX() == pos.getX() && network.anchorY() == pos.getY()
                && network.anchorZ() == pos.getZ()) {
            boolean wasActive = network.active();
            store.deactivate(id, ChromaPrivacy.now());
            if (wasActive) {
                ChromaHooks.hubBroken(network);
            }
        }
    }

    private static boolean keepOnRelink(ServerLevel level, long packed) {
        BlockPos pos = BlockPos.of(packed);
        return !level.isLoaded(pos) || ChromaTags.isCapable(level.getBlockState(pos));
    }

    /** Whether the network's hub block is still where it should be (true if that chunk is not loaded). */
    static boolean hubPresent(ServerLevel level, HubNetwork network) {
        BlockPos anchor = new BlockPos(network.anchorX(), network.anchorY(), network.anchorZ());
        if (!level.isLoaded(anchor)) {
            return true;
        }
        return level.getBlockEntity(anchor) instanceof ChromaHubBlockEntity hub && hub.networkId() == network.id();
    }

    // --- lookups ----------------------------------------------------------------------------------------------------

    /**
     * What the network applies to the block at {@code pos}, if anything. A network whose hub has vanished without a
     * removal callback (e.g. replaced by a command) is deactivated here, so its blocks revert.
     */
    public static Optional<Applied> applied(ServerLevel level, BlockPos pos) {
        ChromaNetworkStore store = store(level);
        long id = store.claimant(pos.getX(), pos.getY(), pos.getZ());
        HubNetwork network = id == 0L ? null : store.get(id);
        if (network == null || !network.active()) {
            return Optional.empty();
        }
        if (!hubPresent(level, network)) {
            store.deactivate(id, ChromaPrivacy.now());
            return Optional.empty();
        }
        int rel = RelativePos.pack(pos.getX() - network.anchorX(), pos.getY() - network.anchorY(),
                pos.getZ() - network.anchorZ());
        return Optional.of(new Applied(id, network.paletteIndexAt(rel), network.zoneAt(rel)));
    }

    // --- edits ----------------------------------------------------------------------------------------------------

    /** Link or unlink one block (Chroma Linker click). */
    public static ChromaVerdict toggle(ServerLevel level, ServerPlayer player, long id, BlockPos pos) {
        ChromaNetworkStore store = store(level);
        HubNetwork network = store.get(id);
        if (network != null && store.claimant(pos.getX(), pos.getY(), pos.getZ()) == id) {
            return unlink(level, player, id, pos);
        }
        return link(level, player, id, pos);
    }

    /** Link one block: rate limit, rules, protection. */
    public static ChromaVerdict link(ServerLevel level, ServerPlayer player, long id, BlockPos pos) {
        ChromaVerdict pre = precheck(level, player, id);
        if (!pre.isOk()) {
            return pre;
        }
        ChromaVerdict rate = limiter().tryAcquire(player.getUUID(), 1, System.nanoTime());
        if (!rate.isOk()) {
            return rate;
        }
        return linkOne(level, player, store(level), id, pos, ChromaLimits.fromConfig());
    }

    /** Unlink one block: rate limit, rules, protection. */
    public static ChromaVerdict unlink(ServerLevel level, ServerPlayer player, long id, BlockPos pos) {
        ChromaVerdict pre = precheck(level, player, id);
        if (!pre.isOk()) {
            return pre;
        }
        ChromaNetworkStore store = store(level);
        ChromaVerdict rules = ChromaValidator.checkUnlink(store.get(id), player.getUUID(), pos.getX(), pos.getY(),
                pos.getZ());
        if (!rules.isOk()) {
            return rules;
        }
        ChromaVerdict rate = limiter().tryAcquire(player.getUUID(), 1, System.nanoTime());
        if (!rate.isOk()) {
            return rate;
        }
        if (!ChromaGuard.mayEdit(player, level, pos, level.getBlockState(pos))) {
            return ChromaVerdict.PROTECTED;
        }
        return store.unlink(id, player.getUUID(), pos.getX(), pos.getY(), pos.getZ());
    }

    /** Links every capable block inside the box {@code a..b} (clipped to the hub volume), capped. */
    public static Selection linkBox(ServerLevel level, ServerPlayer player, long id, BlockPos a, BlockPos b) {
        ChromaVerdict pre = precheck(level, player, id);
        if (!pre.isOk()) {
            return new Selection(0, 0, pre);
        }
        ChromaNetworkStore store = store(level);
        HubNetwork network = store.get(id);
        int minX = Math.max(Math.min(a.getX(), b.getX()), network.anchorX() + HubNetwork.minOffset(network.sizeX()));
        int minY = Math.max(Math.min(a.getY(), b.getY()), network.anchorY() + HubNetwork.minOffset(network.sizeY()));
        int minZ = Math.max(Math.min(a.getZ(), b.getZ()), network.anchorZ() + HubNetwork.minOffset(network.sizeZ()));
        int maxX = Math.min(Math.max(a.getX(), b.getX()), network.anchorX() + HubNetwork.maxOffset(network.sizeX()));
        int maxY = Math.min(Math.max(a.getY(), b.getY()), network.anchorY() + HubNetwork.maxOffset(network.sizeY()));
        int maxZ = Math.min(Math.max(a.getZ(), b.getZ()), network.anchorZ() + HubNetwork.maxOffset(network.sizeZ()));
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            return new Selection(0, 0, ChromaVerdict.OUT_OF_VOLUME);
        }
        long volume = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        ChromaVerdict size = ChromaValidator.checkSelection(volume, ChromaLimits.fromConfig());
        if (!size.isOk()) {
            return new Selection(0, 0, size);
        }
        List<BlockPos> candidates = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    cursor.set(x, y, z);
                    if (level.isLoaded(cursor) && ChromaTags.isCapable(level.getBlockState(cursor))
                            && store.claimant(x, y, z) != id) {
                        candidates.add(cursor.immutable());
                    }
                }
            }
        }
        return linkAll(level, player, store, id, candidates);
    }

    /** Links the capable blocks connected to {@code start} (6-neighbour flood inside the hub volume), capped. */
    public static Selection linkFlood(ServerLevel level, ServerPlayer player, long id, BlockPos start) {
        ChromaVerdict pre = precheck(level, player, id);
        if (!pre.isOk()) {
            return new Selection(0, 0, pre);
        }
        ChromaNetworkStore store = store(level);
        HubNetwork network = store.get(id);
        ChromaLimits limits = ChromaLimits.fromConfig();
        if (!ChromaTags.isCapable(level.getBlockState(start))) {
            return new Selection(0, 0, ChromaVerdict.NOT_CAPABLE);
        }
        if (!network.contains(start.getX(), start.getY(), start.getZ())) {
            return new Selection(0, 0, ChromaVerdict.OUT_OF_VOLUME);
        }
        int budget = limits.maxSelection();
        List<BlockPos> candidates = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start.immutable());
        seen.add(start.asLong());
        while (!queue.isEmpty() && candidates.size() < budget) {
            BlockPos pos = queue.poll();
            long claimant = store.claimant(pos.getX(), pos.getY(), pos.getZ());
            if (claimant != 0L && claimant != id) {
                continue; // another hub's block: neither linked nor walked through
            }
            if (claimant != id) {
                candidates.add(pos);
            }
            for (Direction d : Direction.values()) {
                BlockPos next = pos.relative(d);
                if (seen.size() >= budget * 6 || !seen.add(next.asLong())) {
                    continue;
                }
                if (network.contains(next.getX(), next.getY(), next.getZ()) && level.isLoaded(next)
                        && ChromaTags.isCapable(level.getBlockState(next))) {
                    queue.add(next);
                }
            }
        }
        return linkAll(level, player, store, id, candidates);
    }

    /**
     * Auto-adopt: a capable block placed by a network's owner inside the volume of one of their auto-adopt hubs joins
     * the nearest such hub. Silent; never displaces another hub's block.
     */
    public static boolean autoAdopt(ServerLevel level, BlockPos pos, ServerPlayer placer) {
        ChromaLimits limits = ChromaLimits.fromConfig();
        if (placer == null || !limits.enabled() || !ChromaTags.isCapable(level.getBlockState(pos))) {
            return false;
        }
        ChromaNetworkStore store = store(level);
        if (store.claimant(pos.getX(), pos.getY(), pos.getZ()) != 0L) {
            return false;
        }
        HubNetwork best = null;
        long bestDistance = Long.MAX_VALUE;
        for (HubNetwork network : store.all()) {
            if (network.active() && network.autoAdopt() && network.owner().equals(placer.getUUID())
                    && network.contains(pos.getX(), pos.getY(), pos.getZ())) {
                long dx = pos.getX() - network.anchorX();
                long dy = pos.getY() - network.anchorY();
                long dz = pos.getZ() - network.anchorZ();
                long distance = dx * dx + dy * dy + dz * dz;
                if (distance < bestDistance) {
                    best = network;
                    bestDistance = distance;
                }
            }
        }
        return best != null && store.link(best.id(), placer.getUUID(), pos.getX(), pos.getY(), pos.getZ(), true,
                limits) == ChromaVerdict.OK;
    }

    private static ChromaVerdict precheck(ServerLevel level, ServerPlayer player, long id) {
        if (!ChromaConfig.ENABLED.get()) {
            return ChromaVerdict.DISABLED;
        }
        HubNetwork network = store(level).get(id);
        if (network == null) {
            return ChromaVerdict.NO_NETWORK;
        }
        if (!network.active()) {
            return ChromaVerdict.INACTIVE;
        }
        return ChromaValidator.checkAction(network, player.getUUID(), ChromaAction.LINK);
    }

    private static ChromaVerdict linkOne(ServerLevel level, ServerPlayer player, ChromaNetworkStore store, long id,
            BlockPos pos, ChromaLimits limits) {
        BlockState state = level.getBlockState(pos);
        boolean capable = ChromaTags.isCapable(state);
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        ChromaVerdict rules = ChromaValidator.checkLink(store.get(id), player.getUUID(), x, y, z, capable,
                store.claimant(x, y, z), limits);
        if (rules != ChromaVerdict.OK) {
            return rules;
        }
        if (!ChromaGuard.mayEdit(player, level, pos, state)) {
            return ChromaVerdict.PROTECTED;
        }
        return store.link(id, player.getUUID(), x, y, z, capable, limits);
    }

    private static Selection linkAll(ServerLevel level, ServerPlayer player, ChromaNetworkStore store, long id,
            List<BlockPos> candidates) {
        ChromaLimits limits = ChromaLimits.fromConfig();
        HubNetwork network = store.get(id);
        int room = Math.max(0, limits.maxBlocksPerHub() - network.size());
        int take = Math.min(Math.min(candidates.size(), limits.maxSelection()), room);
        ChromaVerdict rate = limiter().tryAcquire(player.getUUID(), take, System.nanoTime());
        if (!rate.isOk()) {
            return new Selection(0, candidates.size(), rate);
        }
        int linked = 0;
        int refused = candidates.size() - take;
        ChromaVerdict first = take < candidates.size()
                ? (room < candidates.size() ? ChromaVerdict.BLOCK_CAP : ChromaVerdict.SELECTION_TOO_LARGE)
                : ChromaVerdict.OK;
        for (int k = 0; k < take; k++) {
            ChromaVerdict verdict = linkOne(level, player, store, id, candidates.get(k), limits);
            if (verdict == ChromaVerdict.OK) {
                linked++;
            } else if (verdict != ChromaVerdict.ALREADY_LINKED) {
                refused++;
                if (first == ChromaVerdict.OK) {
                    first = verdict;
                }
            }
        }
        return new Selection(linked, refused, first);
    }

    // --- erasure + messages -----------------------------------------------------------------------------------------

    /** Action-bar feedback for a verdict ({@code message.nerodecor.chroma.<verdict>}). */
    public static void tell(ServerPlayer player, ChromaVerdict verdict) {
        if (player != null && player.connection != null) {
            player.sendSystemMessage(Component.translatable("message." + NeroDecorCommon.MOD_ID + ".chroma."
                    + verdict.id()), true);
        }
    }

    /** Action-bar feedback for a selection. */
    public static void tell(ServerPlayer player, Selection selection) {
        if (player != null && player.connection != null) {
            player.sendSystemMessage(Component.translatable("message." + NeroDecorCommon.MOD_ID + ".chroma.selection",
                    selection.linked(), selection.refused()), true);
        }
    }
}
