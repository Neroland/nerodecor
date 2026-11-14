package za.co.neroland.nerodecor.chroma.editor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import za.co.neroland.nerodecor.chroma.ChromaColor;
import za.co.neroland.nerodecor.chroma.ChromaPalette;
import za.co.neroland.nerodecor.chroma.RelativePos;
import za.co.neroland.nerodecor.chroma.SceneCodec;
import za.co.neroland.nerodecor.chroma.ZoneAssignment;

/**
 * Everything the Chroma Hub screen shows about one network, sent server to client when the screen opens and after
 * every change made from it. Layout is hub-relative (never world coordinates); the trusted list (player ids and roles,
 * never names) is included only for the owner and admins, who manage it. Names are resolved on the client from its
 * own online-player list.
 *
 * @param network     network id
 * @param role        viewer's role ordinal (0 none, 1 viewer, 2 painter, 3 admin, 4 owner)
 * @param active      whether the hub is placed and applying colour
 * @param autoAdopt   auto-adopt flag
 * @param sizeX       volume width
 * @param sizeY       volume height
 * @param sizeZ       volume depth
 * @param maxBlocks   per-hub block cap
 * @param maxZones    per-hub zone cap
 * @param palette     packed palette colours
 * @param zones       zones in id order
 * @param offsets     linked blocks, packed {@link RelativePos}
 * @param values      per block: palette index {@code | zone << 8}
 * @param trusted     trusted players (owner/admin viewers only, else empty)
 * @param scenes      scene slots (empty until the hub has any)
 * @param activeScene active slot, or -1
 * @param notice      outcome of the request this detail answers: a verdict id ({@code message.nerodecor.chroma.<id>}),
 *                    or empty
 */
