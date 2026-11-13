package za.co.neroland.nerodecor.chroma;

import java.util.ArrayList;
import java.util.List;

/**
 * Byte codecs for the three Chroma packets, pure Java (the network layer carries the bytes as-is):
 *
 * <ul>
 *   <li><b>Snapshot</b> (server to client): palette (packed colours) plus a run-length encoding of the per-block
 *       palette indices over the bounding box of the linked blocks, scanned x-fastest, then z, then y. Run value 0
 *       is "not linked", {@code n} is palette index {@code n - 1}. At most {@value #MAX_BYTES} bytes per packet;
 *       {@link #encodeSnapshotParts} splits a layout that does not fit into several boxes.</li>
 *   <li><b>Delta</b> (server to client): changed palette entries and changed blocks; also capped at
 *       {@value #MAX_BYTES} bytes (the server sends a snapshot instead when a delta would not fit).</li>
 *   <li><b>Ops</b> (client to server): a {@link ChromaOpBatch} for one network.</li>
 * </ul>
 *
 * <p>Decoding never trusts sizes: every count is bounded before allocation and trailing bytes are rejected.
 * Error details are fixed strings or numbers, never player data.
 */
public final class ChromaSyncCodec {

    /** Per-packet byte budget for snapshots and deltas. */
    public static final int MAX_BYTES = 8192;
    /** Largest box edge a snapshot may describe (the hub volume hard cap). */
    public static final int MAX_AXIS = 64;
    /** Wire version (bump on any layout change). */
    public static final int VERSION = 1;

    private static final int MAGIC_SNAPSHOT = 'C' << 8 | 'S';
    private static final int MAGIC_DELTA = 'C' << 8 | 'D';
    private static final int MAGIC_OPS = 'C' << 8 | 'O';
    /** Offset of the flags byte in a snapshot: magic (2) + version (1) + network (8). */
    private static final int SNAPSHOT_FLAGS_AT = 11;
    private static final int MAX_DELTA_ENTRIES = MAX_BYTES;

    /** A decoded op packet: which network, and the validated batch. */
    public record OpsMessage(long network, ChromaOpBatch batch) {
    }

    private ChromaSyncCodec() {
    }

    // --- snapshot ---------------------------------------------------------------------------------------------------

    /** Encodes one snapshot; fails {@link ChromaError#OVERSIZED} above {@value #MAX_BYTES} bytes. */
    public static ChromaResult<byte[]> encodeSnapshot(ChromaSnapshot s) {
        if (s.palette().length > ChromaPalette.MAX_ENTRIES) {
            return ChromaResult.fail(ChromaError.OUT_OF_RANGE, "palette");
        }
        ChromaWire.Out out = new ChromaWire.Out();
        header(out, MAGIC_SNAPSHOT).int64(s.network()).byteValue(s.replace() ? 1 : 0)
                .zigZag(s.anchorX()).zigZag(s.anchorY()).zigZag(s.anchorZ());
        out.varInt(s.palette().length);
        for (int packed : s.palette()) {
            if (!ChromaColor.isValidPacked(packed)) {
                return ChromaResult.fail(ChromaError.OUT_OF_RANGE, "colour");
            }
            out.int32(packed);
        }
        int n = s.size();
        if (n == 0) {
            out.varInt(0);
            return finish(out);
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int i = 0; i < n; i++) {
            int rel = s.offsets()[i];
            int idx = s.indices()[i];
            if (!RelativePos.isValid(rel) || idx < 0 || idx >= ChromaPalette.MAX_ENTRIES) {
                return ChromaResult.fail(ChromaError.OUT_OF_RANGE, "entry " + i);
            }
            minX = Math.min(minX, RelativePos.x(rel));
            minY = Math.min(minY, RelativePos.y(rel));
            minZ = Math.min(minZ, RelativePos.z(rel));
            maxX = Math.max(maxX, RelativePos.x(rel));
            maxY = Math.max(maxY, RelativePos.y(rel));
            maxZ = Math.max(maxZ, RelativePos.z(rel));
        }
        int sx = maxX - minX + 1;
        int sy = maxY - minY + 1;
        int sz = maxZ - minZ + 1;
        if (sx > MAX_AXIS || sy > MAX_AXIS || sz > MAX_AXIS) {
            return ChromaResult.fail(ChromaError.OUT_OF_RANGE, "box");
        }
        int[] cells = new int[sx * sy * sz];
        for (int i = 0; i < n; i++) {
            int rel = s.offsets()[i];
            cells[cell(RelativePos.x(rel) - minX, RelativePos.y(rel) - minY, RelativePos.z(rel) - minZ, sx, sz)] =
                    s.indices()[i] + 1;
        }
        out.varInt(sx).varInt(sy).varInt(sz).zigZag(minX).zigZag(minY).zigZag(minZ);
        // Runs: count first, so the reader can bound its loop.
        List<int[]> runs = new ArrayList<>();
        int value = cells[0];
        int length = 1;
        for (int c = 1; c < cells.length; c++) {
            if (cells[c] == value) {
                length++;
            } else {
                runs.add(new int[] {value, length});
                value = cells[c];
                length = 1;
            }
        }
        runs.add(new int[] {value, length});
        out.varInt(runs.size());
        for (int[] run : runs) {
            out.varInt(run[0]).varInt(run[1]);
            if (out.size() > MAX_BYTES) {
                return ChromaResult.fail(ChromaError.OVERSIZED, "snapshot");
            }
        }
        return finish(out);
    }

