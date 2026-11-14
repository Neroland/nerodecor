package za.co.neroland.nerodecor.content.block.command;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

import za.co.neroland.nerodecor.content.block.bulkhead.BhcBlock;
import za.co.neroland.nerodecor.content.block.bulkhead.BhcShapes;
import za.co.neroland.nerodecor.content.entity.SeatEntity;

/**
 * Operator chair: eight-way {@code facing} (it turns to face whoever placed it). Using it empty-handed sits the player
 * on a {@link SeatEntity} at {@link #SEAT_Y} (the cushion top).
 */
public class OperatorChairBlock extends BhcBlock {

    /** Cushion top (9.5px), from the operator_chair model in the command collection module. */
    public static final double SEAT_Y = 9.5 / 16.0;

    public static final EnumProperty<ChairFacing> FACING = EnumProperty.create("facing", ChairFacing.class);

    public OperatorChairBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, ChairFacing.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Player yaw: south = 0, west = 90; the chair's bearing (north = 0) facing back at the player equals the yaw.
        return defaultBlockState().setValue(FACING, ChairFacing.fromBearing(context.getRotation()));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
        return SeatEntity.sit(level, pos, player, SEAT_Y) ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    @Override
    protected VoxelShape computeShape(BlockState state) {
        return BhcShapes.of(BhcShapes.box(2, 0, 2, 14, 16, 14));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        int turns = switch (rotation) {
            case CLOCKWISE_90 -> 1;
            case CLOCKWISE_180 -> 2;
            case COUNTERCLOCKWISE_90 -> 3;
            default -> 0;
        };
        return state.setValue(FACING, state.getValue(FACING).rotate(turns));
    }
}
