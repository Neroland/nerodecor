package za.co.neroland.nerodecor.content.block.holo;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import za.co.neroland.nerodecor.content.block.bulkhead.SyncedBlockEntity;

/** World globe: stores nothing (the world is the {@code world} blockstate property); exists for its renderer. */
public class WorldGlobeBlockEntity extends SyncedBlockEntity {

    public WorldGlobeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    protected void write(ValueOutput output) {
    }

    @Override
    protected void read(ValueInput input) {
    }
}
