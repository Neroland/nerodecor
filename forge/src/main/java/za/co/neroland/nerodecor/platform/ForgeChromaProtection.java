package za.co.neroland.nerodecor.platform;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
//? if <1.21.6 {
/*import net.minecraftforge.common.MinecraftForge;
*///?}
import net.minecraftforge.event.level.BlockEvent;

import za.co.neroland.nerodecor.chroma.server.ChromaProtection;

/**
 * Forge Chroma protection: posts a {@link BlockEvent.BreakEvent} for the block as a permission probe (nothing is
 * broken). Claim/protection mods cancel it for blocks the player may not modify, which refuses the Chroma edit.
 */
public final class ForgeChromaProtection implements ChromaProtection {

    @Override
    public boolean allowsEdit(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
        //? if >=1.21.6 {
        BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(level, pos, state, player,
                net.minecraftforge.common.util.Result.DEFAULT);
        return !BlockEvent.BreakEvent.BUS.post(event) && !event.getResult().isDenied();
        //?} else {
        /*return !MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, state, player));
        *///?}
    }
}
