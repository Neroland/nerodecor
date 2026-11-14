package za.co.neroland.nerodecor.gametest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.registries.DeferredRegister;

import za.co.neroland.nerodecor.NeroDecorCommon;
import za.co.neroland.nerodecor.chroma.ChromaColor;
import za.co.neroland.nerodecor.chroma.ChromaOp;
import za.co.neroland.nerodecor.chroma.ChromaOpBatch;
import za.co.neroland.nerodecor.chroma.ChromaSyncCodec;
import za.co.neroland.nerodecor.chroma.RelativePos;
import za.co.neroland.nerodecor.chroma.net.ChromaEdits;
import za.co.neroland.nerodecor.chroma.server.ChromaLimits;
import za.co.neroland.nerodecor.chroma.server.ChromaNetworkData;
import za.co.neroland.nerodecor.chroma.server.ChromaNetworkStore;
import za.co.neroland.nerodecor.chroma.server.ChromaNetworks;
import za.co.neroland.nerodecor.chroma.server.ChromaRole;
import za.co.neroland.nerodecor.chroma.server.ChromaTags;
import za.co.neroland.nerodecor.chroma.server.ChromaVerdict;
import za.co.neroland.nerodecor.chroma.server.HubNetwork;
import za.co.neroland.nerodecor.content.DecorColor;
import za.co.neroland.nerodecor.content.DecorProperties;
import za.co.neroland.nerodecor.content.block.ChromaCubeBlock;
import za.co.neroland.nerodecor.content.block.ChromaHubBlockEntity;
import za.co.neroland.nerodecor.content.block.NeonShimBlock;
import za.co.neroland.nerodecor.content.item.PaintGunItem;
import za.co.neroland.nerodecor.registry.ModDataComponents;
import za.co.neroland.nerodecor.registry.ChromaContent;
import za.co.neroland.nerodecor.registry.DecorBlocks;

/**
 * Chroma Stage 2 (server network) GameTests: linking, overlap, roles, caps, hub break/re-place and corrupt-file
 * recovery. Each runs in the empty 24x24x24 {@code chroma_arena} (neoforge/src/gametest/gen_chroma_arena.py) with
 * mock server players that are not connected (so no chat packets are sent).
 */
final class ChromaGameTests {

    private ChromaGameTests() {
    }

    static void register(DeferredRegister<Consumer<GameTestHelper>> functions) {
        functions.register("chroma_capable_tag", () -> ChromaGameTests::capableTag);
        functions.register("chroma_link_block", () -> ChromaGameTests::linkBlock);
        functions.register("chroma_overlap_rejected", () -> ChromaGameTests::overlapRejected);
        functions.register("chroma_untrusted_denied", () -> ChromaGameTests::untrustedDenied);
        functions.register("chroma_block_cap", () -> ChromaGameTests::blockCap);
        functions.register("chroma_volume_and_hub_caps", () -> ChromaGameTests::volumeAndHubCaps);
        functions.register("chroma_hub_break_relink", () -> ChromaGameTests::hubBreakRelink);
        functions.register("chroma_corrupt_recovery", () -> ChromaGameTests::corruptRecovery);
        functions.register("chroma_paint_gun", () -> ChromaGameTests::paintGun);
        functions.register("chroma_neon_item_folds", () -> ChromaGameTests::neonItemFolds);
        functions.register("chroma_cube_light_follows_brightness", () -> ChromaGameTests::cubeLightFollowsBrightness);
        functions.register("chroma_sync_ops", () -> ChromaGameTests::syncOps);
    }

    // --- helpers ---------------------------------------------------------------------------------------------------

    private static ServerPlayer player(GameTestHelper helper, GameType mode) {
        return (ServerPlayer) helper.makeMockServerPlayer(mode);
    }

    private static Block hull() {
        return DecorBlocks.HULL_NERO_ALLOY.get();
    }

