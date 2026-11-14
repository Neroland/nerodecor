package za.co.neroland.nerodecor.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongPredicate;

import com.google.gson.JsonObject;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerolandcore.link.LinkActionResult;
import za.co.neroland.nerolandcore.link.LinkEvent;

import za.co.neroland.nerodecor.chroma.ChromaColor;
import za.co.neroland.nerodecor.chroma.ChromaOp;
import za.co.neroland.nerodecor.chroma.ChromaOpBatch;
import za.co.neroland.nerodecor.chroma.EffectParams;
import za.co.neroland.nerodecor.chroma.EffectProgram;
import za.co.neroland.nerodecor.chroma.EffectType;
import za.co.neroland.nerodecor.chroma.Scene;
import za.co.neroland.nerodecor.chroma.SceneSlots;
import za.co.neroland.nerodecor.chroma.ZoneAssignment;
import za.co.neroland.nerodecor.chroma.server.ChromaLimits;
import za.co.neroland.nerodecor.chroma.server.ChromaNetworkStore;
import za.co.neroland.nerodecor.chroma.server.ChromaRole;
import za.co.neroland.nerodecor.chroma.server.ChromaScenes;
import za.co.neroland.nerodecor.chroma.server.ChromaVerdict;
import za.co.neroland.nerodecor.chroma.server.HubNetwork;

/**
 * Link schema 2, the Chroma half, on an in-memory store: scoping (a stranger sees nothing and can do nothing),
 * the payload carries no coordinates and no roster, the one-per-second limit, and the actions going through the
 * GUI's own rules.
 */
class ChromaLinkTest {

    private static final UUID OWNER = new UUID(0x1111L, 0x1111L);
    private static final UUID PAINTER = new UUID(0x2222L, 0x2222L);
    private static final UUID VIEWER = new UUID(0x3333L, 0x3333L);
    private static final UUID STRANGER = new UUID(0x4444L, 0x4444L);
    private static final long HUB = 0x00C0FFEEABCDEF01L;
    private static final int ANCHOR_X = 1913;
    private static final int ANCHOR_Y = 77;
    private static final int ANCHOR_Z = -5678;

    /** The test world: one store, everybody online, nothing protected unless a test says so. */
    private static final class FakeSource implements ChromaLinkSource {
        final ChromaNetworkStore store = new ChromaNetworkStore();
        final Set<UUID> online = new HashSet<>(List.of(OWNER, PAINTER, VIEWER, STRANGER));
        boolean enabled = true;
        LongPredicate guard = packed -> true;
        int charged;

        @Override
        public boolean enabled() {
            return enabled;
        }

        @Override
        public List<HubNetwork> networks() {
            return new ArrayList<>(store.all());
        }

        @Override
        public Optional<Hub> hub(long id) {
            HubNetwork network = store.get(id);
            return network == null ? Optional.empty() : Optional.of(new Hub(network, store, 100L));
        }

        @Override
        public LongPredicate editor(UUID player, Hub hub) {
            return online.contains(player) ? guard : null;
        }

        @Override
        public ChromaVerdict charge(UUID player, int ops) {
            charged += ops;
            return ChromaVerdict.OK;
        }
    }

    private static FakeSource world() {
        FakeSource source = new FakeSource();
        ChromaNetworkStore store = source.store;
        store.create(HUB, OWNER, ANCHOR_X, ANCHOR_Y, ANCHOR_Z, 32, 32, 32);
        for (int i = 0; i < 4; i++) {
            assertEquals(ChromaVerdict.OK, store.link(HUB, OWNER, ANCHOR_X + i, ANCHOR_Y, ANCHOR_Z, true,
                    ChromaLimits.DEFAULTS));
        }
        HubNetwork network = store.get(HUB);
        int rel = network.linkedOffsets().get(0);
        assertEquals(ChromaVerdict.OK, store.applyOps(HUB, OWNER, ChromaOpBatch.of(List.of(
                ChromaOp.setPaletteEntry(0, ChromaColor.ofRgb(0x336699, 12)),
                ChromaOp.setIndex(rel, 0))).value(), p -> true));
        assertEquals(ChromaVerdict.OK, store.setTrust(HUB, OWNER, PAINTER, ChromaRole.PAINTER));
        assertEquals(ChromaVerdict.OK, store.setTrust(HUB, OWNER, VIEWER, ChromaRole.VIEWER));
        for (int slot = 0; slot < 2; slot++) {
            assertEquals(ChromaVerdict.OK, ChromaScenes.save(store, HUB, OWNER, slot, calm(slot), false, 10L));
        }
        return source;
    }

