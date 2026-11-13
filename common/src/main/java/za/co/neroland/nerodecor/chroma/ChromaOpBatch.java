package za.co.neroland.nerodecor.chroma;

import java.util.List;

/** An immutable, validated batch of at most {@link #MAX_OPS} ops. */
public final class ChromaOpBatch {

    public static final int MAX_OPS = 512;

    private final List<ChromaOp> ops;

    private ChromaOpBatch(List<ChromaOp> ops) {
        this.ops = ops;
    }

    /** Validates size and every op; never throws. */
    public static ChromaResult<ChromaOpBatch> of(List<ChromaOp> ops) {
        if (ops == null) {
            return ChromaResult.fail(ChromaError.BAD_OP, "no ops");
        }
        if (ops.size() > MAX_OPS) {
            return ChromaResult.fail(ChromaError.TOO_MANY_OPS, ops.size() + " > " + MAX_OPS);
        }
        for (int i = 0; i < ops.size(); i++) {
            ChromaOp op = ops.get(i);
            if (op == null || !op.isValid()) {
                return ChromaResult.fail(ChromaError.BAD_OP, "op " + i);
            }
        }
        return ChromaResult.ok(new ChromaOpBatch(List.copyOf(ops)));
    }

    public List<ChromaOp> ops() {
        return ops;
    }

    public int size() {
        return ops.size();
    }

    /**
     * Checks palette references against a palette of {@code paletteSize} entries, allowing entries that
     * earlier ops in this batch append. Returns the first bad op index, or -1.
     */
    public int firstUnresolvedIndex(int paletteSize) {
        int size = paletteSize;
        for (int i = 0; i < ops.size(); i++) {
            ChromaOp op = ops.get(i);
            if (op.kind() == ChromaOp.Kind.SET_PALETTE_ENTRY) {
                if (op.target() > size) {
                    return i;
                }
                if (op.target() == size) {
                    size++;
                }
            } else if (op.kind() == ChromaOp.Kind.SET_INDEX && op.value() >= size) {
                return i;
            }
        }
        return -1;
    }
}
