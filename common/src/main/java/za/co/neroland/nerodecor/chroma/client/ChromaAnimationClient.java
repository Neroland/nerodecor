package za.co.neroland.nerodecor.chroma.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.state.BlockState;

import za.co.neroland.nerodecor.chroma.ChromaResult;
import za.co.neroland.nerodecor.chroma.RelativePos;
import za.co.neroland.nerodecor.chroma.Scene;
import za.co.neroland.nerodecor.chroma.SceneAnimator;
import za.co.neroland.nerodecor.chroma.SceneSyncCodec;
import za.co.neroland.nerodecor.chroma.server.ChromaAnimationConfig;
import za.co.neroland.nerodecor.client.render.bhc.QuadBuffer;

/**
 * The client animation layer (Stage 4). Per hub that plays a scene it keeps the zone map, the hub's animated blocks,
 * a cache of their exposed tinted model quads ({@link ChromaModelQuads}) and one colour per block. Every client tick
 * it re-evaluates the colours from the scene's pure functions of {@code gameTime - startTick} (no packets), and every
 * frame the hub's block-entity renderer ({@link ChromaHubLayerRenderer}) writes the cached quads with those colours in
 * one emissive pass. The chunk mesh is never rebuilt for animation; the static colours stay underneath and show
 * whenever the layer does not draw (disabled, over budget, far away). Light levels are never touched.
 *
 * <p>Budgets ({@link ChromaLayerBudget}): 4,096 blocks per hub, 32 hubs drawn (nearest first), 0.5 ms per frame;
 * over budget, far hubs update every other tick or are skipped. Client thread only; nothing is logged or sent.
 */
public final class ChromaAnimationClient {

    /** Ticks between full cache rebuilds of a playing hub (neighbour changes, model states). */
    private static final int REBUILD_INTERVAL = 100;
    private static final int MUSIC_RADIUS = 8;
    private static final int MUSIC_SCAN_INTERVAL = 40;
    /** Faces are drawn this far outside the block so they never z-fight the chunk mesh. */
    private static final float NUDGE = 0.002F;

    private static final class Hub {
        int ax;
        int ay;
        int az;
        Scene scene;
        long startTick;
        long seed;
        boolean animationsOff;
        SceneAnimator animator;
        // zone map (every linked block)
        int[] mapRel = new int[0];
        int[] mapZone = new int[0];
        int[] pendingRel;
        int[] pendingZone;
        int pendingCount;
        // animated blocks
        int count;
        int[] rel = new int[0];
        int[] zone = new int[0];
        int[] colours = new int[0];
        boolean coloursValid;
        // quad cache
        float[] quads = new float[0];
        int[] quadBlock = new int[0];
        boolean[] quadTranslucent = new boolean[0];
        int quadCount;
        boolean dirty = true;
        long lastRebuild = Long.MIN_VALUE / 2;
        // budget
        ChromaLayerBudget.Mode mode = ChromaLayerBudget.Mode.FULL;
        long drawNanos;
        long updateNanos;
        long frameNanos;
        int frames;
        // music
        final ChromaMusicPulse music = new ChromaMusicPulse();
        long[] notes = new long[0];
        boolean[] notePowered = new boolean[0];
        long[] jukeboxes = new long[0];
        long lastMusicScan = Long.MIN_VALUE / 2;

        long anchorKey() {
            return BlockPos.asLong(ax, ay, az);
        }
    }

    private static ChromaLayerProfile profile;

    /** Opens a measurement window (tests); replaces any open one. Client thread. */
    public static void startProfile() {
        profile = new ChromaLayerProfile();
    }

    /** Closes the window and returns it, or {@code null} when none was open. Client thread. */
    public static ChromaLayerProfile stopProfile() {
        ChromaLayerProfile p = profile;
        profile = null;
        return p;
    }

    private static final Map<Long, Hub> HUBS = new HashMap<>();
    private static final Map<Long, Hub> BY_ANCHOR = new HashMap<>();
    private static Object boundLevel;

    private ChromaAnimationClient() {
    }

    /** Client bootstrap: the hub's layer renderer and the packet seam. Called once from client render registration. */
    public static void register() {
        za.co.neroland.nerodecor.client.ClientRenderRegistry.blockEntityRenderer(
                () -> za.co.neroland.nerodecor.registry.ChromaContent.HUB_BLOCK_ENTITY.get(), ChromaHubLayerRenderer::new);
        init();
    }

    /** Wires the packet seam. */
    public static void init() {
        za.co.neroland.nerodecor.chroma.net.ChromaScenePayloads.setClientHandlers(ChromaAnimationClient::onScene,
                ChromaAnimationClient::onZones);
    }

    // --- packets ----------------------------------------------------------------------------------------------------

