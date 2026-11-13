package za.co.neroland.nerodecor.content.item;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The Chroma Linker's state (item data component {@code nerodecor:chroma_linker}): the network it is bound to (0 =
 * unbound), whether it is in box mode, and the first corner of a pending box. No player data.
 */
public record LinkerState(long network, boolean boxMode, Optional<BlockPos> corner) {

    public static final LinkerState EMPTY = new LinkerState(0L, false, Optional.empty());

    public static final Codec<LinkerState> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.optionalFieldOf("network", 0L).forGetter(LinkerState::network),
            Codec.BOOL.optionalFieldOf("box", false).forGetter(LinkerState::boxMode),
            BlockPos.CODEC.optionalFieldOf("corner").forGetter(LinkerState::corner)
    ).apply(i, LinkerState::new));

    public static final StreamCodec<ByteBuf, LinkerState> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, LinkerState::network,
            ByteBufCodecs.BOOL, LinkerState::boxMode,
            ByteBufCodecs.optional(BlockPos.STREAM_CODEC), LinkerState::corner,
            LinkerState::new);

    public LinkerState bound(long id) {
        return new LinkerState(id, boxMode, Optional.empty());
    }

    public LinkerState toggledMode() {
        return new LinkerState(network, !boxMode, Optional.empty());
    }

    public LinkerState withCorner(BlockPos pos) {
        return new LinkerState(network, boxMode, Optional.of(pos.immutable()));
    }

    public LinkerState clearedCorner() {
        return new LinkerState(network, boxMode, Optional.empty());
    }
}
