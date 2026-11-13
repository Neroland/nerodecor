package za.co.neroland.nerodecor.chroma;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-side light updates for Chroma blocks whose emitted light follows their committed static brightness: at
 * most one light change per block per {@link #MIN_INTERVAL_NANOS}. A newer request for a waiting block replaces
 * the older one, so only the final level is ever applied. Animations never come through here. Positions are opaque
 * packed longs.
 */
public final class ChromaLightSchedule {

    public static final long MIN_INTERVAL_NANOS = 1_000_000_000L;

    public record Update(long pos, int level) {
    }

    private final Map<Long, Integer> pending = new LinkedHashMap<>();
    private final Map<Long, Long> lastApplied = new HashMap<>();

    public void request(long pos, int level) {
        pending.put(pos, Math.max(0, Math.min(15, level)));
    }

    public int pendingCount() {
        return pending.size();
    }

    /** Updates whose block has not changed light in the last second; they are marked applied now. */
    public List<Update> drain(long nowNanos, int max) {
        List<Update> out = new ArrayList<>();
        Iterator<Map.Entry<Long, Integer>> it = pending.entrySet().iterator();
        while (it.hasNext() && out.size() < max) {
            Map.Entry<Long, Integer> e = it.next();
            Long last = lastApplied.get(e.getKey());
            if (last == null || nowNanos - last >= MIN_INTERVAL_NANOS) {
                it.remove();
                lastApplied.put(e.getKey(), nowNanos);
                out.add(new Update(e.getKey(), e.getValue()));
            }
        }
        if (lastApplied.size() > 8192) {
            lastApplied.values().removeIf(t -> nowNanos - t >= MIN_INTERVAL_NANOS);
        }
        return out;
    }
}
