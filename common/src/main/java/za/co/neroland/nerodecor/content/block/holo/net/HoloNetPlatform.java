package za.co.neroland.nerodecor.content.block.holo.net;

import java.util.function.BiConsumer;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Loader seam for the holo collection's one serverbound payload. Each loader ships an implementation registered in
 * {@code META-INF/services} (NeoForge: payload registrar on the mod bus; Forge: an optional {@code nerodecor:holo}
 * channel; Fabric: the payload type registry). Handlers run on the server thread.
 */
public interface HoloNetPlatform {

    <T extends CustomPacketPayload> void registerServerbound(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec, BiConsumer<T, ServerPlayer> handler);

    /** Client only. */
    void sendToServer(CustomPacketPayload payload);
}
