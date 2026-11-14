package za.co.neroland.nerodecor.chroma.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerodecor.chroma.ChromaColor;
import za.co.neroland.nerodecor.chroma.ChromaResult;
import za.co.neroland.nerodecor.chroma.Easing;
import za.co.neroland.nerodecor.chroma.EffectProgram;
import za.co.neroland.nerodecor.chroma.EffectType;
import za.co.neroland.nerodecor.chroma.FlashClamp;
import za.co.neroland.nerodecor.chroma.Scene;
import za.co.neroland.nerodecor.chroma.SceneCodec;
import za.co.neroland.nerodecor.chroma.Timeline;

class SceneDraftTest {

    private static final ChromaColor RED = ChromaColor.of(255, 0, 0);
    private static final ChromaColor GREEN = ChromaColor.of(0, 255, 0);
    private static final ChromaColor BLUE = ChromaColor.of(0, 0, 255);

    private static SceneDraft.ZoneDraft timeline(SceneDraft draft) {
        SceneDraft.ZoneDraft z = draft.zone(0);
        z.setKind(SceneDraft.Kind.TIMELINE);
        return z;
    }

    @Test
    void addKeyGoesHalfwayOrAppends() {
        SceneDraft.ZoneDraft z = timeline(new SceneDraft(List.of(0)));
        assertEquals(2, z.keyCount()); // ticks 0 and 40, length 80
        assertEquals(1, z.addKeyAfter(0, RED));
        assertEquals(20, z.key(1).tick());
        int last = z.addKeyAfter(z.keyCount() - 1, BLUE);
        assertEquals(3, last);
        assertEquals(60, z.key(3).tick());
        assertEquals(80, z.length());
        z.addKeyAfter(3, GREEN); // 80, still inside the length
        int grown = z.addKeyAfter(4, GREEN); // 100: the length grows to fit
        assertEquals(100, z.key(grown).tick());
        assertEquals(100, z.length());
    }

    @Test
    void addKeyStopsAtTheCapAndWhenNoTickIsFree() {
        SceneDraft.ZoneDraft z = timeline(new SceneDraft(List.of(0)));
        while (z.keyCount() < Timeline.MAX_KEYS) {
            assertTrue(z.addKeyAfter(z.keyCount() - 1, RED) >= 0);
        }
        assertEquals(-1, z.addKeyAfter(0, RED));
        SceneDraft.ZoneDraft tight = timeline(new SceneDraft(List.of(0)));
        tight.setTick(1, 1);
        assertEquals(-1, tight.addKeyAfter(0, RED));
    }

    @Test
    void removeKeepsAtLeastOneKey() {
        SceneDraft.ZoneDraft z = timeline(new SceneDraft(List.of(0)));
        assertTrue(z.removeKey(0));
        assertFalse(z.removeKey(0));
        assertEquals(1, z.keyCount());
    }

    @Test
    void moveSwapsColoursAndKeepsTicksSorted() {
        SceneDraft.ZoneDraft z = timeline(new SceneDraft(List.of(0)));
        z.setKeyColour(0, RED);
        z.setKeyColour(1, BLUE);
        z.cycleEasing(0, 1);
        Easing first = z.key(0).easing();
        assertEquals(1, z.moveKey(0, 1));
        assertEquals(0, z.key(0).tick());
        assertEquals(40, z.key(1).tick());
        assertEquals(BLUE, z.key(0).colour());
        assertEquals(RED, z.key(1).colour());
        assertEquals(first, z.key(1).easing());
        assertEquals(-1, z.moveKey(1, 1));
        assertEquals(-1, z.moveKey(0, -1));
    }

    @Test
    void ticksAndLengthAreClamped() {
        SceneDraft.ZoneDraft z = timeline(new SceneDraft(List.of(0)));
        assertEquals(1, z.setTick(1, -5)); // just after key 0
        assertEquals(80, z.setTick(1, 500)); // never past the length
        assertEquals(80, z.setLength(10)); // never below the last key
        assertEquals(Timeline.MAX_TICKS, z.setLength(Integer.MAX_VALUE));
        assertNull(z.validate());
    }

