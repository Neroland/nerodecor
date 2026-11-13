package za.co.neroland.nerodecor.content.block.lab;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

import za.co.neroland.nerodecor.content.block.gantry.ModelShapes;
import za.co.neroland.nerodecor.content.block.gantry.ShapedBlock;
import za.co.neroland.nerodecor.content.entity.SeatEntity;

/**
 * Lab stool: a padded seat on a chrome column. Using it empty-handed sits the player on a {@link SeatEntity} at
 * {@link #SEAT_Y} (the cushion top, 11px).
 */
public class LabStoolBlock extends ShapedBlock {

    /** Cushion top, as {@code SEAT_Y} in the collection module. */
    public static final double SEAT_Y = 11.0 / 16.0;

    private static final VoxelShape SHAPE = ModelShapes.or(
            ModelShapes.box(3, 8, 3, 13, 11, 13),
            ModelShapes.box(6, 0, 6, 10, 8, 10),
            ModelShapes.box(4, 0, 4, 12, 1, 12));

    public LabStoolBlock(Properties properties) {
        super(properties);
        bakeShapes();
    }

    @Override
    protected VoxelShape shape(BlockState state) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
        return SeatEntity.sit(level, pos, player, SEAT_Y) ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }
}
