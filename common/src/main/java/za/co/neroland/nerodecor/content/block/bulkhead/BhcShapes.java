package za.co.neroland.nerodecor.content.block.bulkhead;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Builds {@link VoxelShape}s from boxes authored in 1/16 block units in a canonical orientation, then turns them the
 * same way the blockstate turns the model.
 */
public final class BhcShapes {

    private BhcShapes() {
    }

    /** Boxes as {@code {x0, y0, z0, x1, y1, z1}} rows. */
    public static VoxelShape of(double[]... boxes) {
        VoxelShape out = Shapes.empty();
        for (double[] b : boxes) {
            out = Shapes.or(out, Block.box(b[0], b[1], b[2], b[3], b[4], b[5]));
        }
        return out;
    }

    public static double[] box(double x0, double y0, double z0, double x1, double y1, double z1) {
        return new double[] {x0, y0, z0, x1, y1, z1};
    }

    /** Boxes authored facing north, turned about y like a blockstate {@code "y"} rotation that faces {@code facing}. */
    public static VoxelShape horizontal(Direction facing, double[]... boxes) {
        int turns = switch (facing) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
        double[][] out = new double[boxes.length][];
        for (int i = 0; i < boxes.length; i++) {
            double[] b = boxes[i].clone();
            for (int t = 0; t < turns; t++) {
                // blockstate y=90: (x, z) -> (16 - z, x)
                b = new double[] {16 - b[5], b[1], b[0], 16 - b[2], b[4], b[3]};
            }
            out[i] = b;
        }
        return of(out);
    }

    /**
     * Boxes authored for a part sitting on the floor (mounted on the DOWN face), moved onto {@code face}: the side of
     * the block the part is attached to.
     */
    public static VoxelShape mounted(Direction face, double[]... boxes) {
        double[][] out = new double[boxes.length][];
        for (int i = 0; i < boxes.length; i++) {
            double[] b = boxes[i];
            double x0 = b[0], y0 = b[1], z0 = b[2], x1 = b[3], y1 = b[4], z1 = b[5];
            out[i] = switch (face) {
                case UP -> new double[] {x0, 16 - y1, z0, x1, 16 - y0, z1};
                case NORTH -> new double[] {x0, z0, y0, x1, z1, y1};
                case SOUTH -> new double[] {x0, z0, 16 - y1, x1, z1, 16 - y0};
                case WEST -> new double[] {y0, z0, x0, y1, z1, x1};
                case EAST -> new double[] {16 - y1, z0, x0, 16 - y0, z1, x1};
                default -> b;
            };
        }
        return of(out);
    }

    /** Boxes authored along the x axis, turned onto {@code axis} (y: x becomes y, z: x becomes z). */
    public static VoxelShape axis(Direction.Axis axis, double[]... boxes) {
        double[][] out = new double[boxes.length][];
        for (int i = 0; i < boxes.length; i++) {
            double[] b = boxes[i];
            out[i] = switch (axis) {
                case Y -> new double[] {b[1], b[0], b[2], b[4], b[3], b[5]};
                case Z -> new double[] {b[2], b[1], b[0], b[5], b[4], b[3]};
                default -> b;
            };
        }
        return of(out);
    }
}
