package za.co.neroland.nerodecor.content.block.lab;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.VoxelShape;

import za.co.neroland.nerodecor.content.block.gantry.AxisLinkBlock;
import za.co.neroland.nerodecor.content.block.gantry.ModelShapes;
import za.co.neroland.nerodecor.content.block.gantry.ShapedBlock;

/**
 * Specimen tank: a vertical run ({@code pos_link} = same tank above, {@code neg_link} = below) of octagonal glass
 * around a fluid column the Chroma hub tints (model tintindex 0; listed in {@code #nerodecor:chroma_capable}). Caps
 * only on unlinked ends. Cutout, a soft light source.
 */
public class SpecimenTankBlock extends ShapedBlock {

    private static final VoxelShape COLUMN = ModelShapes.box(2, 0, 2, 14, 16, 14);
    private static final VoxelShape BASE = ModelShapes.box(1, 0, 1, 15, 2, 15);
    private static final VoxelShape LID = ModelShapes.box(1, 14, 1, 15, 16, 15);

    public SpecimenTankBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(AxisLinkBlock.POS_LINK, false)
                .setValue(AxisLinkBlock.NEG_LINK, false));
        bakeShapes();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AxisLinkBlock.POS_LINK, AxisLinkBlock.NEG_LINK);
    }

    @Override
    protected VoxelShape shape(BlockState state) {
        VoxelShape s = COLUMN;
        if (!state.getValue(AxisLinkBlock.NEG_LINK)) {
            s = ModelShapes.or(s, BASE);
        }
        if (!state.getValue(AxisLinkBlock.POS_LINK)) {
            s = ModelShapes.or(s, LID);
        }
        return s;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        return defaultBlockState()
                .setValue(AxisLinkBlock.POS_LINK, context.getLevel().getBlockState(pos.above()).is(this))
                .setValue(AxisLinkBlock.NEG_LINK, context.getLevel().getBlockState(pos.below()).is(this));
    }

    @Override
    protected BlockState onNeighbourShape(BlockState state, LevelReader level, BlockPos pos, Direction direction,
                                          BlockPos neighbourPos, BlockState neighbourState) {
        if (direction.getAxis() != Direction.Axis.Y) {
            return state;
        }
        return state.setValue(direction == Direction.UP ? AxisLinkBlock.POS_LINK : AxisLinkBlock.NEG_LINK,
                neighbourState.is(this));
    }
}
