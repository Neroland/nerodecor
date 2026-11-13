package za.co.neroland.nerodecor.chroma;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A shareable lighting scene: a sanitised name and up to {@link ZoneAssignment#MAX_ZONES} zone
 * assignments (one per zone id, kept sorted by zone).
 *
 * <p>Privacy by construction: a scene holds no player id, no author and no world coordinates, so a
 * share code cannot identify a player or locate a base.
 */
public final class Scene {

    /** Maximum name length in characters (code points). */
    public static final int MAX_NAME_LENGTH = 32;

    private final String name;
    private final List<ZoneAssignment> zones;

    /**
     * @param name any text; it is sanitised (see {@link #sanitiseName(String)})
     * @throws IllegalArgumentException on too many zones or a duplicate zone id
     */
    public Scene(String name, List<ZoneAssignment> zones) {
        String problem = validateZones(zones);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
        List<ZoneAssignment> sorted = new ArrayList<>(zones);
        sorted.sort(Comparator.comparingInt(ZoneAssignment::zone));
        this.name = sanitiseName(name);
        this.zones = List.copyOf(sorted);
    }

    /** Reason the zones are invalid, or {@code null}. */
    public static String validateZones(List<ZoneAssignment> zones) {
        if (zones == null || zones.size() > ZoneAssignment.MAX_ZONES) {
            return "at most " + ZoneAssignment.MAX_ZONES + " zones";
        }
        long seen = 0L;
        for (ZoneAssignment z : zones) {
            if (z == null) {
                return "null zone";
            }
            long bit = 1L << z.zone();
            if ((seen & bit) != 0) {
                return "duplicate zone " + z.zone();
            }
            seen |= bit;
        }
        return null;
    }

    /**
     * Keeps letters, digits, spaces and {@code - _ . , ' ! ? ( ) & # +}; collapses whitespace runs,
     * trims, and truncates to {@link #MAX_NAME_LENGTH} code points. {@code null} becomes empty.
     */
    public static String sanitiseName(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        int count = 0;
        boolean pendingSpace = false;
        for (int i = 0; i < raw.length() && count < MAX_NAME_LENGTH; ) {
            int cp = raw.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) {
                pendingSpace = out.length() > 0;
                continue;
            }
            if (!Character.isLetterOrDigit(cp) && "-_.,'!?()&#+".indexOf(cp) < 0) {
                continue;
            }
            if (pendingSpace) {
                if (count + 1 >= MAX_NAME_LENGTH) {
                    break;
                }
                out.append(' ');
                count++;
                pendingSpace = false;
            }
            out.appendCodePoint(cp);
            count++;
        }
        return out.toString();
    }

    public String name() {
        return name;
    }

    public List<ZoneAssignment> zones() {
        return zones;
    }

    /** The assignment for {@code zone}, or {@code null}. */
    public ZoneAssignment zone(int zone) {
        for (ZoneAssignment z : zones) {
            if (z.zone() == zone) {
                return z;
            }
        }
        return null;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Scene s && s.name.equals(name) && s.zones.equals(zones);
    }

    @Override
    public int hashCode() {
        return name.hashCode() * 31 + zones.hashCode();
    }
}
