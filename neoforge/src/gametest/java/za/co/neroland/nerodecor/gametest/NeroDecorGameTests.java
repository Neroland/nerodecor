package za.co.neroland.nerodecor.gametest;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredRegister;

import za.co.neroland.nerodecor.NeroDecorCommon;
import za.co.neroland.nerodecor.content.DecorColor;
import za.co.neroland.nerodecor.content.DecorProperties;
import za.co.neroland.nerodecor.content.block.ChromaCubeBlock;
import za.co.neroland.nerodecor.content.item.NeonShimItem;
import za.co.neroland.nerodecor.registry.ChromaContent;
import za.co.neroland.nerodecor.registry.ModDataComponents;
import za.co.neroland.nerodecor.registry.DecorBlocks;

/**
 * Development-only server GameTests for NeroDecor (26.3 NeoForge cell). Each test is a vanilla
 * {@code test_function} paired with a data-driven {@code test_instance} under
 * {@code data/nerodecor_gametest/test_instance}. Run with {@code gradlew :neoforge:26.3:runGameTestServer}.
 */
@Mod(NeroDecorGameTests.MOD_ID)
public final class NeroDecorGameTests {

    public static final String MOD_ID = "nerodecor_gametest";

    /** The 200-block registry cap. */
    static final int REGISTRY_CAP = 200;

    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
            DeferredRegister.create(Registries.TEST_FUNCTION, MOD_ID);

    static {
        FUNCTIONS.register("place_every_block", () -> NeroDecorGameTests::placeEveryBlock);
        FUNCTIONS.register("paint_survives_placement", () -> NeroDecorGameTests::paintSurvivesPlacement);
        FUNCTIONS.register("registry_within_cap", () -> NeroDecorGameTests::registryWithinCap);
        FUNCTIONS.register("fixture_071_loads", () -> NeroDecorGameTests::fixture071Loads);
        FUNCTIONS.register("neon_fold_keeps_loot", () -> NeroDecorGameTests::neonFoldKeepsLoot);
        ChromaGameTests.register(FUNCTIONS);
        ChromaSceneGameTests.register(FUNCTIONS); // Chroma Stage 4 (scenes)
        ChromaPrivacyGameTests.register(FUNCTIONS);
        ChromaLinkGameTests.register(FUNCTIONS); // Chroma Stage 8 (NeroLink)
        RecipeGameTests.register(FUNCTIONS); // Stage 6 (recipes + unlocks)
        ShowroomGameTests.register(FUNCTIONS); // Stage 9 (showroom)
    }

    public NeroDecorGameTests(IEventBus modEventBus) {
        FUNCTIONS.register(modEventBus);
    }

    /** Every NeroDecor block places as itself (no missing models/states blow up server-side). */
    static void placeEveryBlock(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        for (Block block : DecorBlocks.allBlocks()) {
            helper.setBlock(pos, block);
            helper.assertBlockPresent(block, pos);
        }
        helper.succeed();
    }

    /** A painted COLOR state round-trips through the world for every paintable block. */
    static void paintSurvivesPlacement(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        for (Block block : DecorBlocks.paintableBlocks()) {
            BlockState painted = block.defaultBlockState().setValue(DecorProperties.COLOR, DecorColor.RED);
            helper.setBlock(pos, painted);
            helper.assertBlockProperty(pos, DecorProperties.COLOR, DecorColor.RED);
        }
        helper.succeed();
    }

    /** The registered block count matches DecorBlocks and stays within the cap. */
    static void registryWithinCap(GameTestHelper helper) {
        long registered = BuiltInRegistries.BLOCK.keySet().stream()
                .filter(id -> NeroDecorCommon.MOD_ID.equals(id.getNamespace()))
                .count();
        int expected = DecorBlocks.ALL.size() + ChromaContent.blockCount();
        helper.assertTrue(registered == expected,
                "registered " + registered + " != DecorBlocks.ALL + chroma " + expected);
        helper.assertTrue(registered <= REGISTRY_CAP, "registry " + registered + " exceeds cap " + REGISTRY_CAP);
        helper.succeed();
    }