    @Test
    void easingPreviewSamplesTheTimeline() {
        SceneDraft.ZoneDraft z = timeline(new SceneDraft(List.of(0)));
        z.setKeyColour(0, RED);
        z.setKeyColour(1, BLUE);
        z.setLoop(false);
        z.setLength(40);
        ChromaColor[] strip = z.timeStrip(4, 1L); // ticks 0, 10, 20, 30
        assertEquals(RED, strip[0]);
        // STEP holds the previous key until the next key's tick.
        while (z.key(1).easing() != Easing.STEP) {
            z.cycleEasing(1, 1);
        }
        ChromaColor[] stepped = z.timeStrip(4, 1L);
        for (ChromaColor c : stepped) {
            assertEquals(RED, c);
        }
        // EASE_IN lags LINEAR at the midpoint: less blue at tick 20.
        while (z.key(1).easing() != Easing.EASE_IN) {
            z.cycleEasing(1, 1);
        }
        int easeInBlue = z.timeStrip(4, 1L)[2].blue();
        while (z.key(1).easing() != Easing.LINEAR) {
            z.cycleEasing(1, 1);
        }
        int linearBlue = z.timeStrip(4, 1L)[2].blue();
        assertTrue(easeInBlue < linearBlue, easeInBlue + " !< " + linearBlue);
    }

    @Test
    void effectPreviewMatchesTheProgram() {
        SceneDraft draft = new SceneDraft(List.of(0));
        SceneDraft.ZoneDraft z = draft.zone(0);
        z.cycleType(1); // BREATHE -> CHASE
        assertEquals(EffectType.CHASE, z.type());
        z.setColours(RED, BLUE);
        EffectProgram program = (EffectProgram) z.program();
        ChromaColor[] space = z.spaceStrip(8, 17L, 5L);
        for (int x = 0; x < 8; x++) {
            assertEquals(program.colourAt(17L, x, 0, 0, 5L), space[x]);
        }
    }

    @Test
    void buildsASceneWithPerZonePrograms() {
        SceneDraft draft = new SceneDraft(List.of(0, 3, 15));
        draft.zone(1).setKind(SceneDraft.Kind.TIMELINE);
        draft.zone(2).cycleType(-1); // BREATHE -> PULSE
        Scene scene = draft.build("Fallback");
        assertEquals("Fallback", scene.name());
        assertEquals(3, scene.zones().size());
        assertInstanceOf(EffectProgram.class, scene.zone(0).program());
        assertInstanceOf(Timeline.class, scene.zone(1).program());
        assertEquals(3, scene.zone(1).channel());
        assertEquals(EffectType.PULSE, ((EffectProgram) scene.zone(2).program()).type());
        draft.setName("Night shift");
        Scene named = draft.build("Fallback");
        assertEquals("Night shift", named.name());
        ChromaResult<byte[]> bytes = SceneCodec.encode(named);
        assertTrue(bytes.isOk());
        Scene back = SceneCodec.decode(bytes.value()).value();
        assertEquals(named, back);
        // Round trip into the editor and out again is lossless.
        assertEquals(named, SceneDraft.fromScene(back, List.of(0, 3, 15)).build("x"));
        assertTrue(FlashClamp.check(named, 1L).passed());
    }

    @Test
    void copyToAllKeepsChannels() {
        SceneDraft draft = new SceneDraft(List.of(2, 5));
        SceneDraft.ZoneDraft z = timeline(draft);
        z.setKeyColour(0, GREEN);
        draft.copyToAll(0);
        assertEquals(SceneDraft.Kind.TIMELINE, draft.zone(1).kind());
        assertEquals(GREEN, draft.zone(1).key(0).colour());
        assertEquals(5, draft.zone(1).channel());
        assertNotNull(draft.build("a").zone(1));
    }

    @Test
    void fastStrobeFailsTheClamp() {
        SceneDraft draft = new SceneDraft(List.of(0));
        SceneDraft.ZoneDraft z = draft.zone(0);
        z.cycleType(-1); // PULSE
        z.setColours(ChromaColor.WHITE, ChromaColor.BLACK);
        z.setPeriod(2);
        assertFalse(FlashClamp.check(draft.build("strobe"), 1L).passed());
    }
}
