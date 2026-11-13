package za.co.neroland.nerodecor.chroma;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TimelineTest {

    private static final ChromaColor RED = ChromaColor.of(255, 0, 0);
    private static final ChromaColor BLUE = ChromaColor.of(0, 0, 255);

    @Test
    void easingEndpointsAreExact() {
        for (Easing e : Easing.values()) {
            assertEquals(0.0, e.apply(0.0), 0.0, e.name());
            assertEquals(1.0, e.apply(1.0), 0.0, e.name());
            assertEquals(e, Easing.byId(e.id()));
        }
        assertEquals(0.25, Easing.EASE_IN.apply(0.5), 1e-12);
        assertEquals(0.75, Easing.EASE_OUT.apply(0.5), 1e-12);
        assertEquals(0.5, Easing.EASE_IN_OUT.apply(0.5), 1e-12);
        assertEquals(0.0, Easing.STEP.apply(0.99), 0.0);
        assertNull(Easing.byId(5));
    }

    @Test
    void oneShotHoldsEnds() {
        Timeline tl = new Timeline(List.of(new Keyframe(10, RED, Easing.LINEAR),
                new Keyframe(30, BLUE, Easing.LINEAR)), 40, false);
        assertEquals(RED, tl.colourAt(-100));
        assertEquals(RED, tl.colourAt(10));
        assertEquals(OkLab.mix(RED, BLUE, 0.5), tl.colourAt(20));
        assertEquals(BLUE, tl.colourAt(30));
        assertEquals(BLUE, tl.colourAt(1_000_000));
    }

    @Test
    void loopWrapsBackToFirstKey() {
        Timeline tl = new Timeline(List.of(new Keyframe(0, RED, Easing.LINEAR),
                new Keyframe(10, BLUE, Easing.EASE_IN)), 20, true);
        assertEquals(RED, tl.colourAt(0));
        assertEquals(BLUE, tl.colourAt(10));
        assertEquals(RED, tl.colourAt(20));
        assertEquals(tl.colourAt(5), tl.colourAt(45));
        assertEquals(tl.colourAt(15), tl.colourAt(-5));
        assertEquals(OkLab.mix(RED, BLUE, 0.25), tl.colourAt(5));
        assertEquals(OkLab.mix(BLUE, RED, 0.5), tl.colourAt(15));
    }

    @Test
    void stepHoldsUntilKey() {
        Timeline tl = new Timeline(List.of(new Keyframe(0, RED, Easing.LINEAR),
                new Keyframe(10, BLUE, Easing.STEP)), 10, false);
        assertEquals(RED, tl.colourAt(9));
        assertEquals(BLUE, tl.colourAt(10));
    }

    @Test
    void boundsEnforced() {
        List<Keyframe> many = new ArrayList<>();
        for (int i = 0; i <= Timeline.MAX_KEYS; i++) {
            many.add(new Keyframe(i, RED, Easing.LINEAR));
        }
        assertNotNull(Timeline.validate(many, 100));
        assertNull(Timeline.validate(many.subList(0, Timeline.MAX_KEYS), 100));
        assertNotNull(Timeline.validate(List.of(), 10));
        assertNotNull(Timeline.validate(List.of(new Keyframe(5, RED, Easing.LINEAR)), 4));
        assertNotNull(Timeline.validate(List.of(new Keyframe(5, RED, Easing.LINEAR),
                new Keyframe(5, BLUE, Easing.LINEAR)), 10));
        assertNotNull(Timeline.validate(List.of(new Keyframe(0, RED, Easing.LINEAR)), 0));
        assertThrows(IllegalArgumentException.class, () -> new Keyframe(Timeline.MAX_TICKS + 1, RED, Easing.LINEAR));
        assertThrows(IllegalArgumentException.class, () -> new Timeline(many, 100, true));
    }
}
