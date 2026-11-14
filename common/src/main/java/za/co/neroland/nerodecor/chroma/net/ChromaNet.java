package za.co.neroland.nerodecor.chroma.net;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.nerodecor.content.block.holo.net.HoloNet;
import za.co.neroland.nerodecor.content.block.holo.net.HoloSignEditPayload;

/**
 * NeroDecor's packet table, declared once and wired by each loader (see {@link ChromaNetPlatform}). Clientbound
 * handlers run on the client main thread and reach the client only through lambdas, so a dedicated server never
 * loads a client class; serverbound handlers run on the server main thread with the sending player.
 */
public final class ChromaNet {

    /** A server-to-client payload and its client handler. */
    public record Clientbound<T extends CustomPacketPayload>(CustomPacketPayload.Type<T> type,
            StreamCodec<RegistryFriendlyByteBuf, T> codec, Consumer<T> handler) {
    }

    /** A client-to-server payload and its server handler. */
    public record Serverbound<T extends CustomPacketPayload>(CustomPacketPayload.Type<T> type,
            StreamCodec<RegistryFriendlyByteBuf, T> codec, BiConsumer<T, ServerPlayer> handler) {
    }

    private static final List<Clientbound<?>> CLIENTBOUND = List.of(
            new Clientbound<>(ChromaPayloads.Snapshot.TYPE, ChromaPayloads.Snapshot.STREAM_CODEC,
                    p -> za.co.neroland.nerodecor.chroma.client.ChromaClientSync.onSnapshot(p.data())),
            new Clientbound<>(ChromaPayloads.Delta.TYPE, ChromaPayloads.Delta.STREAM_CODEC,
                    p -> za.co.neroland.nerodecor.chroma.client.ChromaClientSync.onDelta(p.data())),
            new Clientbound<>(ChromaHubNet.Detail.TYPE, ChromaHubNet.Detail.STREAM_CODEC,
                    p -> ChromaHubNet.onClientDetail(p.data())),
            // Stage 4 (scenes)
            new Clientbound<>(ChromaScenePayloads.SceneState.TYPE, ChromaScenePayloads.SceneState.STREAM_CODEC,
                    p -> ChromaScenePayloads.onClientScene(p.data())),
            new Clientbound<>(ChromaScenePayloads.ZoneMap.TYPE, ChromaScenePayloads.ZoneMap.STREAM_CODEC,
                    p -> ChromaScenePayloads.onClientZones(p.data())));

    private static final List<Serverbound<?>> SERVERBOUND = List.of(
            new Serverbound<>(ChromaPayloads.Ops.TYPE, ChromaPayloads.Ops.STREAM_CODEC,
                    (p, player) -> ChromaEdits.handle(player, p.data())),
            new Serverbound<>(HoloSignEditPayload.TYPE, HoloSignEditPayload.STREAM_CODEC, HoloNet::handleEdit),
            new Serverbound<>(ChromaHubNet.Action.TYPE, ChromaHubNet.Action.STREAM_CODEC,
                    (p, player) -> ChromaHubNet.handle(player, p.data())),
            // Stage 4 (scenes)
            new Serverbound<>(ChromaScenePayloads.SceneEdit.TYPE, ChromaScenePayloads.SceneEdit.STREAM_CODEC,
                    (p, player) -> ChromaSceneEdits.handle(player, p.data())));

    private ChromaNet() {
    }

    public static List<Clientbound<?>> clientbound() {
        return CLIENTBOUND;
    }

    public static List<Serverbound<?>> serverbound() {
        return SERVERBOUND;
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        ChromaNetPlatform.get().sendToPlayer(player, payload);
    }

    public static void sendToServer(CustomPacketPayload payload) {
        ChromaNetPlatform.get().sendToServer(payload);
    }
}
