package za.co.neroland.nerodecor.chroma;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

class ChromaSyncCodecTest {

    private static final int[] PALETTE = {ChromaColor.of(255, 0, 0).packed(), ChromaColor.of(0, 255, 0, 7).packed(),
            ChromaColor.of(0, 0, 255, 3).packed(), ChromaColor.WHITE.packed()};

    /** A typical build: 4,096 linked blocks as walls and a floor of a 32x16x32 room, striped in four colours. */
    private static Map<Integer, Integer> room() {
        Map<Integer, Integer> out = new HashMap<>();
        for (int x = -16; x < 16 && out.size() < 4096; x++) {
            for (int z = -16; z < 16 && out.size() < 4096; z++) {
                out.put(RelativePos.pack(x, -1, z), (x + 16) / 8);
            }
        }
        for (int y = 0; y < 32 && out.size() < 4096; y++) {
            for (int i = -16; i < 16 && out.size() < 4096; i++) {
                out.put(RelativePos.pack(i, y, -16), y % 4);
                out.put(RelativePos.pack(i, y, 15), y % 4);
                out.put(RelativePos.pack(-16, y, i), 3);
                out.put(RelativePos.pack(15, y, i), 3);
            }
        }
        java.util.Iterator<Integer> it = out.keySet().iterator();
        while (out.size() > 4096) {
            it.next();
            it.remove();
        }
        return out;
    }

    private static int[][] arrays(Map<Integer, Integer> layout) {
        int[] o = new int[layout.size()];
        int[] i = new int[layout.size()];
        int n = 0;
        for (Map.Entry<Integer, Integer> e : layout.entrySet()) {
            o[n] = e.getKey();
            i[n++] = e.getValue();
        }
        return new int[][] {o, i};
    }

    private static Map<Integer, Integer> asMap(ChromaSnapshot s) {
        Map<Integer, Integer> out = new HashMap<>();
        for (int k = 0; k < s.size(); k++) {
            out.put(s.offsets()[k], s.indices()[k]);
        }
        return out;
    }

    @Test
    void typicalFourThousandBlocksFitOneSnapshot() {
        Map<Integer, Integer> layout = room();
        assertEquals(4096, layout.size());
        int[][] a = arrays(layout);
        ChromaResult<byte[]> bytes = ChromaSyncCodec.encodeSnapshot(
                new ChromaSnapshot(42L, true, 100, 64, -200, PALETTE, a[0], a[1]));
        assertTrue(bytes.isOk(), bytes.toString());
        assertTrue(bytes.value().length <= ChromaSyncCodec.MAX_BYTES, "size " + bytes.value().length);
        ChromaSnapshot back = ChromaSyncCodec.decodeSnapshot(bytes.value()).value();
        assertEquals(42L, back.network());
        assertTrue(back.replace());
        assertEquals(-200, back.anchorZ());
        assertArrayEquals(PALETTE, back.palette());
        assertEquals(layout, asMap(back));
    }

    @Test
    void scatteredLayoutIsRejectedWholeButSplitsIntoParts() {
        Random random = new Random(7);
        Map<Integer, Integer> layout = new HashMap<>();
        while (layout.size() < 4096) {
            layout.put(RelativePos.pack(random.nextInt(48) - 24, random.nextInt(32) - 16, random.nextInt(48) - 24),
                    random.nextInt(4));
        }
        int[][] a = arrays(layout);
        ChromaResult<byte[]> whole = ChromaSyncCodec.encodeSnapshot(
                new ChromaSnapshot(1L, true, 0, 0, 0, PALETTE, a[0], a[1]));
        assertFalse(whole.isOk());
        assertEquals(ChromaError.OVERSIZED, whole.error());

        List<byte[]> parts = ChromaSyncCodec.encodeSnapshotParts(1L, 0, 0, 0, PALETTE, a[0], a[1]).value();
        assertTrue(parts.size() > 1);
        Map<Integer, Integer> merged = new HashMap<>();
        for (int p = 0; p < parts.size(); p++) {
            assertTrue(parts.get(p).length <= ChromaSyncCodec.MAX_BYTES);
            ChromaSnapshot s = ChromaSyncCodec.decodeSnapshot(parts.get(p)).value();
            assertEquals(p == 0, s.replace(), "only the first part replaces");
            merged.putAll(asMap(s));
        }
        assertEquals(layout, merged);
    }

