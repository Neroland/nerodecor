package za.co.neroland.nerodecor.command.showroom;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;

import za.co.neroland.nerodecor.NeroDecorCommon;
import za.co.neroland.nerodecor.chroma.ChromaColor;
import za.co.neroland.nerodecor.chroma.EffectParams;
import za.co.neroland.nerodecor.chroma.EffectProgram;
import za.co.neroland.nerodecor.chroma.EffectType;
import za.co.neroland.nerodecor.chroma.Scene;
import za.co.neroland.nerodecor.chroma.ZoneAssignment;
import za.co.neroland.nerodecor.chroma.server.ChromaNetworks;
import za.co.neroland.nerodecor.chroma.server.ChromaScenes;
import za.co.neroland.nerodecor.chroma.server.ChromaVerdict;
import za.co.neroland.nerodecor.content.DecorColor;
import za.co.neroland.nerodecor.content.DecorProperties;
import za.co.neroland.nerodecor.registry.DecorBlocks;
import za.co.neroland.nerodecor.registry.RegistrationProvider.RegistryEntry;

/**
 * The block catalogue behind {@code /nerodecor gallery grid}: every visible NeroDecor block, one row per collection.
 *
 * <p>The block list is read from the live registry (every {@code nerodecor} block except the hidden neon shims), so a
 * new block shows up without touching this class. Each block lands in the collection {@link #collectionOf} names; one
 * that no collection claims still gets shown, in an extra "UNSORTED" row, and is reported as an error, which the
 * {@code block_grid_builds} GameTest turns into a failure. How a block is staged (a single block, a connected run of
 * three, a 3x2 panel, a column, a wall-mounted fitting, a multiblock placed the way an item would) comes from
 * {@link #exhibit}; anything it does not list stands alone in its default state.
 *
 * <p>Rows run west to east. Each row is {@value #ROW_PITCH} blocks deep: a dark backing wall {@value #WALL_H} high on
 * the north side (face-mounted pieces hang on it), the exhibit line in front of it, then a three-block walkway to the
 * south to stand in. A holo sign on a plinth labels each row at its west end; exhibits sit {@value #GAP} blocks apart.
 * After the collections come a paint swatch row (a hull block in each {@link DecorColor}) and a Chroma demo strip (a
 * hub linked to the lights beside it, playing a slow wave).
 *
 * <p>Like the showroom, the footprint is a fixed box for a given registry ({@link #min}..{@link #max}); building first
 * clears it (air, no drops, any hub network in it deleted), so rebuilding at the same place gives the same result.
 */
public final class BlockGrid {

    /** Offset from the command position to the footprint's north-west corner (east and south). */
    public static final int OFFSET = 3;
    public static final int ROW_PITCH = 5;
    public static final int WALL_H = 4;
    public static final int GAP = 2;
    /** First exhibit column (the label stands at x 1). */
    public static final int FIRST_X = 4;
    public static final int CLEAR_ABOVE = 8;
    private static final int LIGHT_EVERY = 6;

    private static final String FLOOR = "minecraft:deepslate_tiles";
    private static final String EXHIBIT_FLOOR = "minecraft:polished_blackstone";
    private static final String EDGE = "minecraft:polished_blackstone_bricks";
    private static final String WALL = "minecraft:polished_deepslate";
    private static final String WALL_CAP = "minecraft:deepslate_tiles";
    private static final String LIGHT = "minecraft:light[level=11]";

    public static final String PAINT_ROW = "PAINT SWATCHES";
    public static final String CHROMA_DEMO_ROW = "CHROMA DEMO";
    public static final String UNSORTED_ROW = "UNSORTED";

    private BlockGrid() {
    }

    // --- collections ------------------------------------------------------------------------------------------------

    /** Collection titles in row order, north to south. */
    public static final List<String> COLLECTIONS = List.of("ORIGINAL HULL PANEL GLASS", "LUMINOUS", "CHROMA LIGHTS",
            "BULKHEAD", "HOLO", "COMMAND", "LAB AND HABITAT", "GANTRY", "CONDUITS", "SEVEN WORLDS", "ANCIENTS",
            "GREEBLES", "CHROMA CORE");

    private static final Map<String, String> MEMBERS = new LinkedHashMap<>();

    private static void members(String collection, String... ids) {
        for (String id : ids) {
            MEMBERS.put(id, collection);
        }
    }

