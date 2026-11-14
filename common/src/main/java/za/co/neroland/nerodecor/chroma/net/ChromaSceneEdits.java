package za.co.neroland.nerodecor.chroma.net;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.nerodecor.chroma.ChromaResult;
import za.co.neroland.nerodecor.chroma.Scene;
import za.co.neroland.nerodecor.chroma.SceneCodec;
import za.co.neroland.nerodecor.chroma.SceneSlots;
import za.co.neroland.nerodecor.chroma.server.ChromaAction;
import za.co.neroland.nerodecor.chroma.server.ChromaConfig;
import za.co.neroland.nerodecor.chroma.server.ChromaNetworks;
import za.co.neroland.nerodecor.chroma.server.ChromaReach;
import za.co.neroland.nerodecor.chroma.server.ChromaScenes;
import za.co.neroland.nerodecor.chroma.server.ChromaVerdict;
import za.co.neroland.nerodecor.chroma.server.HubNetwork;

/**
 * The serverbound scene-edit message (used by the hub editor): layout {@code u8 op, i64 network, i8 slot}, then
 * <ul>
 *   <li>{@link #OP_SAVE}: {@code u8 activate, u16 length, scene bytes} (Stage 1 {@link SceneCodec} encoding);</li>
 *   <li>{@link #OP_ACTIVATE} / {@link #OP_CLEAR}: nothing more;</li>
 *   <li>{@link #OP_TRIGGER}: {@code u8 trigger, u8 hasMaps, [16 x i8 redstone map, 4 x i8 daylight map]}.</li>
 * </ul>
 * The server validates everything (role, slot, codec CRC, flash clamp) and answers refusals on the action bar.
 * Nothing is logged.
 */
public final class ChromaSceneEdits {

    public static final int OP_SAVE = 0;
    public static final int OP_ACTIVATE = 1;
    public static final int OP_CLEAR = 2;
    public static final int OP_TRIGGER = 3;

    private ChromaSceneEdits() {
    }

    // --- encoding (client) ------------------------------------------------------------------------------------------

    public static ChromaResult<byte[]> encodeSave(long network, int slot, Scene scene, boolean activate) {
        ChromaResult<byte[]> bytes = SceneCodec.encode(scene);
        if (!bytes.isOk()) {
            return bytes;
        }
        ByteArrayOutputStream out = header(OP_SAVE, network, slot);
        out.write(activate ? 1 : 0);
        byte[] b = bytes.value();
        out.write(b.length >>> 8 & 0xFF);
        out.write(b.length & 0xFF);
        out.write(b, 0, b.length);
        return ChromaResult.ok(out.toByteArray());
    }

    public static byte[] encodeActivate(long network, int slot) {
        return header(OP_ACTIVATE, network, slot).toByteArray();
    }

    public static byte[] encodeClear(long network, int slot) {
        return header(OP_CLEAR, network, slot).toByteArray();
    }

    /** {@code redstone} (16) and {@code daylight} (4) may both be null to keep the hub's maps. */
    public static byte[] encodeTrigger(long network, SceneSlots.Trigger trigger, int[] redstone, int[] daylight) {
        ByteArrayOutputStream out = header(OP_TRIGGER, network, 0);
        out.write(trigger.ordinal());
        boolean maps = redstone != null && daylight != null;
        out.write(maps ? 1 : 0);
        if (redstone != null && daylight != null) { // repeated (not `maps`) so null analysis can follow it
            for (int i = 0; i < SceneSlots.REDSTONE_LEVELS; i++) {
                out.write(redstone[i]);
            }
            for (int i = 0; i < SceneSlots.DaylightBand.values().length; i++) {
                out.write(daylight[i]);
            }
        }
        return out.toByteArray();
    }

    /** Client helper: sends a save request for the hub editor. */
    public static boolean sendSave(long network, int slot, Scene scene, boolean activate) {
        ChromaResult<byte[]> data = encodeSave(network, slot, scene, activate);
        if (data.isOk()) {
            ChromaNet.sendToServer(new ChromaScenePayloads.SceneEdit(data.value()));
        }
        return data.isOk();
    }

