package za.co.neroland.nerodecor.platform;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.nerodecor.chroma.net.ChromaNet;
import za.co.neroland.nerodecor.chroma.net.ChromaNetPlatform;

/**
 * Fabric side of the Chroma packet seam. {@link #registerCommon()} (mod init, both sides) registers the payload types
 * and the serverbound receivers; {@link #registerClient()} (client init) the clientbound receivers.
 */
public final class FabricChromaNet implements ChromaNetPlatform {

    public static void registerCommon() {
        for (ChromaNet.Clientbound<?> cb : ChromaNet.clientbound()) {
            clientboundType(cb);
        }
        for (ChromaNet.Serverbound<?> sb : ChromaNet.serverbound()) {
            serverbound(sb);
        }
    }

    public static void registerClient() {
        for (ChromaNet.Clientbound<?> cb : ChromaNet.clientbound()) {
            clientReceiver(cb);
        }
    }

    private static <T extends CustomPacketPayload> void clientboundType(ChromaNet.Clientbound<T> cb) {
        PayloadTypeRegistry.clientboundPlay().register(cb.type(), cb.codec());
    }

    private static <T extends CustomPacketPayload> void serverbound(ChromaNet.Serverbound<T> sb) {
        PayloadTypeRegistry.serverboundPlay().register(sb.type(), sb.codec());
        ServerPlayNetworking.registerGlobalReceiver(sb.type(), (payload, context) -> {
            ServerPlayer player = context.player();
            player.level().getServer().execute(() -> sb.handler().accept(payload, player));
        });
    }

    private static <T extends CustomPacketPayload> void clientReceiver(ChromaNet.Clientbound<T> cb) {
        ClientPlayNetworking.registerGlobalReceiver(cb.type(), (payload, context) ->
                context.client().execute(() -> cb.handler().accept(payload)));
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        ClientPlayNetworking.send(payload);
    }
}
