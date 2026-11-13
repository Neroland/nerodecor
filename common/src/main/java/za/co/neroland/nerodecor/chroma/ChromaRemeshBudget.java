package za.co.neroland.nerodecor.chroma;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Client re-mesh budget for Chroma colour changes: a section is re-meshed at most once per
 * {@link #MIN_INTERVAL_NANOS}, and one poll releases at most {@link #MAX_SECTIONS_PER_CHANGE} sections. Sections
 * that are not due stay queued (nothing is dropped); marking an already-queued section again is free, so a burst
 * of changes to one section costs one re-mesh. Section keys are opaque longs (the caller packs them).
 */
public final class ChromaRemeshBudget {

    public static final long MIN_INTERVAL_NANOS = 500_000_000L;
    public static final int MAX_SECTIONS_PER_CHANGE = 64;

    private final LinkedHashSet<Long> pending = new LinkedHashSet<>();
    private final Map<Long, Long> lastRemesh = new HashMap<>();

    public void mark(long section) {
        pending.add(section);
    }

    public int pendingCount() {
        return pending.size();
    }

    /** Sections to re-mesh now (at most {@link #MAX_SECTIONS_PER_CHANGE}). */
    public List<Long> poll(long nowNanos) {
        List<Long> out = new ArrayList<>();
        Iterator<Long> it = pending.iterator();
        while (it.hasNext() && out.size() < MAX_SECTIONS_PER_CHANGE) {
            Long section = it.next();
            Long last = lastRemesh.get(section);
            if (last == null || nowNanos - last >= MIN_INTERVAL_NANOS) {
                it.remove();
                lastRemesh.put(section, nowNanos);
                out.add(section);
            }
        }
        if (lastRemesh.size() > 4096) {
            lastRemesh.values().removeIf(t -> nowNanos - t >= MIN_INTERVAL_NANOS);
        }
        return out;
    }

    public void clear() {
        pending.clear();
        lastRemesh.clear();
    }
}