    /**
     * Encodes a whole layout as one or more snapshots, each within {@value #MAX_BYTES} bytes: the box is halved
     * along its longest axis until every part fits. Only the first part carries {@code replace}.
     */
    public static ChromaResult<List<byte[]>> encodeSnapshotParts(long network, int anchorX, int anchorY, int anchorZ,
            int[] palette, int[] offsets, int[] indices) {
        List<byte[]> parts = new ArrayList<>();
        ChromaError error = split(network, anchorX, anchorY, anchorZ, palette, offsets, indices, parts, 0);
        if (error != null) {
            return ChromaResult.fail(error, "snapshot parts");
        }
        parts.get(0)[SNAPSHOT_FLAGS_AT] = 1;
        return ChromaResult.ok(parts);
    }

    private static ChromaError split(long network, int ax, int ay, int az, int[] palette, int[] offsets,
            int[] indices, List<byte[]> parts, int depth) {
        ChromaResult<byte[]> one = encodeSnapshot(new ChromaSnapshot(network, false, ax, ay, az, palette, offsets,
                indices));
        if (one.isOk()) {
            parts.add(one.value());
            return null;
        }
        if (one.error() != ChromaError.OVERSIZED || offsets.length < 2 || depth > 24) {
            return one.error();
        }
        // Halve along the axis with the widest spread, at the median coordinate.
        int axis = widestAxis(offsets);
        int[] coords = new int[offsets.length];
        for (int i = 0; i < offsets.length; i++) {
            coords[i] = coord(offsets[i], axis);
        }
        int[] sorted = coords.clone();
        java.util.Arrays.sort(sorted);
        int pivot = sorted[sorted.length / 2];
        if (pivot == sorted[0]) {
            pivot++;
        }
        int lowCount = 0;
        for (int c : coords) {
            if (c < pivot) {
                lowCount++;
            }
        }
        int[] lowO = new int[lowCount];
        int[] lowI = new int[lowCount];
        int[] highO = new int[offsets.length - lowCount];
        int[] highI = new int[offsets.length - lowCount];
        int l = 0;
        int h = 0;
        for (int i = 0; i < offsets.length; i++) {
            if (coords[i] < pivot) {
                lowO[l] = offsets[i];
                lowI[l++] = indices[i];
            } else {
                highO[h] = offsets[i];
                highI[h++] = indices[i];
            }
        }
        if (lowCount == 0 || h == 0) {
            return ChromaError.OVERSIZED;
        }
        ChromaError e = split(network, ax, ay, az, palette, lowO, lowI, parts, depth + 1);
        return e != null ? e : split(network, ax, ay, az, palette, highO, highI, parts, depth + 1);
    }

