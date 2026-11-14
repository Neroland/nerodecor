package za.co.neroland.nerodecor.chroma.editor;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * A bounded undo/redo history of immutable states. {@link #push} records the state before an edit (dropping the
 * oldest beyond {@link #capacity()}) and clears the redo side.
 */
public final class UndoStack<T> {

    private final int capacity;
    private final Deque<T> undo = new ArrayDeque<>();
    private final Deque<T> redo = new ArrayDeque<>();

    public UndoStack(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
    }

    public int capacity() {
        return capacity;
    }

    /** Records {@code before} as the state to return to; forgets anything redoable. */
    public void push(T before) {
        undo.push(before);
        while (undo.size() > capacity) {
            undo.removeLast();
        }
        redo.clear();
    }

    public boolean canUndo() {
        return !undo.isEmpty();
    }

    public boolean canRedo() {
        return !redo.isEmpty();
    }

    public int undoDepth() {
        return undo.size();
    }

    /** Returns the state to restore, saving {@code current} for redo; {@code null} when there is nothing to undo. */
    public T undo(T current) {
        if (undo.isEmpty()) {
            return null;
        }
        redo.push(current);
        return undo.pop();
    }

    /** Returns the state to restore, saving {@code current} for undo; {@code null} when there is nothing to redo. */
    public T redo(T current) {
        if (redo.isEmpty()) {
            return null;
        }
        undo.push(current);
        while (undo.size() > capacity) {
            undo.removeLast();
        }
        return redo.pop();
    }

    public void clear() {
        undo.clear();
        redo.clear();
    }
}
