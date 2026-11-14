package za.co.neroland.nerodecor.chroma;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Stage 4: deterministic sampling, scene slots + triggers, the sync codec and the chip codec. */
class SceneAnimationTest {

    private static final ChromaColor RED = ChromaColor.of(220, 30, 30);
    private static final ChromaColor BLUE = ChromaColor.of(20, 40, 230, 8);

    static Scene scene(String name) {
        EffectProgram wave = new EffectProgram(EffectType.AXIS_WAVE, EffectParams.builder().primary(RED)
                .secondary(BLUE).periodTicks(60).wavelength(8).build());
        EffectProgram sparkle = new EffectProgram(EffectType.SPARKLE, EffectParams.builder().primary(BLUE)
                .secondary(ChromaColor.WHITE).periodTicks(40).density(40).build());
        Timeline breathe = new Timeline(List.of(new Keyframe(0, RED, Easing.LINEAR),
                new Keyframe(40, BLUE, Easing.LINEAR)), 80, true);
        return new Scene(name, List.of(new ZoneAssignment(0, 0, wave), new ZoneAssignment(1, 1, sparkle),
                new ZoneAssignment(3, 2, breathe)));
    }

    private static int[] sample(SceneAnimator animator, long tick, int[] rel, int[] zone) {
        int[] out = new int[rel.length];
        animator.fill(tick, 42L, rel, zone, rel.length, 1.0, out);
        return out;
    }

    // --- deterministic sampling -------------------------------------------------------------------------------------

    @Test
    void sameTickGivesSameColours() {
        int[] rel = new int[200];
        int[] zone = new int[200];
        for (int i = 0; i < rel.length; i++) {
            rel[i] = RelativePos.pack(i % 10 - 5, i / 10 % 5, i / 50);
            zone[i] = i % 4;
        }
        SceneAnimator a = new SceneAnimator(scene("A"), false);
        SceneAnimator b = new SceneAnimator(scene("A"), false);
        for (long t : new long[] {0L, 7L, 123L, 99_999L, -5L}) {
            assertArrayEquals(sample(a, t, rel, zone), sample(b, t, rel, zone), "tick " + t);
        }
        assertFalse(java.util.Arrays.equals(sample(a, 0L, rel, zone), sample(a, 15L, rel, zone)));
    }

    @Test
    void unassignedZonesStayStaticAndSharedZonesMatchPerBlock() {
        SceneAnimator a = new SceneAnimator(scene("A"), false);
        int[] rel = {RelativePos.pack(0, 0, 0), RelativePos.pack(3, 1, 2), RelativePos.pack(-4, 0, 9)};
        int[] out = sample(a, 10L, rel, new int[] {2, 3, 3});
        assertEquals(-1, out[0]);
        assertEquals(out[1], out[2], "a timeline is position independent");
        assertFalse(a.animates(2));
        assertTrue(a.animates(3));
        ChromaColor single = a.colourAt(3, 10L, 0, 0, 0, 42L);
        assertEquals(SceneAnimator.toRgb(single, 1.0), out[1]);
    }

    @Test
    void reducedMotionIsCalmerButStillDeterministic() {
        int[] rel = {RelativePos.pack(1, 0, 0)};
        int[] zone = {0};
        SceneAnimator calm = new SceneAnimator(scene("A"), true);
        assertArrayEquals(sample(calm, 33L, rel, zone), sample(new SceneAnimator(scene("A"), true), 33L, rel, zone));
        assertTrue(calm.reducedMotion());
    }

    @Test
    void brightnessAndGainFoldIntoRgb() {
        assertEquals(0xFFFFFF, SceneAnimator.toRgb(ChromaColor.WHITE, 1.0));
        int dim = SceneAnimator.toRgb(ChromaColor.WHITE.withBrightness(0), 1.0);
        assertEquals(51, dim >> 16 & 0xFF);
        assertEquals(0xFFFFFF, SceneAnimator.toRgb(ChromaColor.WHITE, 1.25), "clamped");
        assertEquals(RED.rgb(), SceneAnimator.dominantRgb(new Scene("x", List.of(
                new ZoneAssignment(0, 0, new Timeline(List.of(new Keyframe(0, RED, Easing.LINEAR)), 1, true)),
                new ZoneAssignment(1, 0, new Timeline(List.of(new Keyframe(0, RED, Easing.LINEAR)), 1, true)),
                new ZoneAssignment(2, 0, new Timeline(List.of(new Keyframe(0, BLUE, Easing.LINEAR)), 1, true))))));
    }

