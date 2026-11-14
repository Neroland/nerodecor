package za.co.neroland.nerodecor.gametest;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.DeferredRegister;

import za.co.neroland.nerodecor.chroma.ChromaColor;
import za.co.neroland.nerodecor.chroma.Easing;
import za.co.neroland.nerodecor.chroma.EffectParams;
import za.co.neroland.nerodecor.chroma.EffectProgram;
import za.co.neroland.nerodecor.chroma.EffectType;
import za.co.neroland.nerodecor.chroma.Keyframe;
import za.co.neroland.nerodecor.chroma.Scene;
import za.co.neroland.nerodecor.chroma.SceneSlots;
import za.co.neroland.nerodecor.chroma.Timeline;
import za.co.neroland.nerodecor.chroma.ZoneAssignment;
import za.co.neroland.nerodecor.chroma.server.ChromaNetworkStore;
import za.co.neroland.nerodecor.chroma.server.ChromaNetworks;
import za.co.neroland.nerodecor.chroma.server.ChromaRole;
import za.co.neroland.nerodecor.chroma.server.ChromaScenes;
import za.co.neroland.nerodecor.chroma.server.ChromaVerdict;
import za.co.neroland.nerodecor.chroma.server.HubNetwork;
import za.co.neroland.nerodecor.content.block.ChromaHubBlockEntity;
import za.co.neroland.nerodecor.content.item.SceneChipData;
import za.co.neroland.nerodecor.content.item.SceneChipItem;
import za.co.neroland.nerodecor.registry.ChromaContent;

/**
 * Chroma Stage 4 (scenes) GameTests: redstone and daylight triggers pick the slot, the server refuses a strobe, and a
 * Scene Chip loads into a hub only for a painter or higher. All in the empty {@code chroma_arena} with mock players
 * that are not connected.
 */
final class ChromaSceneGameTests {

    private ChromaSceneGameTests() {
    }

    static void register(DeferredRegister<Consumer<GameTestHelper>> functions) {
        functions.register("chroma_scene_redstone", () -> ChromaSceneGameTests::redstoneSelectsSlot);
        functions.register("chroma_scene_daylight", () -> ChromaSceneGameTests::daylightSwitchesSlot);
        functions.register("chroma_scene_strobe_rejected", () -> ChromaSceneGameTests::strobeRejected);
        functions.register("chroma_scene_chip_roles", () -> ChromaSceneGameTests::chipRespectsRoles);
    }

    // --- helpers ---------------------------------------------------------------------------------------------------

    private static ServerPlayer player(GameTestHelper helper) {
        return (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
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

    /** A calm, flash-clamp-safe scene whose colour identifies it. */
    private static Scene calm(int n) {
        ChromaColor c = ChromaColor.of(20 * n + 30, 200 - 20 * n, 120);
        return new Scene("Calm " + n, List.of(new ZoneAssignment(0, 0, new EffectProgram(EffectType.BREATHE,
                EffectParams.builder().primary(c).secondary(ChromaColor.of(10, 10, 40)).periodTicks(80).build()))));
    }

    private static void fill(GameTestHelper helper, ChromaNetworkStore store, long id, ServerPlayer owner) {
        for (int slot = 0; slot < SceneSlots.MAX_SLOTS; slot++) {
            ChromaVerdict v = ChromaScenes.save(store, id, owner.getUUID(), slot, calm(slot), false,
                    helper.getLevel().getGameTime());
            expect(helper, ChromaVerdict.OK, v, "save slot " + slot);
        }
    }

    private static void expect(GameTestHelper helper, Object expected, Object actual, String what) {
        helper.assertTrue(expected.equals(actual), what + ": expected " + expected + " but was " + actual);
    }

    // --- tests -----------------------------------------------------------------------------------------------------

    /** Redstone strength into the hub selects the slot (default map: 0 static, else min(strength - 1, 7)). */
    static void redstoneSelectsSlot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = player(helper);
        BlockPos hubLocal = new BlockPos(12, 2, 12);
        long id = placeHub(helper, hubLocal, owner);
        ChromaNetworkStore store = ChromaNetworks.store(level);
        fill(helper, store, id, owner);
        HubNetwork network = store.get(id);
        expect(helper, ChromaVerdict.OK, ChromaScenes.setTrigger(store, id, owner.getUUID(),
                SceneSlots.Trigger.REDSTONE, null, null), "set trigger");
        ChromaScenes.tick(level, true);
        expect(helper, SceneSlots.STATIC, network.scenes().active(), "unpowered");

        helper.setBlock(hubLocal.east(), Blocks.REDSTONE_BLOCK);
        ChromaScenes.tick(level, true);
        expect(helper, 15, ChromaScenes.redstone(level, network), "strength");
        expect(helper, 7, network.scenes().active(), "strength 15 -> slot 7");
        expect(helper, level.getGameTime(), network.scenes().startTick(), "start tick");

        int[] map = SceneSlots.defaultRedstoneMap();
        map[15] = 2;
        expect(helper, ChromaVerdict.OK, ChromaScenes.setTrigger(store, id, owner.getUUID(),
                SceneSlots.Trigger.REDSTONE, map, SceneSlots.defaultDaylightMap()), "custom map");
        ChromaScenes.tick(level, true);
        expect(helper, 2, network.scenes().active(), "custom map 15 -> slot 2");

        helper.setBlock(hubLocal.east(), Blocks.AIR);
        ChromaScenes.tick(level, true);
        expect(helper, SceneSlots.STATIC, network.scenes().active(), "power removed");

        ServerPlayer stranger = player(helper);
        expect(helper, ChromaVerdict.NO_PERMISSION, ChromaScenes.setTrigger(store, id, stranger.getUUID(),
                SceneSlots.Trigger.MANUAL, null, null), "stranger changes trigger");
        helper.succeed();
    }

    /** The daylight band picks the slot (default map: dawn 0, day 1, dusk 2, night 3). */
    static void daylightSwitchesSlot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = player(helper);
        long id = placeHub(helper, new BlockPos(12, 2, 12), owner);
        ChromaNetworkStore store = ChromaNetworks.store(level);
        fill(helper, store, id, owner);
        HubNetwork network = store.get(id);
        ChromaScenes.setTrigger(store, id, owner.getUUID(), SceneSlots.Trigger.DAYLIGHT, null, null);
        var clock = level.dimensionType().defaultClock();
        helper.assertTrue(clock.isPresent(), "test dimension has no day clock");
        long before = ChromaScenes.dayTime(level);
        try {
            level.getServer().clockManager().setTotalTicks(clock.get(), 6000L);
            ChromaScenes.tick(level, true);
            expect(helper, 1, network.scenes().active(), "day");
            level.getServer().clockManager().setTotalTicks(clock.get(), 18000L);
            ChromaScenes.tick(level, true);
            expect(helper, 3, network.scenes().active(), "night");
            level.getServer().clockManager().setTotalTicks(clock.get(), 12500L);
            ChromaScenes.tick(level, true);
            expect(helper, 2, network.scenes().active(), "dusk");
        } finally {
            level.getServer().clockManager().setTotalTicks(clock.get(), before);
        }
        helper.succeed();
    }

