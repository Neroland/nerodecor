package za.co.neroland.nerodecor.chroma;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ChromaPaletteTest {

    @Test
    void dedupesAndCaps() {
        ChromaPalette p = new ChromaPalette();
        assertEquals(0, p.addOrFind(ChromaColor.WHITE));
        assertEquals(0, p.addOrFind(ChromaColor.of(255, 255, 255)));
        assertEquals(1, p.addOrFind(ChromaColor.WHITE.withBrightness(3)));
        for (int i = 2; i < ChromaPalette.MAX_ENTRIES; i++) {
            assertEquals(i, p.addOrFind(ChromaColor.of(i, 0, 0, 1)));
        }
        assertTrue(p.isFull());
        assertEquals(-1, p.addOrFind(ChromaColor.of(1, 2, 3)));
        assertEquals(0, p.addOrFind(ChromaColor.WHITE));
        assertEquals(ChromaPalette.MAX_ENTRIES, p.size());
        assertFalse(p.set(ChromaPalette.MAX_ENTRIES, ChromaColor.BLACK));
    }

    @Test
    void boundsChecked() {
        ChromaPalette p = new ChromaPalette();
        assertThrows(IndexOutOfBoundsException.class, () -> p.get(0));
        assertFalse(p.set(1, ChromaColor.BLACK));
        assertTrue(p.set(0, ChromaColor.BLACK));
        assertTrue(p.set(0, ChromaColor.WHITE));
        assertEquals(ChromaColor.WHITE, p.get(0));
        assertThrows(IndexOutOfBoundsException.class, () -> p.get(-1));
        assertFalse(p.set(-1, ChromaColor.BLACK));
    }

    @Test
    void packedRoundTrip() {
        ChromaPalette p = new ChromaPalette();
        p.addOrFind(ChromaColor.of(1, 2, 3, 4));
        p.addOrFind(ChromaColor.of(9, 8, 7, 6));
        ChromaPalette q = ChromaPalette.fromPacked(p.toPacked());
        assertEquals(2, q.size());
        assertEquals(p.get(1), q.get(1));
        assertNull(ChromaPalette.fromPacked(new int[257]));
        assertNull(ChromaPalette.fromPacked(new int[] {0xF0000000}));
    }
}
