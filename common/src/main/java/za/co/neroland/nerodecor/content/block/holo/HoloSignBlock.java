package za.co.neroland.nerodecor.content.block.holo;

import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

import za.co.neroland.nerolandcore.link.display.DisplaySurfaces;

import za.co.neroland.nerodecor.content.block.bulkhead.BhcBlock;
import za.co.neroland.nerodecor.content.block.bulkhead.BhcProperties;
import za.co.neroland.nerodecor.content.block.bulkhead.BhcShapes;

/**
 * Holo sign: an emitter rail with a floating panel ({@code facing} = the side the text front looks at). Using it with
 * an empty hand opens the two-line editor on the client; the edit travels as one validated, rate-limited payload.
 */
public class HoloSignBlock extends BhcBlock implements EntityBlock {

    /** Client hook that opens the editor; set by the client setup, a no-op on dedicated servers. */
    public static Consumer<HoloSignBlockEntity> editor = be -> { };

    private final Supplier<? extends BlockEntityType<HoloSignBlockEntity>> type;

    public HoloSignBlock(Properties properties, Supplier<? extends BlockEntityType<HoloSignBlockEntity>> type) {
        super(properties);
        this.type = type;
        registerDefaultState(stateDefinition.any().setValue(BhcProperties.FACING, Direction.SOUTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BhcProperties.FACING);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HoloSignBlockEntity(type.get(), pos, state);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(BhcProperties.FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide() && level.getBlockEntity(pos) instanceof HoloSignBlockEntity sign) {
            editor.accept(sign);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void onRemovedFromWorld(BlockState state, Level level, BlockPos pos) {
        DisplaySurfaces.remove(SurfaceBlockEntity.address(level, pos));
    }

    @Override
    protected VoxelShape computeShape(BlockState state) {
        return BhcShapes.horizontal(state.getValue(BhcProperties.FACING), BhcShapes.box(2, 0, 6, 14, 2, 10),
                BhcShapes.box(0, 5, 7.5, 16, 16, 8.5));
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