public record HubDetail(long network, int role, boolean active, boolean autoAdopt, int sizeX, int sizeY, int sizeZ,
        int maxBlocks, int maxZones, int[] palette, List<Zone> zones, int[] offsets, int[] values, List<Trust> trusted,
        List<Slot> scenes, int activeScene, String notice) {

    public static final int VERSION = 1;
    public static final int MAX_BYTES = 64 * 1024;
    public static final int MAX_BLOCKS = 4096;
    public static final int MAX_TRUSTED = 64;
    public static final int MAX_SLOTS = 16;
    public static final int MAX_TEXT = 32;

    public static final int ROLE_VIEWER = 1;
    public static final int ROLE_PAINTER = 2;
    public static final int ROLE_ADMIN = 3;
    public static final int ROLE_OWNER = 4;

    public record Zone(String name, int channel) {
    }

    public record Trust(UUID player, int role) {
    }

    /** A scene slot: its name and the scene's {@link SceneCodec} bytes (empty when the slot is unset). */
    public record Slot(String name, byte[] scene) {
    }

    public HubDetail {
        palette = palette.clone();
        offsets = offsets.clone();
        values = values.clone();
        zones = List.copyOf(zones);
        trusted = List.copyOf(trusted);
        scenes = List.copyOf(scenes);
        notice = notice == null ? "" : notice;
        if (offsets.length != values.length) {
            throw new IllegalArgumentException("offsets and values differ in length");
        }
    }

    /** A copy answering a request with {@code verdictId} (empty = no notice). */
    public HubDetail withNotice(String verdictId) {
        return new HubDetail(network, role, active, autoAdopt, sizeX, sizeY, sizeZ, maxBlocks, maxZones, palette, zones,
                offsets, values, trusted, scenes, activeScene, verdictId);
    }

    public int blockCount() {
        return offsets.length;
    }

    public boolean canPaint() {
        return role >= ROLE_PAINTER;
    }

    public boolean canManage() {
        return role >= ROLE_ADMIN;
    }

    public boolean canTrust() {
        return role >= ROLE_OWNER;
    }

    // --- codec ---------------------------------------------------------------------------------------------------

    public byte[] encode() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(VERSION);
            out.writeLong(network);
            out.writeByte(role);
            out.writeByte((active ? 1 : 0) | (autoAdopt ? 2 : 0));
            out.writeByte(sizeX);
            out.writeByte(sizeY);
            out.writeByte(sizeZ);
            out.writeShort(maxBlocks);
            out.writeByte(maxZones);
            out.writeShort(palette.length);
            for (int p : palette) {
                out.writeInt(p);
            }
            out.writeByte(zones.size());
            for (Zone z : zones) {
                out.writeUTF(cut(z.name()));
                out.writeByte(z.channel());
            }
            out.writeShort(offsets.length);
            for (int i = 0; i < offsets.length; i++) {
                out.writeInt(offsets[i]);
                out.writeShort(values[i]);
            }
            out.writeByte(trusted.size());
            for (Trust t : trusted) {
                out.writeLong(t.player().getMostSignificantBits());
                out.writeLong(t.player().getLeastSignificantBits());
                out.writeByte(t.role());
            }
            out.writeByte(scenes.size());
            for (Slot s : scenes) {
                out.writeUTF(cut(s.name()));
                out.writeShort(s.scene().length);
                out.write(s.scene());
            }
            out.writeByte(activeScene);
            out.writeUTF(cut(notice));
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
        return bytes.toByteArray();
    }

    /** Decodes and range-checks a detail; empty on any malformed input. Never throws. */
    public static Optional<HubDetail> decode(byte[] data) {
        if (data == null || data.length > MAX_BYTES) {
            return Optional.empty();
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            if (in.readUnsignedByte() != VERSION) {
                return Optional.empty();
            }
            long network = in.readLong();
            int role = in.readUnsignedByte();
            int flags = in.readUnsignedByte();
            int sx = in.readUnsignedByte();
            int sy = in.readUnsignedByte();
            int sz = in.readUnsignedByte();
            int maxBlocks = in.readUnsignedShort();
            int maxZones = in.readUnsignedByte();
            int paletteSize = in.readUnsignedShort();
            if (role > ROLE_OWNER || paletteSize > ChromaPalette.MAX_ENTRIES) {
                return Optional.empty();
            }
            int[] palette = new int[paletteSize];
            for (int i = 0; i < paletteSize; i++) {
                palette[i] = in.readInt();
                if (!ChromaColor.isValidPacked(palette[i])) {
                    return Optional.empty();
                }
            }
            int zoneCount = in.readUnsignedByte();
            if (zoneCount > ZoneAssignment.MAX_ZONES) {
                return Optional.empty();
            }
            List<Zone> zones = new ArrayList<>();
            for (int i = 0; i < zoneCount; i++) {
                String name = in.readUTF();
                int channel = in.readUnsignedByte();
                if (name.length() > MAX_TEXT * 2 || channel >= ZoneAssignment.MAX_CHANNELS) {
                    return Optional.empty();
                }
                zones.add(new Zone(name, channel));
            }
            int blocks = in.readUnsignedShort();
            if (blocks > MAX_BLOCKS) {
                return Optional.empty();
            }
            int[] offsets = new int[blocks];
            int[] values = new int[blocks];
            for (int i = 0; i < blocks; i++) {
                offsets[i] = in.readInt();
                values[i] = in.readUnsignedShort();
                if (!RelativePos.isValid(offsets[i]) || (values[i] >>> 8) >= ZoneAssignment.MAX_ZONES) {
                    return Optional.empty();
                }
            }
            int trustCount = in.readUnsignedByte();
            if (trustCount > MAX_TRUSTED) {
                return Optional.empty();
            }
            List<Trust> trusted = new ArrayList<>();
            for (int i = 0; i < trustCount; i++) {
                UUID id = new UUID(in.readLong(), in.readLong());
                int r = in.readUnsignedByte();
                if (r < ROLE_VIEWER || r > ROLE_ADMIN) {
                    return Optional.empty();
                }
                trusted.add(new Trust(id, r));
            }
            int slots = in.readUnsignedByte();
            if (slots > MAX_SLOTS) {
                return Optional.empty();
            }
            List<Slot> scenes = new ArrayList<>();
            for (int i = 0; i < slots; i++) {
                String name = in.readUTF();
                int len = in.readUnsignedShort();
                if (name.length() > MAX_TEXT * 2 || len > SceneCodec.MAX_ENCODED_BYTES) {
                    return Optional.empty();
                }
                byte[] scene = new byte[len];
                in.readFully(scene);
                scenes.add(new Slot(name, scene));
            }
            int activeScene = in.readByte();
            String notice = in.readUTF();
            if (activeScene < -1 || activeScene >= slots || notice.length() > MAX_TEXT * 2 || in.available() > 0) {
                return Optional.empty();
            }
            return Optional.of(new HubDetail(network, role, (flags & 1) != 0, (flags & 2) != 0, sx, sy, sz,
                    maxBlocks, maxZones, palette, zones, offsets, values, trusted, scenes, activeScene, notice));
        } catch (IOException | RuntimeException malformed) {
            return Optional.empty();
        }
    }

    private static String cut(String s) {
        if (s == null) {
            return "";
        }
        return s.codePointCount(0, s.length()) <= MAX_TEXT ? s : s.substring(0, s.offsetByCodePoints(0, MAX_TEXT));
    }
}
