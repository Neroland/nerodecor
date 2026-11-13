package za.co.neroland.nerodecor.chroma.net;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.nerodecor.platform.Services;

/**
 * Loader seam for sending Chroma packets. Each loader registers {@link ChromaNet}'s payloads on its own networking
 * API (NeoForge {@code PayloadRegistrar}, Forge {@code ChannelBuilder}, Fabric {@code PayloadTypeRegistry}) and
 * implements sending; provided through {@code META-INF/services}.
 */
public interface ChromaNetPlatform {

    void sendToPlayer(ServerPlayer player, CustomPacketPayload payload);

    /** Client only. */
    void sendToServer(CustomPacketPayload payload);

    static ChromaNetPlatform get() {
        return Holder.INSTANCE;
    }

    /** Lazy holder so pure code never triggers the ServiceLoader. */
    final class Holder {
        private static final ChromaNetPlatform INSTANCE = Services.load(ChromaNetPlatform.class);

        private Holder() {
        }
    }
}
