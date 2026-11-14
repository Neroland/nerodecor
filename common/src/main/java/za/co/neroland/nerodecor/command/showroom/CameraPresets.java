package za.co.neroland.nerodecor.command.showroom;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Camera viewpoints for {@code /nerodecor capture} and the Fabric showcase client game test, as data, so both shoot
 * the same frames. A preset is a feet position offset from the remembered origin of the {@link Showroom} or the
 * {@link BlockGrid} (its north-west corner at standing level), a yaw and pitch (Minecraft convention: yaw 0 faces
 * south, -90 east, 90 west, 180 north; positive pitch looks down) and a field of view in degrees.
 *
 * <p>The showroom gets a low, wide hero shot from the corridor and a detail shot inside every bay, plus one shot down
 * the corridor. The grid gets six: three high overviews, a pass along the first row, the paint swatches and the
 * Chroma demo strip. Grid presets follow the grid's live layout, so they keep framing it as blocks are added.
 *
 * <p>Pure data: no client classes, so the server's {@code capture goto} resolves the same ids.
 */
public final class CameraPresets {

    public static final String SHOWROOM = "showroom";
    public static final String GRID = "grid";

    /** One viewpoint. */
    public record Preset(String id, String target, double dx, double dy, double dz, float yaw, float pitch, int fov) {
    }

    /** Detail-shot overrides per bay id: {x, y, z (bay-local), yaw, pitch}. */
    private static final Map<String, double[]> DETAIL = Map.of(
            // The airlock's first bulkhead stands two blocks in: shoot the chamber behind it, towards the blast door.
            "airlock", new double[] {9.8, 0.6, 3.0, 58.0, 6.0},
            "seven_worlds", new double[] {2.5, 3.0, 1.0, -35.0, 26.0},
            "chroma_corridor", new double[] {5.5, 1.0, 0.5, 0.0, 6.0});
    private static final double[] DETAIL_DEFAULT = {1.8, 3.2, 0.6, -45.0, 28.0};

    private CameraPresets() {
    }

    /** Showroom presets, offsets from the showroom origin. */
    public static List<Preset> showroom() {
        List<Preset> out = new ArrayList<>();
        out.add(new Preset("showroom_corridor", SHOWROOM, 0.5, 3.0, 0.5, -72.0F, 16.0F, 75));
        for (int i = 0; i < ShowroomScenes.ALL.size(); i++) {
            String bay = ShowroomScenes.ALL.get(i).id();
            double bx = 1 + i * Showroom.PITCH;
            double bz = Showroom.CORRIDOR;
            String name = String.format(Locale.ROOT, "showroom_%d_%s", i + 1, bay);
            // Hero: low and wide, from just outside the corridor, level so the floor arrows stay out of frame.
            out.add(new Preset(name, SHOWROOM, bx + Showroom.BAY_W / 2.0, 2.0, -2.0, 0.0F, 6.0F, 75));
            double[] d = DETAIL.getOrDefault(bay, DETAIL_DEFAULT);
            out.add(new Preset(name + "_detail", SHOWROOM, bx + d[0], d[1], bz + d[2], (float) d[3], (float) d[4],
                    60));
        }
        return out;
    }

    /** Grid presets for the current layout, offsets from the grid origin. */
    public static List<Preset> grid() {
        return grid(BlockGrid.layout());
    }

    public static List<Preset> grid(BlockGrid.Layout layout) {
        double w = layout.width();
        double d = layout.depth();
        List<Preset> out = new ArrayList<>();
        // High and oblique from the south, then from each southern corner.
        out.add(new Preset("grid_overview", GRID, w / 2.0, 30.0, d + 16.0, 180.0F, 45.0F, 75));
        out.add(new Preset("grid_overview_east", GRID, w + 4.0, 18.0, d + 4.0, 135.0F, 30.0F, 75));
        out.add(new Preset("grid_overview_west", GRID, -4.0, 18.0, d + 4.0, -135.0F, 30.0F, 75));
        // Over the first row's walkway at its west end, looking along it at the exhibits' fronts.
        BlockGrid.Row first = layout.rows().get(0);
        out.add(new Preset("grid_row_original", GRID, first.x0() + 1.5, 4.0, first.z() + 5.0, -118.0F, 32.0F, 70));
        BlockGrid.Row paint = layout.row(BlockGrid.PAINT_ROW);
        if (paint != null) {
            out.add(new Preset("grid_paint", GRID, paint.x0() + BlockGrid.FIRST_X + 8.5, 2.0, paint.z() + 11.0,
                    180.0F, 10.0F, 70));
        }
        BlockGrid.Row demo = layout.row(BlockGrid.CHROMA_DEMO_ROW);
        if (demo != null) {
            out.add(new Preset("grid_chroma_demo", GRID, demo.x0() + BlockGrid.FIRST_X + 11.5, 1.5, demo.z() + 12.0,
                    180.0F, 8.0F, 70));
        }
        return out;
    }

    /** Every preset for {@code target} ({@link #SHOWROOM} or {@link #GRID}). */
    public static List<Preset> forTarget(String target) {
        return GRID.equals(target) ? grid() : showroom();
    }

    public static Optional<Preset> byId(String id) {
        for (Preset p : showroom()) {
            if (p.id().equals(id)) {
                return Optional.of(p);
            }
        }
        for (Preset p : grid()) {
            if (p.id().equals(id)) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }
}