    private static ByteArrayOutputStream header(int op, long network, int slot) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(op);
        for (int s = 56; s >= 0; s -= 8) {
            out.write((int) (network >>> s) & 0xFF);
        }
        out.write(slot);
        return out;
    }

    // --- server -----------------------------------------------------------------------------------------------------

    public static void handle(ServerPlayer player, byte[] data) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        ChromaVerdict verdict = apply(level, player, data);
        if (!verdict.isOk()) {
            ChromaNetworks.tell(player, verdict);
        }
    }

    /** Decodes and applies one scene edit for {@code player}. */
    public static ChromaVerdict apply(ServerLevel level, ServerPlayer player, byte[] data) {
        if (!ChromaConfig.ENABLED.get()) {
            return ChromaVerdict.DISABLED;
        }
        if (data == null || data.length < 10) {
            return ChromaVerdict.BAD_OP;
        }
        int op = data[0] & 0xFF;
        long network = 0L;
        for (int i = 1; i <= 8; i++) {
            network = network << 8 | (data[i] & 0xFF);
        }
        int slot = data[9];
        // Hub-screen edits need the player at the hub (NeroLink's remote actions do not come through here).
        HubNetwork target = ChromaNetworks.store(level).get(network);
        if (target == null) {
            return ChromaVerdict.NO_NETWORK;
        }
        ChromaVerdict near = ChromaReach.mayEditFromScreen(player, level, target,
                op == OP_TRIGGER ? ChromaAction.MANAGE : ChromaAction.PAINT);
        if (!near.isOk()) {
            return near;
        }
        switch (op) {
            case OP_SAVE -> {
                if (data.length < 13) {
                    return ChromaVerdict.BAD_OP;
                }
                boolean activate = data[10] != 0;
                int len = (data[11] & 0xFF) << 8 | data[12] & 0xFF;
                if (data.length != 13 + len) {
                    return ChromaVerdict.BAD_OP;
                }
                ChromaResult<Scene> scene = SceneCodec.decode(Arrays.copyOfRange(data, 13, 13 + len));
                if (!scene.isOk()) {
                    return ChromaVerdict.BAD_SCENE;
                }
                return ChromaScenes.save(level, player, network, slot, scene.value(), activate);
            }
            case OP_ACTIVATE -> {
                return data.length == 10 ? ChromaScenes.activate(level, player, network, slot) : ChromaVerdict.BAD_OP;
            }
            case OP_CLEAR -> {
                if (data.length != 10) {
                    return ChromaVerdict.BAD_OP;
                }
                ChromaVerdict rate = ChromaNetworks.limiter().tryAcquire(player.getUUID(), 1, System.nanoTime());
                return rate.isOk() ? ChromaScenes.clear(ChromaNetworks.store(level), network, player.getUUID(), slot)
                        : rate;
            }
            case OP_TRIGGER -> {
                if (data.length < 12) {
                    return ChromaVerdict.BAD_OP;
                }
                int t = data[10] & 0xFF;
                if (t >= SceneSlots.Trigger.values().length) {
                    return ChromaVerdict.BAD_OP;
                }
                int[] redstone = null;
                int[] daylight = null;
                if (data[11] != 0) {
                    int bands = SceneSlots.DaylightBand.values().length;
                    if (data.length != 12 + SceneSlots.REDSTONE_LEVELS + bands) {
                        return ChromaVerdict.BAD_OP;
                    }
                    redstone = new int[SceneSlots.REDSTONE_LEVELS];
                    for (int i = 0; i < redstone.length; i++) {
                        redstone[i] = data[12 + i];
                    }
                    daylight = new int[bands];
                    for (int i = 0; i < bands; i++) {
                        daylight[i] = data[12 + SceneSlots.REDSTONE_LEVELS + i];
                    }
                } else if (data.length != 12) {
                    return ChromaVerdict.BAD_OP;
                }
                ChromaVerdict rate = ChromaNetworks.limiter().tryAcquire(player.getUUID(), 1, System.nanoTime());
                return rate.isOk() ? ChromaScenes.setTrigger(ChromaNetworks.store(level), network, player.getUUID(),
                        SceneSlots.Trigger.values()[t], redstone, daylight) : rate;
            }
            default -> {
                return ChromaVerdict.BAD_OP;
            }
        }
    }
}
