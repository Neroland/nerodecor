package za.co.neroland.nerodecor.chroma.server.privacy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;

import za.co.neroland.nerodecor.NeroDecorCommon;
import za.co.neroland.nerodecor.chroma.ChromaResult;
import za.co.neroland.nerodecor.chroma.Scene;
import za.co.neroland.nerodecor.chroma.SceneCodec;
import za.co.neroland.nerodecor.chroma.SceneSlots;
import za.co.neroland.nerodecor.chroma.server.ChromaNetworks;
import za.co.neroland.nerodecor.chroma.server.ChromaRole;
import za.co.neroland.nerodecor.chroma.server.HubNetwork;

/**
 * The data-subject access export (GDPR Art. 15/20, POPIA s23) behind {@code /nerodecor privacy export}: everything
 * Chroma holds about <b>one player</b>, as one JSON file at {@code <world>/nerodecor/exports/<uuid>.json}.
 *
 * <ul>
 *   <li><b>Hubs you own</b>: dimension, network id, the hub's anchor coordinates (your own build, so yours to see),
 *       volume, linked-block count, zones (name + channel), saved scenes (name + share code) and who you trusted
 *       (player id + role: a choice you made, so your data too), with a count per role.</li>
 *   <li><b>Roles you hold</b> on other players' hubs: dimension, network id and role only. No coordinates and no
 *       owner, because both belong to someone else.</li>
 * </ul>
 *
 * <p>The path is built from the subject's own UUID and checked to stay inside the export folder. On POSIX file systems
 * (Linux, macOS) the folder and file are made owner-only; on Windows the folder's inherited permissions apply. An export is deleted by an erasure request and after {@link #RETENTION_DAYS}
 * days. Failures are logged by exception class only (the message would hold the path, whose file name is the UUID).
 */
public final class ChromaExport {

    public static final int FORMAT_VERSION = 1;
    /** Export files are deleted this many days after they were written. */
    public static final int RETENTION_DAYS = 7;
    /** One export per player per this many seconds (memory only). */
    public static final int COOLDOWN_SECONDS = 60;
    /** Relative to the world folder; shown to the player. */
    public static final String RELATIVE_DIR = "nerodecor/exports";

    private static final long DAY_MS = 86_400_000L;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Set<PosixFilePermission> OWNER_FILE = PosixFilePermissions.fromString("rw-------");
    private static final Set<PosixFilePermission> OWNER_DIRECTORY = PosixFilePermissions.fromString("rwx------");

    /** One network and the id of the dimension whose store holds it. */
    public record Located(String dimension, HubNetwork network) {
    }

    /** Per-player export cooldown; memory only, cleared on erasure. */
    public static final class Cooldown {
        private final Map<UUID, Long> last = new ConcurrentHashMap<>();

        /** Millis until {@code player} may export again (0 = now); records the attempt when allowed. */
        public long tryAcquire(UUID player, long now) {
            Long previous = last.get(player);
            long wait = previous == null ? 0L : previous + COOLDOWN_SECONDS * 1000L - now;
            if (wait > 0L) {
                return wait;
            }
            last.values().removeIf(at -> now - at > COOLDOWN_SECONDS * 1000L);
            last.put(player, now);
            return 0L;
        }

        public void forget(UUID player) {
            last.remove(player);
        }
    }

    public static final Cooldown COOLDOWN = new Cooldown();

    private ChromaExport() {
    }

    // --- the document (pure) -------------------------------------------------------------------------------------

