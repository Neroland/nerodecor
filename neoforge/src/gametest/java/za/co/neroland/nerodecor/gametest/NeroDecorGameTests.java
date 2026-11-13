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
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredRegister;

import za.co.neroland.nerodecor.NeroDecorCommon;
import za.co.neroland.nerodecor.content.DecorColor;
import za.co.neroland.nerodecor.content.DecorProperties;
import za.co.neroland.nerodecor.registry.DecorBlocks;

/**
 * Development-only server GameTests for NeroDecor (26.3 NeoForge cell). Each test is a vanilla
 * {@code test_function} paired with a data-driven {@code test_instance} under
 * {@code data/nerodecor_gametest/test_instance}. Run with {@code gradlew :neoforge:26.3:runGameTestServer}.
 */
@Mod(NeroDecorGameTests.MOD_ID)
public final class NeroDecorGameTests {

    public static final String MOD_ID = "nerodecor_gametest";

    /** Registry cap from ADR-001 / the 0.8.0 brief. */
    static final int REGISTRY_CAP = 200;

    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
            DeferredRegister.create(Registries.TEST_FUNCTION, MOD_ID);

    static {
        FUNCTIONS.register("place_every_block", () -> NeroDecorGameTests::placeEveryBlock);
        FUNCTIONS.register("paint_survives_placement", () -> NeroDecorGameTests::paintSurvivesPlacement);
        FUNCTIONS.register("registry_within_cap", () -> NeroDecorGameTests::registryWithinCap);
        FUNCTIONS.register("fixture_071_loads", () -> NeroDecorGameTests::fixture071Loads);
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
        helper.assertTrue(registered == DecorBlocks.ALL.size(),
                "registered " + registered + " != DecorBlocks.ALL " + DecorBlocks.ALL.size());
        helper.assertTrue(registered <= REGISTRY_CAP, "registry " + registered + " exceeds cap " + REGISTRY_CAP);
        helper.succeed();
    }

    /**
     * The 0.7.1 fixture (tools/gen_gametest_fixture.py) loads with every block in place and every block item
     * in its chests. Stage 3 extends this to assert the neon cubes come back as same-colour chroma cubes.
     */
    static void fixture071Loads(GameTestHelper helper) {
        helper.succeedWhen(() -> {
            List<String> ids = fixtureIds();
            helper.assertTrue(ids.size() == 59, "fixture manifest has " + ids.size() + " ids, expected 59");
            helper.assertTrue(!helper.getBlockState(new BlockPos(1, 1, 1)).isAir(),
                    "fixture grid origin is air; " + describeNonAir(helper));
            List<Item> stored = new ArrayList<>();
            for (int c = 0; c < 3; c++) {
                BlockPos chestPos = new BlockPos(1 + c * 2, 1, 8);
                helper.assertBlockPresent(net.minecraft.world.level.block.Blocks.CHEST, chestPos);
                boolean isContainer = helper.getLevel().getBlockEntity(helper.absolutePos(chestPos)) instanceof Container;
                helper.assertTrue(isContainer, "fixture chest " + c + " has no container");
                Container chest = (Container) helper.getLevel().getBlockEntity(helper.absolutePos(chestPos));
                for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                    if (!chest.getItem(slot).isEmpty()) {
                        stored.add(chest.getItem(slot).getItem());
                    }
                }
            }
            for (int n = 0; n < ids.size(); n++) {
                Identifier id = Identifier.fromNamespaceAndPath(NeroDecorCommon.MOD_ID, ids.get(n));
                Block expected = BuiltInRegistries.BLOCK.getValue(id);
                helper.assertBlockPresent(expected, new BlockPos(1 + n % 10, 1, 1 + n / 10));
                helper.assertTrue(stored.contains(expected.asItem()), "fixture chests are missing " + id);
            }
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
