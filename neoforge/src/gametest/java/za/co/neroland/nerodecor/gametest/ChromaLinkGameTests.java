package za.co.neroland.nerodecor.gametest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.DeferredRegister;

import za.co.neroland.nerolandcore.link.LinkActionResult;
import za.co.neroland.nerolandcore.link.LinkEvent;
import za.co.neroland.nerolandcore.link.LinkSnapshotProvider;
import za.co.neroland.nerolandcore.link.NeroLinkRegistry;

import za.co.neroland.nerodecor.chroma.ChromaColor;
import za.co.neroland.nerodecor.chroma.EffectParams;
import za.co.neroland.nerodecor.chroma.EffectProgram;
import za.co.neroland.nerodecor.chroma.EffectType;
import za.co.neroland.nerodecor.chroma.Scene;
import za.co.neroland.nerodecor.chroma.ZoneAssignment;
import za.co.neroland.nerodecor.chroma.server.ChromaNetworkStore;
import za.co.neroland.nerodecor.chroma.server.ChromaNetworks;
import za.co.neroland.nerodecor.chroma.server.ChromaRole;
import za.co.neroland.nerodecor.chroma.server.ChromaScenes;
import za.co.neroland.nerodecor.chroma.server.ChromaVerdict;
import za.co.neroland.nerodecor.content.block.ChromaHubBlockEntity;
import za.co.neroland.nerodecor.link.ChromaLinkActions;
import za.co.neroland.nerodecor.link.ChromaLinkSections;
import za.co.neroland.nerodecor.link.LiveChromaLink;
import za.co.neroland.nerodecor.link.NeroDecorLinkModule;
import za.co.neroland.nerodecor.registry.ChromaContent;

/**
 * NeroLink schema 2 against a real world: the registered provider shows a hub only to its members and never a
 * coordinate or a roster; every action is refused for a stranger; one action per second per requester; and the live
 * events reach the hub's members only. Mock players are not in the player list, so the live source is given them.
 */
final class ChromaLinkGameTests {

    private static final Pattern NO_KEYS = Pattern.compile(
            "\"(x|y|z|pos|anchor|dimension|owner|trusted|uuid|player|players)\"\\s*:");

    private ChromaLinkGameTests() {
    }

