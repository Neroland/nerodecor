package za.co.neroland.nerodecor.chroma;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ChromaColorTest {

    @Test
    void packsAndUnpacks() {
        ChromaColor c = ChromaColor.of(0x12, 0x34, 0x56, 9);
        assertEquals(0x09123456, c.packed());
        assertEquals(c, ChromaColor.fromPacked(c.packed()));
        assertEquals(0x12, c.red());
        assertEquals(0x34, c.green());
        assertEquals(0x56, c.blue());
        assertEquals(9, c.brightness());
        assertTrue(ChromaColor.isValidPacked(c.packed()));
        assertFalse(ChromaColor.isValidPacked(0x10000000));
    }

    @Test
    void clampsChannelsAndBrightness() {
        ChromaColor c = ChromaColor.of(-5, 300, 128, 99);
        assertEquals(0, c.red());
        assertEquals(255, c.green());
        assertEquals(15, c.brightness());
        assertEquals(0, ChromaColor.of(0, 0, 0, -1).brightness());
    }

    @Test
    void hexRoundTrip() {
        assertEquals("#FF8000", ChromaColor.of(255, 128, 0).toHex());
        assertEquals(ChromaColor.of(0xAB, 0xCD, 0xEF), ChromaColor.parseHex("#abcdef").orElseThrow());
        assertEquals(ChromaColor.of(1, 2, 3), ChromaColor.parseHex("010203").orElseThrow());
        assertTrue(ChromaColor.parseHex("#12345").isEmpty());
        assertTrue(ChromaColor.parseHex("#12345G").isEmpty());
        assertTrue(ChromaColor.parseHex(null).isEmpty());
        assertTrue(ChromaColor.parseHex("#+12345").isEmpty());
    }

    @Test
    void hsvRoundTrip() {
        ChromaColor[] samples = {ChromaColor.of(255, 0, 0), ChromaColor.of(0, 255, 0), ChromaColor.of(0, 0, 255),
            ChromaColor.of(12, 200, 99), ChromaColor.of(250, 250, 10), ChromaColor.BLACK, ChromaColor.WHITE};
        for (ChromaColor c : samples) {
            double[] hsv = c.toHsv();
            assertEquals(c, ChromaColor.fromHsv(hsv[0], hsv[1], hsv[2], 15));
        }
        double[] red = ChromaColor.of(255, 0, 0).toHsv();
        assertEquals(0.0, red[0], 1e-9);
        assertEquals(1.0, red[1], 1e-9);
        assertEquals(ChromaColor.of(0, 0, 255), ChromaColor.fromHsv(2.0 / 3.0, 1, 1, 15));
    }

    @Test
    void luminanceGolden() {
        assertEquals(1.0, ChromaColor.WHITE.relativeLuminance(), 1e-4);
        assertEquals(0.2126, ChromaColor.of(255, 0, 0).relativeLuminance(), 1e-4);
        assertEquals(0.0, ChromaColor.BLACK.relativeLuminance(), 1e-9);
        assertEquals(0.0, ChromaColor.WHITE.withBrightness(0).emittedLuminance(), 1e-9);
    }
}