    // --- slots and triggers -----------------------------------------------------------------------------------------

    @Test
    void defaultRedstoneMapping() {
        assertEquals(SceneSlots.STATIC, SceneSlots.defaultRedstoneSlot(0, 8));
        assertEquals(0, SceneSlots.defaultRedstoneSlot(1, 8), "strength 1 reaches slot 0");
        assertEquals(1, SceneSlots.defaultRedstoneSlot(2, 8));
        assertEquals(6, SceneSlots.defaultRedstoneSlot(7, 8));
        assertEquals(7, SceneSlots.defaultRedstoneSlot(8, 8));
        assertEquals(7, SceneSlots.defaultRedstoneSlot(15, 8));
        assertEquals(2, SceneSlots.defaultRedstoneSlot(9, 3));
    }

    @Test
    void redstoneTriggerPicksFilledSlots() {
        SceneSlots slots = new SceneSlots();
        slots.set(1, scene("one"), true);
        slots.set(7, scene("seven"), true);
        slots.setTrigger(SceneSlots.Trigger.REDSTONE);
        assertEquals(SceneSlots.STATIC, slots.desiredSlot(0, 0L));
        assertEquals(1, slots.desiredSlot(2, 0L));
        assertEquals(SceneSlots.STATIC, slots.desiredSlot(1, 0L), "empty slot 0 resolves to static");
        assertEquals(SceneSlots.STATIC, slots.desiredSlot(3, 0L), "empty slot resolves to static");
        assertEquals(7, slots.desiredSlot(15, 0L));
        int[] map = new int[16];
        java.util.Arrays.fill(map, 1);
        map[0] = SceneSlots.STATIC;
        slots.setRedstoneMap(map);
        assertEquals(1, slots.desiredSlot(15, 0L));
    }

    @Test
    void daylightBandsAndTrigger() {
        assertEquals(SceneSlots.DaylightBand.DAWN, SceneSlots.DaylightBand.of(23500L));
        assertEquals(SceneSlots.DaylightBand.DAWN, SceneSlots.DaylightBand.of(500L));
        assertEquals(SceneSlots.DaylightBand.DAY, SceneSlots.DaylightBand.of(6000L));
        assertEquals(SceneSlots.DaylightBand.DUSK, SceneSlots.DaylightBand.of(12500L));
        assertEquals(SceneSlots.DaylightBand.NIGHT, SceneSlots.DaylightBand.of(18000L + 24000L * 5));
        SceneSlots slots = new SceneSlots();
        for (int i = 0; i < 4; i++) {
            slots.set(i, scene("s" + i), true);
        }
        slots.setTrigger(SceneSlots.Trigger.DAYLIGHT);
        assertEquals(1, slots.desiredSlot(15, 6000L));
        assertEquals(3, slots.desiredSlot(0, 18000L));
        assertEquals(SceneSlots.Trigger.DAYLIGHT, SceneSlots.Trigger.byId("daylight"));
    }

    @Test
    void activationAndRevision() {
        SceneSlots slots = new SceneSlots();
        int r0 = slots.revision();
        slots.set(2, scene("two"), true);
        assertTrue(slots.activate(2, 100L));
        assertEquals(2, slots.active());
        assertEquals(100L, slots.startTick());
        assertFalse(slots.activate(2, 200L), "same slot does not restart");
        assertTrue(slots.revision() > r0);
        slots.set(2, null, false);
        assertEquals(SceneSlots.STATIC, slots.active(), "clearing the playing slot stops it");
        assertFalse(slots.activate(5, 1L), "empty slot stays static");
    }

    @Test
    void blankNamesKeepsZonesAndPersistenceRoundTrips() {
        SceneSlots slots = new SceneSlots();
        slots.set(0, scene("My secret base"), true);
        slots.set(4, scene("Party"), true);
        slots.activate(4, 77L);
        slots.setTrigger(SceneSlots.Trigger.REDSTONE);
        SceneSlots restored = SceneSlots.restore(slots.slotCodes(), slots.active(), slots.startTick(),
                slots.trigger().id(), slots.redstoneMap(), slots.daylightMap());
        assertEquals(slots.scene(0), restored.scene(0));
        assertEquals(4, restored.active());
        assertEquals(77L, restored.startTick());
        assertFalse(restored.clampPassed(4), "restored scenes are re-checked before they play");
        slots.blankNames();
        assertEquals("Scene 1", slots.scene(0).name());
        assertEquals("Scene 5", slots.scene(4).name());
        assertEquals(scene("x").zones(), slots.scene(0).zones());
        assertTrue(new SceneSlots().isDefault());
    }

