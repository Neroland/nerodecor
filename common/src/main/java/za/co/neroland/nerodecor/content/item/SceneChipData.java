package za.co.neroland.nerodecor.content.item;

import java.nio.ByteBuffer;
import java.util.Arrays;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import za.co.neroland.nerodecor.chroma.SceneChipCodec;

/**
 * The Scene Chip's data component: exactly the Stage 1 scene-codec bytes of one scene (at most 2 KB, CRC-checked when
 * read; see {@link SceneChipCodec}). No author, no player id, no coordinates.
 */
public record SceneChipData(byte[] data) {

    public static final Codec<SceneChipData> CODEC = Codec.BYTE_BUFFER.comapFlatMap(buffer -> {
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return bytes.length > SceneChipCodec.MAX_BYTES ? DataResult.error(() -> "scene chip too large")
                : DataResult.success(new SceneChipData(bytes));
    }, chip -> ByteBuffer.wrap(chip.data()));

    public static final StreamCodec<ByteBuf, SceneChipData> STREAM_CODEC =
            ByteBufCodecs.byteArray(SceneChipCodec.MAX_BYTES).map(SceneChipData::new, SceneChipData::data);

    public SceneChipData {
        data = data == null ? new byte[0] : data.clone();
    }

    @Override
    public byte[] data() {
        return data.clone();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof SceneChipData other && Arrays.equals(other.data, data);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(data);
    }

    @Override
    public String toString() {
        return "SceneChipData[" + data.length + " bytes]";
    }
}
