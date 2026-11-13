package za.co.neroland.nerodecor.platform;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
//? if >=26.1 {
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
//?} else {
/*import net.neoforged.neoforge.event.level.BlockEvent;
*///?}

import za.co.neroland.nerodecor.chroma.server.ChromaProtection;

/**
 * NeoForge Chroma protection: posts the block-break event ({@code BreakBlockEvent} on 26.x,
 * {@code BlockEvent.BreakEvent} before) for the block as a permission probe (nothing is broken). Claim/protection
 * mods cancel it for blocks the player may not modify, which refuses the Chroma edit.
 */
public final class NeoForgeChromaProtection implements ChromaProtection {

    @Override
    public boolean allowsEdit(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
        //? if >=26.1 {
        return !NeoForge.EVENT_BUS.post(new BreakBlockEvent(level, pos, state, player)).isCanceled();
        //?} else {
        /*return !NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, state, player)).isCanceled();
        *///?}
    }
}
