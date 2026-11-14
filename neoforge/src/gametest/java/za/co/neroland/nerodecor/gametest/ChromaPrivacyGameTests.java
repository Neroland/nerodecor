package za.co.neroland.nerodecor.gametest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

import com.google.gson.JsonObject;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.registries.DeferredRegister;

import za.co.neroland.nerodecor.chroma.ChromaResult;
import za.co.neroland.nerodecor.chroma.Scene;
import za.co.neroland.nerodecor.chroma.SceneCodec;
import za.co.neroland.nerodecor.chroma.server.ChromaLimits;
import za.co.neroland.nerodecor.chroma.server.ChromaNetworkData;
import za.co.neroland.nerodecor.chroma.server.ChromaNetworkStore;
import za.co.neroland.nerodecor.chroma.server.ChromaNetworks;
import za.co.neroland.nerodecor.chroma.server.ChromaRole;
import za.co.neroland.nerodecor.chroma.server.ChromaVerdict;
import za.co.neroland.nerodecor.chroma.server.HubNetwork;
import za.co.neroland.nerodecor.chroma.server.privacy.ChromaExport;
import za.co.neroland.nerodecor.chroma.server.privacy.ChromaPrivacy;
import za.co.neroland.nerodecor.content.block.ChromaHubBlockEntity;
import za.co.neroland.nerodecor.registry.ChromaContent;
import za.co.neroland.nerodecor.registry.DecorBlocks;

/**
 * Chroma Stage 7 (compliance) GameTests. An erasure request through Core's {@code PlayerDataErasure} (via
 * {@code ChromaPrivacy.eraseEverywhere}) must leave nothing of the player <em>on disk</em>: the test first proves that
 * the primary {@code chroma_networks.dat} and the last-known-good backup both exist and hold player B (so the search
 * cannot pass vacuously), then searches both files after erasure for every UUID encoding (string, undashed hex, the
 * two longs, the NBT int array). Zone names are read from the raw NBT and scene names by decoding each stored slot,
 * not by searching share-code text. Also covered: an owner's erasure orphans their hubs, the export file is deleted
 * by erasure, export files expire after seven days through the scheduled sweep, and orphans are deleted once their
 * retention runs out (injected clock).
 */
final class ChromaPrivacyGameTests {

    private static final long DAY = 86_400_000L;
    private static final String ZONE_NAME = "SecretLair";
    private static final String SCENE_NAME = "PartyLights";

    private ChromaPrivacyGameTests() {
    }

    static void register(DeferredRegister<Consumer<GameTestHelper>> functions) {
        functions.register("chroma_erasure_leaves_nothing", () -> ChromaPrivacyGameTests::erasureLeavesNothing);
    }

