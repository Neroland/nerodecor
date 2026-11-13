package za.co.neroland.nerodecor.content.block.lab;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.VoxelShape;

import za.co.neroland.nerodecor.content.block.gantry.ModelShapes;
import za.co.neroland.nerodecor.content.block.gantry.ShapedBlock;

/**
 * Crew bunk: a two-block bed look ({@code facing} foot -> head, {@code part} head / foot, as vanilla beds) that is
 * cosmetic only: no sleeping, no spawn point, no night skip. Placing it puts the foot at the clicked block and the
 * head one block along the placer's view; removing either half removes the other, as a bed (so the loot table should
 * drop only from {@code part=head}, as the vanilla bed's does).
 */
public class CrewBunkBlock extends ShapedBlock {

    private static final VoxelShape HEAD_NORTH = ModelShapes.or(
            ModelShapes.box(0, 0, 0, 16, 15, 2),
            ModelShapes.box(0, 0, 2, 16, 9, 16),
            ModelShapes.box(0, 9, 2, 16, 15, 8));
    private static final VoxelShape FOOT_NORTH = ModelShapes.or(
            ModelShapes.box(0, 0, 0, 16, 9, 14),
            ModelShapes.box(0, 0, 14, 16, 10, 16));

    public CrewBunkBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
                .setValue(BlockStateProperties.BED_PART, BedPart.FOOT));
        bakeShapes();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.HORIZONTAL_FACING, BlockStateProperties.BED_PART);
    }

    @Override
    protected VoxelShape shape(BlockState state) {
        VoxelShape north = state.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD ? HEAD_NORTH : FOOT_NORTH;
        return ModelShapes.rotate(north, 0, ModelShapes.yRot(state.getValue(BlockStateProperties.HORIZONTAL_FACING)));
    }

    /** From this half towards the other one. */
    private static Direction toOther(BlockState state) {
        Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        return state.getValue(BlockStateProperties.BED_PART) == BedPart.FOOT ? facing : facing.getOpposite();
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection();
        BlockPos head = context.getClickedPos().relative(facing);
        Level level = context.getLevel();
        if (!level.getBlockState(head).canBeReplaced(context) || !level.getWorldBorder().isWithinBounds(head)) {
            return null;
        }
        return defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide()) {
            BlockPos head = pos.relative(state.getValue(BlockStateProperties.HORIZONTAL_FACING));
            level.setBlock(head, state.setValue(BlockStateProperties.BED_PART, BedPart.HEAD), Block.UPDATE_ALL);
        }
    }

    @Override
    protected BlockState onNeighbourShape(BlockState state, LevelReader level, BlockPos pos, Direction direction,
                                          BlockPos neighbourPos, BlockState neighbourState) {
        if (direction != toOther(state)) {
            return state;
        }
        boolean paired = neighbourState.is(this)
                && neighbourState.getValue(BlockStateProperties.BED_PART) != state.getValue(BlockStateProperties.BED_PART);
        return paired ? state : Blocks.AIR.defaultBlockState();
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // Creative: take the head down without a drop (vanilla bed behaviour).
        if (!level.isClientSide() && player.isCreative()
                && state.getValue(BlockStateProperties.BED_PART) == BedPart.FOOT) {
            BlockPos head = pos.relative(toOther(state));
            BlockState headState = level.getBlockState(head);
            if (headState.is(this) && headState.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD) {
                level.setBlock(head, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                level.levelEvent(player, 2001, head, Block.getId(headState));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(BlockStateProperties.HORIZONTAL_FACING,
                rotation.rotate(state.getValue(BlockStateProperties.HORIZONTAL_FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(BlockStateProperties.HORIZONTAL_FACING)));
    }
}
