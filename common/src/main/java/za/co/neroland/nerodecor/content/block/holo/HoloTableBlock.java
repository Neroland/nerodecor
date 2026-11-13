package za.co.neroland.nerodecor.content.block.holo;

import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

import za.co.neroland.nerolandcore.link.display.DisplaySurfaces;

import za.co.neroland.nerodecor.content.block.bulkhead.BhcBlock;
import za.co.neroland.nerodecor.content.block.bulkhead.BhcShapes;

/** Holo table: a table with an emitter well; a terrain hologram plays above it and it accepts display payloads. */
public class HoloTableBlock extends BhcBlock implements EntityBlock {

    private final Supplier<? extends BlockEntityType<HoloTableBlockEntity>> type;

    public HoloTableBlock(Properties properties, Supplier<? extends BlockEntityType<HoloTableBlockEntity>> type) {
        super(properties);
        this.type = type;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HoloTableBlockEntity(type.get(), pos, state);
    }

    @Override
    protected void onRemovedFromWorld(BlockState state, Level level, BlockPos pos) {
        DisplaySurfaces.remove(SurfaceBlockEntity.address(level, pos));
    }

    @Override
    protected VoxelShape computeShape(BlockState state) {
        return BhcShapes.of(BhcShapes.box(2, 0, 2, 14, 10, 14), BhcShapes.box(0, 10, 0, 16, 15, 16));
    }
}
