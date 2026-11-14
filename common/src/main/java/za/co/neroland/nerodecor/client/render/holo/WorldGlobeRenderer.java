package za.co.neroland.nerodecor.client.render.holo;

import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.nerodecor.client.render.bhc.BhcRenderer;
import za.co.neroland.nerodecor.client.render.bhc.QuadBuffer;
import za.co.neroland.nerodecor.client.render.bhc.Sprites;
import za.co.neroland.nerodecor.content.block.holo.WorldGlobeBlock;
import za.co.neroland.nerodecor.content.block.holo.WorldGlobeBlockEntity;

/**
 * The globe above the cradle: a UV sphere (centre y = 11.5 px, r = 4.5 px) wrapped with the world's equirectangular
 * map, its emissive night lights, and a translucent scan shell, turning slowly with game time, framed by a
 * camera-facing halo ring ({@code world_globe_halo}).
 */
public class WorldGlobeRenderer extends BhcRenderer<WorldGlobeBlockEntity> {

    private static final int SLICES = 16;
    private static final int STACKS = 8;
    private static final float CY = 11.5F / 16.0F;
    private static final float R = 4.5F / 16.0F;
    /** Half-size of the halo billboard: the ring art's inner edge (5.2 of 8 px) sits just outside the scan shell. */
    private static final float HALO = R * 1.7F;

    public WorldGlobeRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected double reach() {
        return 0.25;
    }

    @Override
    protected void build(WorldGlobeBlockEntity be, float partialTick, QuadBuffer out, int light) {
        BlockState state = be.getBlockState();
        if (be.getLevel() == null || !state.hasProperty(WorldGlobeBlock.WORLD)) {
            return;
        }
        String world = state.getValue(WorldGlobeBlock.WORLD).getSerializedName();
        float now = be.getLevel().getGameTime() + partialTick;
        out.push().translate(0.5F, CY, 0.5F).rotateY(now * 0.6F).rotateZ(-12.0F);
        sphere(out, QuadBuffer.Mode.CUTOUT, Sprites.get("holo", "world_globe_" + world), R, -1, light);
        sphere(out, QuadBuffer.Mode.CUTOUT, Sprites.get("holo", "world_globe_" + world + "_glow"), R * 1.004F, -1,
                QuadBuffer.FULL_BRIGHT);
        out.pop();
        out.push().translate(0.5F, CY, 0.5F).rotateY(-now * 1.5F);
        sphere(out, QuadBuffer.Mode.TRANSLUCENT, Sprites.get("holo", "world_globe_scan"), R * 1.08F, 0x88B8F0FF,
                QuadBuffer.FULL_BRIGHT);
        out.pop();
        halo(be.getBlockPos(), out);
    }

    /** The halo ring, turned to face the camera. */
    private void halo(BlockPos pos, QuadBuffer out) {
        Vec3 cam = camera();
        double dx = cam.x - (pos.getX() + 0.5);
        double dy = cam.y - (pos.getY() + CY);
        double dz = cam.z - (pos.getZ() + 0.5);
        float yaw = (float) Math.toDegrees(Math.atan2(dx, dz));
        float pitch = (float) Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        out.push().translate(0.5F, CY, 0.5F).rotateY(yaw).rotateX(-pitch);
        out.rectZ(QuadBuffer.Mode.TRANSLUCENT, Sprites.get("holo", "world_globe_halo"), -HALO, -HALO, HALO, HALO, 0.0F,
                0.0F, 0.0F, 1.0F, 1.0F, 0xA0B8F0FF, QuadBuffer.FULL_BRIGHT, false);
        out.pop();
    }

    private static void sphere(QuadBuffer out, QuadBuffer.Mode mode, TextureAtlasSprite sprite, float r, int argb,
                               int light) {
        for (int j = 0; j < STACKS; j++) {
            double a0 = Math.PI * j / STACKS - Math.PI / 2;
            double a1 = Math.PI * (j + 1) / STACKS - Math.PI / 2;
            float v0 = 1.0F - (float) j / STACKS;
            float v1 = 1.0F - (float) (j + 1) / STACKS;
            for (int i = 0; i < SLICES; i++) {
                double b0 = 2 * Math.PI * i / SLICES;
                double b1 = 2 * Math.PI * (i + 1) / SLICES;
                float u0 = (float) i / SLICES;
                float u1 = (float) (i + 1) / SLICES;
                out.quad(mode, sprite, new float[] {
                        p(r, a0, b0, 0), p(r, a0, b0, 1), p(r, a0, b0, 2),
                        p(r, a0, b1, 0), p(r, a0, b1, 1), p(r, a0, b1, 2),
                        p(r, a1, b1, 0), p(r, a1, b1, 1), p(r, a1, b1, 2),
                        p(r, a1, b0, 0), p(r, a1, b0, 1), p(r, a1, b0, 2)},
                        new float[] {u0, v0, u1, v0, u1, v1, u0, v1}, argb, light);
            }
        }
    }

    /** Sphere point component {@code k} (0 x, 1 y, 2 z) at latitude {@code lat}, longitude {@code lon}. */
    private static float p(float r, double lat, double lon, int k) {
        return switch (k) {
            case 0 -> (float) (r * Math.cos(lat) * Math.cos(lon));
            case 1 -> (float) (r * Math.sin(lat));
            default -> (float) (-r * Math.cos(lat) * Math.sin(lon));
        };
    }
}