    static void register(DeferredRegister<Consumer<GameTestHelper>> functions) {
        functions.register("chroma_link_scoping", () -> ChromaLinkGameTests::scoping);
        functions.register("chroma_link_rate_limit", () -> ChromaLinkGameTests::rateLimit);
        functions.register("chroma_link_events", () -> ChromaLinkGameTests::events);
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

    private static BlockPos cube(GameTestHelper helper, BlockPos local) {
        helper.setBlock(local, ChromaContent.CUBE.get());
        return helper.absolutePos(local);
    }

    private static Scene calm(int n) {
        ChromaColor c = ChromaColor.of(40 * n + 30, 180, 120);
        return new Scene("Calm " + n, List.of(new ZoneAssignment(0, 0, new EffectProgram(EffectType.BREATHE,
                EffectParams.builder().primary(c).secondary(ChromaColor.of(10, 10, 40)).periodTicks(80).build()))));
    }

    /** A hub owned by {@code owner} with two linked cubes, {@code painter} trusted as painter, two scenes. */
    private static long hub(GameTestHelper helper, ServerPlayer owner, ServerPlayer painter) {
        ServerLevel level = helper.getLevel();
        long id = placeHub(helper, new BlockPos(12, 2, 12), owner);
        expect(helper, ChromaVerdict.OK, ChromaNetworks.link(level, owner, id, cube(helper, new BlockPos(14, 2, 12))),
                "link one");
        expect(helper, ChromaVerdict.OK, ChromaNetworks.link(level, owner, id, cube(helper, new BlockPos(14, 3, 12))),
                "link two");
        ChromaNetworkStore store = ChromaNetworks.store(level);
        expect(helper, ChromaVerdict.OK, store.setTrust(id, owner.getUUID(), painter.getUUID(), ChromaRole.PAINTER),
                "trust");
        for (int slot = 0; slot < 2; slot++) {
            expect(helper, ChromaVerdict.OK, ChromaScenes.save(store, id, owner.getUUID(), slot, calm(slot), false,
                    level.getGameTime()), "save " + slot);
        }
        return id;
    }

    private static LiveChromaLink live(GameTestHelper helper, ServerPlayer... online) {
        Map<UUID, ServerPlayer> players = new HashMap<>();
        for (ServerPlayer p : online) {
            players.put(p.getUUID(), p);
        }
        return new LiveChromaLink(helper.getLevel().getServer(), players::get);
    }

    private static JsonObject body(long id) {
        JsonObject body = new JsonObject();
        body.addProperty("hub", ChromaLinkSections.hubId(id));
        body.addProperty("slot", 1);
        body.addProperty("zone", 0);
        body.addProperty("colour", "#FF0000");
        body.addProperty("value", 4);
        return body;
    }

    private static boolean listsHub(JsonObject hubs, long id) {
        String wanted = ChromaLinkSections.hubId(id);
        for (JsonElement row : hubs.getAsJsonArray("hubs")) {
            if (wanted.equals(row.getAsJsonObject().get("hub").getAsString())) {
                return true;
            }
        }
        return false;
    }

    /** No anchor coordinate appears as a JSON number, no position/roster key appears, no member UUID appears. */
    private static void assertClean(GameTestHelper helper, String payload, BlockPos anchor, List<UUID> members) {
        helper.assertTrue(!NO_KEYS.matcher(payload).find(), "payload has a position or roster key: " + payload);
        for (int c : new int[] {anchor.getX(), anchor.getY(), anchor.getZ()}) {
            Pattern number = Pattern.compile("[:\\[,]\\s*" + Pattern.quote(Integer.toString(c)) + "\\s*[,\\]}]");
            if (Math.abs(c) >= 10) { // small numbers collide with counts and slots
                helper.assertTrue(!number.matcher(payload).find(), "payload contains coordinate " + c);
            }
        }
        for (UUID member : members) {
            helper.assertTrue(!payload.contains(member.toString()), "payload contains a member UUID");
        }
    }

    private static void expect(GameTestHelper helper, Object expected, Object actual, String what) {
        helper.assertTrue(expected.equals(actual), what + ": expected " + expected + " but was " + actual);
    }

    // --- tests -----------------------------------------------------------------------------------------------------

    /** The registered provider scopes by role; a stranger's every action is refused; nothing leaks positions. */
    static void scoping(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        ServerPlayer painter = player(helper);
        ServerPlayer stranger = player(helper);
        long id = hub(helper, owner, painter);
        BlockPos anchor = helper.absolutePos(new BlockPos(12, 2, 12));
        LinkSnapshotProvider provider = NeroLinkRegistry.snapshotProvider(NeroDecorLinkModule.MODULE_ID)
                .orElse(null);
        helper.assertTrue(provider != null, "nerodecor link module not registered");
        expect(helper, NeroDecorLinkModule.SCHEMA_VERSION, provider.schemaVersion(), "schema");
        helper.assertTrue(NeroDecorLinkModule.SCHEMA_VERSION == 2, "schema 2");

        Map<String, String> byHub = Map.of("hub", ChromaLinkSections.hubId(id));
        JsonObject strangerHubs = provider.snapshot(stranger.getUUID(), NeroDecorLinkModule.SECTION_CHROMA_HUBS,
                Map.of());
        helper.assertTrue(!listsHub(strangerHubs, id), "stranger sees the hub");
        helper.assertTrue(provider.snapshot(stranger.getUUID(), NeroDecorLinkModule.SECTION_CHROMA_ZONES, byHub)
                .getAsJsonArray("zones").isEmpty(), "stranger sees zones");
        helper.assertTrue(provider.snapshot(stranger.getUUID(), NeroDecorLinkModule.SECTION_CHROMA_SCENES, byHub)
                .getAsJsonArray("slots").isEmpty(), "stranger sees scenes");

        List<UUID> members = List.of(owner.getUUID(), painter.getUUID(), stranger.getUUID());
        for (ServerPlayer member : List.of(owner, painter)) {
            JsonObject hubs = provider.snapshot(member.getUUID(), NeroDecorLinkModule.SECTION_CHROMA_HUBS, Map.of());
            helper.assertTrue(listsHub(hubs, id), "member does not see the hub");
            JsonObject zones = provider.snapshot(member.getUUID(), NeroDecorLinkModule.SECTION_CHROMA_ZONES, byHub);
            JsonArray rows = zones.getAsJsonArray("zones");
            helper.assertTrue(rows.size() == 1 && rows.get(0).getAsJsonObject().get("blocks").getAsInt() == 2,
                    "zone rows " + rows);
            JsonObject scenes = provider.snapshot(member.getUUID(), NeroDecorLinkModule.SECTION_CHROMA_SCENES, byHub);
            expect(helper, 8, scenes.getAsJsonArray("slots").size(), "slots");
            assertClean(helper, hubs.toString() + zones + scenes, anchor, members);
        }

        LiveChromaLink world = live(helper, owner, painter, stranger);
        for (String action : ChromaLinkActions.ACTIONS) {
            // A fresh handler per call, so the one-per-second limit does not mask the scoping refusal.
            LinkActionResult result = new ChromaLinkActions(() -> world).execute(stranger.getUUID(), action, body(id));
            helper.assertTrue(!result.ok() && result.error() == LinkActionResult.Error.NOT_OWNER,
                    "stranger " + action + " was not refused as not_owner: " + result.message());
        }
        helper.assertTrue(ChromaNetworks.store(helper.getLevel()).get(id).scenes().active() < 0, "scene changed");
        helper.succeed();
    }

    /** Two actions from one requester within a second: the first runs, the second is refused. */
    static void rateLimit(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        ServerPlayer painter = player(helper);
        long id = hub(helper, owner, painter);
        LiveChromaLink world = live(helper, owner, painter);
        ChromaLinkActions actions = new ChromaLinkActions(() -> world);
        LinkActionResult first = actions.execute(painter.getUUID(), ChromaLinkActions.APPLY_SCENE, body(id));
        helper.assertTrue(first.ok(), "first apply_scene refused: " + first.message());
        expect(helper, 1, ChromaNetworks.store(helper.getLevel()).get(id).scenes().active(), "slot 1 playing");
        JsonObject again = body(id);
        again.addProperty("slot", 0);
        LinkActionResult second = actions.execute(painter.getUUID(), ChromaLinkActions.APPLY_SCENE, again);
        helper.assertTrue(!second.ok() && "rate_limited".equals(second.message()),
                "second action within a second was not rate limited: " + second.message());
        expect(helper, 1, ChromaNetworks.store(helper.getLevel()).get(id).scenes().active(), "still slot 1");
        helper.succeed();
    }

    /** scene_changed and hub_broken reach the owner and the painter, nobody else, and carry no position. */
    static void events(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = player(helper);
        ServerPlayer painter = player(helper);
        ServerPlayer stranger = player(helper);
        long id = hub(helper, owner, painter);
        String hubId = ChromaLinkSections.hubId(id);
        BlockPos anchor = helper.absolutePos(new BlockPos(12, 2, 12));
        List<LinkEvent> seen = new ArrayList<>();
        Consumer<LinkEvent> subscriber = event -> {
            if (NeroDecorLinkModule.MODULE_ID.equals(event.moduleId()) && event.payload() != null
                    && event.payload().has("hub") && hubId.equals(event.payload().get("hub").getAsString())) {
                seen.add(event);
            }
        };
        NeroLinkRegistry.eventBus().subscribe(subscriber);
        try {
            expect(helper, ChromaVerdict.OK, ChromaScenes.activate(ChromaNetworks.store(level), id,
                    owner.getUUID(), 0, level.getGameTime()), "activate");
            level.destroyBlock(anchor, false);
        } finally {
            NeroLinkRegistry.eventBus().unsubscribe(subscriber);
        }
        Set<String> types = new HashSet<>();
        Set<UUID> to = new HashSet<>();
        for (LinkEvent event : seen) {
            helper.assertTrue(!event.isBroadcast(), "a Chroma event was broadcast");
            types.add(event.payload().get("type").getAsString());
            to.add(event.playerId());
            assertClean(helper, event.payload().toString(), anchor, List.of(owner.getUUID(), painter.getUUID()));
        }
        helper.assertTrue(types.contains("scene_changed") && types.contains("hub_broken"), "events " + types);
        expect(helper, Set.of(owner.getUUID(), painter.getUUID()), to, "recipients");
        helper.assertTrue(!to.contains(stranger.getUUID()), "a stranger got an event");
        helper.succeed();
    }
}