    static {
        members("LUMINOUS", "circuit_plating", "circuit_plating_slab", "holo_grid_floor", "holo_grid_floor_slab",
                "starfield_panel", "data_stream_panel", "aurora_glass", "lumen_panel", "lumen_panel_slab", "void_rift",
                "crystal_lattice", "capacitor_bank", "xeno_bloom", "ion_vent", "plasma_conduit", "starsteel_pillar",
                "fusion_lamp");
        members("CHROMA LIGHTS", "light_strip", "edge_trim", "floor_tile", "floor_tile_slab", "step_light",
                "ceiling_coffer", "light_pillar", "neon_tube", "light_pane", "sconce_bar", "halo_ring", "cage_lamp");
        members("BULKHEAD", "sliding_door", "iris_hatch", "blast_door", "pressure_trapdoor", "airlock_frame",
                "door_status_light", "access_pad", "viewport_frame", "porthole", "canopy_glass", "shutter",
                "smart_glass");
        members("HOLO", "holo_projector", "holo_sign", "arrow_sign", "world_globe", "holo_table", "holo_curtain",
                "holo_plinth", "ticker_strip");
        members("COMMAND", "command_console", "wall_screen", "keyboard_shelf", "operator_chair", "server_rack",
                "drive_rack", "patch_panel", "blinkenlight_panel");
        members("LAB AND HABITAT", "lab_bench", "fume_hood", "specimen_tank", "sample_fridge", "lab_stool",
                "crew_bunk", "locker", "planter");
        members("GANTRY", "catwalk", "catwalk_stairs", "railing", "gantry_ladder", "floor_grate", "floor_grate_slab",
                "strut", "truss_beam");
        members("CONDUITS", "decor_pipe", "pipe_valve", "pipe_gauge", "cable_tray", "hanging_cables", "duct",
                "duct_fan", "coolant_tube", "beam_emitter_prop");
        members("ANCIENTS", "glyph_block", "ancient_brick", "cracked_ancient_brick", "ancient_pillar",
                "dormant_core", "glyph_strip", "arch_lintel");
        members("GREEBLES", "louvre_vent", "maintenance_panel", "hazard_stripe", "deck_sign", "warning_placard",
                "rivet_plate", "pipe_bracket", "floor_arrow");
        members("CHROMA CORE", "chroma_hub", "chroma_cube");
    }

    private static final String[] WORLD_PREFIXES = {"station_", "luna_", "mars_", "cindara_", "glacira_",
            "greenxertz_", "nyxara_"};

    /** The collection a block path belongs to, or {@code null} when none claims it. */
    public static @Nullable String collectionOf(String path) {
        String listed = MEMBERS.get(path);
        if (listed != null) {
            return listed;
        }
        if (path.startsWith("hull_") || path.startsWith("panel_") || path.startsWith("glass_")) {
            return "ORIGINAL HULL PANEL GLASS";
        }
        for (String prefix : WORLD_PREFIXES) {
            if (path.startsWith(prefix)) {
                return "SEVEN WORLDS";
            }
        }
        return null;
    }

