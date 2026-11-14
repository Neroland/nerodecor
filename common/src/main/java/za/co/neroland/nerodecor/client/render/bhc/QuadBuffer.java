package za.co.neroland.nerodecor.client.render.bhc;

import java.util.ArrayDeque;
import java.util.Arrays;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * Version-neutral quad recorder for the collection renderers. Renderers record quads in block-local space (1 unit =
 * one block) through their own matrix stack, sampling block-atlas sprites; the version glue in {@link BhcRenderer}
 * then emits them into whatever buffer the running Minecraft offers. Two batches: cutout and translucent.
 */
public final class QuadBuffer {

    public enum Mode {
        CUTOUT,
        TRANSLUCENT
    }

    /** Full-bright packed light for emissive layers. */
    public static final int FULL_BRIGHT = 0x00F000F0;
    private static final int FLOATS = 5;  // x y z u v
    private static final int INTS = 2;    // argb, light

    private final Batch[] batches = {new Batch(), new Batch()};
    private final ArrayDeque<Matrix4f> stack = new ArrayDeque<>();
    private Matrix4f pose = new Matrix4f();
    private final Vector3f tmp = new Vector3f();

    public void clear() {
        for (Batch b : batches) {
            b.quads = 0;
        }
        stack.clear();
        pose = new Matrix4f();
    }

    public boolean isEmpty(Mode mode) {
        return batches[mode.ordinal()].quads == 0;
    }

    public boolean isEmpty() {
        return isEmpty(Mode.CUTOUT) && isEmpty(Mode.TRANSLUCENT);
    }

    // --- transforms ---------------------------------------------------------------------------
    public QuadBuffer push() {
        stack.push(new Matrix4f(pose));
        return this;
    }

    public QuadBuffer pop() {
        pose = stack.isEmpty() ? new Matrix4f() : stack.pop();
        return this;
    }

    public QuadBuffer translate(float x, float y, float z) {
        pose.translate(x, y, z);
        return this;
    }

    public QuadBuffer rotateX(float degrees) {
        pose.rotateX((float) Math.toRadians(degrees));
        return this;
    }

    public QuadBuffer rotateY(float degrees) {
        pose.rotateY((float) Math.toRadians(degrees));
        return this;
    }

    public QuadBuffer rotateZ(float degrees) {
        pose.rotateZ((float) Math.toRadians(degrees));
        return this;
    }

    public QuadBuffer scale(float x, float y, float z) {
        pose.scale(x, y, z);
        return this;
    }

    /** Rotate about the block centre so a canonical north-facing part faces {@code yDegrees} like a blockstate y. */
    public QuadBuffer blockRotateY(float yDegrees) {
        translate(0.5F, 0.5F, 0.5F);
        rotateY(-yDegrees);
        translate(-0.5F, -0.5F, -0.5F);
        return this;
    }

    /** Like a blockstate x rotation about the block centre. */
    public QuadBuffer blockRotateX(float xDegrees) {
        translate(0.5F, 0.5F, 0.5F);
        rotateX(-xDegrees);
        translate(-0.5F, -0.5F, -0.5F);
        return this;
    }

    // --- quads --------------------------------------------------------------------------------
    /**
     * One quad. Corners in counter-clockwise order seen from the front; {@code uv} are 0..1 sprite-space coordinates per
     * corner (u0 v0 u1 v1 u2 v2 u3 v3).
     */
    public void quad(Mode mode, TextureAtlasSprite sprite, float[] xyz, float[] uv, int argb, int light) {
        Batch b = batches[mode.ordinal()];
        b.ensure();
        int f = b.quads * 4 * FLOATS;
        int i = b.quads * 4 * INTS;
        for (int c = 0; c < 4; c++) {
            tmp.set(xyz[c * 3], xyz[c * 3 + 1], xyz[c * 3 + 2]);
            pose.transformPosition(tmp);
            b.f[f++] = tmp.x;
            b.f[f++] = tmp.y;
            b.f[f++] = tmp.z;
            b.f[f++] = sprite.getU(uv[c * 2]);
            b.f[f++] = sprite.getV(uv[c * 2 + 1]);
            b.i[i++] = argb;
            b.i[i++] = light;
        }
        b.quads++;
    }

