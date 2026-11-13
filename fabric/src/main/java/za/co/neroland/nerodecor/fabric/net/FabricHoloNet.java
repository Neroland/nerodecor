package za.co.neroland.nerodecor.fabric.net;

import java.util.function.BiConsumer;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.nerodecor.content.block.holo.net.HoloNetPlatform;

/**
 * Fabric side of the holo payload seam: registers the payload type and the server receiver at mod init (both sides);
 * the receiver hops to the server thread. Registered via {@code META-INF/services}.
 */
public final class FabricHoloNet implements HoloNetPlatform {

    @Override
    @SuppressWarnings("unchecked")
    public <T extends CustomPacketPayload> void registerServerbound(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec, BiConsumer<T, ServerPlayer> handler) {
        PayloadTypeRegistry.serverboundPlay().register(type, (StreamCodec<RegistryFriendlyByteBuf, T>) codec);
        ServerPlayNetworking.registerGlobalReceiver(type, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> handler.accept(payload, player));
        });
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        ClientPlayNetworking.send(payload);
    }
}