    /**
     * Every visible NeroDecor block path in registration order: all {@code nerodecor} blocks except the hidden neon
     * shims (which fold into Chroma cubes). The Chroma hub is included although it is not in {@link DecorBlocks#ALL}.
     */
    public static List<String> visibleBlocks() {
        Set<String> hidden = new LinkedHashSet<>();
        for (RegistryEntry<Block> shim : DecorBlocks.NEON_SHIMS) {
            hidden.add(shim.id().getPath());
        }
        List<String> out = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            Identifier id = BuiltInRegistries.BLOCK.getKey(block);
            if (NeroDecorCommon.MOD_ID.equals(id.getNamespace()) && !hidden.contains(id.getPath())) {
                out.add(id.getPath());
            }
        }
        return out;
    }

    // --- exhibits ---------------------------------------------------------------------------------------------------

    /** One block of an exhibit: offset along the row (x), height (y), block spec (see {@link ShowroomScene}). */
    record Cell(int dx, int dy, String spec) {
    }

    /** How one block is staged: its cells, and how many columns it takes. */
    public record Exhibit(List<Cell> cells, int width) {

        static Exhibit of(Cell... cells) {
            int w = 1;
            for (Cell c : cells) {
                w = Math.max(w, c.dx() + 1);
            }
            return new Exhibit(List.of(cells), w);
        }
    }

    private static Exhibit one(String spec) {
        return Exhibit.of(new Cell(0, 0, spec));
    }

    private static Exhibit at(int dy, String spec) {
        return Exhibit.of(new Cell(0, dy, spec));
    }

    /** A connected run of three along the row. */
    private static Exhibit run(int dy, String spec) {
        return Exhibit.of(new Cell(0, dy, spec), new Cell(1, dy, spec), new Cell(2, dy, spec));
    }

    /** A 3 x 2 panel, so a connected surface shows its outer bezel. */
    private static Exhibit panel(String spec) {
        return Exhibit.of(new Cell(0, 0, spec), new Cell(1, 0, spec), new Cell(2, 0, spec),
                new Cell(0, 1, spec), new Cell(1, 1, spec), new Cell(2, 1, spec));
    }

    private static Exhibit column(int height, String spec) {
        Cell[] cells = new Cell[height];
        for (int y = 0; y < height; y++) {
            cells[y] = new Cell(0, y, spec);
        }
        return Exhibit.of(cells);
    }

    private static Exhibit row(int dy, String... specs) {
        Cell[] cells = new Cell[specs.length];
        for (int i = 0; i < specs.length; i++) {
            cells[i] = new Cell(i, dy, specs[i]);
        }
        return Exhibit.of(cells);
    }

    /** The staging for block {@code path}; unlisted blocks stand alone in their default state. */
    public static Exhibit exhibit(String path) {
        // Original families by shape.
        if (path.startsWith("hull_") || path.startsWith("panel_") || path.startsWith("glass_")) {
            if (path.endsWith("_wall") || path.endsWith("_pane")) {
                return run(0, path);
            }
            if (path.endsWith("_stairs")) {
                return one(path + "[facing=north]");
            }
            return one(path);
        }
        return switch (path) {
            // Luminous
            case "circuit_plating", "starfield_panel", "data_stream_panel", "aurora_glass", "lumen_panel" ->
                    panel(path);
            case "holo_grid_floor" -> run(0, path);
            case "plasma_conduit", "starsteel_pillar" -> column(3, path + "[axis=y]");
            case "fusion_lamp" -> Exhibit.of(new Cell(0, 0, "minecraft:redstone_block"),
                    new Cell(0, 1, "fusion_lamp[lit=true]"));
            // Chroma lights
            case "light_strip" -> run(2, "light_strip[face=north,axis=x]");
            case "edge_trim" -> run(0, "edge_trim[facing=south,half=bottom]");
            case "floor_tile", "light_pane" -> run(0, path);
            case "step_light" -> one("step_light[facing=south]");
            case "ceiling_coffer" -> Exhibit.of(new Cell(0, 3, WALL), new Cell(1, 3, WALL), new Cell(2, 3, WALL),
                    new Cell(0, 2, path), new Cell(1, 2, path), new Cell(2, 2, path));
            case "light_pillar" -> column(3, "light_pillar[axis=y]");
            case "neon_tube" -> run(1, "neon_tube[face=north,axis=x]");
            case "sconce_bar" -> at(1, "sconce_bar[facing=south,vertical=true]");
            case "cage_lamp" -> at(2, "cage_lamp[face=north]");
            // Bulkhead: doors closed, multiblocks placed the way an item would place them.
            case "sliding_door" -> row(0, "!sliding_door[facing=south,hinge=left]",
                    "!sliding_door[facing=south,hinge=right]");
            case "iris_hatch" -> at(1, "iris_hatch[face=north]");
            case "blast_door" -> Exhibit.of(new Cell(1, 0, "!blast_door[facing=south,part=7]"),
                    new Cell(2, 0, "minecraft:air"));
            case "pressure_trapdoor" -> one("pressure_trapdoor[facing=south,half=bottom]");
            case "airlock_frame", "viewport_frame", "smart_glass" -> panel(path);
            case "door_status_light" -> at(2, "door_status_light[face=north]");
            case "access_pad" -> at(1, "access_pad[face=north]");
            case "porthole" -> at(1, "porthole[axis=z]");
            case "canopy_glass" -> run(0, "canopy_glass[facing=south,half=bottom]");
            case "shutter" -> run(1, "shutter[axis=x]");
            // Holo
            case "holo_projector" -> one("holo_projector[face=down]");
            case "holo_sign" -> at(1, "holo_sign[facing=south]");
            case "arrow_sign" -> at(1, "arrow_sign[facing=south,direction=right]");
            case "world_globe" -> row(0, "world_globe[world=station]", "world_globe[world=luna]",
                    "world_globe[world=mars]", "world_globe[world=cindara]", "world_globe[world=glacira]",
                    "world_globe[world=greenxertz]", "world_globe[world=nyxara]");
            case "holo_curtain" -> Exhibit.of(new Cell(0, 0, "holo_curtain[axis=x]"),
                    new Cell(1, 0, "holo_curtain[axis=x]"), new Cell(2, 0, "holo_curtain[axis=x]"),
                    new Cell(0, 1, "holo_curtain[axis=x]"), new Cell(1, 1, "holo_curtain[axis=x]"),
                    new Cell(2, 1, "holo_curtain[axis=x]"));
            case "ticker_strip" -> run(2, "ticker_strip[face=north,axis=x]");
            // Command
            case "command_console" -> run(0, "command_console[facing=south]");
            case "wall_screen" -> panel("wall_screen[facing=south]");
            case "keyboard_shelf" -> at(1, "keyboard_shelf[facing=south]");
            case "operator_chair" -> one("operator_chair[facing=south]");
            case "server_rack", "drive_rack" -> column(2, path + "[facing=south]");
            case "patch_panel" -> run(1, "patch_panel[facing=south]");
            // Lab and habitat
            case "lab_bench" -> run(0, "lab_bench[axis=x]");
            case "fume_hood", "sample_fridge" -> one(path + "[facing=south]");
            case "specimen_tank" -> column(2, path);
            case "crew_bunk" -> Exhibit.of(new Cell(0, 0, "!crew_bunk[facing=east]"), new Cell(1, 0, "minecraft:air"));
            case "locker" -> run(0, "locker[facing=south]");
            case "planter" -> row(0, "planter[axis=x,plant=0]", "planter[axis=x,plant=1]", "planter[axis=x,plant=2]");
            // Gantry
            case "catwalk", "railing", "floor_grate" -> run(0, path);
            case "catwalk_stairs" -> one("catwalk_stairs[facing=north]");
            case "gantry_ladder" -> column(3, "gantry_ladder[facing=south]");
            case "strut" -> column(3, "strut[axis=y]");
            case "truss_beam" -> run(0, "truss_beam[axis=x]");
            // Conduits
            case "decor_pipe" -> run(1, path);
            case "pipe_valve", "pipe_gauge" -> row(1, "decor_pipe", path + "[axis=x]", "decor_pipe");
            case "cable_tray" -> run(2, "cable_tray[face=north,axis=x]");
            case "hanging_cables" -> Exhibit.of(new Cell(0, 3, WALL), new Cell(0, 2, path), new Cell(0, 1, path),
                    new Cell(0, 0, path));
            case "duct" -> run(1, "duct[axis=x]");
            case "duct_fan" -> at(1, "duct_fan[facing=south]");
            case "coolant_tube" -> run(0, path);
            case "beam_emitter_prop" -> one("beam_emitter_prop[facing=up]");
            // Seven worlds
            case "station_plating", "glacira_bubble_ice" -> panel(path);
            case "luna_mare_column" -> column(3, "luna_mare_column[axis=y]");
            case "glacira_cryo_fin" -> column(3, "glacira_cryo_fin[axis=y]");
            case "mars_dust_drift_tile" -> one("mars_dust_drift_tile[facing=north]");
            // Ancients
            case "glyph_block" -> Exhibit.of(new Cell(0, 0, "glyph_block[glyph=0]"),
                    new Cell(1, 0, "glyph_block[glyph=1]"), new Cell(2, 0, "glyph_block[glyph=2]"),
                    new Cell(0, 1, "glyph_block[glyph=3]"), new Cell(1, 1, "glyph_block[glyph=4]"),
                    new Cell(2, 1, "glyph_block[glyph=5]"));
            case "ancient_pillar" -> column(3, "ancient_pillar[axis=y]");
            case "dormant_core" -> one("dormant_core[powered=true]");
            case "glyph_strip" -> run(1, "glyph_strip[face=north,axis=x]");
            case "arch_lintel" -> Exhibit.of(new Cell(0, 0, "ancient_pillar[axis=y]"),
                    new Cell(0, 1, "ancient_pillar[axis=y]"), new Cell(2, 0, "ancient_pillar[axis=y]"),
                    new Cell(2, 1, "ancient_pillar[axis=y]"), new Cell(0, 2, "arch_lintel[facing=south]"),
                    new Cell(1, 2, "arch_lintel[facing=south]"), new Cell(2, 2, "arch_lintel[facing=south]"));
            // Greebles
            case "louvre_vent", "maintenance_panel" -> at(1, path + "[face=north]");
            case "hazard_stripe" -> run(0, "hazard_stripe[face=down,axis=x]");
            case "deck_sign" -> row(1, "deck_sign[face=north,number=1]", "deck_sign[face=north,number=2]",
                    "deck_sign[face=north,number=3]");
            case "warning_placard" -> row(1, "warning_placard[face=north,symbol=caution]",
                    "warning_placard[face=north,symbol=radiation]", "warning_placard[face=north,symbol=voltage]");
            case "rivet_plate" -> run(1, "rivet_plate[face=north]");
            case "pipe_bracket" -> at(1, "pipe_bracket[face=north,axis=x]");
            case "floor_arrow" -> one("floor_arrow[facing=north]");
            // Chroma core: the exhibit hub is inert (placed plainly, no network); the demo strip has the live one.
            case "chroma_cube" -> panel(path);
            default -> one(path);
        };
    }

    // --- layout -----------------------------------------------------------------------------------------------------

    /** One placed exhibit: the block it shows, its staging, and its first column. */
    public record Placed(String id, Exhibit exhibit, int x) {
    }

    /** One row: its label, exhibits, and the z of its backing wall. */
    public record Row(String title, List<Placed> exhibits, int z, int width) {

        public int exhibitZ() {
            return z + 1;
        }
    }

    /** The whole grid for the current registry: rows, footprint size, and blocks no collection claims. */
    public record Layout(List<Row> rows, int width, int depth, List<String> unsorted) {

        public @Nullable Row row(String title) {
            for (Row r : rows) {
                if (r.title().equals(title)) {
                    return r;
                }
            }
            return null;
        }
    }

    /** The paint row's swatches: one paintable hull block per colour, side by side. */
    private static final String SWATCH = "hull_nero_alloy";

    /** Lays the grid out from the live registry. Pure: places nothing. */
    public static Layout layout() {
        Map<String, List<String>> byCollection = new LinkedHashMap<>();
        for (String c : COLLECTIONS) {
            byCollection.put(c, new ArrayList<>());
        }
        List<String> unsorted = new ArrayList<>();
        for (String path : visibleBlocks()) {
            String c = collectionOf(path);
            if (c == null) {
                unsorted.add(path);
            } else {
                byCollection.get(c).add(path);
            }
        }
        List<Row> rows = new ArrayList<>();
        int maxWidth = 0;
        for (Map.Entry<String, List<String>> e : byCollection.entrySet()) {
            Row row = row(e.getKey(), e.getValue(), rows.size());
            rows.add(row);
            maxWidth = Math.max(maxWidth, row.width());
        }
        if (!unsorted.isEmpty()) {
            Row row = row(UNSORTED_ROW, unsorted, rows.size());
            rows.add(row);
            maxWidth = Math.max(maxWidth, row.width());
        }
        int paintWidth = FIRST_X + DecorColor.values().length + 2;
        rows.add(new Row(PAINT_ROW, List.of(), rows.size() * ROW_PITCH, paintWidth));
        rows.add(new Row(CHROMA_DEMO_ROW, List.of(), rows.size() * ROW_PITCH, FIRST_X + CHROMA_DEMO_WIDTH + 2));
        maxWidth = Math.max(maxWidth, Math.max(paintWidth, FIRST_X + CHROMA_DEMO_WIDTH + 2));
        return new Layout(List.copyOf(rows), maxWidth + 1, rows.size() * ROW_PITCH + 1, List.copyOf(unsorted));
    }

    private static Row row(String title, List<String> ids, int index) {
        List<Placed> placed = new ArrayList<>();
        int x = FIRST_X;
        for (String id : ids) {
            Exhibit ex = exhibit(id);
            placed.add(new Placed(id, ex, x));
            x += ex.width() + GAP;
        }
        int width = (ids.isEmpty() ? FIRST_X : x - GAP) + 2;
        return new Row(title, List.copyOf(placed), index * ROW_PITCH, width);
    }

    // --- footprint --------------------------------------------------------------------------------------------------

    public static BlockPos originFor(BlockPos at) {
        return at.offset(OFFSET, 0, OFFSET);
    }

    public static BlockPos min(BlockPos origin) {
        return origin.below();
    }

    public static BlockPos max(BlockPos origin, Layout layout) {
        return origin.offset(layout.width() - 1, CLEAR_ABOVE, layout.depth() - 1);
    }

    public static boolean fits(ServerLevel level, BlockPos origin, Layout layout) {
        return !level.isOutsideBuildHeight(min(origin)) && !level.isOutsideBuildHeight(max(origin, layout));
    }

    // --- build ------------------------------------------------------------------------------------------------------

    /**
     * What a build did: blocks placed, the visible block ids it staged, problems (empty when every block found its
     * collection and every spec is sound), notes, and the footprint.
     */
    public record Result(int placed, Set<String> shown, List<String> errors, List<String> notes, BlockPos min,
            BlockPos max) {
    }

    /** Builds the grid with its north-west corner at {@code origin}; {@code owner} owns the Chroma demo's hub. */
    public static Result build(ServerLevel level, BlockPos origin, @Nullable ServerPlayer owner) {
        Layout layout = layout();
        List<String> errors = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        for (String id : layout.unsorted()) {
            errors.add("block " + NeroDecorCommon.MOD_ID + ":" + id + " belongs to no collection "
                    + "(add it to BlockGrid; shown in the UNSORTED row meanwhile)");
        }
        Showroom.clearBox(level, min(origin), max(origin, layout));
        Showroom.Builder b = new Showroom.Builder(level, Showroom.index(), errors);
        int w = layout.width();
        int d = layout.depth();
        // Floor: deepslate tiles, a polished blackstone ring at the edge.
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                boolean edge = x == 0 || z == 0 || x == w - 1 || z == d - 1;
                b.put(origin.offset(x, -1, z), edge ? EDGE : FLOOR, "floor");
            }
        }
        Set<String> shown = new LinkedHashSet<>();
        List<BlockPos> signs = new ArrayList<>();
        List<String[]> signText = new ArrayList<>();
        for (Row row : layout.rows()) {
            int rw = row.width();
            for (int x = 1; x < rw - 1; x++) {
                for (int y = 0; y < WALL_H; y++) {
                    b.put(origin.offset(x, y, row.z()), y == WALL_H - 1 ? WALL_CAP : WALL, row.title());
                }
                b.put(origin.offset(x, -1, row.exhibitZ()), EXHIBIT_FLOOR, row.title());
                if (x % LIGHT_EVERY == 3) {
                    b.put(origin.offset(x, 3, row.z() + 3), LIGHT, row.title());
                }
            }
            // Label: a holo sign on a plinth at the west end of the exhibit line.
            b.put(origin.offset(1, 0, row.exhibitZ()), "holo_plinth", row.title());
            b.put(origin.offset(1, 1, row.exhibitZ()), "holo_sign[facing=south]", row.title());
            signs.add(origin.offset(1, 1, row.exhibitZ()));
            signText.add(new String[] {row.title(), subtitle(row)});
            for (Placed p : row.exhibits()) {
                for (Cell c : p.exhibit().cells()) {
                    BlockPos pos = origin.offset(p.x() + c.dx(), c.dy(), row.exhibitZ());
                    if ("minecraft:air".equals(c.spec())) {
                        continue; // room for a multiblock's other half
                    }
                    b.put(pos, c.spec(), row.title() + " " + p.id());
                    if (c.spec().startsWith("holo_sign")) {
                        signs.add(pos);
                        signText.add(new String[] {"HOLO SIGN", "edit me"});
                    }
                }
                shown.add(p.id());
            }
        }
        Row paint = layout.row(PAINT_ROW);
        DecorColor[] colours = DecorColor.values();
        Block swatch = Showroom.index().get(NeroDecorCommon.MOD_ID + ":" + SWATCH);
        if (paint != null && swatch != null && swatch.defaultBlockState().hasProperty(DecorProperties.COLOR)) {
            for (int k = 0; k < colours.length; k++) {
                b.put(origin.offset(FIRST_X + k, 0, paint.exhibitZ()),
                        swatch.defaultBlockState().setValue(DecorProperties.COLOR, colours[k]));
            }
        } else {
            errors.add("paint row: " + SWATCH + " is missing or not paintable");
        }
        Row demo = layout.row(CHROMA_DEMO_ROW);
        if (demo != null) {
            for (String[] cell : CHROMA_DEMO) {
                int x = Integer.parseInt(cell[0]);
                int y = Integer.parseInt(cell[1]);
                b.put(origin.offset(FIRST_X + x, y, demo.exhibitZ()), cell[2], CHROMA_DEMO_ROW);
            }
        }
        b.resolveAll();
        b.placeDeferred(owner);
        for (int i = 0; i < signs.size(); i++) {
            Showroom.sign(level, signs.get(i), signText.get(i)[0], signText.get(i)[1], errors);
        }
        if (demo != null) {
            playDemo(level, origin, demo, owner, errors, notes);
        }
        return new Result(b.placed(), Set.copyOf(shown), List.copyOf(errors), List.copyOf(notes), min(origin),
                max(origin, layout));
    }

    private static String subtitle(Row row) {
        if (PAINT_ROW.equals(row.title())) {
            return DecorColor.values().length + " colours";
        }
        if (CHROMA_DEMO_ROW.equals(row.title())) {
            return "hub + linked lights";
        }
        return row.exhibits().size() + " blocks";
    }

    /** Empties the grid footprint at {@code origin} (for the current registry) and drops its hub networks. */
    public static Showroom.Cleared clear(ServerLevel level, BlockPos origin) {
        return Showroom.clearBox(level, min(origin), max(origin, layout()));
    }

    // --- chroma demo ------------------------------------------------------------------------------------------------

    /** Width of the Chroma demo strip, in columns from {@link #FIRST_X}. */
    private static final int CHROMA_DEMO_WIDTH = 23;

    /** The demo strip as {x, y, spec}: a hub, then cubes, a pillar, a wall strip, neon and a cube panel. */
    private static final List<String[]> CHROMA_DEMO = demoCells();

    private static List<String[]> demoCells() {
        List<String[]> cells = new ArrayList<>();
        cells.add(new String[] {"0", "0", "!chroma_hub"});
        for (int x = 3; x <= 7; x++) {
            cells.add(new String[] {String.valueOf(x), "0", "chroma_cube"});
        }
        for (int y = 0; y < 3; y++) {
            cells.add(new String[] {"10", String.valueOf(y), "light_pillar[axis=y]"});
        }
        for (int x = 13; x <= 17; x++) {
            cells.add(new String[] {String.valueOf(x), "2", "light_strip[face=north,axis=x]"});
            cells.add(new String[] {String.valueOf(x), "0", "neon_tube[face=down,axis=x]"});
        }
        for (int x = 20; x <= 22; x++) {
            for (int y = 0; y < 3; y++) {
                cells.add(new String[] {String.valueOf(x), String.valueOf(y), "chroma_cube"});
            }
        }
        return cells;
    }

    /** Links every capable block in the demo strip to its hub and plays a slow cyan-to-violet wave along the row. */
    private static void playDemo(ServerLevel level, BlockPos origin, Row demo, @Nullable ServerPlayer owner,
                                 List<String> errors, List<String> notes) {
        BlockPos hub = origin.offset(FIRST_X, 0, demo.exhibitZ());
        long id = level.getBlockEntity(hub) instanceof za.co.neroland.nerodecor.content.block.ChromaHubBlockEntity h
                ? h.networkId() : 0L;
        if (owner == null || id == 0L) {
            notes.add("chroma demo: the hub has no network (no player, Chroma disabled, or the hub cap is reached); "
                    + "lights are static");
            return;
        }
        ChromaNetworks.Selection selection = ChromaNetworks.linkBox(level, owner, id,
                origin.offset(FIRST_X, 0, demo.exhibitZ()),
                origin.offset(FIRST_X + CHROMA_DEMO_WIDTH - 1, WALL_H - 1, demo.exhibitZ()));
        if (!selection.verdict().isOk() || selection.linked() == 0) {
            errors.add("chroma demo: linking failed: " + selection.verdict());
            return;
        }
        Scene scene = new Scene("Grid demo", List.of(new ZoneAssignment(0, 0, new EffectProgram(EffectType.AXIS_WAVE,
                EffectParams.builder().primary(ChromaColor.of(0x22, 0xE8, 0xFF)).secondary(ChromaColor.of(0x8A, 0x5C,
                        0xFF)).axis(EffectParams.AXIS_X).wavelength(16).periodTicks(200).build()))));
        ChromaVerdict verdict = ChromaScenes.save(ChromaNetworks.store(level), id, owner.getUUID(), 0, scene, true,
                level.getGameTime());
        if (!verdict.isOk()) {
            errors.add("chroma demo: starting the scene failed: " + verdict);
        }
    }
}