    /**
     * One pre-transformed quad (Chroma animation layer): {@code xyzuv[offset ..]} holds four corners of x, y, z (block
     * space, already final) and atlas u, v, so neither the pose nor a sprite lookup is applied.
     */
    public void rawQuad(Mode mode, float[] xyzuv, int offset, int argb, int light) {
        Batch b = batches[mode.ordinal()];
        b.ensure();
        int f = b.quads * 4 * FLOATS;
        int i = b.quads * 4 * INTS;
        System.arraycopy(xyzuv, offset, b.f, f, 4 * FLOATS);
        for (int c = 0; c < 4; c++) {
            b.i[i++] = argb;
            b.i[i++] = light;
        }
        b.quads++;
    }

    /**
     * Axis-aligned rectangle facing +z (towards south) at depth {@code z}, from (x0, y0) to (x1, y1), sampling the
     * sprite rectangle u0..u1, v0..v1 (v0 at the top edge). {@code doubleSided} adds the back face.
     */
    public void rectZ(Mode mode, TextureAtlasSprite sprite, float x0, float y0, float x1, float y1, float z,
                      float u0, float v0, float u1, float v1, int argb, int light, boolean doubleSided) {
        quad(mode, sprite, new float[] {x0, y0, z, x1, y0, z, x1, y1, z, x0, y1, z},
                new float[] {u0, v1, u1, v1, u1, v0, u0, v0}, argb, light);
        if (doubleSided) {
            quad(mode, sprite, new float[] {x1, y0, z, x0, y0, z, x0, y1, z, x1, y1, z},
                    new float[] {u1, v1, u0, v1, u0, v0, u1, v0}, argb, light);
        }
    }

    /** Horizontal rectangle facing up at height {@code y}; u runs with x, v with z. */
    public void rectY(Mode mode, TextureAtlasSprite sprite, float x0, float z0, float x1, float z1, float y,
                      float u0, float v0, float u1, float v1, int argb, int light, boolean doubleSided) {
        quad(mode, sprite, new float[] {x0, y, z1, x1, y, z1, x1, y, z0, x0, y, z0},
                new float[] {u0, v1, u1, v1, u1, v0, u0, v0}, argb, light);
        if (doubleSided) {
            quad(mode, sprite, new float[] {x0, y, z0, x1, y, z0, x1, y, z1, x0, y, z1},
                    new float[] {u0, v0, u1, v0, u1, v1, u0, v1}, argb, light);
        }
    }

    /**
     * A vertical panel in a plane of constant z, given by its left and right x as the viewer sees it ({@code xL} may be
     * greater than {@code xR}), sampling u from {@code uL} (left) to {@code uR} and v from {@code vT} (top) to
     * {@code vB}.
     */
    public void panelZ(Mode mode, TextureAtlasSprite sprite, float xL, float xR, float yB, float yT, float z,
                       float uL, float uR, float vT, float vB, int argb, int light) {
        quad(mode, sprite, new float[] {xL, yB, z, xR, yB, z, xR, yT, z, xL, yT, z},
                new float[] {uL, vB, uR, vB, uR, vT, uL, vT}, argb, light);
    }

    /** A vertical panel in a plane of constant x (left / right given as z values). */
    public void panelX(Mode mode, TextureAtlasSprite sprite, float zL, float zR, float yB, float yT, float x,
                       float uL, float uR, float vT, float vB, int argb, int light) {
        quad(mode, sprite, new float[] {x, yB, zL, x, yB, zR, x, yT, zR, x, yT, zL},
                new float[] {uL, vB, uR, vB, uR, vT, uL, vT}, argb, light);
    }

