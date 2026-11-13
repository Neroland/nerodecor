package za.co.neroland.nerodecor.content.block.holo;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

import za.co.neroland.nerodecor.content.block.bulkhead.BhcBlock;
import za.co.neroland.nerodecor.content.block.bulkhead.BhcProperties;
import za.co.neroland.nerodecor.content.block.bulkhead.BhcShapes;

/** Arrow sign: floating chevrons in eight directions; use with an empty hand to turn the arrow clockwise. */
public class ArrowSignBlock extends BhcBlock {

    public static final EnumProperty<ArrowDirection> DIRECTION = EnumProperty.create("direction", ArrowDirection.class);

    public ArrowSignBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(BhcProperties.FACING, Direction.SOUTH)
                .setValue(DIRECTION, ArrowDirection.RIGHT));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BhcProperties.FACING, DIRECTION);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(BhcProperties.FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!level.isClientSide()) {
            level.setBlock(pos, state.setValue(DIRECTION, state.getValue(DIRECTION).next()), Block.UPDATE_ALL);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected VoxelShape computeShape(BlockState state) {
        return BhcShapes.horizontal(state.getValue(BhcProperties.FACING), BhcShapes.box(6, 0, 6, 10, 1, 10),
                BhcShapes.box(0, 1, 7.5, 16, 16, 8.5));
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
