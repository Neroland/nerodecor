package za.co.neroland.nerodecor.chroma;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Wire layout of the two Stage 4 clientbound messages. Pure Java, identical on every version and loader.
 *
 * <ul>
 *   <li><b>Scene state</b> (one small packet per scene change): {@code u8 version, i64 network, i32 anchorX, i32
 *       anchorY, i32 anchorZ, u8 flags, i8 activeSlot, i64 startTick, i64 seed, u16 sceneLength, scene bytes}. The
 *       scene bytes are the Stage 1 {@link SceneCodec} encoding (magic + CRC) of the playing scene, or empty for
 *       static.</li>
 *   <li><b>Zone map part</b> (which zone each linked block is in, sent only while a scene plays and the layout
 *       changed): {@code u8 version, i64 network, u8 flags (1 = first part, 2 = last part), u16 groups}, then per
 *       group {@code u8 zone, varint count, varint deltas} over ascending packed {@link RelativePos} offsets.</li>
 * </ul>
 *
 * <p>Privacy: network ids, hub positions, hub-relative offsets and colours only; no player data.
 */
public final class SceneSyncCodec {

    public static final int VERSION = 1;
    /** Scene-state flag: the server's {@code chroma.animations} kill-switch is off (draw static colour only). */
    public static final int FLAG_ANIMATIONS_OFF = 1;
    public static final int PART_FIRST = 1;
    public static final int PART_LAST = 2;
    /** Most block entries in one zone-map part (keeps each part well under the packet cap). */
    public static final int MAX_PART_ENTRIES = 1024;
    /** Upper bound on any message here. */
    public static final int MAX_BYTES = 8192;

    private SceneSyncCodec() {
    }

    /** Decoded scene state. {@code scene} is {@code null} when the hub is static. */
    public record SceneState(long network, int anchorX, int anchorY, int anchorZ, int flags, int activeSlot,
            long startTick, long seed, Scene scene) {

        public boolean animationsOff() {
            return (flags & FLAG_ANIMATIONS_OFF) != 0;
        }
    }

    /** Decoded zone-map part: parallel arrays of packed offsets and zones. */
    public record ZonePart(long network, boolean first, boolean last, int[] offsets, int[] zones) {
    }

    // --- scene state ------------------------------------------------------------------------------------------------