    private static int widestAxis(int[] offsets) {
        int best = 0;
        int bestSpread = -1;
        for (int axis = 0; axis < 3; axis++) {
            int min = Integer.MAX_VALUE;
            int max = Integer.MIN_VALUE;
            for (int rel : offsets) {
                int c = coord(rel, axis);
                min = Math.min(min, c);
                max = Math.max(max, c);
            }
            if (max - min > bestSpread) {
                bestSpread = max - min;
                best = axis;
            }
        }
        return best;
    }

    private static int coord(int rel, int axis) {
        return axis == 0 ? RelativePos.x(rel) : axis == 1 ? RelativePos.y(rel) : RelativePos.z(rel);
    }

    public static ChromaResult<ChromaSnapshot> decodeSnapshot(byte[] data) {
        if (data == null || data.length > MAX_BYTES) {
            return ChromaResult.fail(ChromaError.OVERSIZED, "snapshot");
        }
        try {
            ChromaWire.In in = new ChromaWire.In(data);
            ChromaError bad = checkHeader(in, MAGIC_SNAPSHOT);
            if (bad != null) {
                return ChromaResult.fail(bad, "snapshot");
            }
            long network = in.int64();
            boolean replace = (in.byteValue() & 1) != 0;
            int ax = in.zigZag();
            int ay = in.zigZag();
            int az = in.zigZag();
            int paletteSize = in.varInt();
            if (paletteSize < 0 || paletteSize > ChromaPalette.MAX_ENTRIES) {
                return ChromaResult.fail(ChromaError.OUT_OF_RANGE, "palette");
            }
            int[] palette = new int[paletteSize];
            for (int i = 0; i < paletteSize; i++) {
                palette[i] = in.int32();
                if (!ChromaColor.isValidPacked(palette[i])) {
                    return ChromaResult.fail(ChromaError.OUT_OF_RANGE, "colour");
                }
            }
            int sx = in.varInt();
            if (sx == 0) {
                return in.atEnd() ? ChromaResult.ok(new ChromaSnapshot(network, replace, ax, ay, az, palette,
                        new int[0], new int[0])) : ChromaResult.fail(ChromaError.TRAILING_DATA, "snapshot");
            }
            int sy = in.varInt();
            int sz = in.varInt();
            if (sx < 1 || sy < 1 || sz < 1 || sx > MAX_AXIS || sy > MAX_AXIS || sz > MAX_AXIS) {
                return ChromaResult.fail(ChromaError.OUT_OF_RANGE, "box");
            }
            int minX = in.zigZag();
            int minY = in.zigZag();
            int minZ = in.zigZag();
            if (!RelativePos.inRange(minX, minY, minZ)
                    || !RelativePos.inRange(minX + sx - 1, minY + sy - 1, minZ + sz - 1)) {
                return ChromaResult.fail(ChromaError.OUT_OF_RANGE, "box");
            }
            int total = sx * sy * sz;
            int runCount = in.varInt();
            if (runCount < 1 || runCount > total) {
                return ChromaResult.fail(ChromaError.OUT_OF_RANGE, "runs");
            }
            List<Integer> offsets = new ArrayList<>();
            List<Integer> indices = new ArrayList<>();
            int c = 0;
            for (int r = 0; r < runCount; r++) {
                int value = in.varInt();
                int length = in.varInt();
                if (length < 1 || length > total - c || value < 0 || value > ChromaPalette.MAX_ENTRIES) {
                    return ChromaResult.fail(ChromaError.OUT_OF_RANGE, "run " + r);
                }
                if (value > 0) {
                    for (int k = 0; k < length; k++) {
                        int cellIndex = c + k;
                        int x = cellIndex % sx;
                        int z = (cellIndex / sx) % sz;
                        int y = cellIndex / (sx * sz);
                        offsets.add(RelativePos.pack(minX + x, minY + y, minZ + z));
                        indices.add(value - 1);
                    }
                }
                c += length;
            }
            if (c != total) {
                return ChromaResult.fail(ChromaError.TRUNCATED, "runs");
            }
            if (!in.atEnd()) {
                return ChromaResult.fail(ChromaError.TRAILING_DATA, "snapshot");
            }
            return ChromaResult.ok(new ChromaSnapshot(network, replace, ax, ay, az, palette, toArray(offsets),
                    toArray(indices)));
        } catch (ChromaWire.Truncated e) {
            return ChromaResult.fail(ChromaError.TRUNCATED, "snapshot");
        }
    }