    public static void onScene(byte[] data) {
        bind();
        ChromaResult<SceneSyncCodec.SceneState> decoded = SceneSyncCodec.decodeScene(data);
        if (!decoded.isOk()) {
            return;
        }
        SceneSyncCodec.SceneState s = decoded.value();
        Hub hub = HUBS.computeIfAbsent(s.network(), id -> new Hub());
        BY_ANCHOR.remove(hub.anchorKey(), hub);
        hub.ax = s.anchorX();
        hub.ay = s.anchorY();
        hub.az = s.anchorZ();
        BY_ANCHOR.put(hub.anchorKey(), hub);
        boolean sceneChanged = hub.scene == null ? s.scene() != null : !hub.scene.equals(s.scene());
        hub.scene = s.scene();
        hub.startTick = s.startTick();
        hub.seed = s.seed();
        hub.animationsOff = s.animationsOff();
        hub.animator = null;
        hub.coloursValid = false;
        if (sceneChanged) {
            hub.dirty = true;
        }
    }

    public static void onZones(byte[] data) {
        bind();
        ChromaResult<SceneSyncCodec.ZonePart> decoded = SceneSyncCodec.decodeZonePart(data);
        if (!decoded.isOk()) {
            return;
        }
        SceneSyncCodec.ZonePart part = decoded.value();
        Hub hub = HUBS.computeIfAbsent(part.network(), id -> new Hub());
        if (part.first() || hub.pendingRel == null) {
            hub.pendingRel = new int[Math.max(16, part.offsets().length)];
            hub.pendingZone = new int[hub.pendingRel.length];
            hub.pendingCount = 0;
        }
        int need = hub.pendingCount + part.offsets().length;
        if (need > hub.pendingRel.length) {
            hub.pendingRel = Arrays.copyOf(hub.pendingRel, Math.max(need, hub.pendingRel.length * 2));
            hub.pendingZone = Arrays.copyOf(hub.pendingZone, hub.pendingRel.length);
        }
        System.arraycopy(part.offsets(), 0, hub.pendingRel, hub.pendingCount, part.offsets().length);
        System.arraycopy(part.zones(), 0, hub.pendingZone, hub.pendingCount, part.zones().length);
        hub.pendingCount = need;
        if (part.last()) {
            hub.mapRel = Arrays.copyOf(hub.pendingRel, hub.pendingCount);
            hub.mapZone = Arrays.copyOf(hub.pendingZone, hub.pendingCount);
            hub.pendingRel = null;
            hub.pendingZone = null;
            hub.dirty = true;
            hub.coloursValid = false;
        }
    }

    /** The server dropped the hub for this client. */
    public static void drop(long network) {
        Hub hub = HUBS.remove(network);
        if (hub != null) {
            BY_ANCHOR.remove(hub.anchorKey(), hub);
        }
    }

    public static void clear() {
        HUBS.clear();
        BY_ANCHOR.clear();
    }

    private static void bind() {
        Object level = Minecraft.getInstance().level;
        if (level != boundLevel) {
            boundLevel = level;
            clear();
        }
    }

    private static boolean clientDisabled() {
        return ChromaAnimationConfig.DISABLE_ANIMATION.get() || !ChromaAnimationConfig.ANIMATIONS.get();
    }

    // --- tick -------------------------------------------------------------------------------------------------------

