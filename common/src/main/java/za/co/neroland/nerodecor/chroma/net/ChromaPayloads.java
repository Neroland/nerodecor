package za.co.neroland.nerodecor.chroma.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import za.co.neroland.nerodecor.NeroDecorCommon;
import za.co.neroland.nerodecor.chroma.ChromaSyncCodec;

/**
 * The three Chroma packets. Each carries the bytes of a pure-Java codec ({@link ChromaSyncCodec}) unchanged, so the
 * wire layout is identical on every Minecraft version and loader and is unit-tested without a game. The stream codecs
 * only bound the length.
 *
 * <p>Privacy (POPIA/GDPR): network ids, hub-relative positions and colours only; never a player id or name.
 */
public final class ChromaPayloads {

    /** Upper bound on any Chroma packet body (codec budget plus headroom for an op batch). */
    public static final int MAX_BYTES = ChromaSyncCodec.MAX_BYTES * 2;

    private ChromaPayloads() {
    }

    /** Server to client: one hub's static layout (or one part of it). */
    public record Snapshot(byte[] data) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE = new Type<>(id("chroma_snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> buf.writeByteArray(p.data()), buf -> new Snapshot(buf.readByteArray(MAX_BYTES)));

        @Override
        public Type<Snapshot> type() {
            return TYPE;
        }
    }

    /** Server to client: coalesced changes to one hub's layout, or "drop this hub". */
    public record Delta(byte[] data) implements CustomPacketPayload {
        public static final Type<Delta> TYPE = new Type<>(id("chroma_delta"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Delta> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> buf.writeByteArray(p.data()), buf -> new Delta(buf.readByteArray(MAX_BYTES)));

        @Override
        public Type<Delta> type() {
            return TYPE;
        }
    }

    /** Client to server: an edit op batch for one network. */
    public record Ops(byte[] data) implements CustomPacketPayload {
        public static final Type<Ops> TYPE = new Type<>(id("chroma_ops"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Ops> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> buf.writeByteArray(p.data()), buf -> new Ops(buf.readByteArray(MAX_BYTES)));

        @Override
        public Type<Ops> type() {
            return TYPE;
        }
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(NeroDecorCommon.MOD_ID, path);
    }
}
