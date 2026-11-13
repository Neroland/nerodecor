package za.co.neroland.nerodecor.content.block.lab;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

import za.co.neroland.nerodecor.content.block.gantry.ModelShapes;
import za.co.neroland.nerodecor.content.block.gantry.ShapedBlock;

/**
 * A facing cabinet with an {@code open} toggle (fume_hood: sash raised; sample_fridge: twin doors swung out).
 * {@code facing} is the side the front faces (towards the placer). Using it toggles {@code open}. Swung doors render
 * outside the block, but the shapes stay inside it. Purely decorative: no inventory.
 */
public class CabinetBlock extends ShapedBlock {

    private final VoxelShape closedNorth;
    private final VoxelShape openNorth;
    private final SoundEvent openSound;
    private final SoundEvent closeSound;

    public CabinetBlock(Properties properties, VoxelShape closedNorth, VoxelShape openNorth, SoundEvent openSound,
                        SoundEvent closeSound) {
        super(properties);
        this.closedNorth = closedNorth;
        this.openNorth = openNorth;
        this.openSound = openSound;
        this.closeSound = closeSound;
        registerDefaultState(defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
                .setValue(BlockStateProperties.OPEN, false));
        bakeShapes();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.HORIZONTAL_FACING, BlockStateProperties.OPEN);
    }

    @Override
    protected VoxelShape shape(BlockState state) {
        VoxelShape north = state.getValue(BlockStateProperties.OPEN) ? openNorth : closedNorth;
        return ModelShapes.rotate(north, 0, ModelShapes.yRot(state.getValue(BlockStateProperties.HORIZONTAL_FACING)));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,
                context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!level.isClientSide()) {
            boolean open = !state.getValue(BlockStateProperties.OPEN);
            level.setBlock(pos, state.setValue(BlockStateProperties.OPEN, open), Block.UPDATE_ALL);
            level.playSound(null, pos, open ? openSound : closeSound, SoundSource.BLOCKS, 0.7F, 1.0F);
        }
        return InteractionResult.SUCCESS;
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
