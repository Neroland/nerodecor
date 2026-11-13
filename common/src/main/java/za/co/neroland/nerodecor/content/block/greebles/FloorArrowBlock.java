package za.co.neroland.nerodecor.content.block.greebles;

import java.util.Locale;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import za.co.neroland.nerodecor.content.block.lights.CollectionBlock;

/**
 * Floor arrow: a painted stencil on the floor pointing one of eight {@code facing} directions (the way the placer
 * was looking). Walk-through: no collision. Empty-hand use turns it 45 degrees clockwise (sneak: anticlockwise).
 */
public class FloorArrowBlock extends CollectionBlock {

    /** Eight compass points, clockwise from north (the module's {@code ARROW_DIRS}). */
    public enum Heading implements StringRepresentable {
        NORTH, NORTH_EAST, EAST, SOUTH_EAST, SOUTH, SOUTH_WEST, WEST, NORTH_WEST;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        Heading turn(int steps) {
            Heading[] all = values();
            return all[Math.floorMod(ordinal() + steps, all.length)];
        }
    }

    public static final EnumProperty<Heading> FACING = EnumProperty.create("facing", Heading.class);

    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 1, 16);

    public FloorArrowBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Heading.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected VoxelShape shapeOf(BlockState state) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Yaw 180 looks north; compass bearing = yaw + 180, in 45-degree steps.
        int step = Mth.floor((context.getRotation() + 180.0F) / 45.0F + 0.5F);
        return defaultBlockState().setValue(FACING, Heading.NORTH.turn(step));
    }

    @Override
    @Nullable
    protected BlockState cycled(BlockState state, Player player) {
        return state.setValue(FACING, state.getValue(FACING).turn(player.isShiftKeyDown() ? -1 : 1));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        int quarter = switch (rotation) {
            case CLOCKWISE_90 -> 2;
            case CLOCKWISE_180 -> 4;
            case COUNTERCLOCKWISE_90 -> 6;
            default -> 0;
        };
        return state.setValue(FACING, state.getValue(FACING).turn(quarter));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        Heading h = state.getValue(FACING);
        return switch (mirror) {
            // FRONT_BACK flips x (east <-> west): heading index i -> -i.
            case FRONT_BACK -> state.setValue(FACING, Heading.NORTH.turn(-h.ordinal()));
            // LEFT_RIGHT flips z (north <-> south): heading index i -> 4 - i.
            case LEFT_RIGHT -> state.setValue(FACING, Heading.NORTH.turn(4 - h.ordinal()));
            default -> state;
        };
    }
}
