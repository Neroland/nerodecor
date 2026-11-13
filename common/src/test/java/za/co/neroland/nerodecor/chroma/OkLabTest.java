package za.co.neroland.nerodecor.chroma;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class OkLabTest {

    private static void assertLab(ChromaColor c, double l, double a, double b) {
        double[] lab = OkLab.fromColor(c);
        assertEquals(l, lab[0], 1e-3, "L of " + c);
        assertEquals(a, lab[1], 1e-3, "a of " + c);
        assertEquals(b, lab[2], 1e-3, "b of " + c);
    }

    @Test
    void goldenValues() {
        assertLab(ChromaColor.WHITE, 1.0, 0.0, 0.0);
        assertLab(ChromaColor.BLACK, 0.0, 0.0, 0.0);
        assertLab(ChromaColor.of(255, 0, 0), 0.627955, 0.224863, 0.125846);
        assertLab(ChromaColor.of(0, 255, 0), 0.866440, -0.233888, 0.179498);
        assertLab(ChromaColor.of(0, 0, 255), 0.452014, -0.032457, -0.311528);
    }

    @Test
    void transferFunctionsInvert() {
        for (int i = 0; i <= 255; i++) {
            double c = i / 255.0;
            assertEquals(c, OkLab.linearToSrgb(OkLab.srgbToLinear(c)), 1e-12);
        }
    }

    @Test
    void roundTripsEveryPrimaryAndGrey() {
        for (int v = 0; v <= 255; v += 17) {
            ChromaColor[] cs = {ChromaColor.of(v, v, v), ChromaColor.of(v, 0, 255 - v), ChromaColor.of(255, v, 0)};
            for (ChromaColor c : cs) {
                double[] lab = OkLab.fromColor(c);
                assertEquals(c, OkLab.toColor(lab[0], lab[1], lab[2], 15));
            }
        }
    }

    @Test
    void twoPointGradient() {
        ChromaColor a = ChromaColor.of(0, 0, 0, 0);
        ChromaColor b = ChromaColor.of(255, 255, 255, 15);
        assertEquals(a, OkLab.mix(a, b, 0));
        assertEquals(b, OkLab.mix(a, b, 1));
        assertEquals(a, OkLab.mix(a, b, -3));
        ChromaColor mid = OkLab.mix(a, b, 0.5);
        // OKLab L = 0.5 -> linear 0.125 -> sRGB 0.3896 -> 99.
        assertEquals(99, mid.red(), 1);
        assertEquals(mid.red(), mid.green());
        assertEquals(mid.red(), mid.blue());
        assertEquals(8, mid.brightness());
    }

    @Test
    void multiStopGradient() {
        ChromaColor r = ChromaColor.of(255, 0, 0);
        ChromaColor g = ChromaColor.of(0, 255, 0);
        ChromaColor b = ChromaColor.of(0, 0, 255);
        ChromaGradient grad = ChromaGradient.even(List.of(r, g, b));
        assertEquals(r, grad.sample(0));
        assertEquals(g, grad.sample(0.5));
        assertEquals(b, grad.sample(1));
        assertEquals(OkLab.mix(r, g, 0.5), grad.sample(0.25));
        assertEquals(OkLab.mix(g, b, 0.5), grad.sample(0.75));
        ChromaGradient custom = ChromaGradient.of(new double[] {0.0, 0.2, 1.0}, List.of(r, g, b));
        assertEquals(g, custom.sample(0.2));
        assertThrows(IllegalArgumentException.class, () -> ChromaGradient.even(List.of(r)));
        assertThrows(IllegalArgumentException.class,
                () -> ChromaGradient.of(new double[] {0.5, 0.2}, List.of(r, g)));
    }
}