    private static Scene calm(int n) {
        ChromaColor c = ChromaColor.of(20 * n + 30, 200 - 20 * n, 120);
        return new Scene("Calm " + n, List.of(new ZoneAssignment(0, 0, new EffectProgram(EffectType.BREATHE,
                EffectParams.builder().primary(c).secondary(ChromaColor.of(10, 10, 40)).periodTicks(80).build()))));
    }

    /** A clock the test moves by hand, starting well past zero. */
    private static ChromaLinkActions actions(FakeSource source, AtomicLong clock) {
        return new ChromaLinkActions(() -> source, clock::get);
    }

    private static JsonObject body(String hub) {
        JsonObject body = new JsonObject();
        body.addProperty("hub", hub);
        return body;
    }

    private static String hub() {
        return ChromaLinkSections.hubId(HUB);
    }

    // --- scoping --------------------------------------------------------------------------------------------------

    @Test
    void strangerSeesNothing() {
        FakeSource source = world();
        assertEquals(0, ChromaLinkSections.hubs(source, STRANGER).getAsJsonArray("hubs").size());
        assertEquals(0, ChromaLinkSections.zones(source, STRANGER, hub()).getAsJsonArray("zones").size());
        assertEquals(0, ChromaLinkSections.scenes(source, STRANGER, hub()).getAsJsonArray("slots").size());
        // Not yours and does-not-exist answer identically: no probing ids.
        assertEquals(ChromaLinkSections.zones(source, STRANGER, hub()).toString(),
                ChromaLinkSections.zones(source, STRANGER, "00000000deadbeef").toString());
    }

    @Test
    void membersSeeTheirHub() {
        FakeSource source = world();
        for (UUID member : List.of(OWNER, PAINTER, VIEWER)) {
            JsonObject hubs = ChromaLinkSections.hubs(source, member);
            assertEquals(1, hubs.getAsJsonArray("hubs").size(), "member sees the hub");
        }
        JsonObject row = ChromaLinkSections.hubs(source, VIEWER).getAsJsonArray("hubs").get(0).getAsJsonObject();
        assertEquals("viewer", row.get("role").getAsString());
        assertFalse(row.get("can_control").getAsBoolean());
        assertEquals(4, row.get("blocks").getAsInt());
        assertEquals(12, row.get("brightness").getAsInt());
        assertEquals(1, row.getAsJsonObject("members").get("painter").getAsInt());
        assertEquals(1, row.getAsJsonObject("members").get("viewer").getAsInt());
        JsonObject zones = ChromaLinkSections.zones(source, OWNER, hub());
        assertEquals(1, zones.getAsJsonArray("zones").size());
        assertEquals("#336699", zones.getAsJsonArray("zones").get(0).getAsJsonObject().get("colour").getAsString());
        JsonObject scenes = ChromaLinkSections.scenes(source, OWNER, hub());
        assertEquals(SceneSlots.MAX_SLOTS, scenes.getAsJsonArray("slots").size());
        assertEquals("Calm 1", scenes.getAsJsonArray("slots").get(1).getAsJsonObject().get("name").getAsString());
    }

    @Test
    void payloadHasNoCoordinatesAndNoRoster() {
        FakeSource source = world();
        String all = ChromaLinkSections.hubs(source, OWNER).toString()
                + ChromaLinkSections.zones(source, OWNER, hub()).toString()
                + ChromaLinkSections.scenes(source, OWNER, hub()).toString();
        for (UUID player : List.of(OWNER, PAINTER, VIEWER)) {
            assertFalse(all.contains(player.toString()), "no UUID in the payload");
            assertFalse(all.toLowerCase(Locale.ROOT).contains(Long.toHexString(player.getMostSignificantBits())
                    + "-"), "no UUID fragment in the payload");
        }
        for (int c : new int[] {ANCHOR_X, ANCHOR_Y, ANCHOR_Z, ANCHOR_X + 1, ANCHOR_X + 3}) {
            assertFalse(all.contains(Integer.toString(c)), "no coordinate " + c + " in the payload");
        }
        for (String key : new String[] {"x", "y", "z", "anchor", "pos", "dimension", "owner",
                "trusted", "uuid", "player"}) {
            assertFalse(all.contains("\"" + key + "\":"), "no " + key + " key in the payload");
        }
    }