    /** Rotate about the block centre so the DOWN face of a floor-mounted part ends up on {@code face}. */
    public QuadBuffer mount(net.minecraft.core.Direction face) {
        translate(0.5F, 0.5F, 0.5F);
        switch (face) {
            case UP -> rotateX(180.0F);
            case NORTH -> rotateX(90.0F);
            case SOUTH -> rotateX(-90.0F);
            case WEST -> rotateZ(-90.0F);
            case EAST -> rotateZ(90.0F);
            default -> {
            }
        }
        translate(-0.5F, -0.5F, -0.5F);
        return this;
    }

    /** All six faces of a box with one sprite region on every face (good for slabs and edges). */
    public void box(Mode mode, TextureAtlasSprite front, TextureAtlasSprite edge, float x0, float y0, float z0,
                    float x1, float y1, float z1, float fu0, float fv0, float fu1, float fv1, int argb, int light) {
        // south (+z) and north (-z) faces carry the front region; north is mirrored so it reads from behind.
        rectZ(mode, front, x0, y0, x1, y1, z1, fu0, fv0, fu1, fv1, argb, light, false);
        quad(mode, front, new float[] {x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0},
                new float[] {fu0, fv1, fu1, fv1, fu1, fv0, fu0, fv0}, argb, light);
        float e0 = 0.25F;
        float e1 = 0.75F;
        quad(mode, edge, new float[] {x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0},
                new float[] {e0, e1, e1, e1, e1, e0, e0, e0}, argb, light);
        quad(mode, edge, new float[] {x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1},
                new float[] {e0, e1, e1, e1, e1, e0, e0, e0}, argb, light);
        quad(mode, edge, new float[] {x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1},
                new float[] {e0, e1, e1, e1, e1, e0, e0, e0}, argb, light);
        quad(mode, edge, new float[] {x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0},
                new float[] {e0, e1, e1, e1, e1, e0, e0, e0}, argb, light);
    }

    // --- emission -----------------------------------------------------------------------------
    /** Write one batch into {@code out} under {@code pose} (the block's render pose). */
    public void emit(PoseStack.Pose pose, VertexConsumer out, Mode mode) {
        Batch b = batches[mode.ordinal()];
        for (int q = 0; q < b.quads; q++) {
            int f = q * 4 * FLOATS;
            int i = q * 4 * INTS;
            // face normal from the first three corners
            float ax = b.f[f + FLOATS] - b.f[f];
            float ay = b.f[f + FLOATS + 1] - b.f[f + 1];
            float az = b.f[f + FLOATS + 2] - b.f[f + 2];
            float bx = b.f[f + 2 * FLOATS] - b.f[f];
            float by = b.f[f + 2 * FLOATS + 1] - b.f[f + 1];
            float bz = b.f[f + 2 * FLOATS + 2] - b.f[f + 2];
            float nx = ay * bz - az * by;
            float ny = az * bx - ax * bz;
            float nz = ax * by - ay * bx;
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len > 1.0E-6F) {
                nx /= len;
                ny /= len;
                nz /= len;
            } else {
                ny = 1.0F;
            }
            for (int c = 0; c < 4; c++) {
                int k = f + c * FLOATS;
                int m = i + c * INTS;
                out.addVertex(pose, b.f[k], b.f[k + 1], b.f[k + 2])
                        .setColor(b.i[m])
                        .setUv(b.f[k + 3], b.f[k + 4])
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(b.i[m + 1])
                        .setNormal(pose, nx, ny, nz);
            }
        }
    }

    private static final class Batch {
        float[] f = new float[64 * 4 * FLOATS];
        int[] i = new int[64 * 4 * INTS];
        int quads;

        void ensure() {
            if ((quads + 1) * 4 * FLOATS > f.length) {
                f = Arrays.copyOf(f, f.length * 2);
                i = Arrays.copyOf(i, i.length * 2);
            }
        }
    }
}
