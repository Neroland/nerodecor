package za.co.neroland.nerodecor.chroma.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import za.co.neroland.nerodecor.NeroDecorCommon;
import za.co.neroland.nerodecor.chroma.SceneSyncCodec;

/**
 * The Stage 4 (scene) packets. Like {@link ChromaPayloads} each carries the bytes of a pure-Java codec unchanged
 * ({@link SceneSyncCodec}, {@link ChromaSceneEdits}); the stream codecs only bound the length.
 *
 * <p>Privacy (POPIA/GDPR): network ids, hub positions, hub-relative offsets, colours and scene programs; never a
 * player id or name.
 */
public final class ChromaScenePayloads {

    public static final int MAX_BYTES = SceneSyncCodec.MAX_BYTES;

    /** Client seams, set by the client bootstrap so shared code never loads a client class. */
    private static volatile java.util.function.Consumer<byte[]> clientScene = data -> { };
    private static volatile java.util.function.Consumer<byte[]> clientZones = data -> { };

    private ChromaScenePayloads() {
    }

    /** Client bootstrap: where {@link SceneState} and {@link ZoneMap} payloads go. */
    public static void setClientHandlers(java.util.function.Consumer<byte[]> scene,
            java.util.function.Consumer<byte[]> zones) {
        clientScene = scene == null ? data -> { } : scene;
        clientZones = zones == null ? data -> { } : zones;
    }

    static void onClientScene(byte[] data) {
        clientScene.accept(data);
    }

    static void onClientZones(byte[] data) {
        clientZones.accept(data);
    }

    /** Server to client: one hub's playing scene (or static), its start tick and seed. */
    public record SceneState(byte[] data) implements CustomPacketPayload {
        public static final Type<SceneState> TYPE = new Type<>(id("chroma_scene"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SceneState> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> buf.writeByteArray(p.data()), buf -> new SceneState(buf.readByteArray(MAX_BYTES)));

        @Override
        public Type<SceneState> type() {
            return TYPE;
        }
    }

    /** Server to client: one part of a hub's zone map (which zone each linked block is in). */
    public record ZoneMap(byte[] data) implements CustomPacketPayload {
        public static final Type<ZoneMap> TYPE = new Type<>(id("chroma_zones"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ZoneMap> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> buf.writeByteArray(p.data()), buf -> new ZoneMap(buf.readByteArray(MAX_BYTES)));

        @Override
        public Type<ZoneMap> type() {
            return TYPE;
        }
    }

    /** Client to server: a scene edit (save / activate / clear a slot, or the trigger). */
    public record SceneEdit(byte[] data) implements CustomPacketPayload {
        public static final Type<SceneEdit> TYPE = new Type<>(id("chroma_scene_edit"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SceneEdit> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> buf.writeByteArray(p.data()), buf -> new SceneEdit(buf.readByteArray(MAX_BYTES)));

        @Override
        public Type<SceneEdit> type() {
            return TYPE;
        }
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(NeroDecorCommon.MOD_ID, path);
    }
}
