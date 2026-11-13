package za.co.neroland.nerodecor.content.block.lights;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Function;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Outline shapes for the collection blocks, derived from the same canonical boxes and the same blockstate
 * rotations the art modules under {@code tools/collections/} use, so the hit box always sits where the model is.
 * Boxes are {@code {x0, y0, z0, x1, y1, z1}} in model pixels (0..16).
 */
public final class ModelShapes {

    /** Model rotation (blockstate {@code x}, {@code y}) in degrees. */
    public record Rot(int x, int y) {
    }

    /** A strip variant: which canonical model ({@code z} = the {@code _z} one) and its rotation. */
    public record StripRot(boolean z, Rot rot) {
    }

    private static final int[] STEPS = {0, 90, 180, 270};

    private ModelShapes() {
    }

    /** Vanilla blockstate rotation of a vector, x first then y (x=90 maps up to north, y=90 north to east). */
    public static int[] rot(int[] v, int rx, int ry) {
        int x = v[0];
        int y = v[1];
        int z = v[2];
        for (int i = 0; i < rx / 90; i++) {
            int t = y;
            y = z;
            z = -t;
        }
        for (int i = 0; i < ry / 90; i++) {
            int t = x;
            x = -z;
            z = t;
        }
        return new int[] {x, y, z};
    }

    private static double[] rotPoint(double x, double y, double z, int rx, int ry) {
        for (int i = 0; i < rx / 90; i++) {
            double t = y;
            y = z;
            z = -t;
        }
        for (int i = 0; i < ry / 90; i++) {
            double t = x;
            x = -z;
            z = t;
        }
        return new double[] {x, y, z};
    }

    private static int[] vec(Direction d) {
        return new int[] {d.getStepX(), d.getStepY(), d.getStepZ()};
    }

    private static int[] vec(Direction.Axis a) {
        return new int[] {a == Direction.Axis.X ? 1 : 0, a == Direction.Axis.Y ? 1 : 0, a == Direction.Axis.Z ? 1 : 0};
    }

    private static boolean same(int[] a, int[] b) {
        return a[0] == b[0] && a[1] == b[1] && a[2] == b[2];
    }

    private static boolean negated(int[] a, int[] b) {
        return a[0] == -b[0] && a[1] == -b[1] && a[2] == -b[2];
    }

    /** One box rotated about the block centre. */
    public static VoxelShape box(double[] b, Rot r) {
        double[] p = rotPoint(b[0] - 8, b[1] - 8, b[2] - 8, r.x(), r.y());
        double[] q = rotPoint(b[3] - 8, b[4] - 8, b[5] - 8, r.x(), r.y());
        return Block.box(Math.min(p[0], q[0]) + 8, Math.min(p[1], q[1]) + 8, Math.min(p[2], q[2]) + 8,
                Math.max(p[0], q[0]) + 8, Math.max(p[1], q[1]) + 8, Math.max(p[2], q[2]) + 8);
    }

    /** The union of several boxes, rotated. */
    public static VoxelShape boxes(Rot r, double[]... boxes) {
        VoxelShape out = Shapes.empty();
        for (double[] b : boxes) {
            out = Shapes.or(out, box(b, r));
        }
        return out;
    }

    public static double[] b(double x0, double y0, double z0, double x1, double y1, double z1) {
        return new double[] {x0, y0, z0, x1, y1, z1};
    }

    /** {@code face_rotation}: the rotation taking a model mounted on DOWN to {@code face}. */
    public static Rot fromDown(Direction face) {
        int[] want = vec(face);
        for (int rx : STEPS) {
            for (int ry : STEPS) {
                if (same(rot(vec(Direction.DOWN), rx, ry), want)) {
                    return new Rot(rx, ry);
                }
            }
        }
        throw new IllegalArgumentException(face.toString());
    }

    /** The greebles {@code FACE_ROT} table: a plate drawn mounted on NORTH, turned to {@code face}. */
    public static Rot fromNorth(Direction face) {
        return switch (face) {
            case NORTH -> new Rot(0, 0);
            case EAST -> new Rot(0, 90);
            case SOUTH -> new Rot(0, 180);
            case WEST -> new Rot(0, 270);
            case DOWN -> new Rot(90, 0);
            case UP -> new Rot(270, 0);
        };
    }

    /** y rotation of a model drawn facing north. */
    public static Rot horizontal(Direction facing) {
        return switch (facing) {
            case EAST -> new Rot(0, 90);
            case SOUTH -> new Rot(0, 180);
            case WEST -> new Rot(0, 270);
            default -> new Rot(0, 0);
        };
    }

    /** {@code _kit.strip_rotation}: canonical strip (on DOWN, along x or z) to ({@code face}, {@code axis}). */
    public static StripRot strip(Direction face, Direction.Axis axis) {
        int[] wantF = vec(face);
        int[] wantA = vec(axis);
        for (boolean z : new boolean[] {false, true}) {
            int[] run0 = z ? new int[] {0, 0, 1} : new int[] {1, 0, 0};
            for (int rx : STEPS) {
                for (int ry : STEPS) {
                    if (!same(rot(vec(Direction.DOWN), rx, ry), wantF)) {
                        continue;
                    }
                    int[] run = rot(run0, rx, ry);
                    if (same(run, wantA) || negated(run, wantA)) {
                        return new StripRot(z, new Rot(rx, ry));
                    }
                }
            }
        }
        throw new IllegalArgumentException(face + "/" + axis);
    }

    /** Whether {@code axis} lies in the plane of {@code face} (a valid strip run). */
    public static boolean inPlane(Direction face, Direction.Axis axis) {
        return face.getAxis() != axis;
    }

    /** The in-plane axis of {@code face} that is not {@code axis}. */
    public static Direction.Axis cross(Direction face, Direction.Axis axis) {
        for (Direction.Axis a : Direction.Axis.values()) {
            if (a != axis && a != face.getAxis()) {
                return a;
            }
        }
        throw new IllegalArgumentException(face + "/" + axis);
    }

    /** Precomputes one shape per state (identity map, read-only after construction). */
    public static Map<BlockState, VoxelShape> cache(Iterable<BlockState> states, Function<BlockState, VoxelShape> f) {
        Map<BlockState, VoxelShape> out = new IdentityHashMap<>();
        for (BlockState s : states) {
            out.put(s, f.apply(s));
        }
        return out;
    }
}
