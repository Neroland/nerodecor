package za.co.neroland.nerodecor.chroma.server;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The world-side "may this player modify this block" check every Chroma edit passes, after the network's own role
 * check: vanilla build permission ({@code mayBuild}: not adventure/spectator), {@code Level.mayInteract} (spawn
 * protection) and the world border, then the loader's protection hook ({@link ChromaProtection}) so claim mods are
 * respected. A protection hook that throws denies.
 */
public final class ChromaGuard {

    private ChromaGuard() {
    }

    public static boolean mayEdit(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
        if (player == null || !player.mayBuild() || !level.mayInteract(player, pos)
                || !level.getWorldBorder().isWithinBounds(pos)) {
            return false;
        }
        try {
            return ChromaProtection.get().allowsEdit(player, level, pos, state);
        } catch (RuntimeException failure) {
            return false;
        }
    }
}
