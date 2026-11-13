package za.co.neroland.nerodecor.neoforge.net;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
//? if >=1.21.6 {
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
//?} else {
/*import net.neoforged.neoforge.network.PacketDistributor;
*///?}
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import za.co.neroland.nerodecor.NeroDecorCommon;
import za.co.neroland.nerodecor.content.block.holo.net.HoloNetPlatform;

/**
 * NeoForge side of the holo payload seam: payloads declared during mod construction are registered on an optional
 * registrar in {@code RegisterPayloadHandlersEvent} (a client without NeroDecor can still connect). Registered via
 * {@code META-INF/services}.
 */
public final class NeoForgeHoloNet implements HoloNetPlatform {

    private final List<Runnable> pending = new ArrayList<>();
    private PayloadRegistrar registrar;
    private boolean listening;

    @Override
    public <T extends CustomPacketPayload> void registerServerbound(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec, BiConsumer<T, ServerPlayer> handler) {
        pending.add(() -> registrar.playToServer(type, codec, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                handler.accept(payload, player);
            }
        })));
        if (!listening) {
            listening = true;
            IEventBus bus = ModList.get().getModContainerById(NeroDecorCommon.MOD_ID)
                    .map(ModContainer::getEventBus).orElse(null);
            if (bus != null) {
                bus.addListener(this::onRegister);
            }
        }
    }

    private void onRegister(RegisterPayloadHandlersEvent event) {
        registrar = event.registrar("1").optional();
        pending.forEach(Runnable::run);
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
