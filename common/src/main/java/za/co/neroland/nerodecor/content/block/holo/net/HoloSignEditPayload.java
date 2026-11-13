package za.co.neroland.nerodecor.content.block.holo.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import za.co.neroland.nerodecor.NeroDecorCommon;

/**
 * Client to server: "set the two lines of the holo sign at {@code pos}". Carries only a block position and the two
 * lines of sign text (no player data). Strings are capped on read; the server sanitises and validates.
 */
public record HoloSignEditPayload(BlockPos pos, String first, String second) implements CustomPacketPayload {

    /** Wire cap per line, well above the 32 shown characters, so a bad client cannot send large strings. */
    private static final int WIRE_MAX = 96;

    public static final Type<HoloSignEditPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(NeroDecorCommon.MOD_ID, "holo_sign_edit"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HoloSignEditPayload> STREAM_CODEC =
            StreamCodec.of(HoloSignEditPayload::write, HoloSignEditPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, HoloSignEditPayload payload) {
        buf.writeBlockPos(payload.pos);
        buf.writeUtf(payload.first, WIRE_MAX);
        buf.writeUtf(payload.second, WIRE_MAX);
    }

    private static HoloSignEditPayload read(RegistryFriendlyByteBuf buf) {
        return new HoloSignEditPayload(buf.readBlockPos(), buf.readUtf(WIRE_MAX), buf.readUtf(WIRE_MAX));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
