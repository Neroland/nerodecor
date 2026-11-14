package za.co.neroland.nerodecor.chroma.server.privacy;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.function.LongSupplier;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import za.co.neroland.nerolandcore.data.PlayerDataErasure;

import za.co.neroland.nerodecor.NeroDecorCommon;
import za.co.neroland.nerodecor.chroma.server.ChromaConfig;
import za.co.neroland.nerodecor.chroma.server.ChromaNetworkData;
import za.co.neroland.nerodecor.chroma.server.ChromaNetworkStore;
import za.co.neroland.nerodecor.chroma.server.ChromaNetworks;
import za.co.neroland.nerodecor.chroma.server.ChromaVerdict;
import za.co.neroland.nerodecor.chroma.server.HubNetwork;

/**
 * Chroma's POPIA/GDPR surface: the eraser registered with Core's {@code PlayerDataErasure}, the two retention
 * sweeps, and the operator claim of an orphaned network.
 *
 * <ul>
 *   <li><b>Erasure</b> of player P: P leaves every trusted list; every network P owns is <em>orphaned</em> (owner
 *       cleared, trusted list emptied, zone and scene names replaced with neutral defaults, auto-adopt off, orphan
 *       clock started). Its linked blocks keep their colours, because decor is not personal data, but nobody may edit
 *       it until an operator claims it. P's export file and rate-limit bucket go as well. Every step runs even if an
 *       earlier one failed; then every dimension's primary file is written and its last-known-good backup refreshed
 *       synchronously ({@link ChromaNetworkData#persistNow}), so neither copy on disk keeps P. Failures are reported
 *       once, by exception class only.</li>
 *   <li><b>Retention</b>: orphaned networks are deleted after {@code chroma.orphanRetentionDays} (default 30);
 *       separately, networks whose hub has been broken are deleted after {@code chroma.inactiveRetentionDays}
 *       (default 90). Whichever comes first wins. Export files are deleted after
 *       {@value ChromaExport#RETENTION_DAYS} days. {@link #tick} runs these sweeps when a server starts and every
 *       ten minutes after, whether or not Chroma is enabled.</li>
 * </ul>
 *
 * <p>Nothing here logs a player, a UUID, a name or a position.
 */
public final class ChromaPrivacy {

    private static final long DAY_MS = 24L * 60L * 60L * 1000L;

    private static final long SWEEP_INTERVAL_MS = 10L * 60L * 1000L;
    /** How often logged-out players' rate-limit buckets are dropped. */
    private static final int LIMITER_PRUNE_TICKS = 100;

    private static volatile LongSupplier clock = System::currentTimeMillis;
    private static WeakReference<MinecraftServer> sweptServer = new WeakReference<>(null);
    private static long lastSweep;
    private static int ticks;

    private ChromaPrivacy() {
    }

    /** Wall-clock millis used for orphan and retention times (overridable by tests). */
    public static long now() {
        return clock.getAsLong();
    }

    /** Test seam: replaces the clock ({@code null} restores the system clock). */
    public static void overrideClock(LongSupplier override) {
        clock = override == null ? System::currentTimeMillis : override;
    }

    /**
     * Runs a full erasure through Core's shared {@code PlayerDataErasure} (every registered mod, Chroma included), the
     * way an operator or {@code /neroland data eraseme} would. Verification code calls this so it exercises the real
     * registration rather than {@link #erase} directly.
     */
    public static void eraseEverywhere(MinecraftServer server, UUID player) {
        PlayerDataErasure.erase(server, player);
    }

    /**
     * The Core-registered eraser. Never logs who. Each step is attempted even if an earlier one failed; the primary
     * files and backups are written in a {@code finally}, and any failure is reported once afterwards (exception
     * classes only) so Core records the erasure as incomplete.
     */
    public static void erase(MinecraftServer server, UUID player) {
        long now = now();
        List<String> failures = new ArrayList<>();
        List<ServerLevel> levels = new ArrayList<>();
        try {
            for (ServerLevel level : server.getAllLevels()) {
                levels.add(level);
                try {
                    ChromaNetworkData.get(level).store().eraseFor(player, now);
                } catch (RuntimeException failure) {
                    failures.add(failure.getClass().getSimpleName());
                }
            }
            try {
                ChromaNetworks.limiter().forget(player);
                ChromaExport.COOLDOWN.forget(player);
            } catch (RuntimeException failure) {
                failures.add(failure.getClass().getSimpleName());
            }
            try {
                Path directory = ChromaExport.directory(server);
                if (directory != null) {
                    Files.deleteIfExists(ChromaExport.file(directory, player));
                }
            } catch (IOException | RuntimeException failure) {
                failures.add(failure.getClass().getSimpleName());
            }
        } finally {
            // Written even when nothing changed here: an earlier erasure's state may not have reached disk yet.
            for (ServerLevel level : levels) {
                try {
                    ChromaNetworkData.persistNow(level, ChromaNetworkData.get(level));
                } catch (RuntimeException failure) {
                    failures.add(failure.getClass().getSimpleName());
                }
            }
        }
        if (!failures.isEmpty()) {
            StringJoiner kinds = new StringJoiner(", ");
            new HashSet<>(failures).forEach(kinds::add);
            NeroDecorCommon.LOGGER.warn("[NeroDecor] Chroma erasure finished with {} failed step(s) ({}).",
                    failures.size(), kinds);
            throw new IllegalStateException("Chroma erasure incomplete: " + failures.size() + " failed step(s) ("
                    + kinds + ")");
        }
    }

