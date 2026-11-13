package za.co.neroland.nerodecor.chroma;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.zip.CRC32;

/**
 * Versioned binary scene format and its share code.
 *
 * <pre>
 * magic "NDCS" | version u8 | payloadLength u16 | payload | crc32(payload) u32     (big-endian)
 * payload  = nameLen u8 | name UTF-8 | zoneCount u8 | zone*   (zones strictly ascending by id)
 * zone     = zone u8 | channel u8 | kind u8 (0 timeline, 1 effect) | body
 * timeline = flags u8 (bit0 loop) | length u16 | keyCount u8 | (tick u16 | colour i32 | easing u8)*
 * effect   = type u8 | primary i32 | secondary i32 | period u16 | amplitude u8 | axis u8
 *            | wavelength u8 | density u8 | sky i32 | daylight u8
 * </pre>
 *
 * <p>Encoding is canonical, so decode(encode(s)) equals s and encode(decode(b)) equals b. Decoding never
 * throws: every malformed input maps to a {@link ChromaError}.
 */
public final class SceneCodec {

    public static final byte[] MAGIC = {'N', 'D', 'C', 'S'};
    public static final int VERSION = 1;
    /** Hard cap on an encoded scene, header and CRC included. */
    public static final int MAX_ENCODED_BYTES = 2048;
    public static final String SHARE_PREFIX = "ND1-";
    /** Longest share code that can hold {@link #MAX_ENCODED_BYTES} (unpadded base64). */
    public static final int MAX_SHARE_CODE_LENGTH = SHARE_PREFIX.length() + (MAX_ENCODED_BYTES * 4 + 2) / 3;

    private static final int HEADER_BYTES = MAGIC.length + 1 + 2;
    private static final int CRC_BYTES = 4;
    private static final int KIND_TIMELINE = 0;
    private static final int KIND_EFFECT = 1;
    private static final int FLAG_LOOP = 1;
    private static final int MAX_NAME_BYTES = 255;

    private SceneCodec() {
    }

    /** Encodes a scene; fails with {@link ChromaError#OVERSIZED} past {@link #MAX_ENCODED_BYTES}. */
    public static ChromaResult<byte[]> encode(Scene scene) {
        ByteArrayOutputStream payload = new ByteArrayOutputStream(256);
        byte[] name = scene.name().getBytes(StandardCharsets.UTF_8);
        if (name.length > MAX_NAME_BYTES) {
            return ChromaResult.fail(ChromaError.BAD_NAME, "name too long");
        }
        payload.write(name.length);
        payload.write(name, 0, name.length);
        payload.write(scene.zones().size());
        for (ZoneAssignment z : scene.zones()) {
            payload.write(z.zone());
            payload.write(z.channel());
            if (z.program() instanceof Timeline tl) {
                payload.write(KIND_TIMELINE);
                payload.write(tl.loops() ? FLAG_LOOP : 0);
                writeU16(payload, tl.length());
                payload.write(tl.keys().size());
                for (Keyframe k : tl.keys()) {
                    writeU16(payload, k.tick());
                    writeI32(payload, k.color().packed());
                    payload.write(k.easing().id());
                }
            } else if (z.program() instanceof EffectProgram ep) {
                EffectParams p = ep.params();
                payload.write(KIND_EFFECT);
                payload.write(ep.type().id());
                writeI32(payload, p.primary().packed());
                writeI32(payload, p.secondary().packed());
                writeU16(payload, p.periodTicks());
                payload.write(p.amplitude());
                payload.write(p.axis());
                payload.write(p.wavelength());
                payload.write(p.density());
                writeI32(payload, p.sky().packed());
                payload.write(p.daylight());
            }
            if (payload.size() > MAX_ENCODED_BYTES) {
                return ChromaResult.fail(ChromaError.OVERSIZED, "scene exceeds " + MAX_ENCODED_BYTES + " bytes");
            }
        }
        byte[] body = payload.toByteArray();
        int total = HEADER_BYTES + body.length + CRC_BYTES;
        if (total > MAX_ENCODED_BYTES) {
            return ChromaResult.fail(ChromaError.OVERSIZED, "scene exceeds " + MAX_ENCODED_BYTES + " bytes");
        }
        ByteBuffer out = ByteBuffer.allocate(total);
        out.put(MAGIC);
        out.put((byte) VERSION);
        out.putShort((short) body.length);
        out.put(body);
        out.putInt((int) crc(body, 0, body.length));
        return ChromaResult.ok(out.array());
    }