    @Test
    void disabledChromaShowsNothing() {
        FakeSource source = world();
        source.enabled = false;
        assertEquals(0, ChromaLinkSections.hubs(source, OWNER).getAsJsonArray("hubs").size());
        assertFalse(ChromaLinkSections.hubs(source, OWNER).get("chroma_enabled").getAsBoolean());
    }

    // --- actions ----------------------------------------------------------------------------------------------------

    @Test
    void everyActionRefusedForStrangerAndViewer() {
        FakeSource source = world();
        AtomicLong clock = new AtomicLong(1_000_000_000_000L);
        ChromaLinkActions actions = actions(source, clock);
        for (UUID who : List.of(STRANGER, VIEWER)) {
            for (String action : ChromaLinkActions.ACTIONS) {
                clock.addAndGet(2_000_000_000L);
                JsonObject body = body(hub());
                body.addProperty("slot", 0);
                body.addProperty("zone", 0);
                body.addProperty("colour", "#FF0000");
                body.addProperty("value", 3);
                LinkActionResult result = actions.execute(who, action, body);
                assertFalse(result.ok(), action + " refused");
                assertEquals(LinkActionResult.Error.NOT_OWNER, result.error(), action);
            }
        }
        HubNetwork network = source.store.get(HUB);
        assertEquals(SceneSlots.STATIC, network.scenes().active(), "nothing changed");
        assertEquals(12, network.palette().get(0).brightness(), "nothing changed");
        assertEquals(0, source.charged, "no Chroma edit was charged");
    }

    @Test
    void secondActionWithinASecondIsRefused() {
        FakeSource source = world();
        AtomicLong clock = new AtomicLong(1_000_000_000_000L);
        ChromaLinkActions actions = actions(source, clock);
        JsonObject apply = body(hub());
        apply.addProperty("slot", 0);
        assertTrue(actions.execute(PAINTER, ChromaLinkActions.APPLY_SCENE, apply).ok());
        clock.addAndGet(400_000_000L);
        LinkActionResult second = actions.execute(PAINTER, ChromaLinkActions.APPLY_SCENE, apply);
        assertFalse(second.ok());
        assertEquals("rate_limited", second.message());
        // The limit is per requester: the owner is not held up by the painter.
        JsonObject owners = body(hub());
        owners.addProperty("slot", 1);
        assertTrue(actions.execute(OWNER, ChromaLinkActions.APPLY_SCENE, owners).ok());
        clock.addAndGet(700_000_000L);
        assertTrue(actions.execute(PAINTER, ChromaLinkActions.APPLY_SCENE, apply).ok(), "refilled after a second");
    }

    @Test
    void painterCanDriveTheHub() {
        FakeSource source = world();
        AtomicLong clock = new AtomicLong(1_000_000_000_000L);
        ChromaLinkActions actions = actions(source, clock);
        HubNetwork network = source.store.get(HUB);

        JsonObject apply = body(hub());
        apply.addProperty("slot", 1);
        LinkActionResult applied = actions.execute(PAINTER, ChromaLinkActions.APPLY_SCENE, apply);
        assertTrue(applied.ok());
        assertEquals(1, network.scenes().active());
        assertEquals(1, applied.state().getAsJsonObject("hub").get("active_scene").getAsInt());

        clock.addAndGet(1_100_000_000L);
        assertTrue(actions.execute(PAINTER, ChromaLinkActions.TOGGLE_ANIMATION, body(hub())).ok());
        assertEquals(SceneSlots.STATIC, network.scenes().active(), "toggle stops the scene");
        clock.addAndGet(1_100_000_000L);
        assertTrue(actions.execute(PAINTER, ChromaLinkActions.TOGGLE_ANIMATION, body(hub())).ok());
        assertEquals(1, network.scenes().active(), "toggle resumes the last scene");

        clock.addAndGet(1_100_000_000L);
        JsonObject colour = body(hub());
        colour.addProperty("zone", 0);
        colour.addProperty("colour", "#FF8800");
        assertTrue(actions.execute(PAINTER, ChromaLinkActions.SET_ZONE_COLOUR, colour).ok());
        assertEquals(0xFF8800, ChromaLinkSections.zoneColour(network, 0).rgb());
        assertEquals(12, ChromaLinkSections.zoneColour(network, 0).brightness(), "zone colour keeps brightness");

        clock.addAndGet(1_100_000_000L);
        JsonObject bright = body(hub());
        bright.addProperty("value", 5);
        assertTrue(actions.execute(PAINTER, ChromaLinkActions.SET_BRIGHTNESS, bright).ok());
        assertEquals(5, ChromaLinkSections.brightness(network).getAsInt());
        assertTrue(source.charged > 0, "the shared Chroma limiter was charged");
    }

