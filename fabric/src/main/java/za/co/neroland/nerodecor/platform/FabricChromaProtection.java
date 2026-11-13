package za.co.neroland.nerodecor.platform;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

import za.co.neroland.nerodecor.chroma.server.ChromaProtection;

/**
 * Fabric Chroma protection: asks {@link PlayerBlockBreakEvents#BEFORE} (the hook Fabric claim mods use) whether the
 * player may break the block, without breaking it. A {@code false} answer refuses the Chroma edit.
 */
public final class FabricChromaProtection implements ChromaProtection {

    @Override
    public boolean allowsEdit(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
        return PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, player, pos, state,
                level.getBlockEntity(pos));
    }
}