    /** Strictly decodes a scene. Never throws. */
    public static ChromaResult<Scene> decode(byte[] data) {
        if (data == null) {
            return ChromaResult.fail(ChromaError.TRUNCATED, "no data");
        }
        if (data.length > MAX_ENCODED_BYTES) {
            return ChromaResult.fail(ChromaError.OVERSIZED, "input exceeds " + MAX_ENCODED_BYTES + " bytes");
        }
        if (data.length < HEADER_BYTES + CRC_BYTES) {
            return ChromaResult.fail(ChromaError.TRUNCATED, "shorter than header");
        }
        for (int i = 0; i < MAGIC.length; i++) {
            if (data[i] != MAGIC[i]) {
                return ChromaResult.fail(ChromaError.BAD_MAGIC, "not a scene");
            }
        }
        int version = data[MAGIC.length] & 0xFF;
        if (version != VERSION) {
            return ChromaResult.fail(ChromaError.UNSUPPORTED_VERSION, "version " + version);
        }
        int payloadLength = (data[MAGIC.length + 1] & 0xFF) << 8 | data[MAGIC.length + 2] & 0xFF;
        int expected = HEADER_BYTES + payloadLength + CRC_BYTES;
        if (data.length < expected) {
            return ChromaResult.fail(ChromaError.TRUNCATED, "payload truncated");
        }
        if (data.length > expected) {
            return ChromaResult.fail(ChromaError.TRAILING_DATA, "bytes after CRC");
        }
        long storedCrc = ByteBuffer.wrap(data, HEADER_BYTES + payloadLength, CRC_BYTES).getInt() & 0xFFFFFFFFL;
        if (storedCrc != crc(data, HEADER_BYTES, payloadLength)) {
            return ChromaResult.fail(ChromaError.BAD_CRC, "checksum mismatch");
        }
        try {
            return readPayload(new Reader(data, HEADER_BYTES, HEADER_BYTES + payloadLength));
        } catch (Truncated e) {
            return ChromaResult.fail(ChromaError.TRUNCATED, "payload ends early");
        } catch (IllegalArgumentException e) {
            // Belt and braces: every range is checked below, so this is only reachable on a missed bound.
            return ChromaResult.fail(ChromaError.OUT_OF_RANGE, "invalid field");
        }
    }

