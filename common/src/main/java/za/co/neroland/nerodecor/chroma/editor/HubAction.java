package za.co.neroland.nerodecor.chroma.editor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import za.co.neroland.nerodecor.chroma.SceneCodec;

/**
 * One request from the Chroma Hub screen that is not a colour edit (colour edits ride the ops packet). The server
 * re-checks the sender's role for every kind and answers with a fresh {@link HubDetail}.
 *
 * @param network network id
 * @param kind    what to do
 * @param a       zone, slot, channel or role, by kind
 * @param b       channel or flag, by kind
 * @param player  target player (trust only), else {@code null}
 * @param text    zone or scene name (sanitised again on the server), else empty
 * @param blob    scene bytes ({@link Kind#SCENE_STORE}), else empty
 */
public record HubAction(long network, Kind kind, int a, int b, UUID player, String text, byte[] blob) {

    public static final int MAX_BYTES = SceneCodec.MAX_ENCODED_BYTES + 256;
    public static final int MAX_TEXT = 64;

    /** Ids are wire values: append only. */
    public enum Kind {
        /** Resend the detail. */
        REFRESH,
        /** Rename zone {@code a} to {@code text}. */
        RENAME_ZONE,
        /** Put zone {@code a} on channel {@code b}. */
        SET_CHANNEL,
        /** Add a zone named {@code text} on channel {@code b}. */
        ADD_ZONE,
        /** Grant role ordinal {@code a} (0 = revoke) to {@code player}. */
        SET_TRUST,
        /** Auto-adopt on ({@code b = 1}) or off. */
        SET_AUTO_ADOPT,
        /** Activate scene slot {@code a} (-1 = static colours). */
        SCENE_ACTIVATE,
        /** Rename scene slot {@code a}. */
        SCENE_RENAME,
        /** Store the scene in {@code blob} in slot {@code a}. */
        SCENE_STORE;

        private static final Kind[] VALUES = values();

        public static Kind byId(int id) {
            return id >= 0 && id < VALUES.length ? VALUES[id] : null;
        }
    }

    public HubAction {
        text = text == null ? "" : text;
        blob = blob == null ? new byte[0] : blob.clone();
    }

    public static HubAction simple(long network, Kind kind, int a, int b) {
        return new HubAction(network, kind, a, b, null, "", null);
    }

    public static HubAction named(long network, Kind kind, int a, int b, String text) {
        return new HubAction(network, kind, a, b, null, text, null);
    }

    public static HubAction trust(long network, UUID player, int role) {
        return new HubAction(network, Kind.SET_TRUST, role, 0, player, "", null);
    }

    public static HubAction storeScene(long network, int slot, byte[] scene) {
        return new HubAction(network, Kind.SCENE_STORE, slot, 0, null, "", scene);
    }

    public byte[] encode() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeLong(network);
            out.writeByte(kind.ordinal());
            out.writeInt(a);
            out.writeInt(b);
            out.writeBoolean(player != null);
            if (player != null) {
                out.writeLong(player.getMostSignificantBits());
                out.writeLong(player.getLeastSignificantBits());
            }
            String t = text.length() > MAX_TEXT ? text.substring(0, MAX_TEXT) : text;
            out.writeUTF(t);
            out.writeShort(blob.length);
            out.write(blob);
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
        return bytes.toByteArray();
    }

    /** Never throws; empty on malformed or oversized input. */
    public static Optional<HubAction> decode(byte[] data) {
        if (data == null || data.length > MAX_BYTES) {
            return Optional.empty();
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            long network = in.readLong();
            Kind kind = Kind.byId(in.readUnsignedByte());
            int a = in.readInt();
            int b = in.readInt();
            UUID player = in.readBoolean() ? new UUID(in.readLong(), in.readLong()) : null;
            String text = in.readUTF();
            int len = in.readUnsignedShort();
            if (kind == null || text.length() > MAX_TEXT || len > SceneCodec.MAX_ENCODED_BYTES) {
                return Optional.empty();
            }
            byte[] blob = new byte[len];
            in.readFully(blob);
            if (in.available() > 0) {
                return Optional.empty();
            }
            return Optional.of(new HubAction(network, kind, a, b, player, text, blob));
        } catch (IOException | RuntimeException malformed) {
            return Optional.empty();
        }
    }
}