    public static ChromaResult<byte[]> encodeScene(SceneState s) {
        byte[] scene = new byte[0];
        if (s.scene() != null) {
            ChromaResult<byte[]> enc = SceneCodec.encode(s.scene());
            if (!enc.isOk()) {
                return ChromaResult.fail(enc.error(), enc.detail());
            }
            scene = enc.value();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(48 + scene.length);
        out.write(VERSION);
        writeLong(out, s.network());
        writeInt(out, s.anchorX());
        writeInt(out, s.anchorY());
        writeInt(out, s.anchorZ());
        out.write(s.flags() & 0xFF);
        out.write(s.scene() == null ? SceneSlots.STATIC : s.activeSlot());
        writeLong(out, s.startTick());
        writeLong(out, s.seed());
        out.write(scene.length >>> 8 & 0xFF);
        out.write(scene.length & 0xFF);
        out.write(scene, 0, scene.length);
        if (out.size() > MAX_BYTES) {
            return ChromaResult.fail(ChromaError.OVERSIZED, "scene state");
        }
        return ChromaResult.ok(out.toByteArray());
    }

    public static ChromaResult<SceneState> decodeScene(byte[] data) {
        if (data == null || data.length > MAX_BYTES) {
            return ChromaResult.fail(ChromaError.OVERSIZED, "scene state");
        }
        Cursor in = new Cursor(data);
        try {
            if (in.u8() != VERSION) {
                return ChromaResult.fail(ChromaError.UNSUPPORTED_VERSION, "scene state");
            }
            long network = in.i64();
            int ax = in.i32();
            int ay = in.i32();
            int az = in.i32();
            int flags = in.u8();
            int slot = (byte) in.u8();
            long start = in.i64();
            long seed = in.i64();
            int len = in.u16();
            byte[] sceneBytes = in.bytes(len);
            if (in.remaining() != 0) {
                return ChromaResult.fail(ChromaError.TRAILING_DATA, "scene state");
            }
            Scene scene = null;
            if (len > 0) {
                ChromaResult<Scene> decoded = SceneCodec.decode(sceneBytes);
                if (!decoded.isOk()) {
                    return ChromaResult.fail(decoded.error(), decoded.detail());
                }
                scene = decoded.value();
            }
            if (scene != null && !SceneSlots.isSlot(slot)) {
                return ChromaResult.fail(ChromaError.OUT_OF_RANGE, "slot");
            }
            return ChromaResult.ok(new SceneState(network, ax, ay, az, flags, scene == null ? SceneSlots.STATIC : slot,
                    start, seed, scene));
        } catch (IndexOutOfBoundsException e) {
            return ChromaResult.fail(ChromaError.TRUNCATED, "scene state");
        }
    }

    // --- zone map ---------------------------------------------------------------------------------------------------

    /** The whole zone map as one or more parts (at least one, so an empty map still resets the client). */
    public static List<byte[]> encodeZoneMap(long network, int[] offsets, int[] zones) {
        int n = Math.min(offsets.length, zones.length);
        long[] entries = new long[n];
        for (int i = 0; i < n; i++) {
            entries[i] = (long) (zones[i] & 0xFF) << 32 | (offsets[i] & 0xFFFFFFFFL);
        }
        Arrays.sort(entries); // by zone, then offset (offsets are non-negative 30-bit values)
        List<byte[]> parts = new ArrayList<>();
        int i = 0;
        do {
            int end = Math.min(n, i + MAX_PART_ENTRIES);
            int flags = (i == 0 ? PART_FIRST : 0) | (end == n ? PART_LAST : 0);
            parts.add(encodePart(network, flags, entries, i, end));
            i = end;
        } while (i < n);
        return parts;
    }

    private static byte[] encodePart(long network, int flags, long[] entries, int from, int to) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        int groups = 0;
        int i = from;
        while (i < to) {
            int zone = (int) (entries[i] >>> 32);
            int j = i;
            while (j < to && (int) (entries[j] >>> 32) == zone) {
                j++;
            }
            body.write(zone);
            writeVarInt(body, j - i);
            int prev = 0;
            for (int k = i; k < j; k++) {
                int off = (int) entries[k];
                writeVarInt(body, off - prev);
                prev = off;
            }
            groups++;
            i = j;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(body.size() + 12);
        out.write(VERSION);
        writeLong(out, network);
        out.write(flags);
        out.write(groups >>> 8 & 0xFF);
        out.write(groups & 0xFF);
        byte[] b = body.toByteArray();
        out.write(b, 0, b.length);
        return out.toByteArray();
    }

    public static ChromaResult<ZonePart> decodeZonePart(byte[] data) {
        if (data == null || data.length > MAX_BYTES) {
            return ChromaResult.fail(ChromaError.OVERSIZED, "zone map");
        }
        Cursor in = new Cursor(data);
        try {
            if (in.u8() != VERSION) {
                return ChromaResult.fail(ChromaError.UNSUPPORTED_VERSION, "zone map");
            }
            long network = in.i64();
            int flags = in.u8();
            int groups = in.u16();
            int[] offsets = new int[16];
            int[] zones = new int[16];
            int n = 0;
            for (int g = 0; g < groups; g++) {
                int zone = in.u8();
                int count = in.varInt();
                if (zone >= ZoneAssignment.MAX_ZONES || count < 0 || n + count > MAX_PART_ENTRIES) {
                    return ChromaResult.fail(ChromaError.OUT_OF_RANGE, "zone map");
                }
                int prev = 0;
                for (int k = 0; k < count; k++) {
                    int off = prev + in.varInt();
                    if (!RelativePos.isValid(off)) {
                        return ChromaResult.fail(ChromaError.OUT_OF_RANGE, "offset");
                    }
                    if (n == offsets.length) {
                        offsets = Arrays.copyOf(offsets, n * 2);
                        zones = Arrays.copyOf(zones, n * 2);
                    }
                    offsets[n] = off;
                    zones[n] = zone;
                    n++;
                    prev = off;
                }
            }
            if (in.remaining() != 0) {
                return ChromaResult.fail(ChromaError.TRAILING_DATA, "zone map");
            }
            return ChromaResult.ok(new ZonePart(network, (flags & PART_FIRST) != 0, (flags & PART_LAST) != 0,
                    Arrays.copyOf(offsets, n), Arrays.copyOf(zones, n)));
        } catch (IndexOutOfBoundsException e) {
            return ChromaResult.fail(ChromaError.TRUNCATED, "zone map");
        }
    }

    // --- primitives -------------------------------------------------------------------------------------------------

    private static void writeInt(ByteArrayOutputStream out, int v) {
        out.write(v >>> 24);
        out.write(v >>> 16 & 0xFF);
        out.write(v >>> 8 & 0xFF);
        out.write(v & 0xFF);
    }

    private static void writeLong(ByteArrayOutputStream out, long v) {
        writeInt(out, (int) (v >>> 32));
        writeInt(out, (int) v);
    }

    private static void writeVarInt(ByteArrayOutputStream out, int v) {
        while ((v & ~0x7F) != 0) {
            out.write(v & 0x7F | 0x80);
            v >>>= 7;
        }
        out.write(v);
    }

    private static final class Cursor {
        private final byte[] data;
        private int pos;

        Cursor(byte[] data) {
            this.data = data;
        }

        int remaining() {
            return data.length - pos;
        }

        int u8() {
            if (pos >= data.length) {
                throw new IndexOutOfBoundsException();
            }
            return data[pos++] & 0xFF;
        }

        int u16() {
            return u8() << 8 | u8();
        }

        int i32() {
            return u8() << 24 | u8() << 16 | u8() << 8 | u8();
        }

        long i64() {
            return (long) i32() << 32 | i32() & 0xFFFFFFFFL;
        }

        byte[] bytes(int n) {
            if (n < 0 || pos + n > data.length) {
                throw new IndexOutOfBoundsException();
            }
            byte[] out = Arrays.copyOfRange(data, pos, pos + n);
            pos += n;
            return out;
        }

        int varInt() {
            int v = 0;
            for (int shift = 0; shift < 35; shift += 7) {
                int b = u8();
                v |= (b & 0x7F) << shift;
                if ((b & 0x80) == 0) {
                    return v;
                }
            }
            throw new IndexOutOfBoundsException();
        }
    }
}
