package za.co.neroland.nerodecor.chroma;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ChromaEffectsTest {

    private static final EffectParams PARAMS = EffectParams.builder()
            .primary(ChromaColor.of(10, 20, 120, 12))
            .secondary(ChromaColor.of(240, 230, 255, 15))
            .periodTicks(37).wavelength(6).density(40).daylight(9).build();

    @Test
    void everyEffectIsDeterministicAndInRange() {
        for (EffectType type : EffectType.values()) {
            for (long t = -50; t < 200; t += 7) {
                for (int x = -9; x <= 9; x += 3) {
                    ChromaColor a = ChromaEffects.colourAt(type, t, x, 2, -x, 1234L, PARAMS);
                    ChromaColor b = ChromaEffects.colourAt(type, t, x, 2, -x, 1234L, PARAMS);
                    assertNotNull(a);
                    assertEquals(a, b, type.name());
                    assertTrue(ChromaColor.isValidPacked(a.packed()));
                    assertTrue(a.brightness() >= 0 && a.brightness() <= 15);
                }
            }
            assertEquals(type, EffectType.byId(type.id()));
        }
    }

    @Test
    void periodicEffectsRepeatEveryPeriod() {
        for (EffectType type : EffectType.values()) {
            if (type == EffectType.SPARKLE) {
                continue; // sparkle re-rolls every cycle by design
            }
            for (long t = 0; t < 40; t++) {
                assertEquals(ChromaEffects.colourAt(type, t, 3, 1, 4, 9L, PARAMS),
                        ChromaEffects.colourAt(type, t + PARAMS.periodTicks(), 3, 1, 4, 9L, PARAMS), type.name());
            }
        }
    }

    @Test
    void sparkleDependsOnSeed() {
        int differences = 0;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                ChromaColor a = ChromaEffects.colourAt(EffectType.SPARKLE, 100, x, 0, z, 1L, PARAMS);
                ChromaColor b = ChromaEffects.colourAt(EffectType.SPARKLE, 100, x, 0, z, 2L, PARAMS);
                if (!a.equals(b)) {
                    differences++;
                }
            }
        }
        assertTrue(differences > 10, "seeds should give different sparkle patterns");
    }

    @Test
    void sparkleDensityRoughlyHolds() {
        EffectParams p = PARAMS.toBuilder().density(64).build();
        int lit = 0;
        int total = 0;
        for (int x = 0; x < 64; x++) {
            for (int z = 0; z < 64; z++) {
                total++;
                if (ChromaEffects.colourAt(EffectType.SPARKLE, 5, x, 0, z, 77L, p).equals(p.secondary())) {
                    lit++;
                }
            }
        }
        assertEquals(64 / 255.0, lit / (double) total, 0.03);
    }

    @Test
    void amplitudeZeroIsStatic() {
        EffectParams still = PARAMS.toBuilder().amplitude(0).build();
        for (EffectType type : new EffectType[] {EffectType.PULSE, EffectType.BREATHE, EffectType.CHASE,
            EffectType.AXIS_WAVE, EffectType.RADIAL_RIPPLE}) {
            for (long t = 0; t < 40; t += 5) {
                assertEquals(still.primary(), ChromaEffects.colourAt(type, t, t > 20 ? 3 : 1, 0, 0, 0L, still));
            }
        }
    }

    @Test
    void breatheHitsBothEnds() {
        EffectParams p = PARAMS.toBuilder().periodTicks(40).build();
        assertEquals(p.primary(), ChromaEffects.colourAt(EffectType.BREATHE, 0, 0, 0, 0, 0L, p));
        assertEquals(p.secondary(), ChromaEffects.colourAt(EffectType.BREATHE, 20, 0, 0, 0, 0L, p));
    }

    @Test
    void skyMirrorFollowsDaylight() {
        EffectParams night = PARAMS.toBuilder().daylight(0).build();
        EffectParams day = PARAMS.toBuilder().daylight(15).build();
        assertEquals(night.primary(), ChromaEffects.colourAt(EffectType.SKY_MIRROR, 3, 0, 0, 0, 0L, night));
        assertEquals(day.sky(), ChromaEffects.colourAt(EffectType.SKY_MIRROR, 3, 0, 0, 0, 0L, day));
    }

    @Test
    void rainbowVariesAlongAxis() {
        assertNotEquals(ChromaEffects.colourAt(EffectType.RAINBOW, 0, 0, 0, 0, 0L, PARAMS),
                ChromaEffects.colourAt(EffectType.RAINBOW, 0, 3, 0, 0, 0L, PARAMS));
    }

    @Test
    void paramsAreBoundsChecked() {
        assertThrows(IllegalArgumentException.class, () -> EffectParams.builder().periodTicks(0).build());
        assertThrows(IllegalArgumentException.class, () -> EffectParams.builder().amplitude(256).build());
        assertThrows(IllegalArgumentException.class, () -> EffectParams.builder().axis(3).build());
        assertThrows(IllegalArgumentException.class, () -> EffectParams.builder().wavelength(0).build());
        assertThrows(IllegalArgumentException.class, () -> EffectParams.builder().daylight(16).build());
        assertThrows(IllegalArgumentException.class, () -> EffectParams.builder().primary(null).build());
    }

    @Test
    void reducedMotionIsCalmer() {
        EffectParams calm = ReducedMotion.calmer(PARAMS);
        assertTrue(calm.periodTicks() > PARAMS.periodTicks());
        assertTrue(calm.periodTicks() >= ReducedMotion.MIN_PERIOD_TICKS);
        assertTrue(calm.amplitude() < PARAMS.amplitude());
        assertTrue(calm.density() < PARAMS.density());
        assertEquals(PARAMS.primary(), calm.primary());
        EffectParams huge = PARAMS.toBuilder().periodTicks(EffectParams.MAX_PERIOD).build();
        assertEquals(EffectParams.MAX_PERIOD, ReducedMotion.calmer(huge).periodTicks());
        ZoneProgram strobe = new EffectProgram(EffectType.PULSE, PARAMS.toBuilder().periodTicks(2).build());
        assertTrue(FlashClamp.check(ReducedMotion.calmer(strobe), 0L).passed());
    }
}