    // --- sync codec -------------------------------------------------------------------------------------------------

    @Test
    void sceneStateRoundTrip() {
        SceneSyncCodec.SceneState s = new SceneSyncCodec.SceneState(-99L, 10, -64, 300, 0, 3, 123_456L, 5L,
                scene("Night"));
        ChromaResult<byte[]> bytes = SceneSyncCodec.encodeScene(s);
        assertTrue(bytes.isOk());
        assertTrue(bytes.value().length < 1024, "a scene change is one small packet");
        SceneSyncCodec.SceneState back = SceneSyncCodec.decodeScene(bytes.value()).value();
        assertEquals(s, back);
        SceneSyncCodec.SceneState stat = new SceneSyncCodec.SceneState(1L, 0, 0, 0,
                SceneSyncCodec.FLAG_ANIMATIONS_OFF, 5, 0L, 0L, null);
        SceneSyncCodec.SceneState statBack = SceneSyncCodec.decodeScene(SceneSyncCodec.encodeScene(stat).value())
                .value();
        assertNull(statBack.scene());
        assertEquals(SceneSlots.STATIC, statBack.activeSlot());
        assertTrue(statBack.animationsOff());
        byte[] cut = java.util.Arrays.copyOf(bytes.value(), bytes.value().length - 3);
        assertFalse(SceneSyncCodec.decodeScene(cut).isOk());
    }

    @Test
    void zoneMapRoundTripAcrossParts() {
        int n = 4096;
        int[] rel = new int[n];
        int[] zone = new int[n];
        int k = 0;
        for (int x = -16; x < 16 && k < n; x++) {
            for (int y = 0; y < 8 && k < n; y++) {
                for (int z = -8; z < 8 && k < n; z++) {
                    rel[k] = RelativePos.pack(x, y, z);
                    zone[k] = (x + 16) % 5;
                    k++;
                }
            }
        }
        List<byte[]> parts = SceneSyncCodec.encodeZoneMap(9L, rel, zone);
        assertEquals(4, parts.size());
        java.util.Map<Integer, Integer> got = new java.util.HashMap<>();
        for (int i = 0; i < parts.size(); i++) {
            assertTrue(parts.get(i).length <= SceneSyncCodec.MAX_BYTES);
            SceneSyncCodec.ZonePart p = SceneSyncCodec.decodeZonePart(parts.get(i)).value();
            assertEquals(i == 0, p.first());
            assertEquals(i == parts.size() - 1, p.last());
            for (int j = 0; j < p.offsets().length; j++) {
                got.put(p.offsets()[j], p.zones()[j]);
            }
        }
        assertEquals(n, got.size());
        for (int i = 0; i < n; i++) {
            assertEquals(zone[i], got.get(rel[i]));
        }
        SceneSyncCodec.ZonePart empty = SceneSyncCodec.decodeZonePart(
                SceneSyncCodec.encodeZoneMap(1L, new int[0], new int[0]).get(0)).value();
        assertTrue(empty.first() && empty.last() && empty.offsets().length == 0);
    }

    // --- chip codec -------------------------------------------------------------------------------------------------

    @Test
    void chipCodecRoundTripAndTamper() {
        Scene s = scene("Chip");
        byte[] bytes = SceneChipCodec.encode(s).value();
        assertTrue(bytes.length <= SceneChipCodec.MAX_BYTES);
        assertEquals(s, SceneChipCodec.decode(bytes).value());
        String code = SceneChipCodec.toShareCode(bytes).value();
        assertArrayEquals(bytes, SceneChipCodec.fromShareCode(code).value());
        for (int i = 0; i < bytes.length; i++) {
            byte[] bad = bytes.clone();
            bad[i] ^= 0x10;
            ChromaResult<Scene> r = SceneChipCodec.decode(bad);
            assertFalse(r.isOk() && r.value().equals(s), "tampered byte " + i + " accepted");
        }
        assertFalse(SceneChipCodec.decode(new byte[0]).isOk());
        assertFalse(SceneChipCodec.decode(new byte[SceneChipCodec.MAX_BYTES + 1]).isOk());
        assertNotEquals(ChromaError.OVERSIZED, SceneChipCodec.decode(new byte[3]).error());
    }
}
