package za.co.neroland.nerodecor.forge.net;

import java.util.function.BiConsumer;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.payload.PayloadFlow;

import za.co.neroland.nerodecor.NeroDecorCommon;
import za.co.neroland.nerodecor.content.block.holo.net.HoloNetPlatform;

/**
 * Forge side of the holo payload seam, on NeroDecor's own optional {@code nerodecor:holo} channel (Forge seals a
 * channel at {@code build()}, so the holo collection owns this one rather than sharing another). Registered via
 * {@code META-INF/services}.
 */
public final class ForgeHoloNet implements HoloNetPlatform {

    private Channel<CustomPacketPayload> channel;

    @Override
    @SuppressWarnings("unchecked")
    public <T extends CustomPacketPayload> void registerServerbound(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec, BiConsumer<T, ServerPlayer> handler) {
        if (channel != null) {
            throw new IllegalStateException("nerodecor:holo channel already built");
        }
        PayloadFlow<RegistryFriendlyByteBuf, CustomPacketPayload> play =
                ChannelBuilder.named(Identifier.fromNamespaceAndPath(NeroDecorCommon.MOD_ID, "holo"))
                        .optional()
                        .payloadChannel()
                        .play()
                        .bidirectional();
        play.addMain(type, (StreamCodec<RegistryFriendlyByteBuf, T>) codec, (payload, context) -> {
            if (context.getSender() instanceof ServerPlayer player) {
                context.enqueueWork(() -> handler.accept(payload, player));
            }
            context.setPacketHandled(true);
        });
        channel = play.build();
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        if (channel != null) {
            channel.send(payload, PacketDistributor.SERVER.noArg());
        }
    }
}