    /** A 10 Hz full-area strobe is refused by the server (save and slot stay empty). */
    static void strobeRejected(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = player(helper);
        long id = placeHub(helper, new BlockPos(12, 2, 12), owner);
        ChromaNetworkStore store = ChromaNetworks.store(level);
        Timeline strobe = new Timeline(List.of(new Keyframe(0, ChromaColor.WHITE, Easing.LINEAR),
                new Keyframe(1, ChromaColor.BLACK, Easing.STEP)), 2, true);
        Scene scene = new Scene("Strobe", List.of(new ZoneAssignment(0, 0, strobe)));
        expect(helper, ChromaVerdict.FLASH_REJECTED, ChromaScenes.save(store, id, owner.getUUID(), 0, scene, true,
                level.getGameTime()), "strobe save");
        helper.assertTrue(store.get(id).scenes().scene(0) == null, "strobe was stored");
        expect(helper, SceneSlots.STATIC, store.get(id).scenes().active(), "strobe plays");
        Scene pulse = new Scene("Fast pulse", List.of(new ZoneAssignment(0, 0, new EffectProgram(EffectType.PULSE,
                EffectParams.builder().primary(ChromaColor.BLACK).secondary(ChromaColor.WHITE).periodTicks(2)
                        .build()))));
        expect(helper, ChromaVerdict.FLASH_REJECTED, ChromaScenes.save(store, id, owner.getUUID(), 1, pulse, true,
                level.getGameTime()), "fast pulse save");
        helper.succeed();
    }

    /** A chip loads into a hub only for a painter or higher; a tampered chip is refused. */
    static void chipRespectsRoles(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = player(helper);
        ServerPlayer guest = player(helper);
        BlockPos hubLocal = new BlockPos(12, 2, 12);
        long id = placeHub(helper, hubLocal, owner);
        BlockPos hubPos = helper.absolutePos(hubLocal);
        ChromaNetworkStore store = ChromaNetworks.store(level);
        ItemStack chip = new ItemStack(ChromaContent.SCENE_CHIP_ITEM.get());
        helper.assertTrue(SceneChipItem.write(chip, calm(3)), "write chip");
        helper.assertTrue(SceneChipItem.scene(chip).orElseThrow().equals(calm(3)), "chip round trip");

        expect(helper, ChromaVerdict.NO_PERMISSION, SceneChipItem.load(level, guest, hubPos, chip).verdict(),
                "stranger");
        store.setTrust(id, owner.getUUID(), guest.getUUID(), ChromaRole.VIEWER);
        expect(helper, ChromaVerdict.NO_PERMISSION, SceneChipItem.load(level, guest, hubPos, chip).verdict(),
                "viewer");
        helper.assertTrue(store.get(id).scenes().filled() == 0, "a refused load stored a scene");

        store.setTrust(id, owner.getUUID(), guest.getUUID(), ChromaRole.PAINTER);
        SceneChipItem.Loaded loaded = SceneChipItem.load(level, guest, hubPos, chip);
        expect(helper, ChromaVerdict.OK, loaded.verdict(), "painter");
        expect(helper, 0, loaded.slot(), "first empty slot");
        expect(helper, 0, store.get(id).scenes().active(), "loaded scene plays");

        ItemStack copy = new ItemStack(ChromaContent.SCENE_CHIP_ITEM.get());
        expect(helper, ChromaVerdict.OK, SceneChipItem.copy(level, guest, hubPos, copy), "sneak copy");
        helper.assertTrue(SceneChipItem.scene(copy).orElseThrow().equals(calm(3)), "copied scene");

        byte[] bytes = chip.get(ChromaContent.SCENE_CHIP.get()).data();
        bytes[bytes.length / 2] ^= 0x5A;
        ItemStack tampered = new ItemStack(ChromaContent.SCENE_CHIP_ITEM.get());
        tampered.set(ChromaContent.SCENE_CHIP.get(), new SceneChipData(bytes));
        expect(helper, ChromaVerdict.BAD_SCENE, SceneChipItem.load(level, owner, hubPos, tampered).verdict(),
                "tampered chip");
        helper.succeed();
    }
}
