package za.co.neroland.nerodecor.chroma.net;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.nerodecor.chroma.ChromaResult;
import za.co.neroland.nerodecor.chroma.ChromaSyncCodec;
import za.co.neroland.nerodecor.chroma.server.ChromaConfig;
import za.co.neroland.nerodecor.chroma.server.ChromaGuard;
import za.co.neroland.nerodecor.chroma.server.ChromaNetworks;
import za.co.neroland.nerodecor.chroma.server.ChromaVerdict;

/**
 * Server handler for {@link ChromaPayloads.Ops}: decode and validate the batch ({@link ChromaSyncCodec#decodeOps}),
 * charge the per-player rate limiter (one edit request, one op per block op), then apply it atomically through the
 * store, which checks the role and that every targeted block is linked, and asks {@link ChromaGuard} for each block.
 * Refusals answer with the verdict on the action bar; nothing is logged.
 */
public final class ChromaEdits {

    private ChromaEdits() {
    }

    public static void handle(ServerPlayer player, byte[] data) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        ChromaVerdict verdict = apply(level, player, data);
        if (!verdict.isOk()) {
            ChromaNetworks.tell(player, verdict);
        }
    }

    /** Decodes and applies one op packet for {@code player} in {@code level}. */
    public static ChromaVerdict apply(ServerLevel level, ServerPlayer player, byte[] data) {
        if (!ChromaConfig.ENABLED.get()) {
            return ChromaVerdict.DISABLED;
        }
        ChromaResult<ChromaSyncCodec.OpsMessage> message = ChromaSyncCodec.decodeOps(data);
        if (!message.isOk()) {
            return ChromaVerdict.BAD_OP;
        }
        ChromaSyncCodec.OpsMessage ops = message.value();
        ChromaVerdict rate = ChromaNetworks.limiter().tryAcquire(player.getUUID(), ops.batch().size(),
                System.nanoTime());
        if (!rate.isOk()) {
            return rate;
        }
        return ChromaNetworks.store(level).applyOps(ops.network(), player.getUUID(), ops.batch(), packed -> {
            BlockPos pos = BlockPos.of(packed);
            return level.isLoaded(pos) && ChromaGuard.mayEdit(player, level, pos, level.getBlockState(pos));
        });
    }
}
