package za.co.neroland.nerodecor.content.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
//? if <1.21.6 {
/*import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
*///?}
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import za.co.neroland.nerodecor.registry.ChromaContent;

/**
 * The hub's block entity: holds only its network id (0 = inert). Everything else is in the dimension's
 * {@code ChromaNetworkData}. The id rides on the dropped hub item through {@link #collectImplicitComponents}.
 */
public class ChromaHubBlockEntity extends BlockEntity {

    private static final String NETWORK_KEY = "network";

    private long networkId;

    public ChromaHubBlockEntity(BlockPos pos, BlockState state) {
        super(ChromaContent.HUB_BLOCK_ENTITY.get(), pos, state);
    }

    public long networkId() {
        return networkId;
    }

    public void setNetworkId(long id) {
        this.networkId = id;
        setChanged();
    }

    //? if >=1.21.6 {
    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
    //?} else {
    /*@Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ValueOutput output = ValueOutput.of(tag, registries);
    *///?}
        output.putLong(NETWORK_KEY, networkId);
    }

    //? if >=1.21.6 {
    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
    //?} else {
    /*@Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ValueInput input = ValueInput.of(tag, registries);
    *///?}
        networkId = input.getLongOr(NETWORK_KEY, 0L);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (networkId != 0L) {
            components.set(ChromaContent.NETWORK_ID.get(), networkId);
        }
    }

    //? if >=1.21.5 {
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (this.level instanceof net.minecraft.server.level.ServerLevel server) {
            za.co.neroland.nerodecor.chroma.server.ChromaNetworks.hubRemoved(server, pos, networkId);
        }
        super.preRemoveSideEffects(pos, state);
    }
    //?}
}
