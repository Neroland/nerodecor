package za.co.neroland.nerodecor.content.block.gantry;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * VoxelShape helpers that mirror how the collection blockstates turn a model: models are drawn facing NORTH (or along
 * +Y / X for axis blocks) and turned by the blockstate's {@code x} then {@code y} rotation, so the shapes here go
 * through the same transform. All coordinates in pixels (0..16).
 */
public final class ModelShapes {

    private ModelShapes() {
    }

    /** A box in pixels. */
    public static VoxelShape box(double x1, double y1, double z1, double x2, double y2, double z2) {
        return Shapes.box(x1 / 16.0, y1 / 16.0, z1 / 16.0, x2 / 16.0, y2 / 16.0, z2 / 16.0);
    }

    /** Union of shapes. */
    public static VoxelShape or(VoxelShape first, VoxelShape... rest) {
        VoxelShape out = first;
        for (VoxelShape s : rest) {
            out = Shapes.or(out, s);
        }
        return out;
    }

    /**
     * Applies a blockstate rotation ({@code x} then {@code y}, multiples of 90 degrees) to a shape drawn in model
     * space: {@code x=90} takes north to down, {@code y=90} takes north to east.
     */
    public static VoxelShape rotate(VoxelShape shape, int xRot, int yRot) {
        int xs = Math.floorMod(xRot / 90, 4);
        int ys = Math.floorMod(yRot / 90, 4);
        if (xs == 0 && ys == 0) {
            return shape;
        }
        VoxelShape[] out = {Shapes.empty()};
        shape.forAllBoxes((x1, y1, z1, x2, y2, z2) -> {
            double ax = x1, ay = y1, az = z1, bx = x2, by = y2, bz = z2;
            for (int i = 0; i < xs; i++) {          // (y, z) -> (z, 1 - y)
                double ny1 = az, ny2 = bz, nz1 = 1 - by, nz2 = 1 - ay;
                ay = ny1; by = ny2; az = nz1; bz = nz2;
            }
            for (int i = 0; i < ys; i++) {          // (x, z) -> (1 - z, x)
                double nx1 = 1 - bz, nx2 = 1 - az, nz1 = ax, nz2 = bx;
                ax = nx1; bx = nx2; az = nz1; bz = nz2;
            }
            out[0] = Shapes.or(out[0], Shapes.box(ax, ay, az, bx, by, bz));
        });
        return out[0];
    }

    /** The blockstate {@code y} rotation for a horizontal facing of a north-drawn model. */
    public static int yRot(Direction facing) {
        return switch (facing) {
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> 0;
        };
    }

    /** A north-drawn shape turned to a six-way facing (as {@code _kit.facing_variants}: up x=270, down x=90). */
    public static VoxelShape facing(VoxelShape north, Direction facing) {
        return switch (facing) {
            case UP -> rotate(north, 270, 0);
            case DOWN -> rotate(north, 90, 0);
            default -> rotate(north, 0, yRot(facing));
        };
    }

    /**
     * A box that spans {@code [from, to]} along {@code axis} and {@code [min, max]} on the two other axes (pixels).
     */
    public static VoxelShape along(Direction.Axis axis, double from, double to, double min, double max) {
        return switch (axis) {
            case X -> box(from, min, min, to, max, max);
            case Y -> box(min, from, min, max, to, max);
            case Z -> box(min, min, from, max, max, to);
        };
    }

    /** The {@code north} / {@code east} / ... / {@code up} / {@code down} boolean for a side. */
    public static BooleanProperty side(Direction d) {
        return switch (d) {
            case NORTH -> BlockStateProperties.NORTH;
            case EAST -> BlockStateProperties.EAST;
            case SOUTH -> BlockStateProperties.SOUTH;
            case WEST -> BlockStateProperties.WEST;
            case UP -> BlockStateProperties.UP;
            case DOWN -> BlockStateProperties.DOWN;
        };
    }

    /** Turns the four horizontal side booleans with a rotation. */
    public static BlockState rotateSides(BlockState state, Rotation rotation) {
        BlockState out = state;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            out = out.setValue(side(rotation.rotate(d)), state.getValue(side(d)));
        }
        return out;
    }

    /** Mirrors the four horizontal side booleans. */
    public static BlockState mirrorSides(BlockState state, Mirror mirror) {
        BlockState out = state;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            out = out.setValue(side(mirror.mirror(d)), state.getValue(side(d)));
        }
        return out;
    }
}
