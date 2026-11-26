package za.co.neroland.nerodecor.platform;

//? if >=1.20.5 {
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
//?} else {
/*import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import za.co.neroland.nerodecor.chroma.net.ChromaNet;
import za.co.neroland.nerodecor.chroma.net.ChromaNetPlatform;
import za.co.neroland.nerolandcore.network.legacy.LegacyPayload;
import za.co.neroland.nerolandcore.network.legacy.LegacyStreamCodec;

public final class FabricChromaNet implements ChromaNetPlatform {
    public static void registerCommon() {
        ChromaNet.serverbound().forEach(FabricChromaNet::registerServerbound);
    }

    public static void registerClient() {
        ChromaNet.clientbound().forEach(FabricChromaNet::registerClientbound);
    }

    private static <T extends LegacyPayload> void registerServerbound(ChromaNet.Serverbound<T> packet) {
        ServerPlayNetworking.registerGlobalReceiver(packet.type().id(), (server, player, handler, buffer, sender) -> {
            T message = packet.codec().decode(buffer);
            server.execute(() -> packet.handler().accept(message, player));
        });
    }

    private static <T extends LegacyPayload> void registerClientbound(ChromaNet.Clientbound<T> packet) {
        ClientPlayNetworking.registerGlobalReceiver(packet.type().id(), (client, handler, buffer, sender) -> {
            T message = packet.codec().decode(buffer);
            client.execute(() -> packet.handler().accept(message));
        });
    }

    @Override
    public void sendToPlayer(ServerPlayer player, LegacyPayload payload) {
        var id = payload.type().id();
        if (!ServerPlayNetworking.canSend(player, id)) {
            return;
        }
        for (var packet : ChromaNet.clientbound()) {
            if (packet.type().equals(payload.type())) {
                ServerPlayNetworking.send(player, id, encodeClientbound(packet, payload));
                return;
            }
        }
        throw new IllegalArgumentException("Unregistered clientbound payload " + id);
    }

    @Override
    public void sendToServer(LegacyPayload payload) {
        var id = payload.type().id();
        if (!ClientPlayNetworking.canSend(id)) {
            return;
        }
        for (var packet : ChromaNet.serverbound()) {
            if (packet.type().equals(payload.type())) {
                ClientPlayNetworking.send(id, encodeServerbound(packet, payload));
                return;
            }
        }
        throw new IllegalArgumentException("Unregistered serverbound payload " + id);
    }

    private static <T extends LegacyPayload> FriendlyByteBuf encodeClientbound(
            ChromaNet.Clientbound<T> packet, LegacyPayload payload) {
        return encode(packet.type(), packet.codec(), payload);
    }

    private static <T extends LegacyPayload> FriendlyByteBuf encodeServerbound(
            ChromaNet.Serverbound<T> packet, LegacyPayload payload) {
        return encode(packet.type(), packet.codec(), payload);
    }

    private static <T extends LegacyPayload> FriendlyByteBuf encode(LegacyPayload.Type<T> type,
            LegacyStreamCodec<? super FriendlyByteBuf, T> codec, LegacyPayload payload) {
        FriendlyByteBuf buffer = PacketByteBufs.create();
        codec.encode(buffer, type.payloadClass().cast(payload));
        return buffer;
    }
}
*///?}