    private static int cell(int x, int y, int z, int sx, int sz) {
        return (y * sz + z) * sx + x;
    }

    // --- delta ------------------------------------------------------------------------------------------------------

    /** Encodes a delta; fails {@link ChromaError#OVERSIZED} above {@value #MAX_BYTES} bytes. */
    public static ChromaResult<byte[]> encodeDelta(ChromaDelta d) {
        ChromaWire.Out out = new ChromaWire.Out();
        header(out, MAGIC_DELTA).int64(d.network()).byteValue(d.clear() ? 1 : 0);
        out.varInt(d.paletteIndices().length);
        for (int i = 0; i < d.paletteIndices().length; i++) {
            int index = d.paletteIndices()[i];
            int color = d.paletteColors()[i];
            if (index < 0 || index >= ChromaPalette.MAX_ENTRIES || !ChromaColor.isValidPacked(color)) {
                return ChromaResult.fail(ChromaError.OUT_OF_RANGE, "palette " + i);
            }
            out.varInt(index).int32(color);
        }
        out.varInt(d.offsets().length);
        for (int i = 0; i < d.offsets().length; i++) {
            int rel = d.offsets()[i];
            int index = d.indices()[i];
            if (!RelativePos.isValid(rel) || index < ChromaDelta.UNLINKED || index >= ChromaPalette.MAX_ENTRIES) {
                return ChromaResult.fail(ChromaError.OUT_OF_RANGE, "block " + i);
            }
            out.varInt(rel).varInt(index + 1);
            if (out.size() > MAX_BYTES) {
                return ChromaResult.fail(ChromaError.OVERSIZED, "delta");
            }
        }
        return finish(out);
    }

    public static ChromaResult<ChromaDelta> decodeDelta(byte[] data) {
        if (data == null || data.length > MAX_BYTES) {
            return ChromaResult.fail(ChromaError.OVERSIZED, "delta");
        }
        try {
            ChromaWire.In in = new ChromaWire.In(data);
            ChromaError bad = checkHeader(in, MAGIC_DELTA);
            if (bad != null) {
                return ChromaResult.fail(bad, "delta");
            }
            long network = in.int64();
            boolean clear = (in.byteValue() & 1) != 0;
            int pc = in.varInt();
            if (pc < 0 || pc > ChromaPalette.MAX_ENTRIES) {
                return ChromaResult.fail(ChromaError.OUT_OF_RANGE, "palette");
            }
            int[] pIdx = new int[pc];
            int[] pCol = new int[pc];
            for (int i = 0; i < pc; i++) {
                pIdx[i] = in.varInt();
                pCol[i] = in.int32();
                if (pIdx[i] < 0 || pIdx[i] >= ChromaPalette.MAX_ENTRIES || !ChromaColor.isValidPacked(pCol[i])) {
                    return ChromaResult.fail(ChromaError.OUT_OF_RANGE, "palette " + i);
                }
            }
            int bc = in.varInt();
            if (bc < 0 || bc > MAX_DELTA_ENTRIES) {
                return ChromaResult.fail(ChromaError.OUT_OF_RANGE, "blocks");
            }
            int[] offsets = new int[bc];
            int[] indices = new int[bc];
            for (int i = 0; i < bc; i++) {
                offsets[i] = in.varInt();
                indices[i] = in.varInt() - 1;
                if (!RelativePos.isValid(offsets[i]) || indices[i] < ChromaDelta.UNLINKED
                        || indices[i] >= ChromaPalette.MAX_ENTRIES) {
                    return ChromaResult.fail(ChromaError.OUT_OF_RANGE, "block " + i);
                }
            }
            if (!in.atEnd()) {
                return ChromaResult.fail(ChromaError.TRAILING_DATA, "delta");
            }
            return ChromaResult.ok(new ChromaDelta(network, clear, pIdx, pCol, offsets, indices));
        } catch (ChromaWire.Truncated e) {
            return ChromaResult.fail(ChromaError.TRUNCATED, "delta");
        }
    }

