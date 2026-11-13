package za.co.neroland.nerodecor.content.block.bulkhead;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Blast door: a 3x3 multiblock placed and broken as one. {@code part} = row * 3 + col, row 0 at the top, col 0 at the
 * viewer's left when looking at the front. The item places at the bottom centre (part 7). Only the centre (part 4)
 * holds the block entity; its renderer draws the two sliding panels across the whole opening.
 */
public class BlastDoorBlock extends AnimatedDoorBlock {

    public static final int CENTRE = 4;
    public static final int DURATION = 30;

    public BlastDoorBlock(Properties properties, Supplier<? extends BlockEntityType<DoorAnimBlockEntity>> type) {
        super(properties, type, DURATION, SoundEvents.PISTON_EXTEND, SoundEvents.PISTON_CONTRACT);
        registerDefaultState(stateDefinition.any().setValue(BhcProperties.FACING, Direction.NORTH)
                .setValue(BhcProperties.PART, CENTRE).setValue(OPEN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BhcProperties.FACING, BhcProperties.PART, OPEN);
    }

    /** Position of tile {@code part} for a door whose centre is {@code centre}. */
    public static BlockPos partPos(BlockPos centre, Direction facing, int part) {
        int row = part / 3;
        int col = part % 3;
        return centre.above(1 - row).relative(BhcProperties.leftOf(facing), 1 - col);
    }

    public static BlockPos centreOf(BlockPos pos, BlockState state) {
        int part = state.getValue(BhcProperties.PART);
        int row = part / 3;
        int col = part % 3;
        return pos.below(1 - row).relative(BhcProperties.leftOf(state.getValue(BhcProperties.FACING)), col - 1);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos centre = context.getClickedPos().above();
        for (int part = 0; part < 9; part++) {
            BlockPos p = partPos(centre, facing, part);
            if (level.isOutsideBuildHeight(p) || !level.getBlockState(p).canBeReplaced(context)) {
                return null;
            }
        }
        return defaultBlockState().setValue(BhcProperties.FACING, facing).setValue(BhcProperties.PART, 7);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        super.setPlacedBy(level, pos, state, by, stack);
        BlockPos centre = centreOf(pos, state);
        Direction facing = state.getValue(BhcProperties.FACING);
        for (int part = 0; part < 9; part++) {
            if (part != 7) {
                level.setBlock(partPos(centre, facing, part), state.setValue(BhcProperties.PART, part), Block.UPDATE_ALL);
            }
        }
    }

    @Override
    protected void onRemovedFromWorld(BlockState state, Level level, BlockPos pos) {
        BlockPos centre = centreOf(pos, state);
        Direction facing = state.getValue(BhcProperties.FACING);
        for (int part = 0; part < 9; part++) {
            BlockPos p = partPos(centre, facing, part);
            BlockState s = level.getBlockState(p);
            if (s.is(this) && s.getValue(BhcProperties.PART) == part && s.getValue(BhcProperties.FACING) == facing) {
                clearSilently(level, p, this);
            }
        }
    }

    @Override
    protected boolean hasEntity(BlockState state) {
        return state.getValue(BhcProperties.PART) == CENTRE;
    }

    @Override
    protected List<BlockPos> members(BlockGetter level, BlockPos pos, BlockState state) {
        BlockPos centre = centreOf(pos, state);
        Direction facing = state.getValue(BhcProperties.FACING);
        List<BlockPos> out = new ArrayList<>(9);
        for (int part = 0; part < 9; part++) {
            out.add(partPos(centre, facing, part));
        }
        return out;
    }

    @Override
    protected BlockPos controller(BlockPos pos, BlockState state) {
        return centreOf(pos, state);
    }

    @Override
    protected VoxelShape computeShape(BlockState state) {
        int part = state.getValue(BhcProperties.PART);
        Direction facing = state.getValue(BhcProperties.FACING);
        if (!state.getValue(OPEN)) {
            return BhcShapes.horizontal(facing, BhcShapes.box(0, 0, 4, 16, 16, 12));
        }
        int r = part / 3;
        int c = part % 3;
        double y0 = r == 2 ? 3 : 0;
        double y1 = r == 0 ? 13 : 16;
        List<double[]> bars = new ArrayList<>();
        if (r == 0) {
            bars.add(BhcShapes.box(0, 13, 4, 16, 16, 12));
        }
        if (r == 2) {
            bars.add(BhcShapes.box(0, 0, 4, 16, 3, 12));
        }
        if (c == 0) {
            bars.add(BhcShapes.box(13, y0, 4, 16, y1, 12));
        }
        if (c == 2) {
            bars.add(BhcShapes.box(0, y0, 4, 3, y1, 12));
        }
        return bars.isEmpty() ? Shapes.empty() : BhcShapes.horizontal(facing, bars.toArray(new double[0][]));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(BhcProperties.FACING, rotation.rotate(state.getValue(BhcProperties.FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(BhcProperties.FACING)));
    }
}
