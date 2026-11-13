package za.co.neroland.nerodecor.platform;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
//? if >=1.21.6 {
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
//?}
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import za.co.neroland.nerodecor.chroma.net.ChromaNet;
import za.co.neroland.nerodecor.chroma.net.ChromaNetPlatform;

/** NeoForge side of the Chroma packet seam: registers {@link ChromaNet}'s payloads and sends them. */
public final class NeoForgeChromaNet implements ChromaNetPlatform {

    /** Mod-bus listener for {@code RegisterPayloadHandlersEvent}. */
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        for (ChromaNet.Clientbound<?> cb : ChromaNet.clientbound()) {
            clientbound(registrar, cb);
        }
        for (ChromaNet.Serverbound<?> sb : ChromaNet.serverbound()) {
            serverbound(registrar, sb);
        }
    }

    private static <T extends CustomPacketPayload> void clientbound(PayloadRegistrar registrar,
            ChromaNet.Clientbound<T> cb) {
        registrar.playToClient(cb.type(), cb.codec(),
                (payload, context) -> context.enqueueWork(() -> cb.handler().accept(payload)));
    }

    private static <T extends CustomPacketPayload> void serverbound(PayloadRegistrar registrar,
            ChromaNet.Serverbound<T> sb) {
        registrar.playToServer(sb.type(), sb.codec(), (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                sb.handler().accept(payload, player);
            }
        }));
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        //? if >=1.21.6 {
        ClientPacketDistributor.sendToServer(payload);
        //?} else {
        /*PacketDistributor.sendToServer(payload);
        *///?}
    }
}
