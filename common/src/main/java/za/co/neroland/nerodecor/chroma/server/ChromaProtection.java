package za.co.neroland.nerodecor.chroma.server;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

import za.co.neroland.nerodecor.platform.Services;

/**
 * Loader seam: would the loader (and the claim/protection mods that listen to it) let {@code player} modify the block?
 * Each loader asks its own block-break permission hook, without breaking anything:
 * NeoForge posts {@code BlockEvent.BreakEvent}, Forge posts {@code BlockEvent.BreakEvent}, Fabric invokes
 * {@code PlayerBlockBreakEvents.BEFORE}. A cancelled/false answer refuses the Chroma edit.
 * The vanilla checks (game mode, spawn protection, world border) are applied first by {@link ChromaGuard}.
 */
public interface ChromaProtection {

    boolean allowsEdit(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state);

    /** The loader implementation, loaded on first use. */
    static ChromaProtection get() {
        return Holder.INSTANCE;
    }

    /** Lazy holder so pure-Java code never triggers the ServiceLoader. */
    final class Holder {
        private static final ChromaProtection INSTANCE = Services.load(ChromaProtection.class);

        private Holder() {
        }
    }
}
