package za.co.neroland.nerodecor.platform;

//? if >=1.20.5 {
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
    public static void register(net.neoforged.bus.api.IEventBus modBus) {
        modBus.addListener(NeoForgeChromaNet::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
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
//?} else {
/*import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlerEvent;
import net.neoforged.neoforge.network.registration.IPayloadRegistrar;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

import za.co.neroland.nerodecor.NeroDecorCommon;
import za.co.neroland.nerodecor.chroma.net.ChromaNet;
import za.co.neroland.nerolandcore.network.legacy.LegacyPayload;
import za.co.neroland.nerolandcore.network.legacy.LegacyStreamCodec;
import za.co.neroland.nerodecor.chroma.net.ChromaNetPlatform;

public final class NeoForgeChromaNet implements ChromaNetPlatform {
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(NeoForgeChromaNet::onRegister);
    }

    private static void onRegister(RegisterPayloadHandlerEvent event) {
        IPayloadRegistrar registrar = event.registrar(NeroDecorCommon.MOD_ID).versioned("1").optional();
        ChromaNet.clientbound().forEach(packet -> registerClientbound(registrar, packet));
        ChromaNet.serverbound().forEach(packet -> registerServerbound(registrar, packet));
    }

    private static <T extends LegacyPayload> void registerClientbound(IPayloadRegistrar registrar,
            ChromaNet.Clientbound<T> packet) {
        registrar.play(packet.type().id(),
                buffer -> new WirePayload<>(packet.type(), packet.codec(), packet.codec().decode(buffer)),
                builder -> {
                    builder.client((payload, context) ->
                            context.workHandler().execute(() -> packet.handler().accept(payload.message())));
                });
    }

    private static <T extends LegacyPayload> void registerServerbound(IPayloadRegistrar registrar,
            ChromaNet.Serverbound<T> packet) {
        registrar.play(packet.type().id(),
                buffer -> new WirePayload<>(packet.type(), packet.codec(), packet.codec().decode(buffer)),
                builder -> {
                    builder.server((payload, context) -> context.workHandler().execute(() ->
                            context.player().ifPresent(player -> {
                                if (player instanceof ServerPlayer serverPlayer) {
                                    packet.handler().accept(payload.message(), serverPlayer);
                                }
                            })));
                });
    }

    @Override
    public void sendToPlayer(ServerPlayer player, LegacyPayload payload) {
        if (!NetworkRegistry.getInstance().isConnected(player.connection, payload.type().id())) {
            return;
        }
        for (ChromaNet.Clientbound<?> packet : ChromaNet.clientbound()) {
            if (packet.type().equals(payload.type())) {
                PacketDistributor.PLAYER.with(player).send(wrapClientbound(packet, payload));
                return;
            }
        }
        throw new IllegalArgumentException("Unregistered clientbound payload " + payload.type().id());
    }

    @Override
    public void sendToServer(LegacyPayload payload) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null || !NetworkRegistry.getInstance().isConnected(connection, payload.type().id())) {
            return;
        }
        for (ChromaNet.Serverbound<?> packet : ChromaNet.serverbound()) {
            if (packet.type().equals(payload.type())) {
                PacketDistributor.SERVER.noArg().send(wrapServerbound(packet, payload));
                return;
            }
        }
        throw new IllegalArgumentException("Unregistered serverbound payload " + payload.type().id());
    }

    private static <T extends LegacyPayload> WirePayload<T> wrapClientbound(ChromaNet.Clientbound<T> packet,
            LegacyPayload payload) {
        return new WirePayload<>(packet.type(), packet.codec(), packet.type().payloadClass().cast(payload));
    }

    private static <T extends LegacyPayload> WirePayload<T> wrapServerbound(ChromaNet.Serverbound<T> packet,
            LegacyPayload payload) {
        return new WirePayload<>(packet.type(), packet.codec(), packet.type().payloadClass().cast(payload));
    }

    private record WirePayload<T extends LegacyPayload>(LegacyPayload.Type<T> type,
            LegacyStreamCodec<? super FriendlyByteBuf, T> codec, T message)
            implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
        @Override
        public ResourceLocation id() {
            return type.id();
        }

        @Override
        public void write(FriendlyByteBuf buffer) {
            codec.encode(buffer, message);
        }
    }
}
*///?}
