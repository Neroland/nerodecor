package za.co.neroland.nerodecor.chroma.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Version shim for the Chroma animation layer: the <em>tinted, exposed</em> quads of a block's baked model, as plain
 * floats. This is the only place the layer touches the model API, which moves three times across the supported
 * range:
 *
 * <ul>
 *   <li>26.x: {@code ModelManager.getBlockStateModelSet()}, {@code BlockStateModelPart}, a {@code BakedQuad} record of
 *       vector positions, packed UVs and a {@code MaterialInfo} (tint index, chunk layer);</li>
 *   <li>1.21.11: {@code BlockRenderDispatcher.getBlockModel} parts with the vector/packed-UV {@code BakedQuad};</li>
 *   <li>1.21.5 - 1.21.10: the same parts with an {@code int[]} vertex {@code BakedQuad} record;</li>
 *   <li>1.21.1 - 1.21.4: a {@code BakedModel} queried per cull face, {@code BakedQuad} class with getters.</li>
 * </ul>
 *
 * <p>Translucency is only known on 26.x (the quad's chunk layer); older lines draw the layer as cutout. Client only.
 */
public final class ChromaModelQuads {

    /** Floats per quad handed to the sink: 4 corners of x, y, z (block-local, 0..1) and atlas u, v. */
    public static final int FLOATS_PER_QUAD = 20;

    private static final Direction[] FACES = {null, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH,
            Direction.WEST, Direction.EAST};

    /** Receives one tinted quad; {@code xyzuv} is reused between calls (copy it). */
    @FunctionalInterface
    public interface Sink {
        void quad(float[] xyzuv, boolean translucent);
    }

    private ChromaModelQuads() {
    }

    /** Whether the block's face towards {@code face} can be seen (always true for unculled quads). */
    public static boolean faceVisible(BlockGetter level, BlockPos pos, BlockState state, Direction face) {
        if (face == null) {
            return true;
        }
        BlockPos neighbour = pos.relative(face);
        //? if >=1.21.2 {
        return Block.shouldRenderFace(state, level.getBlockState(neighbour), face);
        //?} else {
        /*return Block.shouldRenderFace(state, level, pos, face, neighbour);
        *///?}
    }

    /** Feeds every tinted quad of {@code state}'s model at {@code pos} whose cull face is visible. */
    public static void collect(BlockGetter level, BlockPos pos, BlockState state, Sink sink) {
        float[] buf = new float[FLOATS_PER_QUAD];
        long seed = state.getSeed(pos);
        RandomSource random = RandomSource.create(seed);
        Minecraft mc = Minecraft.getInstance();
        //? if >=26.1 {
        net.minecraft.client.renderer.block.dispatch.BlockStateModel model =
                mc.getModelManager().getBlockStateModelSet().get(state);
        List<net.minecraft.client.renderer.block.dispatch.BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(random, parts);
        for (Direction face : FACES) {
            if (!faceVisible(level, pos, state, face)) {
                continue;
            }
            for (net.minecraft.client.renderer.block.dispatch.BlockStateModelPart part : parts) {
                for (net.minecraft.client.resources.model.geometry.BakedQuad q : part.getQuads(face)) {
                    if (q.materialInfo().tintIndex() < 0) {
                        continue;
                    }
                    for (int c = 0; c < 4; c++) {
                        org.joml.Vector3fc p = q.position(c);
                        long uv = q.packedUV(c);
                        put(buf, c, p.x(), p.y(), p.z(), net.minecraft.client.model.geom.builders.UVPair.unpackU(uv),
                                net.minecraft.client.model.geom.builders.UVPair.unpackV(uv));
                    }
                    sink.quad(buf, q.materialInfo().layer()
                            == net.minecraft.client.renderer.chunk.ChunkSectionLayer.TRANSLUCENT);
                }
            }
        }
        //?} else if >=1.21.11 {
        /*net.minecraft.client.renderer.block.model.BlockStateModel model = mc.getBlockRenderer().getBlockModel(state);
        List<net.minecraft.client.renderer.block.model.BlockModelPart> parts = new ArrayList<>();
        model.collectParts(random, parts);
        for (Direction face : FACES) {
            if (!faceVisible(level, pos, state, face)) {
                continue;
            }
            for (net.minecraft.client.renderer.block.model.BlockModelPart part : parts) {
                for (net.minecraft.client.renderer.block.model.BakedQuad q : part.getQuads(face)) {
                    if (q.tintIndex() < 0) {
                        continue;
                    }
                    org.joml.Vector3fc[] ps = {q.position0(), q.position1(), q.position2(), q.position3()};
                    long[] uvs = {q.packedUV0(), q.packedUV1(), q.packedUV2(), q.packedUV3()};
                    for (int c = 0; c < 4; c++) {
                        put(buf, c, ps[c].x(), ps[c].y(), ps[c].z(),
                                net.minecraft.client.model.geom.builders.UVPair.unpackU(uvs[c]),
                                net.minecraft.client.model.geom.builders.UVPair.unpackV(uvs[c]));
                    }
                    sink.quad(buf, false);
                }
            }
        }
        *///?} else if >=1.21.5 {
        /*net.minecraft.client.renderer.block.model.BlockStateModel model = mc.getBlockRenderer().getBlockModel(state);
        List<net.minecraft.client.renderer.block.model.BlockModelPart> parts = new ArrayList<>();
        model.collectParts(random, parts);
        for (Direction face : FACES) {
            if (!faceVisible(level, pos, state, face)) {
                continue;
            }
            for (net.minecraft.client.renderer.block.model.BlockModelPart part : parts) {
                for (net.minecraft.client.renderer.block.model.BakedQuad q : part.getQuads(face)) {
                    if (q.tintIndex() < 0) {
                        continue;
                    }
                    putVertices(buf, q.vertices());
                    sink.quad(buf, false);
                }
            }
        }
        *///?} else {
        /*net.minecraft.client.resources.model.BakedModel model = mc.getBlockRenderer().getBlockModel(state);
        for (Direction face : FACES) {
            if (!faceVisible(level, pos, state, face)) {
                continue;
            }
            random.setSeed(seed);
            for (net.minecraft.client.renderer.block.model.BakedQuad q : model.getQuads(state, face, random)) {
                if (q.getTintIndex() < 0) {
                    continue;
                }
                putVertices(buf, q.getVertices());
                sink.quad(buf, false);
            }
        }
        *///?}
    }

    private static void put(float[] buf, int corner, float x, float y, float z, float u, float v) {
        int o = corner * 5;
        buf[o] = x;
        buf[o + 1] = y;
        buf[o + 2] = z;
        buf[o + 3] = u;
        buf[o + 4] = v;
    }

    /** The {@code BLOCK} vertex format: 8 ints per vertex (x, y, z, colour, u, v, light, normal). */
    @SuppressWarnings("unused")
    private static void putVertices(float[] buf, int[] v) {
        for (int c = 0; c < 4; c++) {
            int o = c * 8;
            put(buf, c, Float.intBitsToFloat(v[o]), Float.intBitsToFloat(v[o + 1]), Float.intBitsToFloat(v[o + 2]),
                    Float.intBitsToFloat(v[o + 4]), Float.intBitsToFloat(v[o + 5]));
        }
    }
}