    /** The export document for {@code subject} (plain-JVM testable). Never reads another player's data into it. */
    public static JsonObject build(UUID subject, List<Located> networks, long nowMillis, int orphanRetentionDays,
            int inactiveRetentionDays) {
        JsonObject root = new JsonObject();
        root.addProperty("mod", NeroDecorCommon.MOD_ID);
        root.addProperty("format", FORMAT_VERSION);
        root.addProperty("exported_at", Instant.ofEpochMilli(nowMillis).toString());
        root.addProperty("subject", subject.toString());

        JsonArray owned = new JsonArray();
        JsonArray roles = new JsonArray();
        for (Located at : networks) {
            HubNetwork network = at.network();
            if (network.orphaned()) {
                continue; // nobody's any more
            }
            if (network.owner().equals(subject)) {
                owned.add(ownedHub(at));
            } else {
                ChromaRole role = network.trusted().get(subject);
                if (role != null) {
                    JsonObject out = new JsonObject();
                    out.addProperty("dimension", at.dimension());
                    out.addProperty("network", Long.toHexString(network.id()));
                    out.addProperty("role", role.id());
                    roles.add(out);
                }
            }
        }
        root.add("hubs_owned", owned);
        root.add("roles_held", roles);

        JsonObject retention = new JsonObject();
        retention.addProperty("broken_hub_network_days", inactiveRetentionDays);
        retention.addProperty("orphaned_network_days", orphanRetentionDays);
        retention.addProperty("export_file_days", RETENTION_DAYS);
        root.add("retention", retention);

        JsonObject notes = new JsonObject();
        notes.addProperty("others", "The only other players' ids in this file are the ones you trusted on your own "
                + "hubs (your choice, so your data). The owners of hubs you hold a role on are not named.");
        notes.addProperty("coordinates", "Hub coordinates appear only for hubs you own. Players near a hub also "
                + "receive its position so their game can draw its colours, as with any block.");
        notes.addProperty("world_text", "Holo sign and wall screen text is part of the world, like vanilla sign "
                + "text, and is not recorded against you, so it is not in this file.");
        notes.addProperty("erasure", "Erasing your data removes you from every trusted list and orphans the hubs you "
                + "own: their colours stay, their names are replaced, nobody can edit them until an operator claims "
                + "them, and they are deleted after " + orphanRetentionDays + " days. This file is deleted too.");
        notes.addProperty("retention", "This file is deleted after " + RETENTION_DAYS + " days.");
        root.add("notes", notes);
        return root;
    }

    private static JsonObject ownedHub(Located at) {
        HubNetwork network = at.network();
        JsonObject out = new JsonObject();
        out.addProperty("dimension", at.dimension());
        out.addProperty("network", Long.toHexString(network.id()));
        JsonArray anchor = new JsonArray();
        anchor.add(network.anchorX());
        anchor.add(network.anchorY());
        anchor.add(network.anchorZ());
        out.add("anchor", anchor);
        JsonArray size = new JsonArray();
        size.add(network.sizeX());
        size.add(network.sizeY());
        size.add(network.sizeZ());
        out.add("volume", size);
        out.addProperty("placed", network.active());
        if (!network.active() && network.inactiveSince() > 0L) {
            out.addProperty("broken_since", Instant.ofEpochMilli(network.inactiveSince()).toString());
        }
        out.addProperty("auto_adopt", network.autoAdopt());
        out.addProperty("linked_blocks", network.size());
        JsonArray zones = new JsonArray();
        for (HubNetwork.Zone zone : network.zones()) {
            JsonObject z = new JsonObject();
            z.addProperty("name", zone.name());
            z.addProperty("channel", zone.channel());
            zones.add(z);
        }
        out.add("zones", zones);
        JsonArray scenes = new JsonArray();
        SceneSlots slots = network.scenes();
        for (int slot = 0; slot < SceneSlots.MAX_SLOTS; slot++) {
            Scene scene = slots.scene(slot);
            if (scene == null) {
                continue;
            }
            JsonObject s = new JsonObject();
            s.addProperty("slot", slot + 1);
            s.addProperty("name", scene.name());
            ChromaResult<String> code = SceneCodec.toShareCode(scene);
            if (code.isOk()) {
                s.addProperty("share_code", code.value());
            }
            scenes.add(s);
        }
        out.add("scenes", scenes);
        out.addProperty("scene_trigger", slots.trigger().id());
        Map<ChromaRole, Integer> counts = new EnumMap<>(ChromaRole.class);
        for (ChromaRole role : network.trusted().values()) {
            counts.merge(role, 1, Integer::sum);
        }
        JsonObject trusted = new JsonObject();
        for (Map.Entry<ChromaRole, Integer> e : counts.entrySet()) {
            trusted.addProperty(e.getKey().id(), e.getValue());
        }
        out.add("trusted_players_per_role", trusted);
        JsonArray grants = new JsonArray();
        for (Map.Entry<UUID, ChromaRole> e : network.trusted().entrySet()) {
            JsonObject grant = new JsonObject();
            grant.addProperty("player", e.getKey().toString());
            grant.addProperty("role", e.getValue().id());
            grants.add(grant);
        }
        out.add("trusted_players", grants);
        return out;
    }

