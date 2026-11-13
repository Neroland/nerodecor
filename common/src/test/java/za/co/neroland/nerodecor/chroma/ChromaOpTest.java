package za.co.neroland.nerodecor.chroma;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChromaOpTest {

    @Test
    void relativePosRoundTrip() {
        int[] vals = {RelativePos.MIN, -1, 0, 1, 37, RelativePos.MAX};
        for (int x : vals) {
            for (int y : vals) {
                for (int z : vals) {
                    int p = RelativePos.pack(x, y, z);
                    assertTrue(RelativePos.isValid(p));
                    assertEquals(x, RelativePos.x(p));
                    assertEquals(y, RelativePos.y(p));
                    assertEquals(z, RelativePos.z(p));
                }
            }
        }
        assertThrows(IllegalArgumentException.class, () -> RelativePos.pack(512, 0, 0));
        assertFalse(RelativePos.isValid(1 << 30));
    }

    @Test
    void opValidation() {
        int pos = RelativePos.pack(1, 2, 3);
        assertTrue(ChromaOp.setIndex(pos, 255).isValid());
        assertFalse(ChromaOp.setIndex(pos, 256).isValid());
        assertFalse(ChromaOp.setIndex(-1, 0).isValid());
        assertTrue(ChromaOp.setPaletteEntry(255, ChromaColor.WHITE).isValid());
        assertFalse(ChromaOp.setPaletteEntry(256, ChromaColor.WHITE).isValid());
        assertFalse(new ChromaOp(ChromaOp.Kind.SET_PALETTE_ENTRY, 0, 0xF0000000).isValid());
        assertTrue(ChromaOp.assignZone(pos, 31).isValid());
        assertFalse(ChromaOp.assignZone(pos, 32).isValid());
        assertFalse(new ChromaOp(null, 0, 0).isValid());
        for (ChromaOp.Kind k : ChromaOp.Kind.values()) {
            assertEquals(k, ChromaOp.Kind.byId(k.id()));
        }
    }

    @Test
    void batchCapsAndValidates() {
        List<ChromaOp> ops = new ArrayList<>();
        for (int i = 0; i < ChromaOpBatch.MAX_OPS; i++) {
            ops.add(ChromaOp.assignZone(RelativePos.pack(i % 100, 0, 0), i % 32));
        }
        assertTrue(ChromaOpBatch.of(ops).isOk());
        ops.add(ChromaOp.assignZone(0, 0));
        assertEquals(ChromaError.TOO_MANY_OPS, ChromaOpBatch.of(ops).error());
        assertEquals(ChromaError.BAD_OP, ChromaOpBatch.of(List.of(ChromaOp.setIndex(0, 999))).error());
        assertEquals(ChromaError.BAD_OP, ChromaOpBatch.of(null).error());
    }

    @Test
    void paletteReferencesResolve() {
        ChromaOpBatch batch = ChromaOpBatch.of(List.of(
                ChromaOp.setPaletteEntry(2, ChromaColor.WHITE),
                ChromaOp.setIndex(0, 2),
                ChromaOp.setIndex(0, 3))).value();
        assertEquals(2, batch.firstUnresolvedIndex(2));
        assertEquals(-1, batch.firstUnresolvedIndex(4));
        assertEquals(0, batch.firstUnresolvedIndex(1));
    }
}
