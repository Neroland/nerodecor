package za.co.neroland.nerodecor.content.block.lights;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.VoxelShape;

import static za.co.neroland.nerodecor.content.block.lights.ModelShapes.b;

/**
 * Neon tube: a strip that also bends. {@code shape=straight} behaves as the light strip; {@code shape=bend} reuses
 * the link flags as leg signs: one leg leaves along {@code axis} toward + when {@code pos_link} (else -), the other
 * along the cross axis (the face's other in-plane axis) toward + when {@code neg_link} (else -).
 *
 * <p>The shape is chosen only from which in-plane neighbours are neon tubes on the same face (never from their
 * states), so updates settle in one pass: two opposite neighbours make a straight run, one neighbour on each axis
 * makes a bend toward them, a single neighbour links that end.
 */
public class NeonTubeBlock extends MountedStripBlock {

    public enum TubeShape implements StringRepresentable {
        STRAIGHT, BEND;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final EnumProperty<TubeShape> SHAPE = EnumProperty.create("shape", TubeShape.class);

    private static final double[][] X = {b(0, 0, 7, 16, 3, 9)};
    private static final double[][] Z = {b(7, 0, 0, 9, 3, 16)};

    public NeonTubeBlock(Properties properties) {
        super(properties, X, Z);
        registerDefaultState(defaultBlockState().setValue(SHAPE, TubeShape.STRAIGHT));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SHAPE);
    }

    @Override
    protected VoxelShape shapeOf(BlockState state) {
        if (state.getValue(SHAPE) == TubeShape.STRAIGHT) {
            return super.shapeOf(state);
        }
        Direction face = state.getValue(FACE);
        Direction.Axis axis = state.getValue(AXIS);
        if (!ModelShapes.inPlane(face, axis)) {
            return super.shapeOf(state);
        }
        Direction.Axis cross = ModelShapes.cross(face, axis);
        int sa = state.getValue(POS_LINK) ? 1 : -1;
        int sb = state.getValue(NEG_LINK) ? 1 : -1;
        int[] wantA = scale(axis, sa);
        int[] wantB = scale(cross, sb);
        int[] down = {0, -1, 0};
        int[] f = {face.getStepX(), face.getStepY(), face.getStepZ()};
        for (int rx = 0; rx < 360; rx += 90) {
            for (int ry = 0; ry < 360; ry += 90) {
                if (!eq(ModelShapes.rot(down, rx, ry), f)) {
                    continue;
                }
                for (int sx = -1; sx <= 1; sx += 2) {
                    for (int sz = -1; sz <= 1; sz += 2) {
                        int[] lx = ModelShapes.rot(new int[] {sx, 0, 0}, rx, ry);
                        int[] lz = ModelShapes.rot(new int[] {0, 0, sz}, rx, ry);
                        if ((eq(lx, wantA) && eq(lz, wantB)) || (eq(lx, wantB) && eq(lz, wantA))) {
                            double[] xLeg = sx > 0 ? b(7, 0, 7, 16, 3, 9) : b(0, 0, 7, 9, 3, 9);
                            double[] zLeg = sz > 0 ? b(7, 0, 7, 9, 3, 16) : b(7, 0, 0, 9, 3, 9);
                            return ModelShapes.boxes(new ModelShapes.Rot(rx, ry), xLeg, zLeg);
                        }
                    }
                }
            }
        }
        return super.shapeOf(state);
    }

    private static int[] scale(Direction.Axis a, int s) {
        return new int[] {a == Direction.Axis.X ? s : 0, a == Direction.Axis.Y ? s : 0, a == Direction.Axis.Z ? s : 0};
    }

    private static boolean eq(int[] a, int[] b) {
        return a[0] == b[0] && a[1] == b[1] && a[2] == b[2];
    }

    @Override
    protected BlockState links(BlockState state, BlockGetter level, BlockPos pos) {
        Direction face = state.getValue(FACE);
        List<Direction> offered = new ArrayList<>();
        for (Direction d : Direction.values()) {
            if (d.getAxis() != face.getAxis()) {
                BlockState n = level.getBlockState(pos.relative(d));
                if (n.is(this) && n.getValue(FACE) == face) {
                    offered.add(d);
                }
            }
        }
        Direction.Axis current = state.getValue(AXIS);
        if (!ModelShapes.inPlane(face, current)) {
            current = ModelShapes.cross(face, face.getAxis() == Direction.Axis.Y ? Direction.Axis.Z : Direction.Axis.Y);
        }
        Direction.Axis other = ModelShapes.cross(face, current);
        // A straight run through: both ends of one axis (prefer the current axis).
        for (Direction.Axis a : new Direction.Axis[] {current, other}) {
            if (offered.contains(Direction.fromAxisAndDirection(a, Direction.AxisDirection.POSITIVE))
                    && offered.contains(Direction.fromAxisAndDirection(a, Direction.AxisDirection.NEGATIVE))) {
                return straight(state, a, true, true);
            }
        }
        if (offered.size() == 2) {
            // One neighbour on each in-plane axis: a corner.
            Direction first = offered.get(0).getAxis() == current ? offered.get(0) : offered.get(1);
            Direction second = first == offered.get(0) ? offered.get(1) : offered.get(0);
            return state.setValue(SHAPE, TubeShape.BEND).setValue(AXIS, first.getAxis())
                    .setValue(POS_LINK, first.getAxisDirection() == Direction.AxisDirection.POSITIVE)
                    .setValue(NEG_LINK, second.getAxisDirection() == Direction.AxisDirection.POSITIVE);
        }
        if (offered.size() == 1 || offered.size() == 3) {
            // One neighbour (or a tee, where the lone end wins): link toward it.
            Direction d = offered.size() == 1 ? offered.get(0) : lone(offered);
            boolean plus = d.getAxisDirection() == Direction.AxisDirection.POSITIVE;
            return straight(state, d.getAxis(), plus, !plus);
        }
        return straight(state, current, false, false);
    }

    /** In a tee of three, the direction whose opposite is missing. */
    private static Direction lone(List<Direction> offered) {
        for (Direction d : offered) {
            if (!offered.contains(d.getOpposite())) {
                return d;
            }
        }
        return offered.get(0);
    }

    private BlockState straight(BlockState state, Direction.Axis axis, boolean pos, boolean neg) {
        return state.setValue(SHAPE, TubeShape.STRAIGHT).setValue(AXIS, axis).setValue(POS_LINK, pos)
                .setValue(NEG_LINK, neg);
    }
}