    private static long placeHub(GameTestHelper helper, BlockPos local, ServerPlayer placer) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(local);
        BlockState state = ChromaContent.HUB.get().defaultBlockState();
        level.setBlock(pos, state, 3);
        state.getBlock().setPlacedBy(level, pos, state, placer, new ItemStack(ChromaContent.HUB_ITEM.get()));
        ChromaHubBlockEntity hub = (ChromaHubBlockEntity) level.getBlockEntity(pos);
        helper.assertTrue(hub != null && hub.networkId() != 0L, "hub did not start a network");
        return hub.networkId();
    }

    /** SNBT of the in-memory store, whitespace removed. */
    private static String serialised(ChromaNetworkData data) {
        Tag tag = ChromaNetworkData.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow();
        return tag.toString().replaceAll("\\s", "");
    }

    /** Every way a UUID can appear in SNBT: dashed and undashed text (any case), the two longs, the int array. */
    private static boolean mentions(String snbt, UUID who) {
        String lower = snbt.toLowerCase(Locale.ROOT);
        int[] ints = UUIDUtil.uuidToIntArray(who);
        String array = "[I;" + ints[0] + "," + ints[1] + "," + ints[2] + "," + ints[3] + "]";
        return lower.contains(who.toString())
                || lower.contains(who.toString().replace("-", ""))
                || snbt.contains(array)
                || snbt.contains(ints[0] + "," + ints[1] + "," + ints[2] + "," + ints[3])
                || snbt.contains(who.getMostSignificantBits() + "L")
                || snbt.contains(who.getLeastSignificantBits() + "L");
    }

    private static Path dataFolder(ServerLevel level) {
        return DimensionType.getStorageFolder(level.dimension(), level.getServer().getWorldPath(LevelResource.ROOT))
                .resolve("data");
    }

    private static Path primaryFile(ServerLevel level) {
        return dataFolder(level).resolve(ChromaNetworkData.fileStem(ChromaNetworkData.TYPE) + ".dat");
    }

    private static Path backupFile(ServerLevel level) {
        return dataFolder(level).resolve(ChromaNetworkData.RECOVERY_NAME.replace(':', '_') + "_backup.dat");
    }

    /** The file as a tag, or {@code null} when it is missing or unreadable. */
    private static CompoundTag read(Path file) {
        try {
            return Files.exists(file) ? NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap()) : null;
        } catch (IOException e) {
            return null;
        }
    }

    private static String snbt(CompoundTag tag) {
        return tag == null ? "" : tag.toString().replaceAll("\\s", "");
    }

    /** Every string stored under {@code key} anywhere in the tree (raw: nothing is sanitised on the way). */
    private static void strings(Tag tag, String key, boolean under, List<String> out) {
        if (tag instanceof CompoundTag compound) {
            for (String k : compound.keySet()) {
                strings(compound.get(k), key, key.equals(k), out);
            }
        } else if (tag instanceof ListTag list) {
            for (Tag item : list) {
                strings(item, key, under, out);
            }
        } else if (under && tag instanceof StringTag text) {
            out.add(text.value());
        }
    }

    /** Zone names as stored on disk. */
    private static List<String> zoneNames(CompoundTag file) {
        List<String> zones = new ArrayList<>();
        if (file != null) {
            List<Tag> found = new ArrayList<>();
            collect(file, "zones", found);
            for (Tag z : found) {
                strings(z, "name", false, zones);
            }
        }
        return zones;
    }

    private static void collect(Tag tag, String key, List<Tag> out) {
        if (tag instanceof CompoundTag compound) {
            for (String k : compound.keySet()) {
                if (key.equals(k)) {
                    out.add(compound.get(k));
                } else {
                    collect(compound.get(k), key, out);
                }
            }
        } else if (tag instanceof ListTag list) {
            for (Tag item : list) {
                collect(item, key, out);
            }
        }
    }

    /** Scene names as stored on disk: each slot's share code decoded. Undecodable slots are reported as such. */
    private static List<String> sceneNames(CompoundTag file) {
        List<String> names = new ArrayList<>();
        List<String> codes = new ArrayList<>();
        if (file != null) {
            strings(file, "slots", false, codes);
        }
        for (String code : codes) {
            if (code.isEmpty()) {
                continue;
            }
            ChromaResult<Scene> scene = SceneCodec.fromShareCode(code);
            names.add(scene.isOk() ? scene.value().name() : "<undecodable>");
        }
        return names;
    }

    static void erasureLeavesNothing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer alice = (ServerPlayer) helper.makeMockServerPlayer(GameType.CREATIVE);
        ServerPlayer bob = (ServerPlayer) helper.makeMockServerPlayer(GameType.CREATIVE);
        UUID a = alice.getUUID();
        UUID b = bob.getUUID();
        long first = placeHub(helper, new BlockPos(4, 1, 4), alice);
        long second = placeHub(helper, new BlockPos(18, 1, 18), alice);
        ChromaNetworkStore store = ChromaNetworks.store(level);
        helper.setBlock(new BlockPos(5, 1, 4), DecorBlocks.HULL_NERO_ALLOY.get());
        BlockPos linked = helper.absolutePos(new BlockPos(5, 1, 4));
        ChromaVerdict link = ChromaNetworks.link(level, alice, first, linked);
        helper.assertTrue(link == ChromaVerdict.OK, "link: " + link);
        helper.assertTrue(store.setTrust(first, a, b, ChromaRole.PAINTER).isOk(), "trust first");
        helper.assertTrue(store.setTrust(second, a, b, ChromaRole.ADMIN).isOk(), "trust second");
        helper.assertTrue(store.addZone(first, a, ZONE_NAME, 4, ChromaLimits.DEFAULTS).isOk(), "zone");
        store.get(first).scenes().set(0, new Scene(SCENE_NAME, List.of()), true);
        ChromaNetworkData data = ChromaNetworkData.get(level);

        // Preconditions on disk: both files exist and hold B, A's zone and A's scene (no vacuous pass).
        ChromaNetworkData.persistNow(level, data);
        CompoundTag primary0 = read(primaryFile(level));
        CompoundTag backup0 = read(backupFile(level));
        helper.assertTrue(primary0 != null, "precondition: no primary file at " + ChromaNetworkData.fileStem(
                ChromaNetworkData.TYPE) + ".dat after the forced save");
        helper.assertTrue(backup0 != null, "precondition: no backup file after backupNow");
        helper.assertTrue(mentions(snbt(primary0), b) && mentions(snbt(backup0), b), "precondition: B not on disk");
        helper.assertTrue(zoneNames(primary0).contains(ZONE_NAME) && zoneNames(backup0).contains(ZONE_NAME),
                "precondition: zone name not on disk");
        helper.assertTrue(sceneNames(primary0).contains(SCENE_NAME) && sceneNames(backup0).contains(SCENE_NAME),
                "precondition: scene not decodable from disk: " + sceneNames(primary0));

        JsonObject exportA = ChromaExport.build(a, ChromaExport.collect(level.getServer()), 0L, 30, 90);
        helper.assertTrue(ChromaExport.toJson(exportA).contains(SCENE_NAME), "A's export misses the scene");
        helper.assertTrue(ChromaExport.toJson(exportA).contains(b.toString()), "A's export misses who A trusted");
        // B's export lists the roles B holds, without coordinates or the owner
        JsonObject exportB = ChromaExport.build(b, ChromaExport.collect(level.getServer()), 0L, 30, 90);
        helper.assertTrue(exportB.getAsJsonArray("roles_held").size() == 2, "B's export misses a role");
        helper.assertTrue(!ChromaExport.toJson(exportB).contains(a.toString()), "B's export names the owner");
        Path exports = ChromaExport.directory(level.getServer());
        try {
            ChromaExport.write(exports, b, exportB);
        } catch (IOException e) {
            helper.assertTrue(false, "export write failed: " + e.getClass().getSimpleName());
            return;
        }
        helper.assertTrue(Files.exists(ChromaExport.file(exports, b)), "precondition: B's export not written");

        long t0 = System.currentTimeMillis();
        ChromaPrivacy.overrideClock(() -> t0);
        try {
            ChromaPrivacy.eraseEverywhere(level.getServer(), b);
            helper.assertTrue(!mentions(serialised(data), b), "B still in the saved data after erasure");
            CompoundTag primaryB = read(primaryFile(level));
            CompoundTag backupB = read(backupFile(level));
            helper.assertTrue(primaryB != null && backupB != null, "a store file vanished");
            helper.assertTrue(!mentions(snbt(primaryB), b), "B still in the primary file after erasure");
            helper.assertTrue(!mentions(snbt(backupB), b), "B still in the backup after erasure");
            helper.assertTrue(!Files.exists(ChromaExport.file(exports, b)), "B's export survived erasure");
            helper.assertTrue(store.get(first).roleOf(b) == ChromaRole.NONE
                    && store.get(second).roleOf(b) == ChromaRole.NONE, "B still trusted");
            helper.assertTrue(store.get(first).owner().equals(a) && zoneNames(primaryB).contains(ZONE_NAME),
                    "erasing B touched A's data");

            ChromaPrivacy.eraseEverywhere(level.getServer(), a);
            HubNetwork orphan = store.get(first);
            helper.assertTrue(orphan != null && orphan.orphaned(), "A's hub not orphaned");
            helper.assertTrue(orphan.owner().equals(HubNetwork.NO_OWNER), "owner not cleared");
            helper.assertTrue(orphan.orphanedSince() == t0, "orphan time not the injected clock");
            helper.assertTrue(orphan.trusted().isEmpty(), "orphan keeps a trusted list");
            helper.assertTrue(orphan.zones().get(1).name().equals("Zone 2"), "zone name not blanked");
            helper.assertTrue(orphan.scenes().scene(0) != null
                    && orphan.scenes().scene(0).name().equals("Scene 1"), "scene name not blanked");
            helper.assertTrue(ChromaNetworks.applied(level, linked).isPresent(), "orphan stopped colouring");
            helper.assertTrue(store.get(second).orphaned(), "second hub not orphaned");
            helper.assertTrue(!mentions(serialised(data), a), "A still in the saved data after erasure");
            CompoundTag primaryA = read(primaryFile(level));
            CompoundTag backupA = read(backupFile(level));
            for (CompoundTag file : new CompoundTag[] {primaryA, backupA}) {
                helper.assertTrue(file != null, "a store file vanished");
                helper.assertTrue(!mentions(snbt(file), a), "A still on disk after erasure");
                helper.assertTrue(!zoneNames(file).contains(ZONE_NAME), "zone name still on disk: "
                        + zoneNames(file));
                List<String> scenes = sceneNames(file);
                helper.assertTrue(scenes.contains("Scene 1") && !scenes.contains(SCENE_NAME),
                        "scene name still on disk: " + scenes);
            }
            helper.assertTrue(store.setTrust(first, a, b, ChromaRole.ADMIN) == ChromaVerdict.NO_PERMISSION,
                    "an erased owner can still manage the orphan");

            // Export retention: kept for 7 days, then deleted by the scheduled sweep (injected time).
            try {
                ChromaExport.write(exports, a, exportA);
                Files.setLastModifiedTime(ChromaExport.file(exports, a), FileTime.fromMillis(t0));
            } catch (IOException e) {
                helper.assertTrue(false, "export write failed: " + e.getClass().getSimpleName());
                return;
            }
            helper.assertTrue(ChromaExport.purgeExpired(exports, t0 + 6 * DAY) == 0
                    && Files.exists(ChromaExport.file(exports, a)), "export deleted before 7 days");
            ChromaPrivacy.sweepAll(level.getServer(), t0 + 8 * DAY);
            helper.assertTrue(!Files.exists(ChromaExport.file(exports, a)), "export kept past 7 days");
            helper.assertTrue(store.get(first) != null, "orphan deleted before its retention ran out");

            int kept = ChromaPrivacy.sweep(level, data, t0 + 29 * DAY);
            helper.assertTrue(kept >= 0 && store.get(first) != null, "orphan deleted before its retention ran out");
            int removed = ChromaPrivacy.sweep(level, data, t0 + 31 * DAY);
            helper.assertTrue(removed >= 2 && store.get(first) == null && store.get(second) == null,
                    "orphans not deleted after 30 days");
            helper.assertTrue(store.claimant(linked.getX(), linked.getY(), linked.getZ()) == 0L,
                    "deleted orphan still claims its block");
        } finally {
            ChromaPrivacy.overrideClock(null);
        }
        helper.succeed();
    }
}