    /** Every network on the server, with its dimension id (server thread). */
    public static List<Located> collect(MinecraftServer server) {
        List<Located> out = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            String dimension = level.dimension().identifier().toString();
            for (HubNetwork network : ChromaNetworks.store(level).all()) {
                out.add(new Located(dimension, network));
            }
        }
        return out;
    }

    public static String toJson(JsonObject document) {
        return GSON.toJson(document);
    }

    // --- files ---------------------------------------------------------------------------------------------------

    private static volatile Path directoryOverride;

    /** Test seam: the export folder used without a server ({@code null} restores). */
    public static void overrideDirectory(Path directory) {
        directoryOverride = directory;
    }

    /** The export folder of {@code server} (or the test override); null without either. */
    public static Path directory(MinecraftServer server) {
        Path forced = directoryOverride;
        if (forced != null) {
            return forced;
        }
        return server == null ? null
                : server.getWorldPath(LevelResource.ROOT).resolve("nerodecor").resolve("exports").normalize();
    }

    /** One subject's export path, refusing anything that escapes the export folder. */
    public static Path file(Path directory, UUID subject) {
        return file(directory, String.valueOf(subject));
    }

    static Path file(Path directory, String name) {
        Path root = directory.normalize();
        Path output = root.resolve(name + ".json").normalize();
        if (!output.startsWith(root) || output.getParent() == null || !output.getParent().equals(root)) {
            throw new IllegalStateException("Unsafe export path");
        }
        return output;
    }

    /** The path the player is told, relative to the world folder. */
    public static String relativePath(UUID subject) {
        return RELATIVE_DIR + "/" + subject + ".json";
    }

    /** Writes the document, replacing an earlier export. Server thread. */
    public static String write(Path directory, UUID subject, JsonObject document) throws IOException {
        Path output = file(directory, subject);
        Files.createDirectories(directory);
        try {
            Files.setPosixFilePermissions(directory, OWNER_DIRECTORY);
        } catch (UnsupportedOperationException | IOException bestEffort) {
            // Not POSIX (Windows), or not ours to re-mode: the platform defaults apply.
        }
        try {
            if (Files.exists(output)) {
                Files.setPosixFilePermissions(output, OWNER_FILE);
            } else {
                Files.createFile(output, PosixFilePermissions.asFileAttribute(OWNER_FILE));
            }
        } catch (UnsupportedOperationException notPosix) {
            // No POSIX permission view on this file system.
        }
        Files.writeString(output, toJson(document), StandardCharsets.UTF_8);
        return relativePath(subject);
    }

    /** Erasure: an export must not outlive the data it was taken from. Never throws. */
    public static boolean delete(Path directory, UUID subject) {
        if (directory == null || subject == null) {
            return false;
        }
        try {
            return Files.deleteIfExists(file(directory, subject));
        } catch (IOException | RuntimeException failure) {
            NeroDecorCommon.LOGGER.warn("[NeroDecor] A Chroma export file could not be deleted ({}).",
                    failure.getClass().getName());
            return false;
        }
    }

    /** Retention: deletes export files older than {@link #RETENTION_DAYS}. Returns how many. Never throws. */
    public static int purgeExpired(Path directory, long nowMillis) {
        if (directory == null || !Files.isDirectory(directory)) {
            return 0;
        }
        long cutoff = nowMillis - RETENTION_DAYS * DAY_MS;
        int removed = 0;
        try (Stream<Path> listing = Files.list(directory)) {
            for (Path file : listing.toList()) {
                if (Files.isRegularFile(file) && file.getFileName().toString().endsWith(".json")
                        && Files.getLastModifiedTime(file).toMillis() <= cutoff && Files.deleteIfExists(file)) {
                    removed++;
                }
            }
        } catch (IOException | RuntimeException failure) {
            NeroDecorCommon.LOGGER.warn("[NeroDecor] Chroma export retention sweep failed ({}).",
                    failure.getClass().getName());
        }
        return removed;
    }
}