    /** Client tick: budget plan, cache rebuilds, music, colour evaluation. */
    public static void tick() {
        bind();
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        Player player = mc.player;
        if (level == null || player == null || HUBS.isEmpty() || clientDisabled()) {
            return;
        }
        List<Hub> playing = new ArrayList<>();
        for (Hub hub : HUBS.values()) {
            if (hub.scene != null && !hub.animationsOff) {
                playing.add(hub);
            }
        }
        if (playing.isEmpty()) {
            return;
        }
        double[] dist = new double[playing.size()];
        long[] draw = new long[dist.length];
        long[] update = new long[dist.length];
        for (int i = 0; i < dist.length; i++) {
            Hub hub = playing.get(i);
            double dx = hub.ax + 0.5 - player.getX();
            double dy = hub.ay + 0.5 - player.getY();
            double dz = hub.az + 0.5 - player.getZ();
            dist[i] = dx * dx + dy * dy + dz * dz;
            if (hub.frames > 0) {
                hub.drawNanos = ChromaLayerBudget.smooth(hub.drawNanos, hub.frameNanos / hub.frames);
            }
            hub.frameNanos = 0L;
            hub.frames = 0;
            draw[i] = hub.drawNanos;
            update[i] = hub.updateNanos;
        }
        ChromaLayerBudget.Mode[] modes = ChromaLayerBudget.plan(dist, draw, update,
                ChromaLayerBudget.FRAME_BUDGET_NANOS);
        long now = level.getGameTime();
        boolean reduced = ChromaAnimationConfig.REDUCED_MOTION.get();
        boolean music = ChromaAnimationConfig.MUSIC_REACTIVE.get();
        boolean rebuilt = false;
        boolean scanned = false;
        // nearest first, so the one rebuild per tick goes where it is seen
        Integer[] order = new Integer[dist.length];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> Double.compare(dist[a], dist[b]));
        for (int k : order) {
            Hub hub = playing.get(k);
            hub.mode = modes[k];
            if (profile != null) {
                profile.mode(hub.mode);
                profile.size(hub.count, hub.quadCount);
            }
            if (hub.mode == ChromaLayerBudget.Mode.SKIP) {
                continue;
            }
            if (hub.animator == null || hub.animator.reducedMotion() != reduced) {
                hub.animator = new SceneAnimator(hub.scene, reduced);
                hub.dirty = true;
            }
            if (!rebuilt && (hub.dirty || now - hub.lastRebuild >= REBUILD_INTERVAL)) {
                rebuild(level, hub, now);
                rebuilt = true;
            }
            if (hub.dirty) {
                continue; // waits for its rebuild; the static colour shows meanwhile
            }
            double gain = 1.0;
            if (music) {
                if (!scanned && now - hub.lastMusicScan >= MUSIC_SCAN_INTERVAL) {
                    scanMusic(level, hub, now);
                    scanned = true;
                }
                gain = pollMusic(level, hub, now, reduced);
            }
            if (!hub.coloursValid || ChromaLayerBudget.updatesOn(hub.mode, now, hub.seed)) {
                long t0 = System.nanoTime();
                hub.animator.fill(now - hub.startTick, hub.seed, hub.rel, hub.zone, hub.count, gain, hub.colours);
                long spent = System.nanoTime() - t0;
                hub.updateNanos = ChromaLayerBudget.smooth(hub.updateNanos, spent);
                if (profile != null) {
                    profile.update(spent);
                }
                hub.coloursValid = true;
            }
        }
    }

    /** Re-collects the hub's animated blocks and their exposed tinted quads. */
    private static void rebuild(ClientLevel level, Hub hub, long now) {
        hub.lastRebuild = now;
        hub.dirty = false;
        hub.coloursValid = false;
        SceneAnimator animator = hub.animator;
        int n = 0;
        int[] rel = new int[Math.min(hub.mapRel.length, ChromaLayerBudget.MAX_BLOCKS_PER_HUB)];
        int[] zone = new int[rel.length];
        for (int i = 0; i < hub.mapRel.length && n < rel.length; i++) {
            if (animator.animates(hub.mapZone[i])) {
                rel[n] = hub.mapRel[i];
                zone[n] = hub.mapZone[i];
                n++;
            }
        }
        hub.rel = rel;
        hub.zone = zone;
        hub.count = n;
        hub.colours = new int[n];
        float[] quads = new float[Math.max(1, n) * 2 * ChromaModelQuads.FLOATS_PER_QUAD];
        hub.quadBlock = new int[Math.max(16, n * 2)];
        hub.quadTranslucent = new boolean[hub.quadBlock.length];
        int[] q = {0};
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < n; i++) {
            int rx = RelativePos.x(rel[i]);
            int ry = RelativePos.y(rel[i]);
            int rz = RelativePos.z(rel[i]);
            pos.set(hub.ax + rx, hub.ay + ry, hub.az + rz);
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            final int block = i;
            final float[][] qs = {quads};
            ChromaModelQuads.collect(level, pos.immutable(), state, (xyzuv, trans) -> {
                int at = q[0];
                if ((at + 1) * ChromaModelQuads.FLOATS_PER_QUAD > qs[0].length) {
                    qs[0] = Arrays.copyOf(qs[0], qs[0].length * 2);
                }
                writeQuad(qs[0], at * ChromaModelQuads.FLOATS_PER_QUAD, xyzuv, rx, ry, rz);
                hub.quadBlock = grow(hub.quadBlock, at);
                hub.quadTranslucent = grow(hub.quadTranslucent, at);
                hub.quadBlock[at] = block;
                hub.quadTranslucent[at] = trans;
                q[0]++;
            });
            quads = qs[0];
        }
        hub.quads = quads;
        hub.quadCount = q[0];
    }

    private static int[] grow(int[] a, int index) {
        return index < a.length ? a : Arrays.copyOf(a, Math.max(16, a.length * 2));
    }

    private static boolean[] grow(boolean[] a, int index) {
        return index < a.length ? a : Arrays.copyOf(a, Math.max(16, a.length * 2));
    }

    /** Copies a quad into hub space, pushed {@link #NUDGE} out along its face normal. */
    private static void writeQuad(float[] out, int o, float[] src, int rx, int ry, int rz) {
        float ax = src[5] - src[0];
        float ay = src[6] - src[1];
        float az = src[7] - src[2];
        float bx = src[10] - src[0];
        float by = src[11] - src[1];
        float bz = src[12] - src[2];
        float nx = ay * bz - az * by;
        float ny = az * bx - ax * bz;
        float nz = ax * by - ay * bx;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len > 1.0E-6F) {
            nx = nx / len * NUDGE;
            ny = ny / len * NUDGE;
            nz = nz / len * NUDGE;
        } else {
            nx = ny = nz = 0.0F;
        }
        for (int c = 0; c < 4; c++) {
            int s = c * 5;
            int d = o + s;
            out[d] = src[s] + rx + nx;
            out[d + 1] = src[s + 1] + ry + ny;
            out[d + 2] = src[s + 2] + rz + nz;
            out[d + 3] = src[s + 3];
            out[d + 4] = src[s + 4];
        }
    }

    // --- music (client only) ----------------------------------------------------------------------------------------

    private static void scanMusic(ClientLevel level, Hub hub, long now) {
        hub.lastMusicScan = now;
        List<Long> notes = new ArrayList<>();
        List<Long> jukes = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -MUSIC_RADIUS; dx <= MUSIC_RADIUS; dx++) {
            for (int dy = -MUSIC_RADIUS; dy <= MUSIC_RADIUS; dy++) {
                for (int dz = -MUSIC_RADIUS; dz <= MUSIC_RADIUS; dz++) {
                    pos.set(hub.ax + dx, hub.ay + dy, hub.az + dz);
                    if (!level.isLoaded(pos)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(pos);
                    if (state.getBlock() instanceof NoteBlock) {
                        notes.add(pos.asLong());
                    } else if (state.getBlock() instanceof JukeboxBlock) {
                        jukes.add(pos.asLong());
                    }
                }
            }
        }
        hub.notes = notes.stream().mapToLong(Long::longValue).toArray();
        hub.notePowered = new boolean[hub.notes.length];
        for (int i = 0; i < hub.notes.length; i++) {
            BlockState s = level.getBlockState(BlockPos.of(hub.notes[i]));
            hub.notePowered[i] = s.getBlock() instanceof NoteBlock && s.getValue(NoteBlock.POWERED);
        }
        hub.jukeboxes = jukes.stream().mapToLong(Long::longValue).toArray();
    }

    /** Note blocks turning powered start a pulse; a jukebox holding a record swells. */
    private static double pollMusic(ClientLevel level, Hub hub, long now, boolean reduced) {
        for (int i = 0; i < hub.notes.length; i++) {
            BlockState s = level.getBlockState(BlockPos.of(hub.notes[i]));
            boolean powered = s.getBlock() instanceof NoteBlock && s.getValue(NoteBlock.POWERED);
            if (powered && !hub.notePowered[i]) {
                hub.music.onNote(now);
            }
            hub.notePowered[i] = powered;
        }
        boolean jukebox = false;
        for (long p : hub.jukeboxes) {
            BlockState s = level.getBlockState(BlockPos.of(p));
            if (s.getBlock() instanceof JukeboxBlock && s.getValue(JukeboxBlock.HAS_RECORD)) {
                jukebox = true;
                break;
            }
        }
        return hub.music.gain(now, jukebox, reduced);
    }

    // --- render -----------------------------------------------------------------------------------------------------

    /** Writes the hub's animated quads (hub-local space) for this frame. Called by the hub's renderer. */
    public static void build(BlockPos hubPos, QuadBuffer out) {
        if (clientDisabled()) {
            return;
        }
        Hub hub = BY_ANCHOR.get(hubPos.asLong());
        if (hub == null || hub.scene == null || hub.animationsOff || hub.mode == ChromaLayerBudget.Mode.SKIP
                || hub.dirty || !hub.coloursValid) {
            return;
        }
        long t0 = System.nanoTime();
        for (int q = 0; q < hub.quadCount; q++) {
            int rgb = hub.colours[hub.quadBlock[q]];
            if (rgb < 0) {
                continue;
            }
            QuadBuffer.Mode mode = hub.quadTranslucent[q] ? QuadBuffer.Mode.TRANSLUCENT : QuadBuffer.Mode.CUTOUT;
            out.rawQuad(mode, hub.quads, q * ChromaModelQuads.FLOATS_PER_QUAD, 0xFF000000 | rgb,
                    QuadBuffer.FULL_BRIGHT);
        }
        long spent = System.nanoTime() - t0;
        hub.frameNanos += spent;
        hub.frames++;
        if (profile != null) {
            profile.frame(spent);
        }
    }

    /** Whether any quad is cached for the hub at {@code hubPos} (renderer early-out). */
    public static boolean playingAt(BlockPos hubPos) {
        Hub hub = BY_ANCHOR.get(hubPos.asLong());
        return hub != null && hub.scene != null && hub.quadCount > 0;
    }
}
