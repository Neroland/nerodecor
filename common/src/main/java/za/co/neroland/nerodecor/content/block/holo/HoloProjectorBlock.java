package za.co.neroland.nerodecor.content.block.holo;

import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import za.co.neroland.nerodecor.content.block.bulkhead.BhcShapes;
import za.co.neroland.nerodecor.content.block.bulkhead.MountedBlock;

/** Holo projector: a puck on any face that projects a light cone and a turning wireframe gem; use toggles it. */
public class HoloProjectorBlock extends MountedBlock implements EntityBlock {

    private final Supplier<? extends BlockEntityType<HoloProjectorBlockEntity>> type;

    public HoloProjectorBlock(Properties properties, Supplier<? extends BlockEntityType<HoloProjectorBlockEntity>> type) {
        super(properties, BhcShapes.box(2, 0, 2, 14, 6, 14));
        this.type = type;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HoloProjectorBlockEntity(type.get(), pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof HoloProjectorBlockEntity be) {
            be.toggle();
        }
        return InteractionResult.SUCCESS;
    }
}
