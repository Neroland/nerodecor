package za.co.neroland.nerodecor.chroma;

import java.io.ByteArrayOutputStream;

/**
 * Minimal bounded byte writer/reader for the Chroma sync codecs: unsigned varints, zigzag varints and fixed-width
 * ints/longs. Pure Java so the codecs are unit-testable; the network layer only carries the resulting bytes.
 */
final class ChromaWire {

    private ChromaWire() {
    }

    /** Growable output. */
    static final class Out {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream(256);

        Out byteValue(int v) {
            bytes.write(v & 0xFF);
            return this;
        }

        Out varInt(int v) {
            int value = v;
            while ((value & ~0x7F) != 0) {
                bytes.write((value & 0x7F) | 0x80);
                value >>>= 7;
            }
            bytes.write(value);
            return this;
        }

        Out zigZag(int v) {
            return varInt((v << 1) ^ (v >> 31));
        }

        Out int32(int v) {
            bytes.write(v >>> 24);
            bytes.write(v >>> 16);
            bytes.write(v >>> 8);
            bytes.write(v);
            return this;
        }

        Out int64(long v) {
            int32((int) (v >>> 32));
            return int32((int) v);
        }

        int size() {
            return bytes.size();
        }

        byte[] toByteArray() {
            return bytes.toByteArray();
        }
    }

    /** Bounds-checked input; every read past the end throws {@link Truncated}. */
    static final class In {
        private final byte[] data;
        private int pos;

        In(byte[] data) {
            this.data = data;
        }

        int byteValue() {
            if (pos >= data.length) {
                throw new Truncated();
            }
            return data[pos++] & 0xFF;
        }

        int varInt() {
            int value = 0;
            for (int shift = 0; shift < 35; shift += 7) {
                int b = byteValue();
                value |= (b & 0x7F) << shift;
                if ((b & 0x80) == 0) {
                    return value;
                }
            }
            throw new Truncated();
        }

        int zigZag() {
            int raw = varInt();
            return (raw >>> 1) ^ -(raw & 1);
        }

        int int32() {
            return byteValue() << 24 | byteValue() << 16 | byteValue() << 8 | byteValue();
        }

        long int64() {
            return (long) int32() << 32 | (int32() & 0xFFFFFFFFL);
        }

        boolean atEnd() {
            return pos == data.length;
        }
    }

    /** Thrown on a short or malformed buffer; mapped to {@link ChromaError#TRUNCATED}. */
    static final class Truncated extends RuntimeException {
        private static final long serialVersionUID = 1L;

        Truncated() {
            super(null, null, false, false);
        }
    }
}
