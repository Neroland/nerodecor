package za.co.neroland.nerodecor.chroma;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.CRC32;
import org.junit.jupiter.api.Test;

class SceneCodecTest {

    static Scene representative() {
        List<ZoneAssignment> zones = new ArrayList<>();
        zones.add(new ZoneAssignment(0, 0, new Timeline(List.of(
                new Keyframe(0, ChromaColor.of(255, 0, 0, 15), Easing.LINEAR),
                new Keyframe(20, ChromaColor.of(0, 255, 0, 7), Easing.EASE_IN_OUT),
                new Keyframe(65535, ChromaColor.of(0, 0, 255, 0), Easing.STEP)), 65535, true)));
        zones.add(new ZoneAssignment(31, 15, new EffectProgram(EffectType.RAINBOW, EffectParams.builder()
                .periodTicks(100).wavelength(255).axis(2).amplitude(200).build())));
        zones.add(new ZoneAssignment(7, 3, new EffectProgram(EffectType.SKY_MIRROR, EffectParams.builder()
                .sky(ChromaColor.of(1, 2, 3, 4)).daylight(0).density(0).build())));
        return new Scene("Neon Hall #2", zones);
    }

    private static byte[] encode(Scene s) {
        ChromaResult<byte[]> r = SceneCodec.encode(s);
        assertTrue(r.isOk(), r.toString());
        return r.value();
    }

    /** Rewrites the payload length and CRC after tampering with the payload, so deeper checks run. */
    private static byte[] reseal(byte[] payload) {
        ByteBuffer b = ByteBuffer.allocate(7 + payload.length + 4);
        b.put(SceneCodec.MAGIC).put((byte) SceneCodec.VERSION).putShort((short) payload.length).put(payload);
        CRC32 crc = new CRC32();
        crc.update(payload);
        b.putInt((int) crc.getValue());
        return b.array();
    }

    private static byte[] payloadOf(byte[] encoded) {
        return Arrays.copyOfRange(encoded, 7, encoded.length - 4);
    }

    @Test
    void roundTripIsExact() {
        Scene scene = representative();
        byte[] bytes = encode(scene);
        Scene back = SceneCodec.decode(bytes).value();
        assertEquals(scene, back);
        assertArrayEquals(bytes, encode(back));
        assertEquals(List.of(0, 7, 31), back.zones().stream().map(ZoneAssignment::zone).toList());
        Scene empty = new Scene("", List.of());
        assertEquals(empty, SceneCodec.decode(encode(empty)).value());
    }

    @Test
    void everyEffectRoundTrips() {
        List<ZoneAssignment> zones = new ArrayList<>();
        for (EffectType t : EffectType.values()) {
            zones.add(new ZoneAssignment(t.id(), t.id(), new EffectProgram(t, EffectParams.builder()
                    .periodTicks(1 + t.id() * 1000).amplitude(t.id() * 30).density(255).build())));
        }
        Scene s = new Scene("all", zones);
        assertEquals(s, SceneCodec.decode(encode(s)).value());
    }

    @Test
    void shareCodeRoundTrip() {
        Scene scene = representative();
        String code = SceneCodec.toShareCode(scene).value();
        assertTrue(code.startsWith(SceneCodec.SHARE_PREFIX));
        assertTrue(code.substring(4).matches("[A-Za-z0-9_-]+"));
        assertEquals(scene, SceneCodec.fromShareCode("  " + code + "\n").value());
        assertEquals(ChromaError.BAD_SHARE_CODE, SceneCodec.fromShareCode("XX1-" + code.substring(4)).error());
        assertEquals(ChromaError.BAD_SHARE_CODE, SceneCodec.fromShareCode("ND1-***").error());
        assertEquals(ChromaError.BAD_SHARE_CODE, SceneCodec.fromShareCode(null).error());
        assertEquals(ChromaError.OVERSIZED, SceneCodec.fromShareCode("ND1-" + "A".repeat(4000)).error());
    }

    @Test
    void flippedByteFailsCrc() {
        byte[] bytes = encode(representative());
        for (int i = 7; i < bytes.length; i++) {
            byte[] copy = bytes.clone();
            copy[i] ^= 0x10;
            assertEquals(ChromaError.BAD_CRC, SceneCodec.decode(copy).error(), "byte " + i);
        }
    }

    @Test
    void headerTampering() {
        byte[] bytes = encode(representative());
        byte[] magic = bytes.clone();
        magic[0] = 'X';
        assertEquals(ChromaError.BAD_MAGIC, SceneCodec.decode(magic).error());
        byte[] version = bytes.clone();
        version[4] = 2;
        assertEquals(ChromaError.UNSUPPORTED_VERSION, SceneCodec.decode(version).error());
        assertEquals(ChromaError.TRUNCATED, SceneCodec.decode(Arrays.copyOf(bytes, bytes.length - 1)).error());
        assertEquals(ChromaError.TRUNCATED, SceneCodec.decode(Arrays.copyOf(bytes, 5)).error());
        assertEquals(ChromaError.TRUNCATED, SceneCodec.decode(null).error());
        assertEquals(ChromaError.TRAILING_DATA, SceneCodec.decode(Arrays.copyOf(bytes, bytes.length + 1)).error());
        assertEquals(ChromaError.OVERSIZED, SceneCodec.decode(new byte[SceneCodec.MAX_ENCODED_BYTES + 1]).error());
    }

