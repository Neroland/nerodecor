package za.co.neroland.nerodecor.chroma.client;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

import za.co.neroland.nerodecor.chroma.ChromaDelta;
import za.co.neroland.nerodecor.chroma.ChromaRemeshBudget;
import za.co.neroland.nerodecor.chroma.ChromaResult;
import za.co.neroland.nerodecor.chroma.ChromaSnapshot;
import za.co.neroland.nerodecor.chroma.ChromaSyncCodec;
import za.co.neroland.nerodecor.chroma.RelativePos;

/**
 * Client side of Chroma static sync: applies snapshots and deltas to a per-hub model, writes the resulting colours
 * into {@link ChromaColorMap} (which the tint bridge reads) and re-meshes only the sections whose colours changed,
 * through {@link ChromaRemeshBudget} (a section at most once per 500 ms, at most 64 sections per tick). Everything
 * is dropped when the client changes or leaves its level. Client thread only.
 */
public final class ChromaClientSync {

    private static final ChromaColorMap COLORS = new ChromaColorMap();
    private static final ChromaRemeshBudget REMESH = new ChromaRemeshBudget();
    private static final Map<Long, Hub> HUBS = new HashMap<>();
    private static Object boundLevel;

    /** One hub as the client knows it. */
    private static final class Hub {
        final int anchorX;
        final int anchorY;
        final int anchorZ;
        final int[] palette = new int[256];
        final Map<Integer, Integer> blocks = new HashMap<>();

        Hub(int ax, int ay, int az) {
            anchorX = ax;
            anchorY = ay;
            anchorZ = az;
            Arrays.fill(palette, -1);
        }
    }

    private ChromaClientSync() {
    }

    /** The colour map the tint bridge reads (any thread). */
    public static ChromaColorMap colors() {
        return COLORS;
    }

    public static void onSnapshot(byte[] data) {
        bind();
        ChromaResult<ChromaSnapshot> decoded = ChromaSyncCodec.decodeSnapshot(data);
        if (!decoded.isOk()) {
            return;
        }
        ChromaSnapshot s = decoded.value();
        Hub hub = HUBS.get(s.network());
        if (s.replace() || hub == null || hub.anchorX != s.anchorX() || hub.anchorY != s.anchorY()
                || hub.anchorZ != s.anchorZ()) {
            if (hub != null) {
                drop(hub);
            }
            hub = new Hub(s.anchorX(), s.anchorY(), s.anchorZ());
            HUBS.put(s.network(), hub);
        }
        Arrays.fill(hub.palette, -1);
        for (int i = 0; i < s.palette().length; i++) {
            hub.palette[i] = s.palette()[i] & 0xFFFFFF;
        }
        for (int i = 0; i < s.size(); i++) {
            hub.blocks.put(s.offsets()[i], s.indices()[i]);
        }
        for (int rel : hub.blocks.keySet()) {
            paint(hub, rel);
        }
    }

    public static void onDelta(byte[] data) {
        bind();
        ChromaResult<ChromaDelta> decoded = ChromaSyncCodec.decodeDelta(data);
        if (!decoded.isOk()) {
            return;
        }
        ChromaDelta d = decoded.value();
        Hub hub = HUBS.get(d.network());
        if (hub == null) {
            return;
        }
        if (d.clear()) {
            drop(hub);
            HUBS.remove(d.network());
            return;
        }
        Set<Integer> changedIndices = new HashSet<>();
        for (int i = 0; i < d.paletteIndices().length; i++) {
            hub.palette[d.paletteIndices()[i]] = d.paletteColors()[i] & 0xFFFFFF;
            changedIndices.add(d.paletteIndices()[i]);
        }
        for (int i = 0; i < d.offsets().length; i++) {
            int rel = d.offsets()[i];
            if (d.indices()[i] == ChromaDelta.UNLINKED) {
                hub.blocks.remove(rel);
            } else {
                hub.blocks.put(rel, d.indices()[i]);
            }
            paint(hub, rel);
        }
        if (!changedIndices.isEmpty()) {
            for (Map.Entry<Integer, Integer> e : hub.blocks.entrySet()) {
                if (changedIndices.contains(e.getValue())) {
                    paint(hub, e.getKey());
                }
            }
        }
    }

    /** Client tick: release due section re-meshes. */
    public static void tick() {
        bind();
        if (REMESH.pendingCount() == 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        for (long key : REMESH.poll(System.nanoTime())) {
            int sx = ChromaColorMap.sectionX(key);
            int sy = ChromaColorMap.sectionY(key);
            int sz = ChromaColorMap.sectionZ(key);
            // ClientLevel gained setSectionRangeDirty in 1.21.2; 1.21.1 marks sections through the level renderer.
            //? if >=1.21.2 {
            level.setSectionRangeDirty(sx, sy, sz, sx, sy, sz);
            //?} else {
            /*mc.levelRenderer.setSectionDirty(sx, sy, sz);
            *///?}
        }
    }

    private static void paint(Hub hub, int rel) {
        int x = hub.anchorX + RelativePos.x(rel);
        int y = hub.anchorY + RelativePos.y(rel);
        int z = hub.anchorZ + RelativePos.z(rel);
        Integer index = hub.blocks.get(rel);
        int rgb = index == null || index < 0 || index >= hub.palette.length ? -1 : hub.palette[index];
        if (COLORS.set(x, y, z, rgb)) {
            REMESH.mark(ChromaColorMap.sectionKey(x, y, z));
        }
    }

    private static void drop(Hub hub) {
        for (int rel : hub.blocks.keySet()) {
            int x = hub.anchorX + RelativePos.x(rel);
            int y = hub.anchorY + RelativePos.y(rel);
            int z = hub.anchorZ + RelativePos.z(rel);
            if (COLORS.set(x, y, z, -1)) {
                REMESH.mark(ChromaColorMap.sectionKey(x, y, z));
            }
        }
        hub.blocks.clear();
    }

    /** Drops everything when the client's level changes (dimension change, disconnect). */
    private static void bind() {
        Object level = Minecraft.getInstance().level;
        if (level != boundLevel) {
            boundLevel = level;
            HUBS.clear();
            COLORS.clear();
            REMESH.clear();
        }
    }
}
