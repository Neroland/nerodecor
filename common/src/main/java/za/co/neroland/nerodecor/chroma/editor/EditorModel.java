package za.co.neroland.nerodecor.chroma.editor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import za.co.neroland.nerodecor.chroma.ChromaColor;
import za.co.neroland.nerodecor.chroma.ChromaOp;
import za.co.neroland.nerodecor.chroma.ChromaOpBatch;
import za.co.neroland.nerodecor.chroma.ChromaPalette;
import za.co.neroland.nerodecor.chroma.ChromaResult;
import za.co.neroland.nerodecor.chroma.RelativePos;

/**
 * The editor's working copy of one hub: its linked blocks (fixed set, from the last {@link HubDetail}), their palette
 * index and zone, and the palette. Tools change the working copy; {@link #buildBatches()} turns the difference from the
 * server's state into op batches (palette entries first, then block indices and zones), which the server re-validates.
 * Every tool stroke starts with {@link #begin()}, giving a {@value #UNDO_DEPTH}-step undo.
 *
 * <p>Palette entries are reused before new ones are appended: an exact colour match, then an entry no block uses any
 * more (never index 0, which newly linked blocks take). Client thread only.
 */
public final class EditorModel {

    public static final int UNDO_DEPTH = 32;

    private record State(int[] values, int[] palette, int paletteSize) {
    }

    private final long network;
    private final int[] offsets;
    private final Map<Integer, Integer> slotOf = new HashMap<>();
    private final int[] baseValues;
    private final int[] basePalette;
    private final int baseSize;
    private int[] values;
    private int[] palette = new int[ChromaPalette.MAX_ENTRIES];
    private int paletteSize;
    private final UndoStack<State> history = new UndoStack<>(UNDO_DEPTH);

    public EditorModel(HubDetail detail) {
        this(detail.network(), detail.offsets(), detail.values(), detail.palette());
    }

    public EditorModel(long network, int[] offsets, int[] values, int[] packedPalette) {
        if (offsets.length != values.length || packedPalette.length > ChromaPalette.MAX_ENTRIES) {
            throw new IllegalArgumentException("bad layout");
        }
        this.network = network;
        this.offsets = offsets.clone();
        for (int i = 0; i < offsets.length; i++) {
            slotOf.put(offsets[i], i);
        }
        this.baseValues = values.clone();
        this.values = values.clone();
        this.basePalette = Arrays.copyOf(packedPalette, ChromaPalette.MAX_ENTRIES);
        this.baseSize = packedPalette.length;
        System.arraycopy(packedPalette, 0, palette, 0, packedPalette.length);
        this.paletteSize = packedPalette.length;
    }

    public long network() {
        return network;
    }

    private int[] fallback;

    /**
     * What each block shows when it has no palette entry (index beyond the palette), as {@code 0xRRGGBB}, or -1 for
     * nothing known; asked once per block now. Display only: never part of an edit.
     */
    public void setFallbackColours(java.util.function.IntUnaryOperator byOffset) {
        int[] out = new int[offsets.length];
        for (int i = 0; i < offsets.length; i++) {
            out[i] = byOffset.applyAsInt(offsets[i]);
        }
        fallback = out;
    }

    /** The display fallback of a linked block ({@link #setFallbackColours}), or -1. */
    public int fallbackRgb(int rel) {
        Integer slot = slotOf.get(rel);
        return fallback == null || slot == null ? -1 : fallback[slot];
    }

    // --- reading -------------------------------------------------------------------------------------------------

    public int blockCount() {
        return offsets.length;
    }

    /** Packed relative position of block {@code i}. */
    public int offset(int i) {
        return offsets[i];
    }

    public boolean contains(int rel) {
        return slotOf.containsKey(rel);
    }

    public boolean contains(int x, int y, int z) {
        return RelativePos.inRange(x, y, z) && slotOf.containsKey(RelativePos.pack(x, y, z));
    }

    /** Palette index of a linked block, or -1. */
    public int indexAt(int rel) {
        Integer slot = slotOf.get(rel);
        return slot == null ? -1 : values[slot] & 0xFF;
    }

    /** Zone of a linked block, or -1. */
    public int zoneAt(int rel) {
        Integer slot = slotOf.get(rel);
        return slot == null ? -1 : values[slot] >>> 8;
    }