    /**
     * The 0.7.1 fixture (tools/gen_gametest_fixture.py) loads with every block in place and every block item in its
     * chests, and the neon fold turns it into 0.8.0 content: a {@code ChunkEvent.Load} is posted for each fixture
     * chunk (the real hook the loader fires), the queued swap runs on the next server tick, and then every neon
     * position holds a {@code chroma_cube} with the old neon's COLOR and light 15, and every neon chest stack is a
     * chroma cube stack with that colour.
     */
    static void fixture071Loads(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        java.util.Set<Long> chunks = new java.util.HashSet<>();
        for (int x = 0; x < 12; x += 1) {
            for (int z = 0; z < 10; z += 1) {
                BlockPos abs = helper.absolutePos(new BlockPos(x, 1, z));
                if (chunks.add(((long) (abs.getX() >> 4) << 32) ^ ((abs.getZ() >> 4) & 0xFFFFFFFFL))) {
                    NeoForge.EVENT_BUS.post(new ChunkEvent.Load(level.getChunkAt(abs), false));
                }
            }
        }
        helper.succeedWhen(() -> {
            List<String> ids = fixtureIds();
            helper.assertTrue(ids.size() == 59, "fixture manifest has " + ids.size() + " ids, expected 59");
            helper.assertTrue(!helper.getBlockState(new BlockPos(1, 1, 1)).isAir(),
                    "fixture grid origin is air; " + describeNonAir(helper));
            List<ItemStack> stored = new ArrayList<>();
            for (int c = 0; c < 3; c++) {
                BlockPos chestPos = new BlockPos(1 + c * 2, 1, 8);
                helper.assertBlockPresent(net.minecraft.world.level.block.Blocks.CHEST, chestPos);
                boolean isContainer = helper.getLevel().getBlockEntity(helper.absolutePos(chestPos)) instanceof Container;
                helper.assertTrue(isContainer, "fixture chest " + c + " has no container");
                Container chest = (Container) helper.getLevel().getBlockEntity(helper.absolutePos(chestPos));
                for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                    if (!chest.getItem(slot).isEmpty()) {
                        stored.add(chest.getItem(slot));
                    }
                }
            }
            for (ItemStack stack : stored) {
                helper.assertTrue(!(stack.getItem() instanceof NeonShimItem), "a chest still holds a legacy neon stack");
            }
            Block cube = ChromaContent.CUBE.get();
            for (int n = 0; n < ids.size(); n++) {
                String path = ids.get(n);
                BlockPos pos = new BlockPos(1 + n % 10, 1, 1 + n / 10);
                if (path.startsWith("neon_")) {
                    DecorColor dye = DecorColor.valueOf(path.substring(5).toUpperCase(java.util.Locale.ROOT));
                    helper.assertBlockPresent(cube, pos);
                    helper.assertBlockProperty(pos, DecorProperties.COLOR, dye);
                    helper.assertBlockProperty(pos, ChromaCubeBlock.LIGHT, 15);
                    helper.assertTrue(level.getLightEmission(helper.absolutePos(pos)) == 15, path + " lost its light");
                    helper.assertTrue(stored.stream().anyMatch(s -> s.is(cube.asItem())
                            && s.get(ModDataComponents.COLOR.get()) == dye), "no " + dye + " chroma cube for " + path);
                    continue;
                }
                Identifier id = Identifier.fromNamespaceAndPath(NeroDecorCommon.MOD_ID, path);
                Block expected = BuiltInRegistries.BLOCK.getValue(id);
                helper.assertBlockPresent(expected, pos);
                helper.assertTrue(stored.stream().anyMatch(s -> s.is(expected.asItem())), "fixture chests are missing " + id);
            }
        });
    }

    /**
     * A chest that still carries a loot table is never read by the neon fold: after a chunk-load pass and a direct
     * {@code convertContainer} call it still has its table, unrolled (reading it would roll loot with no player).
     */
    static void neonFoldKeepsLoot(GameTestHelper helper) {
        BlockPos chestPos = new BlockPos(1, 1, 1);
        helper.setBlock(chestPos, net.minecraft.world.level.block.Blocks.CHEST);
        BlockPos abs = helper.absolutePos(chestPos);
        ServerLevel level = helper.getLevel();
        var chest = (net.minecraft.world.level.block.entity.ChestBlockEntity) level.getBlockEntity(abs);
        var table = net.minecraft.world.level.storage.loot.BuiltInLootTables.SIMPLE_DUNGEON;
        chest.setLootTable(table, 42L);
        helper.assertTrue(za.co.neroland.nerodecor.content.NeonFold.hasPendingLoot(chest), "loot table not detected");
        helper.assertTrue(za.co.neroland.nerodecor.content.NeonFold.convertContainer(chest) == 0,
                "the fold touched a loot chest");
        NeoForge.EVENT_BUS.post(new ChunkEvent.Load(level.getChunkAt(abs), false));
        NeoForge.EVENT_BUS.post(new ChunkEvent.Load(level.getChunkAt(abs), true));
        helper.runAfterDelay(5, () -> {
            var after = (net.minecraft.world.level.block.entity.ChestBlockEntity) level.getBlockEntity(abs);
            helper.assertTrue(after != null && table.equals(after.getLootTable()),
                    "the loot chest lost its loot table after the fold ran");
            helper.succeed();
        });
    }

    private static String describeNonAir(GameTestHelper helper) {
        var template = helper.getLevel().getStructureTemplateManager()
                .get(Identifier.fromNamespaceAndPath(MOD_ID, "fixture_071")).orElse(null);
        if (template == null) {
            return "template not found";
        }
        var settings = new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings();
        int chests = template.filterBlocks(BlockPos.ZERO, settings, net.minecraft.world.level.block.Blocks.CHEST).size();
        int hull = template.filterBlocks(BlockPos.ZERO, settings,
                BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath(NeroDecorCommon.MOD_ID, "hull_nero_alloy"))).size();
        int air = template.filterBlocks(BlockPos.ZERO, settings, net.minecraft.world.level.block.Blocks.AIR).size();
        return "template size " + template.getSize() + " chests " + chests + " hull " + hull + " air " + air;
    }

    private static List<String> fixtureIds() {
        List<String> ids = new ArrayList<>();
        try (InputStream in = NeroDecorGameTests.class.getResourceAsStream("/data/nerodecor_gametest/fixture_071_ids.json")) {
            if (in == null) {
                return ids;
            }
            for (JsonElement e : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonArray("ids")) {
                ids.add(e.getAsString());
            }
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("fixture manifest unreadable", ex);
        }
        return ids;
    }
}
