package za.co.neroland.nerodecor.content.block.holo.net;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

import za.co.neroland.nerodecor.chroma.net.ChromaNet;
import za.co.neroland.nerodecor.chroma.server.ChromaGuard;
import za.co.neroland.nerodecor.content.block.holo.HoloSignBlockEntity;

/**
 * Holo sign editing over the network: {@link HoloSignEditPayload} rides NeroDecor's one packet table ({@link ChromaNet},
 * wired per loader through {@code ChromaNetPlatform}); this class sends it and does the server-side validation
 * (reach, loaded chunk, block-edit permission incl. claim mods via {@code ChromaGuard}, and a per-player rate limit).
 * The rate limiter keeps only the online player object as a weak key and a tick count in memory; nothing is logged
 * or persisted.
 */
public final class HoloNet {

    /** Minimum game ticks between two edits by one player. */
    static final int COOLDOWN_TICKS = 10;
    private static final double REACH_SQ = 8.0 * 8.0;
    private static final Map<ServerPlayer, Long> LAST_EDIT = new WeakHashMap<>();

    private HoloNet() {
    }

    /** Client: send a sign edit. */
    public static void sendEdit(BlockPos pos, String first, String second) {
        ChromaNet.sendToServer(new HoloSignEditPayload(pos, first, second));
    }

    /** Server: validate and apply one edit (registered in {@link ChromaNet}'s serverbound table). */
    public static void handleEdit(HoloSignEditPayload payload, ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = payload.pos();
        if (!level.isLoaded(pos) || player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > REACH_SQ) {
            return;
        }
        long now = level.getGameTime();
        synchronized (LAST_EDIT) {
            Long last = LAST_EDIT.get(player);
            if (last != null && now - last < COOLDOWN_TICKS && now >= last) {
                return;
            }
            LAST_EDIT.put(player, now);
        }
        BlockState state = level.getBlockState(pos);
        if (level.getBlockEntity(pos) instanceof HoloSignBlockEntity sign && ChromaGuard.mayEdit(player, level, pos, state)) {
            sign.setLines(payload.first(), payload.second());
        }
    }
}
