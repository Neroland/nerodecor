package za.co.neroland.nerodecor.platform;

//? if >=1.20.5 {
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.payload.PayloadFlow;

import za.co.neroland.nerodecor.NeroDecorCommon;
import za.co.neroland.nerodecor.chroma.net.ChromaNet;
import za.co.neroland.nerodecor.chroma.net.ChromaNetPlatform;

/** Forge side of the Chroma packet seam: one play channel carrying {@link ChromaNet}'s payloads. */
public final class ForgeChromaNet implements ChromaNetPlatform {

    private static Channel<CustomPacketPayload> channel;

    /** Builds the channel; call once from the mod constructor. */
    public static void register() {
        PayloadFlow<RegistryFriendlyByteBuf, CustomPacketPayload> play =
                ChannelBuilder.named(Identifier.fromNamespaceAndPath(NeroDecorCommon.MOD_ID, "chroma"))
                        .payloadChannel()
                        .play()
                        .bidirectional();
        for (ChromaNet.Clientbound<?> cb : ChromaNet.clientbound()) {
            clientbound(play, cb);
        }
        for (ChromaNet.Serverbound<?> sb : ChromaNet.serverbound()) {
            serverbound(play, sb);
        }
        channel = play.build();
    }

    private static <T extends CustomPacketPayload> void clientbound(
            PayloadFlow<RegistryFriendlyByteBuf, CustomPacketPayload> play, ChromaNet.Clientbound<T> cb) {
        play.addMain(cb.type(), cb.codec(), (payload, context) -> cb.handler().accept(payload));
    }

    private static <T extends CustomPacketPayload> void serverbound(
            PayloadFlow<RegistryFriendlyByteBuf, CustomPacketPayload> play, ChromaNet.Serverbound<T> sb) {
        play.addMain(sb.type(), sb.codec(), (payload, context) -> {
            if (context.getSender() instanceof ServerPlayer player) {
                sb.handler().accept(payload, player);
            }
        });
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        if (channel != null) {
            channel.send(payload, PacketDistributor.PLAYER.with(player));
        }
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        if (channel != null) {
            channel.send(payload, PacketDistributor.SERVER.noArg());
        }
    }
}
//?} else {
/*import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
//? if >=1.20.2 {
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.SimpleChannel;
//?} else {
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
//?}

import za.co.neroland.nerodecor.NeroDecorCommon;
import za.co.neroland.nerodecor.chroma.net.ChromaNet;
import za.co.neroland.nerolandcore.network.legacy.LegacyPayload;
import za.co.neroland.nerodecor.chroma.net.ChromaNetPlatform;

public final class ForgeChromaNet implements ChromaNetPlatform {
    private static final int PROTOCOL_VERSION = 1;
    private static SimpleChannel channel;

    public static void register() {
        //? if >=1.20.2 {
        channel = ChannelBuilder.named(new ResourceLocation(NeroDecorCommon.MOD_ID, "main"))
                .networkProtocolVersion(PROTOCOL_VERSION).optional().simpleChannel();
        //?} else {
        channel = NetworkRegistry.newSimpleChannel(new ResourceLocation(NeroDecorCommon.MOD_ID, "main"),
                () -> Integer.toString(PROTOCOL_VERSION), ForgeChromaNet::acceptVersion, ForgeChromaNet::acceptVersion);
        //?}
        int id = 0;
        for (ChromaNet.Clientbound<?> packet : ChromaNet.clientbound()) {
            registerClientbound(packet, id++);
        }
        for (ChromaNet.Serverbound<?> packet : ChromaNet.serverbound()) {
            registerServerbound(packet, id++);
        }
    }

    //? if <1.20.2 {
    private static boolean acceptVersion(String version) {
        return Integer.toString(PROTOCOL_VERSION).equals(version) || NetworkRegistry.ABSENT.version().equals(version)
                || NetworkRegistry.ACCEPTVANILLA.equals(version);
    }
    //?}

    private static <T extends LegacyPayload> void registerClientbound(ChromaNet.Clientbound<T> packet, int id) {
        channel.messageBuilder(packet.type().payloadClass(), id, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((payload, buffer) -> packet.codec().encode(buffer, payload))
                .decoder(packet.codec()::decode)
                .consumerMainThread((payload, context) -> packet.handler().accept(payload))
                .add();
    }

    private static <T extends LegacyPayload> void registerServerbound(ChromaNet.Serverbound<T> packet, int id) {
        channel.messageBuilder(packet.type().payloadClass(), id, NetworkDirection.PLAY_TO_SERVER)
                .encoder((payload, buffer) -> packet.codec().encode(buffer, payload))
                .decoder(packet.codec()::decode)
                .consumerMainThread((payload, context) -> {
                    //? if >=1.20.2 {
                    ServerPlayer player = context.getSender();
                    //?} else {
                    ServerPlayer player = context.get().getSender();
                    //?}
                    if (player != null) {
                        packet.handler().accept(payload, player);
                    }
                })
                .add();
    }

    @Override
    public void sendToPlayer(ServerPlayer player, LegacyPayload payload) {
        //? if >=1.20.2 {
        var connection = player.connection.getConnection();
        //?} else {
        var connection = player.connection.connection;
        //?}
        if (channel != null && channel.isRemotePresent(connection)) {
            //? if >=1.20.2 {
            channel.send(payload, connection);
            //?} else {
            channel.send(PacketDistributor.PLAYER.with(() -> player), payload);
            //?}
        }
    }

    @Override
    public void sendToServer(LegacyPayload payload) {
        var connection = Minecraft.getInstance().getConnection();
        if (channel != null && connection != null && channel.isRemotePresent(connection.getConnection())) {
            //? if >=1.20.2 {
            channel.send(payload, connection.getConnection());
            //?} else {
            channel.sendToServer(payload);
            //?}
        }
    }
}
*///?}
