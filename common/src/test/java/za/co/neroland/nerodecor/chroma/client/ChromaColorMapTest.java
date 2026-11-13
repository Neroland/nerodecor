package za.co.neroland.nerodecor.chroma.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ChromaColorMapTest {

    @Test
    void setLookupAndSectionBookkeeping() {
        ChromaColorMap map = new ChromaColorMap();
        assertEquals(0, map.argbAt(-5, -64, 17));
        assertTrue(map.set(-5, -64, 17, 0x123456));
        assertFalse(map.set(-5, -64, 17, 0x123456));
        assertEquals(0xFF123456, map.argbAt(-5, -64, 17));
        assertEquals(1, map.sectionCount());
        assertTrue(map.set(-5, -64, 17, -1));
        assertEquals(0, map.sectionCount());
    }

    @Test
    void sectionKeysRoundTripNegativeCoordinates() {
        long key = ChromaColorMap.sectionKey(-33, -64, 1_000_000);
        assertEquals(-3, ChromaColorMap.sectionX(key));
        assertEquals(-4, ChromaColorMap.sectionY(key));
        assertEquals(62_500, ChromaColorMap.sectionZ(key));
    }
}