    /**
     * Server tick (end), run whether or not Chroma is enabled: the first tick of a server (start-up) and every ten
     * minutes after run both network retention sweeps in every dimension and the export-file retention; every few
     * seconds the rate limiter drops the buckets of players who logged out.
     */
    public static synchronized void tick(MinecraftServer server) {
        long now = now();
        boolean started = sweptServer.get() != server;
        if (started || now - lastSweep >= SWEEP_INTERVAL_MS) {
            sweptServer = new WeakReference<>(server);
            lastSweep = now;
            sweepAll(server, now);
        }
        if (++ticks % LIMITER_PRUNE_TICKS == 0) {
            Set<UUID> online = new HashSet<>();
            server.getPlayerList().getPlayers().forEach(p -> online.add(p.getUUID()));
            ChromaNetworks.limiter().retainOnly(online);
        }
    }

    /** Every retention sweep now: networks in every dimension, then expired export files. Never throws. */
    public static void sweepAll(MinecraftServer server, long now) {
        for (ServerLevel level : server.getAllLevels()) {
            try {
                ChromaNetworkData data = ChromaNetworkData.get(level);
                data.dueForSweep(now, 0L); // restarts the store's own throttle
                sweep(level, data, now);
            } catch (RuntimeException failure) {
                NeroDecorCommon.LOGGER.warn("[NeroDecor] A Chroma retention sweep failed ({}).",
                        failure.getClass().getName());
            }
        }
        ChromaExport.purgeExpired(ChromaExport.directory(server), now);
    }

    /** Orphan retention in millis, from config. */
    public static long orphanRetentionMillis() {
        return ChromaConfig.ORPHAN_RETENTION_DAYS.get() * DAY_MS;
    }

    /** Broken-hub retention in millis, from config. */
    public static long inactiveRetentionMillis() {
        return ChromaConfig.INACTIVE_RETENTION_DAYS.get() * DAY_MS;
    }

    /** Both retention sweeps over one store. @return how many networks were deleted */
    public static int sweep(ChromaNetworkStore store, long now) {
        return store.purgeInactive(now, inactiveRetentionMillis()) + store.purgeOrphaned(now, orphanRetentionMillis());
    }

    /**
     * Both sweeps over one dimension's store, writing the primary file and the backup straight away when anything
     * went, so deleted networks linger in neither copy. @return how many networks were deleted
     */
    public static int sweep(ServerLevel level, ChromaNetworkData data, long now) {
        int removed = sweep(data.store(), now);
        if (removed > 0) {
            ChromaNetworkData.persistNow(level, data);
        }
        return removed;
    }

    /**
     * An operator claims the orphaned network whose volume holds {@code x,y,z} in {@code level} (nearest hub wins).
     * The caller has already checked the operator permission.
     */
    public static ChromaVerdict claimAt(ServerLevel level, int x, int y, int z, UUID newOwner) {
        ChromaNetworkStore store = ChromaNetworks.store(level);
        HubNetwork best = null;
        long bestDistance = Long.MAX_VALUE;
        for (HubNetwork network : store.all()) {
            if (network.orphaned() && network.contains(x, y, z)) {
                long dx = x - network.anchorX();
                long dy = y - network.anchorY();
                long dz = z - network.anchorZ();
                long distance = dx * dx + dy * dy + dz * dz;
                if (distance < bestDistance) {
                    best = network;
                    bestDistance = distance;
                }
            }
        }
        return best == null ? ChromaVerdict.NO_NETWORK : store.claimOrphan(best.id(), newOwner);
    }
}
