package za.co.neroland.nerodecor.content.block.lab;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import za.co.neroland.nerodecor.content.block.gantry.AxisLinkBlock;
import za.co.neroland.nerodecor.content.block.gantry.ModelShapes;
import za.co.neroland.nerodecor.content.block.gantry.ShapedBlock;

/**
 * Locker: {@code facing}, {@code open} and a vertical run ({@code pos_link} = same locker, same facing, above;
 * {@code neg_link} = below), so a stack reads as one tall pair of doors. Using any piece opens or closes the whole
 * stack. Open doors swing 7px out of the block; the shapes stay inside it. Purely decorative: no inventory.
 */
public class LockerBlock extends ShapedBlock {

    private static final int MAX_STACK = 16;
    private static final VoxelShape OPEN_NORTH = ModelShapes.box(0, 0, 1, 16, 16, 16);

    public LockerBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
                .setValue(BlockStateProperties.OPEN, false)
                .setValue(AxisLinkBlock.POS_LINK, false).setValue(AxisLinkBlock.NEG_LINK, false));
        bakeShapes();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.HORIZONTAL_FACING, BlockStateProperties.OPEN, AxisLinkBlock.POS_LINK,
                AxisLinkBlock.NEG_LINK);
    }

    @Override
    protected VoxelShape shape(BlockState state) {
        if (!state.getValue(BlockStateProperties.OPEN)) {
            return Shapes.block();
        }
        return ModelShapes.rotate(OPEN_NORTH, 0, ModelShapes.yRot(state.getValue(BlockStateProperties.HORIZONTAL_FACING)));
    }

    private boolean linksTo(BlockState other, Direction facing) {
        return other.is(this) && other.getValue(BlockStateProperties.HORIZONTAL_FACING) == facing;
    }

    private BlockState withLinks(BlockState state, BlockGetter level, BlockPos pos) {
        Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        return state.setValue(AxisLinkBlock.POS_LINK, linksTo(level.getBlockState(pos.above()), facing))
                .setValue(AxisLinkBlock.NEG_LINK, linksTo(level.getBlockState(pos.below()), facing));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,
                context.getHorizontalDirection().getOpposite());
        return withLinks(state, context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState onNeighbourShape(BlockState state, LevelReader level, BlockPos pos, Direction direction,
                                          BlockPos neighbourPos, BlockState neighbourState) {
        if (direction.getAxis() != Direction.Axis.Y) {
            return state;
        }
        Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        return state.setValue(direction == Direction.UP ? AxisLinkBlock.POS_LINK : AxisLinkBlock.NEG_LINK,
                linksTo(neighbourState, facing));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!level.isClientSide()) {
            boolean open = !state.getValue(BlockStateProperties.OPEN);
            setOpen(level, pos, state, open);
            BlockPos at = pos;
            BlockState cur = state;
            for (int i = 0; i < MAX_STACK && cur.getValue(AxisLinkBlock.POS_LINK); i++) {
                at = at.above();
                cur = level.getBlockState(at);
                if (!cur.is(this)) {
                    break;
                }
                setOpen(level, at, cur, open);
            }
            at = pos;
            cur = state;
            for (int i = 0; i < MAX_STACK && cur.getValue(AxisLinkBlock.NEG_LINK); i++) {
                at = at.below();
                cur = level.getBlockState(at);
                if (!cur.is(this)) {
                    break;
                }
                setOpen(level, at, cur, open);
            }
            level.playSound(null, pos, open ? SoundEvents.IRON_TRAPDOOR_OPEN : SoundEvents.IRON_TRAPDOOR_CLOSE,
                    SoundSource.BLOCKS, 0.7F, 1.1F);
        }
        return InteractionResult.SUCCESS;
    }

    private static void setOpen(Level level, BlockPos pos, BlockState state, boolean open) {
        level.setBlock(pos, state.setValue(BlockStateProperties.OPEN, open), Block.UPDATE_ALL);
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
