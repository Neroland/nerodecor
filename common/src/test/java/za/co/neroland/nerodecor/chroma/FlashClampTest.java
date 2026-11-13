package za.co.neroland.nerodecor.chroma;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class FlashClampTest {

    private static final ChromaColor DARK = ChromaColor.of(10, 10, 60);

    private static EffectProgram effect(EffectType type, EffectParams.Builder b) {
        return new EffectProgram(type, b.build());
    }

    @Test
    void saneRainbowAndChasePass() {
        assertTrue(FlashClamp.check(effect(EffectType.RAINBOW, EffectParams.builder().periodTicks(100)
                .wavelength(16)), 1L).passed());
        assertTrue(FlashClamp.check(effect(EffectType.RAINBOW, EffectParams.builder().periodTicks(40)
                .wavelength(255)), 1L).passed());
        assertTrue(FlashClamp.check(effect(EffectType.CHASE, EffectParams.builder().periodTicks(10)
                .wavelength(8).primary(ChromaColor.WHITE).secondary(DARK)), 1L).passed());
        assertTrue(FlashClamp.check(effect(EffectType.AXIS_WAVE, EffectParams.builder().periodTicks(10)
                .wavelength(6)), 1L).passed());
        assertTrue(FlashClamp.check(effect(EffectType.RADIAL_RIPPLE, EffectParams.builder().periodTicks(20)
                .wavelength(4)), 1L).passed());
        assertTrue(FlashClamp.check(effect(EffectType.BREATHE, EffectParams.builder().periodTicks(40)), 1L).passed());
        assertTrue(FlashClamp.check(effect(EffectType.SKY_MIRROR, EffectParams.builder()), 1L).passed());
    }

    @Test
    void tenHertzFullAreaStrobeFails() {
        Timeline strobe = new Timeline(List.of(new Keyframe(0, ChromaColor.WHITE, Easing.LINEAR),
                new Keyframe(1, ChromaColor.BLACK, Easing.STEP)), 2, true);
        FlashClamp.Verdict v = FlashClamp.check(strobe, 0L);
        assertFalse(v.passed());
        assertTrue(v.tick() < FlashClamp.SAMPLE_TPS);
        assertFalse(FlashClamp.check(effect(EffectType.PULSE, EffectParams.builder().periodTicks(2)), 0L).passed());
        assertFalse(FlashClamp.check(effect(EffectType.BREATHE, EffectParams.builder().periodTicks(4)), 0L).passed());
    }

    @Test
    void threeHertzBoundary() {
        // Square wave with a 7-tick period is under 3 Hz; 6 ticks is just over (6.7 changes/s).
        assertTrue(FlashClamp.check(square(8), 0L).passed());
        assertFalse(FlashClamp.check(square(6), 0L).passed());
    }

    private static Timeline square(int period) {
        return new Timeline(List.of(new Keyframe(0, ChromaColor.WHITE, Easing.LINEAR),
                new Keyframe(period / 2, ChromaColor.BLACK, Easing.STEP)), period, true);
    }

    @Test
    void saturationStrobeFails() {
        // Equal-luminance swap would dodge a luminance-only check; saturation catches it.
        Timeline satStrobe = new Timeline(List.of(new Keyframe(0, ChromaColor.of(200, 0, 0), Easing.LINEAR),
                new Keyframe(1, ChromaColor.of(102, 102, 102), Easing.STEP)), 2, true);
        assertFalse(FlashClamp.check(satStrobe, 0L).passed());
    }

    @Test
    void fastSparkleOnSmallFractionPasses() {
        EffectProgram sparkle = effect(EffectType.SPARKLE, EffectParams.builder().periodTicks(1).density(13)
                .primary(DARK).secondary(ChromaColor.WHITE));
        assertTrue(FlashClamp.check(sparkle, 42L).passed());
        EffectProgram dense = effect(EffectType.SPARKLE, EffectParams.builder().periodTicks(1).density(128)
                .primary(DARK).secondary(ChromaColor.WHITE));
        assertFalse(FlashClamp.check(dense, 42L).passed());
    }

    @Test
    void lowAmplitudeFastPulsePasses() {
        // A 10 Hz flicker of a few percent luminance is not a flash.
        assertTrue(FlashClamp.check(effect(EffectType.PULSE, EffectParams.builder().periodTicks(2)
                .primary(ChromaColor.of(128, 128, 128)).secondary(ChromaColor.of(140, 140, 140))), 0L).passed());
    }

    @Test
    void sceneReportsFailingZone() {
        ZoneProgram calm = effect(EffectType.BREATHE, EffectParams.builder().periodTicks(60));
        ZoneProgram strobe = effect(EffectType.PULSE, EffectParams.builder().periodTicks(2));
        Scene ok = new Scene("ok", List.of(new ZoneAssignment(0, 0, calm), new ZoneAssignment(5, 1, calm)));
        assertTrue(FlashClamp.check(ok, 0L).passed());
        Scene bad = new Scene("bad", List.of(new ZoneAssignment(0, 0, calm), new ZoneAssignment(9, 1, strobe)));
        FlashClamp.Verdict v = FlashClamp.check(bad, 0L);
        assertFalse(v.passed());
        assertEquals(9, v.zone());
    }
}
