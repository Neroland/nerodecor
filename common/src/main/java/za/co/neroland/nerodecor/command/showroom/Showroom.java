package za.co.neroland.nerodecor.command.showroom;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import za.co.neroland.nerodecor.NeroDecorCommon;
import za.co.neroland.nerodecor.chroma.server.ChromaNetworks;
import za.co.neroland.nerodecor.content.block.ChromaHubBlockEntity;
import za.co.neroland.nerodecor.content.block.bulkhead.AnimatedDoorBlock;
import za.co.neroland.nerodecor.content.block.holo.HoloSignBlockEntity;

/**
 * The walkable showroom behind {@code /nerodecor gallery}: a corridor along the north side with one bay per
 * {@link ShowroomScenes} entry to the south of it, side by side along +x.
 *
 * <p>Footprint (bounded, fixed): {@link #width()} x {@link #DEPTH} blocks, from one block below the origin to
 * {@link #CLEAR_ABOVE} above it. Building first clears that whole box (air, no drops) and removes any Chroma hub
 * network whose hub stood in it, so running the command twice at the same place gives the same result. The outer
 * floor ring is polished blackstone so the edge of the footprint is visible. {@link #clear} empties the box again,
 * floor included.
 *
 * <p>Errors (an unknown block id, a property a block does not have, a block that would not survive where the layout
 * put it) are collected rather than thrown, so the GameTest can assert there are none.
 */
public final class Showroom {

    public static final int BAY_W = 11;
    public static final int BAY_D = 9;
    public static final int BAY_H = 6;
    /** Corridor depth in front of the bays. */
    public static final int CORRIDOR = 3;
    /** Bay pitch along x: interior plus one shared side wall. */
    public static final int PITCH = BAY_W + 1;
    /** Corridor + bay + back wall. */
    public static final int DEPTH = CORRIDOR + BAY_D + 1;
    /** Height cleared above the origin (the ceiling is at {@link #BAY_H}, plus headroom for terrain). */
    public static final int CLEAR_ABOVE = BAY_H + 4;
    /** Offset from the command position to the footprint's north-west corner (east and south). */
    public static final int OFFSET = 3;

    /** setBlock flags: send to clients, skip neighbour shape updates (we resolve shapes ourselves). */
    private static final int PLACE = 2 | 16;
    /** setBlock flags for clearing: as {@link #PLACE}, and no drops. */
    private static final int REMOVE = 2 | 16 | 32;

    private static final String SIDE_WALL = "hull_nero_alloy";
    private static final String CORRIDOR_FLOOR = "minecraft:polished_deepslate";
    private static final String EDGE_FLOOR = "minecraft:polished_blackstone";

    private Showroom() {
    }

    /** Result of a build: blocks placed, problems found (empty when the layouts are sound), the footprint. */
    public record Result(int placed, List<String> errors, List<String> notes, BlockPos min, BlockPos max) {
    }

    public static int width() {
        return ShowroomScenes.ALL.size() * PITCH + 1;
    }

    /** Footprint origin (north-west corner, standing level) for a command run at {@code at}. */
    public static BlockPos originFor(BlockPos at) {
        return at.offset(OFFSET, 0, OFFSET);
    }

    public static BlockPos min(BlockPos origin) {
        return origin.below();
    }

    public static BlockPos max(BlockPos origin) {
        return origin.offset(width() - 1, CLEAR_ABOVE, DEPTH - 1);
    }

    /** Local origin (x 0, y 0, z 0 = front-west interior block) of bay {@code index}. */
    public static BlockPos bayOrigin(BlockPos origin, int index) {
        return origin.offset(1 + index * PITCH, 0, CORRIDOR);
    }

    /** Whether the whole footprint fits inside the level's build height. */
    public static boolean fits(ServerLevel level, BlockPos origin) {
        return !level.isOutsideBuildHeight(min(origin)) && !level.isOutsideBuildHeight(max(origin));
    }

    // --- build ------------------------------------------------------------------------------------------------------