    // --- ops --------------------------------------------------------------------------------------------------------

    public static byte[] encodeOps(long network, ChromaOpBatch batch) {
        ChromaWire.Out out = new ChromaWire.Out();
        header(out, MAGIC_OPS).int64(network).varInt(batch.size());
        for (ChromaOp op : batch.ops()) {
            out.byteValue(op.kind().id()).varInt(op.target()).varInt(op.value());
        }
        return out.toByteArray();
    }

    /** Decodes and validates an op packet (size, every op's ranges). */
    public static ChromaResult<OpsMessage> decodeOps(byte[] data) {
        if (data == null || data.length > MAX_BYTES * 2) {
            return ChromaResult.fail(ChromaError.OVERSIZED, "ops");
        }
        try {
            ChromaWire.In in = new ChromaWire.In(data);
            ChromaError bad = checkHeader(in, MAGIC_OPS);
            if (bad != null) {
                return ChromaResult.fail(bad, "ops");
            }
            long network = in.int64();
            int count = in.varInt();
            if (count < 0 || count > ChromaOpBatch.MAX_OPS) {
                return ChromaResult.fail(ChromaError.TOO_MANY_OPS, String.valueOf(count));
            }
            List<ChromaOp> ops = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                ChromaOp.Kind kind = ChromaOp.Kind.byId(in.byteValue());
                if (kind == null) {
                    return ChromaResult.fail(ChromaError.BAD_OP, "op " + i);
                }
                ops.add(new ChromaOp(kind, in.varInt(), in.varInt()));
            }
            if (!in.atEnd()) {
                return ChromaResult.fail(ChromaError.TRAILING_DATA, "ops");
            }
            ChromaResult<ChromaOpBatch> batch = ChromaOpBatch.of(ops);
            return batch.isOk() ? ChromaResult.ok(new OpsMessage(network, batch.value()))
                    : ChromaResult.fail(batch.error(), batch.detail());
        } catch (ChromaWire.Truncated e) {
            return ChromaResult.fail(ChromaError.TRUNCATED, "ops");
        }
    }

    // --- shared -----------------------------------------------------------------------------------------------------

    private static ChromaWire.Out header(ChromaWire.Out out, int magic) {
        return out.byteValue(magic >>> 8).byteValue(magic).byteValue(VERSION);
    }

    private static ChromaError checkHeader(ChromaWire.In in, int magic) {
        if ((in.byteValue() << 8 | in.byteValue()) != magic) {
            return ChromaError.BAD_MAGIC;
        }
        return in.byteValue() == VERSION ? null : ChromaError.UNSUPPORTED_VERSION;
    }

    private static ChromaResult<byte[]> finish(ChromaWire.Out out) {
        if (out.size() > MAX_BYTES) {
            return ChromaResult.fail(ChromaError.OVERSIZED, String.valueOf(out.size()));
        }
        return ChromaResult.ok(out.toByteArray());
    }

    private static int[] toArray(List<Integer> list) {
        int[] out = new int[list.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = list.get(i);
        }
        return out;
    }
}
