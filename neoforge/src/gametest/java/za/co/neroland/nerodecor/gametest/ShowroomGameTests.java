package za.co.neroland.nerodecor.gametest;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.DeferredRegister;

import za.co.neroland.nerodecor.NeroDecorCommon;
import za.co.neroland.nerodecor.chroma.server.ChromaNetworks;
import za.co.neroland.nerodecor.command.showroom.BlockGrid;
import za.co.neroland.nerodecor.command.showroom.CameraPresets;
import za.co.neroland.nerodecor.command.showroom.Showroom;
import za.co.neroland.nerodecor.command.showroom.ShowroomScenes;

/**
 * Stage 9 showroom: {@code /nerodecor gallery}'s builder places every bay without a layout error (unknown id,
 * property or value, or a block that cannot stand where it is put), puts down only vanilla and NeroDecor blocks,
 * starts the Chroma corridor's hub network, rebuilds in place without leaking a second network, and clears to air.
 * Built 1,024 blocks south of the test so it cannot touch neighbouring tests.
 *
 * <p>{@code block_grid_builds}: {@code /nerodecor gallery grid}'s {@link BlockGrid} gives every visible registered
 * block a collection (a new block that no collection claims fails here), shows each of them at least once and nothing
 * else from NeroDecor, starts the Chroma demo's hub network, rebuilds in place, and clears to air. Every camera preset
 * id is unique.
 */
final class ShowroomGameTests {

    private ShowroomGameTests() {
    }

    static void register(DeferredRegister<Consumer<GameTestHelper>> functions) {
        functions.register("showroom_builds", () -> ShowroomGameTests::showroomBuilds);
        functions.register("block_grid_builds", () -> ShowroomGameTests::blockGridBuilds);
    }

    static void showroomBuilds(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = (ServerPlayer) helper.makeMockServerPlayer(GameType.CREATIVE);
        BlockPos origin = helper.absolutePos(BlockPos.ZERO).offset(0, 4, 1024);
        helper.assertTrue(Showroom.fits(level, origin), "showroom does not fit at " + origin.toShortString());

        Showroom.Result first = Showroom.build(level, origin, owner);
        helper.assertTrue(first.errors().isEmpty(), first.errors().size() + " layout errors: "
                + String.join("; ", first.errors()));
        helper.assertTrue(first.notes().isEmpty(), "notes: " + String.join("; ", first.notes()));
        helper.assertTrue(first.placed() > 2000, "only " + first.placed() + " blocks placed");

        Set<String> decorIds = new HashSet<>();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        BlockPos lo = first.min();
        BlockPos hi = first.max();
        for (int x = lo.getX(); x <= hi.getX(); x++) {
            for (int y = lo.getY(); y <= hi.getY(); y++) {
                for (int z = lo.getZ(); z <= hi.getZ(); z++) {
                    BlockState state = level.getBlockState(p.set(x, y, z));
                    if (state.isAir()) {
                        continue;
                    }
                    Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                    String ns = id.getNamespace();
                    helper.assertTrue("minecraft".equals(ns) || NeroDecorCommon.MOD_ID.equals(ns),
                            "foreign block " + id + " at " + p.toShortString());
                    helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(id), "unregistered block " + id);
                    if (NeroDecorCommon.MOD_ID.equals(ns)) {
                        decorIds.add(id.getPath());
                    }
                }
            }
        }
        helper.assertTrue(decorIds.size() >= 90, "only " + decorIds.size() + " distinct NeroDecor blocks shown");
        helper.assertTrue(decorIds.contains("chroma_hub") && decorIds.contains("blast_door")
                && decorIds.contains("sliding_door") && decorIds.contains("world_globe"),
                "a showpiece is missing: " + decorIds);
        helper.assertTrue(ChromaNetworks.ownedCount(level.getServer(), owner.getUUID()) == 1,
                "expected one hub network for the showroom owner");

        Showroom.Result second = Showroom.build(level, origin, owner);
        helper.assertTrue(second.errors().isEmpty() && second.placed() == first.placed(),
                "rebuild differs: " + second.placed() + " vs " + first.placed() + " " + second.errors());
        helper.assertTrue(ChromaNetworks.ownedCount(level.getServer(), owner.getUUID()) == 1,
                "rebuilding leaked a hub network");