    /**
     * Builds the showroom with its north-west corner at {@code origin}. {@code owner} becomes the owner of the Chroma
     * corridor's hub network; without one the hub is left inert (and a note says so).
     */
    public static Result build(ServerLevel level, BlockPos origin, @Nullable ServerPlayer owner) {
        Map<String, Block> index = index();
        List<String> errors = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        clear(level, origin);

        Builder b = new Builder(level, index, errors);
        int w = width();
        // Corridor floor, edge ring and arrows.
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < CORRIDOR; z++) {
                boolean edge = x == 0 || x == w - 1 || z == 0;
                b.put(origin.offset(x, -1, z), edge ? EDGE_FLOOR : CORRIDOR_FLOOR, "corridor");
            }
        }
        b.put(origin.offset(1, 0, 1), "holo_plinth", "entrance");
        b.put(origin.offset(1, 1, 1), "holo_sign[facing=north]", "entrance");

        List<Bay> bays = new ArrayList<>();
        for (int i = 0; i < ShowroomScenes.ALL.size(); i++) {
            ShowroomScene scene = ShowroomScenes.ALL.get(i);
            Bay bay = new Bay(level, bayOrigin(origin, i), owner, i, scene, b, errors, notes);
            bay.shell();
            bay.sides();
            bay.layers();
            bay.label();
            bays.add(bay);
            b.put(origin.offset(1 + i * PITCH + BAY_W / 2, 0, 1), "floor_arrow[facing=east]", "corridor");
        }
        // The outer ring of the bay floors and back walls is part of the footprint edge.
        for (int x = 0; x < w; x++) {
            b.put(origin.offset(x, -1, DEPTH - 1), EDGE_FLOOR, "edge");
        }
        for (int z = CORRIDOR; z < DEPTH; z++) {
            b.put(origin.offset(0, -1, z), EDGE_FLOOR, "edge");
            b.put(origin.offset(w - 1, -1, z), EDGE_FLOOR, "edge");
        }

        b.resolveAll();
        b.placeDeferred(owner);
        sign(level, origin.offset(1, 1, 1), "NERODECOR SHOWROOM", "/nerodecor gallery clear", errors);
        for (Bay bay : bays) {
            bay.afterPlacement();
        }
        return new Result(b.placed, List.copyOf(errors), List.copyOf(notes), min(origin), max(origin));
    }

    /** What {@link #clear} removed: blocks set to air, and Chroma hub networks deleted. */
    public record Cleared(int blocks, int hubs) {
    }

    /**
     * Empties the footprint at {@code origin} (floor included) and deletes every Chroma hub network whose hub stood in
     * it, whoever owns it. That is why {@code /nerodecor gallery} and {@code gallery clear} are operator-only
     * (permission level 2): only an operator may remove another player's hub network this way.
     */
    public static Cleared clear(ServerLevel level, BlockPos origin) {
        return clearBox(level, min(origin), max(origin));
    }

    /**
     * Empties the box {@code lo..hi} (inclusive; air, no drops, attached blocks first) and deletes every Chroma hub
     * network whose hub stood in it, whoever owns it. Shared by the showroom and the block grid.
     */
    public static Cleared clearBox(ServerLevel level, BlockPos lo, BlockPos hi) {
        Set<Long> networks = new LinkedHashSet<>();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = lo.getX(); x <= hi.getX(); x++) {
            for (int z = lo.getZ(); z <= hi.getZ(); z++) {
                for (int y = lo.getY(); y <= hi.getY(); y++) {
                    p.set(x, y, z);
                    if (level.getBlockEntity(p) instanceof ChromaHubBlockEntity hub && hub.networkId() != 0L) {
                        networks.add(hub.networkId());
                    }
                }
            }
        }
        int removed = 0;
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = lo.getX(); x <= hi.getX(); x++) {
            for (int z = lo.getZ(); z <= hi.getZ(); z++) {
                for (int y = hi.getY(); y >= lo.getY(); y--) { // top down: attached blocks go before supports
                    p.set(x, y, z);
                    if (!level.getBlockState(p).isAir()) {
                        level.setBlock(p, air, REMOVE);
                        removed++;
                    }
                }
            }
        }
        int hubs = 0;
        for (long id : networks) {
            if (ChromaNetworks.store(level).remove(id) != null) {
                hubs++;
            }
        }
        return new Cleared(removed, hubs);
    }

    // --- block specs ------------------------------------------------------------------------------------------------

    /** Every registered block by full id (both vanilla and NeroDecor), read from the live registry. */
    static Map<String, Block> index() {
        Map<String, Block> out = new HashMap<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            Identifier id = BuiltInRegistries.BLOCK.getKey(block);
            String ns = id.getNamespace();
            if ("minecraft".equals(ns) || NeroDecorCommon.MOD_ID.equals(ns)) {
                out.put(ns + ":" + id.getPath(), block);
            }
        }
        return out;
    }

    /** A parsed spec: the state, and whether it is placed as an item would place it. */
    record Spec(BlockState state, boolean asItem) {
    }

    /** Parses {@code [!][ns:]path[k=v,...]}; returns null (and records why) when anything is unknown. */
    static @Nullable Spec parse(String spec, Map<String, Block> index, List<String> errors, String where) {
        String s = spec.trim();
        boolean asItem = s.startsWith("!");
        if (asItem) {
            s = s.substring(1);
        }
        String props = "";
        int bracket = s.indexOf('[');
        if (bracket >= 0) {
            if (!s.endsWith("]")) {
                errors.add(where + ": malformed spec " + spec);
                return null;
            }
            props = s.substring(bracket + 1, s.length() - 1);
            s = s.substring(0, bracket);
        }
        String id = s.contains(":") ? s : NeroDecorCommon.MOD_ID + ":" + s;
        Block block = index.get(id);
        if (block == null) {
            errors.add(where + ": unknown block " + id);
            return null;
        }
        BlockState state = block.defaultBlockState();
        if (!props.isEmpty()) {
            for (String kv : props.split(",")) {
                String[] parts = kv.split("=", 2);
                if (parts.length != 2) {
                    errors.add(where + ": malformed property " + kv + " in " + spec);
                    return null;
                }
                Property<?> property = block.getStateDefinition().getProperty(parts[0].trim());
                if (property == null) {
                    errors.add(where + ": " + id + " has no property " + parts[0]);
                    return null;
                }
                BlockState next = with(state, property, parts[1].trim());
                if (next == null) {
                    errors.add(where + ": " + id + " has no value " + parts[1] + " for " + parts[0]);
                    return null;
                }
                state = next;
            }
        }
        return new Spec(state, asItem);
    }

    @Nullable
    private static <T extends Comparable<T>> BlockState with(BlockState state, Property<T> property,
            String value) {
        Optional<T> parsed = property.getValue(value);
        return parsed.map(v -> state.setValue(property, v)).orElse(null);
    }

    static void sign(ServerLevel level, BlockPos pos, String first, String second, List<String> errors) {
        if (level.getBlockEntity(pos) instanceof HoloSignBlockEntity sign) {
            sign.setLines(first, second);
        } else {
            errors.add("sign at " + pos.toShortString() + ": no holo sign block entity");
        }
    }

    // --- placement ----------------------------------------------------------------------------------------------------

    /** Places blocks without neighbour updates, then resolves connection states once everything is down. */
    static final class Builder {
        private final ServerLevel level;
        private final Map<String, Block> index;
        private final List<String> errors;
        private final List<BlockPos> plain = new ArrayList<>();
        private final List<Object[]> deferred = new ArrayList<>();
        private int placed;

        Builder(ServerLevel level, Map<String, Block> index, List<String> errors) {
            this.level = level;
            this.index = index;
            this.errors = errors;
        }

        void put(BlockPos pos, String specText, String where) {
            Spec spec = parse(specText, index, errors, where);
            if (spec == null) {
                return;
            }
            if (spec.asItem()) {
                deferred.add(new Object[] {pos.immutable(), spec.state(), where});
                return;
            }
            level.setBlock(pos, spec.state(), PLACE);
            plain.add(pos.immutable());
            placed++;
        }

        /** Places a ready-made state (no spec parsing), resolved with the others in {@link #resolveAll}. */
        void put(BlockPos pos, BlockState state) {
            level.setBlock(pos, state, PLACE);
            plain.add(pos.immutable());
            placed++;
        }

        int placed() {
            return placed;
        }

        void resolveAll() {
            for (BlockPos pos : plain) {
                resolve(pos, "layout");
            }
        }

        void resolve(BlockPos pos, String where) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                return;
            }
            BlockState resolved = Block.updateFromNeighbourShapes(state, level, pos);
            if (resolved.isAir()) {
                errors.add(where + ": " + BuiltInRegistries.BLOCK.getKey(state.getBlock()) + " at "
                        + pos.toShortString() + " would not survive there");
            } else if (resolved != state) {
                level.setBlock(pos, resolved, PLACE);
            }
        }

        void placeDeferred(@Nullable ServerPlayer owner) {
            for (Object[] entry : deferred) {
                BlockPos pos = (BlockPos) entry[0];
                BlockState state = (BlockState) entry[1];
                level.setBlock(pos, state, Block.UPDATE_ALL);
                if (!level.getBlockState(pos).is(state.getBlock())) {
                    errors.add(entry[2] + ": " + BuiltInRegistries.BLOCK.getKey(state.getBlock()) + " at "
                            + pos.toShortString() + " did not place");
                    continue;
                }
                state.getBlock().setPlacedBy(level, pos, state, owner, new ItemStack(state.getBlock().asItem()));
                placed++;
            }
            deferred.clear();
        }
    }

    /** One bay being built; also the handle a {@link ShowroomHook} works through. */
    public static final class Bay {
        private final ServerLevel level;
        private final BlockPos origin;
        private final @Nullable ServerPlayer owner;
        private final int index;
        private final ShowroomScene scene;
        private final Builder builder;
        private final List<String> errors;
        private final List<String> notes;

        Bay(ServerLevel level, BlockPos origin, @Nullable ServerPlayer owner, int index, ShowroomScene scene,
            Builder builder, List<String> errors, List<String> notes) {
            this.level = level;
            this.origin = origin;
            this.owner = owner;
            this.index = index;
            this.scene = scene;
            this.builder = builder;
            this.errors = errors;
            this.notes = notes;
        }

        public ServerLevel level() {
            return level;
        }

        public @Nullable ServerPlayer owner() {
            return owner;
        }

        public BlockPos at(int x, int y, int z) {
            return origin.offset(x, y, z);
        }

        private String where(int x, int y, int z) {
            return String.format(Locale.ROOT, "%s (%d,%d,%d)", scene.id(), x, y, z);
        }

        public void error(String message) {
            errors.add(scene.id() + ": " + message);
        }

        public void note(String message) {
            notes.add(scene.id() + ": " + message);
        }

        void shell() {
            for (int x = -1; x <= BAY_W; x++) {
                boolean side = x == -1 || x == BAY_W;
                for (int z = 0; z <= BAY_D; z++) {
                    builder.put(at(x, -1, z), side ? SIDE_WALL : scene.floor(), where(x, -1, z));
                    builder.put(at(x, BAY_H, z), side ? SIDE_WALL : scene.ceiling(), where(x, BAY_H, z));
                    for (int y = 0; y < BAY_H; y++) {
                        if (side) {
                            builder.put(at(x, y, z), SIDE_WALL, where(x, y, z));
                        } else if (z == BAY_D) {
                            builder.put(at(x, y, z), scene.back(), where(x, y, z));
                        }
                    }
                }
            }
        }

        /** The bay's own wall lining in the interior columns x 0 and x {@code BAY_W - 1}. */
        void sides() {
            for (Map.Entry<Integer, List<String>> side : scene.sides().entrySet()) {
                int y = side.getKey();
                if (y < 0 || y >= BAY_H) {
                    error("side lining " + y + " is outside the bay");
                    continue;
                }
                for (int s = 0; s < 2; s++) {
                    String row = side.getValue().get(s);
                    int x = s == 0 ? 0 : BAY_W - 1;
                    if (row.length() != BAY_D) {
                        error("side lining " + y + (s == 0 ? " west" : " east") + " is " + row.length()
                                + " long, not " + BAY_D);
                        continue;
                    }
                    for (int z = 0; z < BAY_D; z++) {
                        char c = row.charAt(z);
                        if (c == '.' || c == ' ') {
                            continue;
                        }
                        if (x == 0 && z == 0 && (y == 0 || y == 1)) {
                            error("side lining " + y + " uses the label position (0," + y + ",0)");
                            continue;
                        }
                        String spec = scene.legend().get(c);
                        if (spec == null) {
                            error("side lining " + y + ": no legend entry for '" + c + "'");
                            continue;
                        }
                        builder.put(at(x, y, z), spec, where(x, y, z));
                    }
                }
            }
        }

        void layers() {
            for (Map.Entry<Integer, List<String>> layer : scene.layers().entrySet()) {
                int y = layer.getKey();
                List<String> rows = layer.getValue();
                if (y < -1 || y >= BAY_H || rows.size() > BAY_D) {
                    error("layer " + y + " is outside the bay (" + rows.size() + " rows)");
                    continue;
                }
                for (int z = 0; z < rows.size(); z++) {
                    String row = rows.get(z);
                    if (row.length() != BAY_W) {
                        error("layer " + y + " row " + z + " is " + row.length() + " wide, not " + BAY_W);
                        continue;
                    }
                    for (int x = 0; x < BAY_W; x++) {
                        char c = row.charAt(x);
                        if (c == '.' || c == ' ') {
                            continue;
                        }
                        if (x == 0 && z == 0 && (y == 0 || y == 1)) {
                            error("layer " + y + " uses the label position (0," + y + ",0)");
                            continue;
                        }
                        String spec = scene.legend().get(c);
                        if (spec == null) {
                            error("layer " + y + " row " + z + ": no legend entry for '" + c + "'");
                            continue;
                        }
                        builder.put(at(x, y, z), spec, where(x, y, z));
                    }
                }
            }
        }

        void label() {
            builder.put(at(0, 0, 0), "holo_plinth", where(0, 0, 0));
            builder.put(at(0, 1, 0), "holo_sign[facing=north]", where(0, 1, 0));
        }

        void afterPlacement() {
            Showroom.sign(level, at(0, 1, 0), (index + 1) + " " + scene.title(), scene.subtitle(), errors);
            if (scene.hook() != null) {
                scene.hook().apply(this);
            }
        }

        /** Places one block after the main pass (hooks), resolving it and its neighbours at once. */
        public void set(int x, int y, int z, String spec) {
            BlockPos pos = at(x, y, z);
            builder.put(pos, spec, where(x, y, z));
            builder.placeDeferred(owner);
            builder.resolve(pos, where(x, y, z));
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
                builder.resolve(pos.relative(d), where(x, y, z));
            }
        }

        public void sign(int x, int y, int z, String first, String second) {
            Showroom.sign(level, at(x, y, z), first, second, errors);
        }

        /** Opens the door at local {@code (x, y, z)} (any part of it). */
        public void openDoor(int x, int y, int z) {
            BlockPos pos = at(x, y, z);
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof AnimatedDoorBlock door) {
                door.toggle(level, pos, state, true);
            } else {
                error("no door at (" + x + "," + y + "," + z + ")");
            }
        }

        /** The Chroma network of the hub at local {@code (x, y, z)}, or 0. */
        public long hubNetwork(int x, int y, int z) {
            return level.getBlockEntity(at(x, y, z)) instanceof ChromaHubBlockEntity hub ? hub.networkId() : 0L;
        }
    }
}