    /** Working colour of a linked block, or {@code null} (unlinked, or an index beyond the palette). */
    public ChromaColor colourAt(int rel) {
        int index = indexAt(rel);
        return index >= 0 && index < paletteSize ? ChromaColor.fromPacked(palette[index]) : null;
    }

    public int paletteSize() {
        return paletteSize;
    }

    public ChromaColor paletteEntry(int index) {
        return index >= 0 && index < paletteSize ? ChromaColor.fromPacked(palette[index]) : null;
    }

    /** Linked blocks in {@code zone}. */
    public List<Integer> zoneBlocks(int zone) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < offsets.length; i++) {
            if (values[i] >>> 8 == zone) {
                out.add(offsets[i]);
            }
        }
        return out;
    }

    /** How many blocks use each palette index. */
    public int[] paletteUse() {
        int[] use = new int[ChromaPalette.MAX_ENTRIES];
        for (int v : values) {
            use[v & 0xFF]++;
        }
        return use;
    }

    /** The colour most blocks of {@code zone} show, or {@code null} for an empty zone. */
    public ChromaColor dominantColour(int zone) {
        int[] count = new int[ChromaPalette.MAX_ENTRIES];
        int best = -1;
        for (int v : values) {
            if (v >>> 8 == zone) {
                int idx = v & 0xFF;
                count[idx]++;
                if (best < 0 || count[idx] > count[best]) {
                    best = idx;
                }
            }
        }
        return best < 0 ? null : paletteEntry(best);
    }

    // --- history -------------------------------------------------------------------------------------------------

    private State snapshot() {
        return new State(values.clone(), Arrays.copyOf(palette, paletteSize), paletteSize);
    }

    private void restore(State s) {
        values = s.values().clone();
        palette = Arrays.copyOf(s.palette(), ChromaPalette.MAX_ENTRIES);
        paletteSize = s.paletteSize();
    }

    /** Marks the start of one undoable edit. */
    public void begin() {
        history.push(snapshot());
    }

    public boolean canUndo() {
        return history.canUndo();
    }

    public boolean canRedo() {
        return history.canRedo();
    }

    public boolean undo() {
        State s = history.undo(snapshot());
        if (s == null) {
            return false;
        }
        restore(s);
        return true;
    }

    public boolean redo() {
        State s = history.redo(snapshot());
        if (s == null) {
            return false;
        }
        restore(s);
        return true;
    }

    /** Drops every unsent change. */
    public void revert() {
        begin();
        values = baseValues.clone();
        palette = Arrays.copyOf(basePalette, ChromaPalette.MAX_ENTRIES);
        paletteSize = baseSize;
    }

    // --- editing -------------------------------------------------------------------------------------------------

    /**
     * A palette index showing {@code colour}: an equal entry, else an entry no block uses (index 1 and up), else a new
     * entry.
     *
     * @return the index, or -1 when the palette is full and every entry is in use
     */
    public int allocate(ChromaColor colour) {
        int packed = colour.packed();
        for (int i = 0; i < paletteSize; i++) {
            if (palette[i] == packed) {
                return i;
            }
        }
        int[] use = paletteUse();
        for (int i = 1; i < paletteSize; i++) {
            if (use[i] == 0) {
                palette[i] = packed;
                return i;
            }
        }
        if (paletteSize < ChromaPalette.MAX_ENTRIES) {
            palette[paletteSize] = packed;
            return paletteSize++;
        }
        return -1;
    }

    /** Sets palette entry {@code index} (existing, or the next new one). */
    public boolean setPaletteEntry(int index, ChromaColor colour) {
        if (index < 0 || index > paletteSize || index >= ChromaPalette.MAX_ENTRIES) {
            return false;
        }
        palette[index] = colour.packed();
        if (index == paletteSize) {
            paletteSize++;
        }
        return true;
    }

    /** Points one block at a palette index. */
    public boolean setIndex(int rel, int index) {
        Integer slot = slotOf.get(rel);
        if (slot == null || index < 0 || index >= paletteSize) {
            return false;
        }
        values[slot] = (values[slot] & ~0xFF) | index;
        return true;
    }

    public boolean setZone(int rel, int zone) {
        Integer slot = slotOf.get(rel);
        if (slot == null || zone < 0 || zone >= 32) {
            return false;
        }
        values[slot] = (values[slot] & 0xFF) | (zone << 8);
        return true;
    }

    /**
     * Paints every linked block in {@code rels} with {@code colour}.
     *
     * @return how many blocks changed colour, or -1 when the palette is full
     */
    public int paint(Collection<Integer> rels, ChromaColor colour) {
        int index = allocate(colour);
        if (index < 0) {
            return -1;
        }
        int changed = 0;
        for (int rel : rels) {
            if (indexAt(rel) != index && setIndex(rel, index)) {
                changed++;
            }
        }
        return changed;
    }

    /**
     * Paints each block its own colour (gradients). Colours are allocated in order, so a full palette leaves the
     * remaining blocks untouched.
     *
     * @return how many blocks changed, or -1 when the palette filled up before every colour was placed
     */
    public int paintEach(Map<Integer, ChromaColor> colours) {
        int changed = 0;
        Map<Integer, Integer> cache = new HashMap<>();
        for (Map.Entry<Integer, ChromaColor> e : colours.entrySet()) {
            int packed = e.getValue().packed();
            Integer index = cache.get(packed);
            if (index == null) {
                index = allocate(e.getValue());
                if (index < 0) {
                    return -1;
                }
                cache.put(packed, index);
            }
            if (indexAt(e.getKey()) != index && setIndex(e.getKey(), index)) {
                changed++;
            }
        }
        return changed;
    }

    /**
     * Gives a whole zone one colour. When the zone's blocks share an entry nothing else uses (and it is not index 0),
     * that entry is changed in place, one op; otherwise the blocks are repainted.
     *
     * @return blocks recoloured, 0 for an empty zone, -1 when the palette is full
     */
    public int recolourZone(int zone, ChromaColor colour) {
        List<Integer> blocks = zoneBlocks(zone);
        if (blocks.isEmpty()) {
            return 0;
        }
        int shared = indexAt(blocks.get(0));
        boolean single = shared > 0;
        for (int rel : blocks) {
            if (indexAt(rel) != shared) {
                single = false;
                break;
            }
        }
        if (single && paletteUse()[shared] == blocks.size()) {
            palette[shared] = colour.packed();
            return blocks.size();
        }
        return paint(blocks, colour) < 0 ? -1 : blocks.size();
    }

    // --- diff ----------------------------------------------------------------------------------------------------

    /** The ops that turn the server's state into the working copy: palette entries, then indices, then zones. */
    public List<ChromaOp> buildOps() {
        List<ChromaOp> ops = new ArrayList<>();
        for (int i = 0; i < paletteSize; i++) {
            if (i >= baseSize || palette[i] != basePalette[i]) {
                ops.add(new ChromaOp(ChromaOp.Kind.SET_PALETTE_ENTRY, i, palette[i]));
            }
        }
        for (int i = 0; i < offsets.length; i++) {
            if ((values[i] & 0xFF) != (baseValues[i] & 0xFF)) {
                ops.add(ChromaOp.setIndex(offsets[i], values[i] & 0xFF));
            }
        }
        for (int i = 0; i < offsets.length; i++) {
            if ((values[i] >>> 8) != (baseValues[i] >>> 8)) {
                ops.add(ChromaOp.assignZone(offsets[i], values[i] >>> 8));
            }
        }
        return ops;
    }

    public int pendingOps() {
        return buildOps().size();
    }

    public boolean isDirty() {
        return pendingOps() > 0;
    }

    /**
     * {@link #buildOps()} cut into valid batches of at most {@link ChromaOpBatch#MAX_OPS}, in order (one batch for any
     * edit of up to that many ops). Palette ops come first, so later batches only reference entries earlier ones added.
     */
    public List<ChromaOpBatch> buildBatches() {
        List<ChromaOp> ops = buildOps();
        List<ChromaOpBatch> out = new ArrayList<>();
        for (int from = 0; from < ops.size(); from += ChromaOpBatch.MAX_OPS) {
            ChromaResult<ChromaOpBatch> batch = ChromaOpBatch.of(
                    ops.subList(from, Math.min(ops.size(), from + ChromaOpBatch.MAX_OPS)));
            if (!batch.isOk()) {
                throw new IllegalStateException("editor built an invalid op: " + batch.error());
            }
            out.add(batch.value());
        }
        return out;
    }
}