        Showroom.Cleared cleared = Showroom.clear(level, origin);
        helper.assertTrue(cleared.hubs() == 1, "clear reported " + cleared.hubs() + " hub networks, expected 1");
        helper.assertTrue(cleared.blocks() > 2000, "clear reported only " + cleared.blocks() + " blocks");
        for (int x = lo.getX(); x <= hi.getX(); x++) {
            for (int y = lo.getY(); y <= hi.getY(); y++) {
                for (int z = lo.getZ(); z <= hi.getZ(); z++) {
                    helper.assertTrue(level.getBlockState(p.set(x, y, z)).isAir(),
                            "clear left a block at " + p.toShortString());
                }
            }
        }
        helper.assertTrue(ChromaNetworks.ownedCount(level.getServer(), owner.getUUID()) == 0,
                "clear left the hub network behind");
        helper.assertTrue(ShowroomScenes.ALL.size() == 8, "expected eight bays");
        helper.succeed();
    }

    static void blockGridBuilds(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = (ServerPlayer) helper.makeMockServerPlayer(GameType.CREATIVE);
        BlockPos origin = helper.absolutePos(BlockPos.ZERO).offset(256, 4, 1024);
        BlockGrid.Layout layout = BlockGrid.layout();
        helper.assertTrue(layout.unsorted().isEmpty(), "blocks with no grid collection: " + layout.unsorted());
        helper.assertTrue(BlockGrid.fits(level, origin, layout), "grid does not fit at " + origin.toShortString());

        BlockGrid.Result first = BlockGrid.build(level, origin, owner);
        helper.assertTrue(first.errors().isEmpty(), first.errors().size() + " grid errors: "
                + String.join("; ", first.errors()));
        helper.assertTrue(first.notes().isEmpty(), "notes: " + String.join("; ", first.notes()));

        Set<String> visible = new HashSet<>(BlockGrid.visibleBlocks());
        helper.assertTrue(visible.size() >= 150, "only " + visible.size() + " visible blocks registered");
        helper.assertTrue(first.shown().equals(visible), "grid staged " + first.shown().size() + " of "
                + visible.size() + " visible blocks");
        Set<String> found = new HashSet<>();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        BlockPos lo = first.min();
        BlockPos hi = first.max();
        for (int x = lo.getX(); x <= hi.getX(); x++) {
            for (int y = lo.getY(); y <= hi.getY(); y++) {
                for (int z = lo.getZ(); z <= hi.getZ(); z++) {
                    BlockState state = level.getBlockState(p.set(x, y, z));
                    if (state.isAir()) {
                        continue;
                    }
                    Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                    String ns = id.getNamespace();
                    helper.assertTrue("minecraft".equals(ns) || NeroDecorCommon.MOD_ID.equals(ns),
                            "foreign block " + id + " at " + p.toShortString());
                    if (NeroDecorCommon.MOD_ID.equals(ns)) {
                        helper.assertTrue(visible.contains(id.getPath()), "unknown or hidden block " + id + " at "
                                + p.toShortString());
                        found.add(id.getPath());
                    }
                }
            }
        }
        Set<String> missing = new HashSet<>(visible);
        missing.removeAll(found);
        helper.assertTrue(missing.isEmpty(), "visible blocks missing from the grid: " + missing);
        helper.assertTrue(ChromaNetworks.ownedCount(level.getServer(), owner.getUUID()) == 1,
                "expected one hub network (the Chroma demo) for the grid owner");

        BlockGrid.Result second = BlockGrid.build(level, origin, owner);
        helper.assertTrue(second.errors().isEmpty() && second.placed() == first.placed(),
                "rebuild differs: " + second.placed() + " vs " + first.placed() + " " + second.errors());
        helper.assertTrue(ChromaNetworks.ownedCount(level.getServer(), owner.getUUID()) == 1,
                "rebuilding leaked a hub network");

        Showroom.Cleared cleared = BlockGrid.clear(level, origin);
        helper.assertTrue(cleared.hubs() == 1, "clear reported " + cleared.hubs() + " hub networks, expected 1");
        for (int x = lo.getX(); x <= hi.getX(); x++) {
            for (int y = lo.getY(); y <= hi.getY(); y++) {
                for (int z = lo.getZ(); z <= hi.getZ(); z++) {
                    helper.assertTrue(level.getBlockState(p.set(x, y, z)).isAir(),
                            "clear left a block at " + p.toShortString());
                }
            }
        }
        helper.assertTrue(ChromaNetworks.ownedCount(level.getServer(), owner.getUUID()) == 0,
                "clear left the hub network behind");

        Set<String> ids = new HashSet<>();
        for (CameraPresets.Preset preset : CameraPresets.showroom()) {
            helper.assertTrue(ids.add(preset.id()), "duplicate camera preset " + preset.id());
        }
        for (CameraPresets.Preset preset : CameraPresets.grid(layout)) {
            helper.assertTrue(ids.add(preset.id()), "duplicate camera preset " + preset.id());
        }
        helper.assertTrue(CameraPresets.grid(layout).size() >= 4, "too few grid camera presets");
        helper.succeed();
    }
}