    private static ChromaResult<Scene> readPayload(Reader in) {
        int nameLength = in.u8();
        byte[] nameBytes = in.bytes(nameLength);
        String name;
        try {
            name = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(nameBytes)).toString();
        } catch (CharacterCodingException e) {
            return ChromaResult.fail(ChromaError.BAD_NAME, "name is not UTF-8");
        }
        if (!Scene.sanitiseName(name).equals(name)) {
            return ChromaResult.fail(ChromaError.BAD_NAME, "name is not sanitised");
        }
        int zoneCount = in.u8();
        if (zoneCount > ZoneAssignment.MAX_ZONES) {
            return ChromaResult.fail(ChromaError.OUT_OF_RANGE, "zone count " + zoneCount);
        }
        List<ZoneAssignment> zones = new ArrayList<>(zoneCount);
        int previousZone = -1;
        for (int i = 0; i < zoneCount; i++) {
            int zone = in.u8();
            int channel = in.u8();
            if (zone >= ZoneAssignment.MAX_ZONES || zone <= previousZone) {
                return ChromaResult.fail(ChromaError.OUT_OF_RANGE, "zone id " + zone);
            }
            if (channel >= ZoneAssignment.MAX_CHANNELS) {
                return ChromaResult.fail(ChromaError.OUT_OF_RANGE, "channel " + channel);
            }
            previousZone = zone;
            int kind = in.u8();
            ZoneProgram program;
            if (kind == KIND_TIMELINE) {
                int flags = in.u8();
                if ((flags & ~FLAG_LOOP) != 0) {
                    return ChromaResult.fail(ChromaError.OUT_OF_RANGE, "timeline flags");
                }
                int length = in.u16();
                int keyCount = in.u8();
                if (keyCount < 1 || keyCount > Timeline.MAX_KEYS) {
                    return ChromaResult.fail(ChromaError.OUT_OF_RANGE, "key count " + keyCount);
                }
                List<Keyframe> keys = new ArrayList<>(keyCount);
                for (int k = 0; k < keyCount; k++) {
                    int tick = in.u16();
                    int packed = in.i32();
                    Easing easing = Easing.byId(in.u8());
                    if (!ChromaColor.isValidPacked(packed) || easing == null) {
                        return ChromaResult.fail(ChromaError.OUT_OF_RANGE, "key field");
                    }
                    keys.add(new Keyframe(tick, ChromaColor.fromPacked(packed), easing));
                }
                if (Timeline.validate(keys, length) != null) {
                    return ChromaResult.fail(ChromaError.OUT_OF_RANGE, "timeline bounds");
                }
                program = new Timeline(keys, length, (flags & FLAG_LOOP) != 0);
            } else if (kind == KIND_EFFECT) {
                EffectType type = EffectType.byId(in.u8());
                int primary = in.i32();
                int secondary = in.i32();
                int period = in.u16();
                int amplitude = in.u8();
                int axis = in.u8();
                int wavelength = in.u8();
                int density = in.u8();
                int sky = in.i32();
                int daylight = in.u8();
                if (type == null || !ChromaColor.isValidPacked(primary) || !ChromaColor.isValidPacked(secondary)
                        || !ChromaColor.isValidPacked(sky)
                        || EffectParams.validate(period, amplitude, axis, wavelength, density, daylight) != null) {
                    return ChromaResult.fail(ChromaError.OUT_OF_RANGE, "effect field");
                }
                program = new EffectProgram(type, EffectParams.builder()
                        .primary(ChromaColor.fromPacked(primary))
                        .secondary(ChromaColor.fromPacked(secondary))
                        .periodTicks(period).amplitude(amplitude).axis(axis).wavelength(wavelength)
                        .density(density).sky(ChromaColor.fromPacked(sky)).daylight(daylight)
                        .build());
            } else {
                return ChromaResult.fail(ChromaError.OUT_OF_RANGE, "zone kind " + kind);
            }
            zones.add(new ZoneAssignment(zone, channel, program));
        }
        if (in.remaining() != 0) {
            return ChromaResult.fail(ChromaError.TRAILING_DATA, "bytes after last zone");
        }
        return ChromaResult.ok(new Scene(name, zones));
    }

    /** Share code: {@value #SHARE_PREFIX} followed by URL-safe unpadded base64 of the binary. */
    public static ChromaResult<String> toShareCode(Scene scene) {
        ChromaResult<byte[]> encoded = encode(scene);
        if (!encoded.isOk()) {
            return ChromaResult.fail(encoded.error(), encoded.detail());
        }
        return ChromaResult.ok(SHARE_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(encoded.value()));
    }

    /** Parses a share code (surrounding whitespace ignored). Never throws. */
    public static ChromaResult<Scene> fromShareCode(String code) {
        if (code == null) {
            return ChromaResult.fail(ChromaError.BAD_SHARE_CODE, "no code");
        }
        String trimmed = code.trim();
        if (trimmed.length() > MAX_SHARE_CODE_LENGTH) {
            return ChromaResult.fail(ChromaError.OVERSIZED, "code too long");
        }
        if (!trimmed.startsWith(SHARE_PREFIX)) {
            return ChromaResult.fail(ChromaError.BAD_SHARE_CODE, "missing prefix");
        }
        byte[] data;
        try {
            data = Base64.getUrlDecoder().decode(trimmed.substring(SHARE_PREFIX.length()));
        } catch (IllegalArgumentException e) {
            return ChromaResult.fail(ChromaError.BAD_SHARE_CODE, "not base64");
        }
        return decode(data);
    }

    private static long crc(byte[] data, int offset, int length) {
        CRC32 crc = new CRC32();
        crc.update(data, offset, length);
        return crc.getValue();
    }

    private static void writeU16(ByteArrayOutputStream out, int v) {
        out.write(v >>> 8 & 0xFF);
        out.write(v & 0xFF);
    }

    private static void writeI32(ByteArrayOutputStream out, int v) {
        out.write(v >>> 24 & 0xFF);
        out.write(v >>> 16 & 0xFF);
        out.write(v >>> 8 & 0xFF);
        out.write(v & 0xFF);
    }

    /** Thrown internally when a read runs past the payload. */
    private static final class Truncated extends RuntimeException {
        private static final long serialVersionUID = 1L;

        Truncated() {
            super(null, null, false, false);
        }
    }

    private static final class Reader {
        private final byte[] data;
        private final int end;
        private int pos;

        Reader(byte[] data, int start, int end) {
            this.data = data;
            this.pos = start;
            this.end = end;
        }

        int remaining() {
            return end - pos;
        }

        private void need(int n) {
            if (end - pos < n) {
                throw new Truncated();
            }
        }

        int u8() {
            need(1);
            return data[pos++] & 0xFF;
        }

        int u16() {
            need(2);
            int v = (data[pos] & 0xFF) << 8 | data[pos + 1] & 0xFF;
            pos += 2;
            return v;
        }

        int i32() {
            need(4);
            int v = (data[pos] & 0xFF) << 24 | (data[pos + 1] & 0xFF) << 16 | (data[pos + 2] & 0xFF) << 8
                    | data[pos + 3] & 0xFF;
            pos += 4;
            return v;
        }

        byte[] bytes(int n) {
            need(n);
            byte[] out = new byte[n];
            System.arraycopy(data, pos, out, 0, n);
            pos += n;
            return out;
        }
    }
}
