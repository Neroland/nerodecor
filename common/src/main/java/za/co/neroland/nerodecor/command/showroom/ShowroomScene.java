package za.co.neroland.nerodecor.command.showroom;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * One showroom bay, as data. The bay interior is {@value Showroom#BAY_W} blocks wide (local x, west to east),
 * {@value Showroom#BAY_D} deep (local z, 0 = the open front on the corridor, north) and {@value Showroom#BAY_H} high
 * (local y, 0 = standing level). The builder draws the shell (floor at y -1, back wall at z {@value Showroom#BAY_D},
 * ceiling at y {@value Showroom#BAY_H}, shared hull side walls) from {@link #floor}, {@link #back} and
 * {@link #ceiling}; the layers fill the interior.
 *
 * <p>Each layer is a plan view: one string per row from the front (z 0) to the back, one character per block from
 * west (x 0) to east. {@code '.'} and {@code ' '} are air (left untouched). Every other character must be in the
 * legend, which maps it to a block spec: {@code [!][namespace:]path[prop=value,...]} with the namespace defaulting
 * to {@code nerodecor}. A leading {@code !} places the block the way an item would (its {@code setPlacedBy} runs with
 * the command's player), which multiblocks (doors, bunks) and the Chroma hub need. Layer {@code -1} replaces floor
 * blocks. Position (0, 0..1, 0) is the bay's label (a holo plinth and sign) and must stay free.
 *
 * <p>{@link #sides} give each bay its own wall language without touching the shared hull side walls: per local y, a
 * west row and an east row of {@value Showroom#BAY_D} characters (z 0 to the back, same legend) placed in the
 * interior columns x 0 and x {@code BAY_W - 1} before the layers (so a layer can still override a cell). Wall-mounted
 * blocks there face into the bay ({@code face=west} on the west side, {@code face=east} on the east side).
 *
 * @param id       short id, used in screenshot names
 * @param title    label line 1 (at most 32 characters)
 * @param subtitle label line 2 (at most 32 characters)
 * @param floor    shell floor spec
 * @param back     shell back-wall spec
 * @param ceiling  shell ceiling spec
 * @param legend   character to block spec
 * @param layers   local y to rows
 * @param sides    local y to {west row, east row} wall linings
 * @param hook     extra steps after the blocks are down (open doors, start a Chroma scene); may be {@code null}
 */
public record ShowroomScene(String id, String title, String subtitle, String floor, String back, String ceiling,
        Map<Character, String> legend, Map<Integer, List<String>> layers, Map<Integer, List<String>> sides,
        ShowroomHook hook) {

    /** Fluent builder so the scene table in {@link ShowroomScenes} reads like a data file. */
    static Builder scene(String id, String title, String subtitle) {
        return new Builder(id, title, subtitle);
    }

    static final class Builder {
        private final String id;
        private final String title;
        private final String subtitle;
        private String floor = "hull_nero_alloy";
        private String back = "hull_nero_alloy";
        private String ceiling = "hull_nero_alloy";
        private final Map<Character, String> legend = new HashMap<>();
        private final Map<Integer, List<String>> layers = new TreeMap<>();
        private final Map<Integer, List<String>> sides = new TreeMap<>();
        private ShowroomHook hook;

        private Builder(String id, String title, String subtitle) {
            this.id = id;
            this.title = title;
            this.subtitle = subtitle;
        }

        Builder shell(String floorSpec, String backSpec, String ceilingSpec) {
            this.floor = floorSpec;
            this.back = backSpec;
            this.ceiling = ceilingSpec;
            return this;
        }

        Builder key(char c, String spec) {
            legend.put(c, spec);
            return this;
        }

        Builder layer(int y, String... rows) {
            layers.put(y, List.of(rows));
            return this;
        }

        /** The same west and east wall lining on every level from {@code fromY} to {@code toY} inclusive. */
        Builder sides(int fromY, int toY, String west, String east) {
            for (int y = fromY; y <= toY; y++) {
                sides.put(y, List.of(west, east));
            }
            return this;
        }

        Builder hook(ShowroomHook h) {
            this.hook = h;
            return this;
        }

        ShowroomScene build() {
            return new ShowroomScene(id, title, subtitle, floor, back, ceiling, Map.copyOf(legend), Map.copyOf(layers),
                    Map.copyOf(sides), hook);
        }
    }
}