    @Test
    void oversizedAndCorruptInputsAreRejected() {
        assertFalse(ChromaSyncCodec.decodeSnapshot(new byte[ChromaSyncCodec.MAX_BYTES + 1]).isOk());
        int[][] a = arrays(room());
        byte[] good = ChromaSyncCodec.encodeSnapshot(new ChromaSnapshot(9L, false, 0, 0, 0, PALETTE, a[0], a[1]))
                .value();
        for (int cut = 0; cut < good.length; cut += 97) {
            assertFalse(ChromaSyncCodec.decodeSnapshot(java.util.Arrays.copyOf(good, cut)).isOk(), "cut " + cut);
        }
        byte[] trailing = java.util.Arrays.copyOf(good, good.length + 1);
        assertEquals(ChromaError.TRAILING_DATA, ChromaSyncCodec.decodeSnapshot(trailing).error());
        byte[] magic = good.clone();
        magic[0] = 'X';
        assertEquals(ChromaError.BAD_MAGIC, ChromaSyncCodec.decodeSnapshot(magic).error());
    }

    @Test
    void emptySnapshotRoundTrips() {
        byte[] bytes = ChromaSyncCodec.encodeSnapshot(new ChromaSnapshot(5L, true, 1, 2, 3, new int[0], new int[0],
                new int[0])).value();
        ChromaSnapshot back = ChromaSyncCodec.decodeSnapshot(bytes).value();
        assertEquals(0, back.size());
        assertEquals(2, back.anchorY());
    }

    @Test
    void deltaRoundTripsAndRejectsOversize() {
        ChromaDelta delta = new ChromaDelta(3L, false, new int[] {0, 5}, new int[] {PALETTE[0], PALETTE[2]},
                new int[] {RelativePos.pack(1, 2, 3), RelativePos.pack(-4, 0, 9)}, new int[] {5, ChromaDelta.UNLINKED});
        ChromaDelta back = ChromaSyncCodec.decodeDelta(ChromaSyncCodec.encodeDelta(delta).value()).value();
        assertArrayEquals(delta.paletteIndices(), back.paletteIndices());
        assertArrayEquals(delta.paletteColors(), back.paletteColors());
        assertArrayEquals(delta.offsets(), back.offsets());
        assertArrayEquals(delta.indices(), back.indices());

        assertTrue(ChromaSyncCodec.decodeDelta(ChromaSyncCodec.encodeDelta(ChromaDelta.clear(3L)).value()).value()
                .clear());

        int n = 4096;
        int[] offsets = new int[n];
        int[] indices = new int[n];
        for (int k = 0; k < n; k++) {
            offsets[k] = RelativePos.pack(k % 64 - 32, k / 64 % 64 - 32, -500);
            indices[k] = 200;
        }
        ChromaResult<byte[]> big = ChromaSyncCodec.encodeDelta(new ChromaDelta(3L, false, new int[0], new int[0],
                offsets, indices));
        assertEquals(ChromaError.OVERSIZED, big.error());
    }

    @Test
    void opsRoundTripAndValidate() {
        ChromaOpBatch batch = ChromaOpBatch.of(List.of(ChromaOp.setPaletteEntry(0, ChromaColor.of(1, 2, 3, 4)),
                ChromaOp.setIndex(RelativePos.pack(1, 1, 1), 0), ChromaOp.assignZone(RelativePos.pack(0, 0, 0), 2)))
                .value();
        ChromaSyncCodec.OpsMessage back = ChromaSyncCodec.decodeOps(ChromaSyncCodec.encodeOps(77L, batch)).value();
        assertEquals(77L, back.network());
        assertEquals(batch.ops(), back.batch().ops());

        byte[] bad = ChromaSyncCodec.encodeOps(77L, batch);
        bad[bad.length - 1] = 99; // zone 99 is out of range
        assertFalse(ChromaSyncCodec.decodeOps(bad).isOk());
    }
}