    @Test
    void protectedBlocksRefuseTheWholeZone() {
        FakeSource source = world();
        source.guard = packed -> false;
        ChromaLinkActions actions = actions(source, new AtomicLong(1_000_000_000_000L));
        JsonObject colour = body(hub());
        colour.addProperty("zone", 0);
        colour.addProperty("colour", "#00FF00");
        LinkActionResult result = actions.execute(OWNER, ChromaLinkActions.SET_ZONE_COLOUR, colour);
        assertFalse(result.ok());
        assertEquals("protected", result.message());
        assertEquals(1, source.store.get(HUB).palette().size(), "no palette entry was added");
    }

    @Test
    void badParamsAndOfflineAreRefused() {
        FakeSource source = world();
        AtomicLong clock = new AtomicLong(1_000_000_000_000L);
        ChromaLinkActions actions = actions(source, clock);
        JsonObject slot = body(hub());
        slot.addProperty("slot", 9);
        assertEquals("bad_slot", actions.execute(OWNER, ChromaLinkActions.APPLY_SCENE, slot).message());
        clock.addAndGet(2_000_000_000L);
        JsonObject value = body(hub());
        value.addProperty("value", 16);
        assertFalse(actions.execute(OWNER, ChromaLinkActions.SET_BRIGHTNESS, value).ok());
        clock.addAndGet(2_000_000_000L);
        source.online.remove(OWNER);
        JsonObject apply = body(hub());
        apply.addProperty("slot", 0);
        LinkActionResult offline = actions.execute(OWNER, ChromaLinkActions.APPLY_SCENE, apply);
        assertEquals(LinkActionResult.Error.PLAYER_OFFLINE_REQUIRED, offline.error());
    }

    // --- events -----------------------------------------------------------------------------------------------------

    @Test
    void eventsReachMembersOnly() {
        FakeSource source = world();
        List<LinkEvent> published = new ArrayList<>();
        AtomicLong clock = new AtomicLong(5_000_000_000L);
        ChromaLinkEvents events = new ChromaLinkEvents(published::add, clock::get);
        HubNetwork network = source.store.get(HUB);
        events.sceneChanged(network, 0, false);
        events.hubBroken(network);
        events.triggerFired(network, SceneSlots.Trigger.REDSTONE, 1);
        events.triggerFired(network, SceneSlots.Trigger.REDSTONE, 2); // within a second: dropped
        assertEquals(9, published.size(), "3 events x 3 members");
        Set<UUID> to = new HashSet<>();
        for (LinkEvent event : published) {
            assertNotNull(event.playerId(), "never a broadcast");
            to.add(event.playerId());
            assertEquals(ChromaLinkEvents.TOPIC, event.topic());
            String payload = event.payload().toString();
            assertFalse(payload.contains(Integer.toString(ANCHOR_X)), "no coordinates in events");
            assertFalse(payload.contains(OWNER.toString()), "no UUIDs in events");
        }
        assertEquals(Set.of(OWNER, PAINTER, VIEWER), to);
        assertFalse(to.contains(STRANGER));
    }

    @Test
    void hubIdsRoundTrip() {
        assertEquals(Optional.of(HUB), ChromaLinkSections.parseHubId(ChromaLinkSections.hubId(HUB)));
        assertEquals(Optional.of(-1L), ChromaLinkSections.parseHubId("ffffffffffffffff"));
        assertTrue(ChromaLinkSections.parseHubId("0").isEmpty());
        assertTrue(ChromaLinkSections.parseHubId("xyz").isEmpty());
        assertTrue(ChromaLinkSections.parseHubId("1ffffffffffffffff").isEmpty());
    }
}
