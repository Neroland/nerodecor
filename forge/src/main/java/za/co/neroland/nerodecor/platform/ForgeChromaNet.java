package za.co.neroland.nerodecor.platform;

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