    @Test
    void truncatedPayloadWithValidCrc() {
        byte[] payload = payloadOf(encode(representative()));
        for (int cut = 0; cut < payload.length; cut++) {
            ChromaResult<Scene> r = SceneCodec.decode(reseal(Arrays.copyOf(payload, cut)));
            assertFalse(r.isOk(), "cut " + cut);
        }
        byte[] longer = Arrays.copyOf(payload, payload.length + 1);
        assertEquals(ChromaError.TRAILING_DATA, SceneCodec.decode(reseal(longer)).error());
    }

    @Test
    void outOfRangeFieldsRejected() {
        Scene one = new Scene("x", List.of(new ZoneAssignment(4, 2, new Timeline(List.of(
                new Keyframe(0, ChromaColor.WHITE, Easing.LINEAR)), 10, false))));
        byte[] p = payloadOf(encode(one));
        // payload: nameLen(1) 'x' zoneCount zone channel kind flags len(2) keyCount tick(2) colour(4) easing
        int zoneCount = 2;
        byte[] t = p.clone();
        t[zoneCount] = 33;
        assertEquals(ChromaError.OUT_OF_RANGE, SceneCodec.decode(reseal(t)).error());
        t = p.clone();
        t[zoneCount + 1] = 32;
        assertEquals(ChromaError.OUT_OF_RANGE, SceneCodec.decode(reseal(t)).error());
        t = p.clone();
        t[zoneCount + 2] = 16;
        assertEquals(ChromaError.OUT_OF_RANGE, SceneCodec.decode(reseal(t)).error());
        t = p.clone();
        t[zoneCount + 3] = 9;
        assertEquals(ChromaError.OUT_OF_RANGE, SceneCodec.decode(reseal(t)).error());
        t = p.clone();
        t[zoneCount + 4] = 2;
        assertEquals(ChromaError.OUT_OF_RANGE, SceneCodec.decode(reseal(t)).error());
        t = p.clone();
        t[zoneCount + 7] = 0;
        assertEquals(ChromaError.OUT_OF_RANGE, SceneCodec.decode(reseal(t)).error());
        t = p.clone();
        t[zoneCount + 7] = 33;
        assertEquals(ChromaError.OUT_OF_RANGE, SceneCodec.decode(reseal(t)).error());
        t = p.clone();
        t[zoneCount + 9] = 11;
        assertEquals(ChromaError.OUT_OF_RANGE, SceneCodec.decode(reseal(t)).error());
        t = p.clone();
        t[zoneCount + 10] = 0x10;
        assertEquals(ChromaError.OUT_OF_RANGE, SceneCodec.decode(reseal(t)).error());
        t = p.clone();
        t[zoneCount + 14] = 5;
        assertEquals(ChromaError.OUT_OF_RANGE, SceneCodec.decode(reseal(t)).error());
        t = p.clone();
        t[1] = 0x01;
        assertEquals(ChromaError.BAD_NAME, SceneCodec.decode(reseal(t)).error());
        t = p.clone();
        t[1] = (byte) 0xC3;
        assertEquals(ChromaError.BAD_NAME, SceneCodec.decode(reseal(t)).error());
    }

    @Test
    void duplicateOrUnsortedZonesRejected() {
        Scene two = new Scene("", List.of(
                new ZoneAssignment(1, 0, new EffectProgram(EffectType.PULSE, EffectParams.builder().build())),
                new ZoneAssignment(2, 0, new EffectProgram(EffectType.PULSE, EffectParams.builder().build()))));
        byte[] p = payloadOf(encode(two));
        int secondZone = 2 + 3 + 20;
        assertEquals(2, p[secondZone]);
        p[secondZone] = 1;
        assertEquals(ChromaError.OUT_OF_RANGE, SceneCodec.decode(reseal(p)).error());
        assertThrows(IllegalArgumentException.class, () -> new Scene("", List.of(two.zones().get(0),
                two.zones().get(0))));
    }

    @Test
    void oversizedSceneRefusedOnEncode() {
        List<ZoneAssignment> zones = new ArrayList<>();
        for (int z = 0; z < ZoneAssignment.MAX_ZONES; z++) {
            List<Keyframe> keys = new ArrayList<>();
            for (int k = 0; k < Timeline.MAX_KEYS; k++) {
                keys.add(new Keyframe(k, ChromaColor.of(k, z, 0), Easing.LINEAR));
            }
            zones.add(new ZoneAssignment(z, 0, new Timeline(keys, 100, true)));
        }
        Scene big = new Scene("big", zones);
        assertEquals(ChromaError.OVERSIZED, SceneCodec.encode(big).error());
        assertEquals(ChromaError.OVERSIZED, SceneCodec.toShareCode(big).error());
    }

    @Test
    void namesAreSanitised() {
        assertEquals("Hello World", Scene.sanitiseName("  Hello \t\n World  "));
        assertEquals("ab", Scene.sanitiseName("a\u0000b<>"));
        assertEquals("", Scene.sanitiseName(null));
        assertEquals(Scene.MAX_NAME_LENGTH, Scene.sanitiseName("x".repeat(100)).length());
        assertEquals("café", Scene.sanitiseName("café"));
        String spaced = Scene.sanitiseName("a".repeat(31) + " b");
        assertEquals("a".repeat(31), spaced);
        assertEquals("Neon Hall #2", new Scene("Neon Hall #2‮", List.of()).name());
    }
}