    /** Places a hub as {@code placer} would (block + setPlacedBy) and returns its network id. */
    private static long placeHub(GameTestHelper helper, BlockPos local, ServerPlayer placer, ItemStack stack) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(local);
        BlockState state = ChromaContent.HUB.get().defaultBlockState();
        level.setBlock(pos, state, 3);
        state.getBlock().setPlacedBy(level, pos, state, placer, stack);
        ChromaHubBlockEntity hub = (ChromaHubBlockEntity) level.getBlockEntity(pos);
        helper.assertTrue(hub != null, "hub has no block entity");
        return hub.networkId();
    }

    private static long placeHub(GameTestHelper helper, BlockPos local, ServerPlayer placer) {
        return placeHub(helper, local, placer, new ItemStack(ChromaContent.HUB_ITEM.get()));
    }

    private static Block decor(String id) {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(
                Identifier.fromNamespaceAndPath(NeroDecorCommon.MOD_ID, id));
    }

    private static BlockPos put(GameTestHelper helper, BlockPos local, Block block) {
        helper.setBlock(local, block);
        return helper.absolutePos(local);
    }

    private static void expect(GameTestHelper helper, ChromaVerdict expected, ChromaVerdict actual, String what) {
        helper.assertTrue(expected == actual, what + ": expected " + expected + " but was " + actual);
    }

    // --- tests -----------------------------------------------------------------------------------------------------

    /**
     * Every paintable block, the Luminous collection and the collection blocks whose module sets {@code chroma=True}
     * are chroma-capable (the tag is generated from those flags); plain collection furniture, the hub and vanilla
     * blocks are not.
     */
    static void capableTag(GameTestHelper helper) {
        for (Block block : DecorBlocks.paintableBlocks()) {
            helper.assertTrue(ChromaTags.isCapable(block.defaultBlockState()),
                    block.builtInRegistryHolder().key().identifier() + " is not chroma_capable");
        }
        for (String id : List.of("circuit_plating", "lumen_panel", "station_plating", "specimen_tank", "coolant_tube",
                "beam_emitter_prop", "light_strip", "glyph_strip", "holo_sign")) {
            helper.assertTrue(ChromaTags.isCapable(decor(id).defaultBlockState()), id + " is not chroma_capable");
        }
        for (String id : List.of("crew_bunk", "gantry_ladder")) {
            helper.assertTrue(!ChromaTags.isCapable(decor(id).defaultBlockState()), id + " is chroma_capable");
        }
        helper.assertTrue(!ChromaTags.isCapable(ChromaContent.HUB.get().defaultBlockState()), "hub is capable");
        helper.assertTrue(!ChromaTags.isCapable(Blocks.STONE.defaultBlockState()), "stone is capable");
        helper.succeed();
    }

    /** Link, look up, unlink; non-capable blocks refused; auto-adopt joins a placed block. */
    static void linkBlock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = player(helper, GameType.SURVIVAL);
        long id = placeHub(helper, new BlockPos(12, 2, 12), owner);
        helper.assertTrue(id != 0L, "hub did not start a network");
        HubNetwork network = ChromaNetworks.store(level).get(id);
        helper.assertTrue(network.sizeX() == 48 && network.sizeY() == 32 && network.sizeZ() == 48,
                "default volume is not 48x32x48");

        BlockPos block = put(helper, new BlockPos(14, 2, 12), hull());
        expect(helper, ChromaVerdict.OK, ChromaNetworks.toggle(level, owner, id, block), "link");
        Optional<ChromaNetworks.Applied> applied = ChromaNetworks.applied(level, block);
        helper.assertTrue(applied.isPresent() && applied.get().network() == id, "linked block not applied");
        expect(helper, ChromaVerdict.OK, ChromaNetworks.toggle(level, owner, id, block), "unlink");
        helper.assertTrue(ChromaNetworks.applied(level, block).isEmpty(), "unlinked block still applied");

        BlockPos stone = put(helper, new BlockPos(15, 2, 12), Blocks.STONE);
        expect(helper, ChromaVerdict.NOT_CAPABLE, ChromaNetworks.link(level, owner, id, stone), "stone");

        ChromaNetworkStore store = ChromaNetworks.store(level);
        expect(helper, ChromaVerdict.OK, store.setAutoAdopt(id, owner.getUUID(), true), "auto-adopt on");
        BlockPos placed = put(helper, new BlockPos(16, 2, 12), hull());
        helper.assertTrue(ChromaNetworks.autoAdopt(level, placed, owner), "auto-adopt did not link");
        helper.assertTrue(ChromaNetworks.applied(level, placed).isPresent(), "adopted block not applied");
        ServerPlayer other = player(helper, GameType.SURVIVAL);
        BlockPos foreign = put(helper, new BlockPos(17, 2, 12), hull());
        helper.assertTrue(!ChromaNetworks.autoAdopt(level, foreign, other), "auto-adopt took a stranger's block");
        helper.succeed();
    }

    /** A block belongs to exactly one hub: a second hub cannot link it, singly or by box. */
    static void overlapRejected(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer a = player(helper, GameType.SURVIVAL);
        ServerPlayer b = player(helper, GameType.SURVIVAL);
        long hubA = placeHub(helper, new BlockPos(4, 2, 4), a);
        long hubB = placeHub(helper, new BlockPos(18, 2, 18), b);
        BlockPos shared = put(helper, new BlockPos(11, 2, 11), hull());
        BlockPos free = put(helper, new BlockPos(12, 2, 11), hull());
        expect(helper, ChromaVerdict.OK, ChromaNetworks.link(level, a, hubA, shared), "first hub");
        expect(helper, ChromaVerdict.CLAIMED_BY_OTHER_HUB, ChromaNetworks.link(level, b, hubB, shared), "second hub");
        ChromaNetworks.Selection box = ChromaNetworks.linkBox(level, b, hubB, shared, free);
        helper.assertTrue(box.linked() == 1 && box.refused() == 1
                && box.verdict() == ChromaVerdict.CLAIMED_BY_OTHER_HUB, "box over a claimed block: " + box);
        helper.assertTrue(ChromaNetworks.applied(level, shared).get().network() == hubA, "shared block changed hub");
        helper.succeed();
    }

    /** Strangers, viewers and painters cannot link; admins can; a player who may not build is refused. */
    static void untrustedDenied(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = player(helper, GameType.SURVIVAL);
        ServerPlayer guest = player(helper, GameType.SURVIVAL);
        ServerPlayer adventurer = player(helper, GameType.ADVENTURE);
        long id = placeHub(helper, new BlockPos(12, 2, 12), owner);
        BlockPos block = put(helper, new BlockPos(13, 2, 12), hull());
        ChromaNetworkStore store = ChromaNetworks.store(level);

        expect(helper, ChromaVerdict.NO_PERMISSION, ChromaNetworks.link(level, guest, id, block), "stranger");
        store.setTrust(id, owner.getUUID(), guest.getUUID(), ChromaRole.VIEWER);
        expect(helper, ChromaVerdict.NO_PERMISSION, ChromaNetworks.link(level, guest, id, block), "viewer");
        store.setTrust(id, owner.getUUID(), guest.getUUID(), ChromaRole.PAINTER);
        expect(helper, ChromaVerdict.NO_PERMISSION, ChromaNetworks.link(level, guest, id, block), "painter");
        expect(helper, ChromaVerdict.NO_PERMISSION,
                store.setTrust(id, guest.getUUID(), adventurer.getUUID(), ChromaRole.ADMIN), "painter granting");
        store.setTrust(id, owner.getUUID(), adventurer.getUUID(), ChromaRole.ADMIN);
        expect(helper, ChromaVerdict.PROTECTED, ChromaNetworks.link(level, adventurer, id, block),
                "admin who may not build");
        store.setTrust(id, owner.getUUID(), guest.getUUID(), ChromaRole.ADMIN);
        expect(helper, ChromaVerdict.OK, ChromaNetworks.link(level, guest, id, block), "admin");
        helper.succeed();
    }

    /** 4,096 blocks per hub: a box of 4,097 capable blocks links exactly 4,096. */
    static void blockCap(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = player(helper, GameType.SURVIVAL);
        long id = placeHub(helper, new BlockPos(20, 10, 20), owner);
        BlockState hull = hull().defaultBlockState();
        for (int x = 2; x < 18; x++) {
            for (int y = 2; y < 18; y++) {
                for (int z = 2; z < 18; z++) {
                    level.setBlock(helper.absolutePos(new BlockPos(x, y, z)), hull, 2);
                }
            }
        }
        BlockPos extra = put(helper, new BlockPos(18, 2, 2), hull());
        ChromaNetworks.Selection box = ChromaNetworks.linkBox(level, owner, id,
                helper.absolutePos(new BlockPos(2, 2, 2)), helper.absolutePos(new BlockPos(18, 17, 17)));
        helper.assertTrue(box.linked() == ChromaLimits.DEFAULTS.maxBlocksPerHub() && box.refused() == 1
                && box.verdict() == ChromaVerdict.BLOCK_CAP, "box past the cap: " + box);
        helper.assertTrue(ChromaNetworks.store(level).get(id).size() == 4096, "hub holds more than 4,096");
        ChromaVerdict single = ChromaNetworks.link(level, owner, id, extra);
        helper.assertTrue(single == ChromaVerdict.BLOCK_CAP, "single link past the cap: " + single);
        helper.succeed();
    }

    /** Volume bounds (and the 64 hard cap) and 8 hubs per player. */
    static void volumeAndHubCaps(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = player(helper, GameType.SURVIVAL);
        long id = placeHub(helper, new BlockPos(12, 2, 12), owner);
        ChromaNetworkStore store = ChromaNetworks.store(level);
        expect(helper, ChromaVerdict.VOLUME_TOO_LARGE, store.setVolume(id, owner.getUUID(), 65, 8, 8), "65 wide");
        expect(helper, ChromaVerdict.OK, store.setVolume(id, owner.getUUID(), 8, 8, 8), "8x8x8");
        BlockPos inside = put(helper, new BlockPos(15, 2, 12), hull());
        BlockPos outside = put(helper, new BlockPos(17, 2, 12), hull());
        expect(helper, ChromaVerdict.OK, ChromaNetworks.link(level, owner, id, inside), "inside");
        expect(helper, ChromaVerdict.OUT_OF_VOLUME, ChromaNetworks.link(level, owner, id, outside), "outside");

        // Seven more hubs reach the cap of eight; the ninth starts no network.
        for (int i = 0; i < 7; i++) {
            helper.assertTrue(placeHub(helper, new BlockPos(2 + i, 2, 2), owner) != 0L, "hub " + (i + 2) + " refused");
        }
        expect(helper, ChromaVerdict.HUB_CAP, ChromaNetworks.canStartNetwork(owner), "ninth hub");
        helper.assertTrue(placeHub(helper, new BlockPos(2, 2, 6), owner) == 0L, "ninth hub started a network");
        helper.succeed();
    }

    /** Breaking the hub drops an item carrying the network and reverts the blocks; re-placing it relinks them. */
    static void hubBreakRelink(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = player(helper, GameType.SURVIVAL);
        BlockPos hubLocal = new BlockPos(12, 2, 12);
        long id = placeHub(helper, hubLocal, owner);
        BlockPos hubPos = helper.absolutePos(hubLocal);
        BlockState red = hull().defaultBlockState().setValue(DecorProperties.COLOR, DecorColor.RED);
        BlockPos one = helper.absolutePos(new BlockPos(14, 2, 12));
        BlockPos two = helper.absolutePos(new BlockPos(14, 3, 12));
        level.setBlock(one, red, 3);
        level.setBlock(two, red, 3);
        expect(helper, ChromaVerdict.OK, ChromaNetworks.link(level, owner, id, one), "link one");
        expect(helper, ChromaVerdict.OK, ChromaNetworks.link(level, owner, id, two), "link two");

        level.destroyBlock(hubPos, true);
        helper.assertTrue(!ChromaNetworks.store(level).get(id).active(), "network still active after hub broke");
        helper.assertTrue(ChromaNetworks.applied(level, one).isEmpty(), "block still controlled after hub broke");
        helper.assertTrue(level.getBlockState(one).getValue(DecorProperties.COLOR) == DecorColor.RED,
                "block lost its own colour");
        List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(hubPos).inflate(2.0D),
                e -> e.getItem().is(ChromaContent.HUB_ITEM.get()));
        helper.assertTrue(drops.size() == 1, "expected one hub drop, found " + drops.size());
        ItemStack dropped = drops.get(0).getItem().copy();
        drops.forEach(ItemEntity::discard);
        Long carried = dropped.get(ChromaContent.NETWORK_ID.get());
        helper.assertTrue(carried != null && carried == id, "dropped hub does not carry its network");

        long relinked = placeHub(helper, hubLocal, owner, dropped);
        helper.assertTrue(relinked == id, "re-placed hub started a new network");
        helper.assertTrue(ChromaNetworks.applied(level, one).isPresent()
                && ChromaNetworks.applied(level, two).isPresent(), "blocks not relinked");
        helper.succeed();
    }

    // Probe types: same codec as the real store, separate files, so the cache never hides the disk read.
    private static final SavedDataType<ChromaNetworkData> PROBE_FRESH = ChromaNetworkData.typeFor(
            Identifier.fromNamespaceAndPath(NeroDecorGameTests.MOD_ID, "chroma_probe_fresh"));
    private static final SavedDataType<ChromaNetworkData> PROBE_WRITER = ChromaNetworkData.typeFor(
            Identifier.fromNamespaceAndPath(NeroDecorGameTests.MOD_ID, "chroma_probe_writer"));
    private static final SavedDataType<ChromaNetworkData> PROBE_READER = ChromaNetworkData.typeFor(
            Identifier.fromNamespaceAndPath(NeroDecorGameTests.MOD_ID, "chroma_probe_reader"));

    /**
     * A corrupt {@code chroma_networks}-format file loads through Core's recovery guard: with no backup it yields a fresh
     * store; with a backup it restores the last-known-good networks. Never throws.
     */
    static void corruptRecovery(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Path storage = DimensionType.getStorageFolder(level.dimension(),
                level.getServer().getWorldPath(LevelResource.ROOT)).resolve("data");
        String freshName = NeroDecorGameTests.MOD_ID + ":chroma_probe_fresh";
        String backupName = NeroDecorGameTests.MOD_ID + ":chroma_probe_backup";
        try {
            Files.deleteIfExists(storage.resolve(freshName.replace(':', '_') + "_backup.dat"));
            writeGarbage(storage.resolve(ChromaNetworkData.fileStem(PROBE_FRESH) + ".dat"));
            ChromaNetworkData fresh = ChromaNetworkData.load(level, PROBE_FRESH, freshName);
            helper.assertTrue(fresh != null && fresh.store().count() == 0, "corrupt file without backup not fresh");

            ChromaNetworkData good = new ChromaNetworkData();
            java.util.UUID owner = java.util.UUID.randomUUID();
            good.store().create(4242L, owner, 0, 64, 0, 48, 32, 48);
            good.store().link(4242L, owner, 2, 64, 0, true, ChromaLimits.DEFAULTS);
            ChromaNetworkData.backupNow(level, PROBE_WRITER, good, backupName);
            writeGarbage(storage.resolve(ChromaNetworkData.fileStem(PROBE_READER) + ".dat"));
            ChromaNetworkData restored = ChromaNetworkData.load(level, PROBE_READER, backupName);
            HubNetwork network = restored.store().get(4242L);
            helper.assertTrue(network != null && network.size() == 1 && network.owner().equals(owner),
                    "backup not restored over a corrupt file");
            helper.assertTrue(restored.store().claimant(2, 64, 0) == 4242L, "restored claims not rebuilt");
        } catch (IOException e) {
            helper.fail("could not prepare the corrupt file: " + e.getClass().getSimpleName());
        }
        helper.succeed();
    }

    private static void writeGarbage(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[] {0x1f, (byte) 0x8b, 0x08, 0x00, 'n', 'o', 't', ' ', 'n', 'b', 't', 0x00, 0x13});
    }

    // --- Stage 3: static sync, tint, neon fold ---------------------------------------------------------------------

    /** The paint gun paints for a builder, copies on sneak (gun component), and is refused where the player may not build. */
    static void paintGun(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer builder = player(helper, GameType.SURVIVAL);
        ServerPlayer adventurer = player(helper, GameType.ADVENTURE);
        BlockPos block = put(helper, new BlockPos(10, 2, 10), hull());
        expect(helper, ChromaVerdict.OK, PaintGunItem.paint(level, builder, block, DecorColor.MAGENTA), "paint");
        helper.assertTrue(level.getBlockState(block).getValue(DecorProperties.COLOR) == DecorColor.MAGENTA,
                "block not painted");
        expect(helper, ChromaVerdict.PROTECTED, PaintGunItem.paint(level, adventurer, block, DecorColor.LIME),
                "adventure-mode paint");
        helper.assertTrue(level.getBlockState(block).getValue(DecorProperties.COLOR) == DecorColor.MAGENTA,
                "denied paint changed the block");
        BlockPos stone = put(helper, new BlockPos(11, 2, 10), Blocks.STONE);
        expect(helper, ChromaVerdict.NOT_CAPABLE, PaintGunItem.paint(level, builder, stone, DecorColor.RED), "stone");
        ItemStack gun = new ItemStack(ChromaContent.PAINT_GUN.get());
        helper.assertTrue(PaintGunItem.colour(gun) == DecorColor.NATURAL, "fresh gun is not natural");
        gun.set(ModDataComponents.COLOR.get(), DecorColor.CYAN);
        helper.assertTrue(PaintGunItem.colour(gun) == DecorColor.CYAN, "gun colour component");
        helper.succeed();
    }

    /** A dropped legacy neon stack becomes a same-colour chroma cube stack (loader item-entity hook). */
    static void neonItemFolds(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Block shim = DecorBlocks.NEON_SHIMS.get(0).get();
        DecorColor dye = ((NeonShimBlock) shim).dye();
        BlockPos at = helper.absolutePos(new BlockPos(12, 3, 12));
        ItemEntity entity = new ItemEntity(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, new ItemStack(shim, 5));
        level.addFreshEntity(entity);
        helper.succeedWhen(() -> {
            ItemStack now = entity.getItem();
            helper.assertTrue(now.is(ChromaContent.cubeItem()), "dropped neon not folded: " + now);
            helper.assertTrue(now.getCount() == 5, "count changed");
            helper.assertTrue(now.get(ModDataComponents.COLOR.get()) == dye, "colour lost");
        });
    }

    /** A linked chroma cube's light follows its palette entry's brightness (applied by the sync tick, >= 1 s apart). */
    static void cubeLightFollowsBrightness(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = player(helper, GameType.SURVIVAL);
        long id = placeHub(helper, new BlockPos(12, 2, 12), owner);
        BlockPos cube = put(helper, new BlockPos(14, 2, 12), ChromaContent.CUBE.get());
        expect(helper, ChromaVerdict.OK, ChromaNetworks.link(level, owner, id, cube), "link cube");
        HubNetwork network = ChromaNetworks.store(level).get(id);
        int rel = RelativePos.pack(cube.getX() - network.anchorX(), cube.getY() - network.anchorY(),
                cube.getZ() - network.anchorZ());
        ChromaOpBatch batch = ChromaOpBatch.of(List.of(ChromaOp.setPaletteEntry(0, ChromaColor.of(255, 40, 40, 5)),
                ChromaOp.setIndex(rel, 0))).value();
        expect(helper, ChromaVerdict.OK, ChromaNetworks.store(level).applyOps(id, owner.getUUID(), batch, p -> true),
                "ops");
        helper.succeedWhen(() -> helper.assertTrue(
                level.getBlockState(cube).getValue(ChromaCubeBlock.LIGHT) == 5
                        && level.getLightEmission(cube) == 5, "cube light did not follow brightness 5"));
    }

    /** Op packets: decoded, role-checked (a stranger is refused) and applied atomically for the owner. */
    static void syncOps(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = player(helper, GameType.SURVIVAL);
        ServerPlayer stranger = player(helper, GameType.SURVIVAL);
        long id = placeHub(helper, new BlockPos(12, 2, 12), owner);
        BlockPos block = put(helper, new BlockPos(13, 2, 12), hull());
        expect(helper, ChromaVerdict.OK, ChromaNetworks.link(level, owner, id, block), "link");
        HubNetwork network = ChromaNetworks.store(level).get(id);
        int rel = RelativePos.pack(1, 0, 0);
        ChromaOpBatch batch = ChromaOpBatch.of(List.of(ChromaOp.setPaletteEntry(0, ChromaColor.of(1, 2, 3)),
                ChromaOp.setIndex(rel, 0))).value();
        byte[] bytes = ChromaSyncCodec.encodeOps(id, batch);
        // Screen edits need the sender at the hub: the role is checked first, then reach.
        BlockPos hub = helper.absolutePos(new BlockPos(12, 2, 12));
        owner.setPos(hub.getX() + 2.5, hub.getY(), hub.getZ() + 0.5);
        stranger.setPos(hub.getX() + 400.5, hub.getY(), hub.getZ() + 0.5);
        expect(helper, ChromaVerdict.NO_PERMISSION, ChromaEdits.apply(level, stranger, bytes), "far stranger ops");
        stranger.setPos(hub.getX() + 1.5, hub.getY(), hub.getZ() + 0.5);
        expect(helper, ChromaVerdict.NO_PERMISSION, ChromaEdits.apply(level, stranger, bytes), "stranger ops");
        helper.assertTrue(network.palette().size() == 0, "refused batch changed the palette");
        ServerPlayer painter = player(helper, GameType.SURVIVAL);
        expect(helper, ChromaVerdict.OK, ChromaNetworks.store(level).setTrust(id, owner.getUUID(), painter.getUUID(),
                ChromaRole.PAINTER), "trust painter");
        painter.setPos(hub.getX() + 400.5, hub.getY(), hub.getZ() + 0.5);
        expect(helper, ChromaVerdict.TOO_FAR, ChromaEdits.apply(level, painter, bytes), "trusted but far ops");
        helper.assertTrue(network.palette().size() == 0, "a far batch changed the palette");
        expect(helper, ChromaVerdict.OK, ChromaEdits.apply(level, owner, bytes), "owner ops");
        helper.assertTrue(network.palette().size() == 1 && network.paletteIndexAt(rel) == 0, "ops not applied");
        expect(helper, ChromaVerdict.BAD_OP, ChromaEdits.apply(level, owner, new byte[] {1, 2, 3}), "garbage");
        helper.succeed();
    }
}
