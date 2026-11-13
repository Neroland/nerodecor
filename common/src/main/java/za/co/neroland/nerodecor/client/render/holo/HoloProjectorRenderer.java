package za.co.neroland.nerodecor.client.render.holo;

import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.level.block.state.BlockState;

import za.co.neroland.nerodecor.client.render.bhc.BhcRenderer;
import za.co.neroland.nerodecor.client.render.bhc.QuadBuffer;
import za.co.neroland.nerodecor.client.render.bhc.Sprites;
import za.co.neroland.nerodecor.content.block.bulkhead.BhcProperties;
import za.co.neroland.nerodecor.content.block.holo.HoloProjectorBlockEntity;

/** The projector's light cone and its turning wireframe gem (authored floor-mounted, projecting up). */
public class HoloProjectorRenderer extends BhcRenderer<HoloProjectorBlockEntity> {

    static final int TINT = 0x7FE6FF;

    public HoloProjectorRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected double reach() {
        return 1.5;
    }

    @Override
    protected void build(HoloProjectorBlockEntity be, float partialTick, QuadBuffer out, int light) {
        BlockState state = be.getBlockState();
        if (!be.enabled() || be.getLevel() == null || !state.hasProperty(BhcProperties.FACE)) {
            return;
        }
        float now = be.getLevel().getGameTime() + partialTick;
        TextureAtlasSprite beam = Sprites.get("holo", "holo_projector_beam");
        TextureAtlasSprite idle = Sprites.get("holo", "holo_projector_idle");
        TextureAtlasSprite shimmer = Sprites.get("holo", "holo_projector_shimmer");
        TextureAtlasSprite scan = Sprites.get("holo", "holo_projector_scanline");
        int cone = 0x99000000 | TINT;
        int gem = 0xE6000000 | TINT;
        out.push().mount(state.getValue(BhcProperties.FACE));
        // light cone: a square frustum from the lens (6 px wide at y = 6) to 14 px wide at y = 26
        float b0 = 5 / 16.0F;
        float b1 = 11 / 16.0F;
        float t0 = 1 / 16.0F;
        float t1 = 15 / 16.0F;
        float yb = 6 / 16.0F;
        float yt = 26 / 16.0F;
        out.quad(QuadBuffer.Mode.TRANSLUCENT, beam, new float[] {b0, yb, b1, b1, yb, b1, t1, yt, t1, t0, yt, t1},
                new float[] {0, 1, 1, 1, 1, 0, 0, 0}, cone, QuadBuffer.FULL_BRIGHT);
        out.quad(QuadBuffer.Mode.TRANSLUCENT, beam, new float[] {b1, yb, b0, b0, yb, b0, t0, yt, t0, t1, yt, t0},
                new float[] {0, 1, 1, 1, 1, 0, 0, 0}, cone, QuadBuffer.FULL_BRIGHT);
        out.quad(QuadBuffer.Mode.TRANSLUCENT, beam, new float[] {b1, yb, b1, b1, yb, b0, t1, yt, t0, t1, yt, t1},
                new float[] {0, 1, 1, 1, 1, 0, 0, 0}, cone, QuadBuffer.FULL_BRIGHT);
        out.quad(QuadBuffer.Mode.TRANSLUCENT, beam, new float[] {b0, yb, b0, b0, yb, b1, t0, yt, t1, t0, yt, t0},
                new float[] {0, 1, 1, 1, 1, 0, 0, 0}, cone, QuadBuffer.FULL_BRIGHT);
        // idle gem: two crossed panels turning slowly, with scanline and shimmer overlays
        float bob = (float) Math.sin(now * 0.08F) * 0.03F;
        out.push().translate(0.5F, 1.15F + bob, 0.5F).rotateY(now * 2.0F);
        for (int i = 0; i < 2; i++) {
            out.push().rotateY(i * 90.0F);
            out.panelZ(QuadBuffer.Mode.TRANSLUCENT, idle, -0.3F, 0.3F, -0.3F, 0.3F, 0.0F, 0, 1, 0, 1, gem,
                    QuadBuffer.FULL_BRIGHT);
            out.panelZ(QuadBuffer.Mode.TRANSLUCENT, scan, -0.3F, 0.3F, -0.3F, 0.3F, 0.002F, 0, 1, 0, 1, 0x66000000 | TINT,
                    QuadBuffer.FULL_BRIGHT);
            out.panelZ(QuadBuffer.Mode.TRANSLUCENT, shimmer, -0.3F, 0.3F, -0.3F, 0.3F, -0.002F, 0, 1, 0, 1,
                    0xCC000000 | TINT, QuadBuffer.FULL_BRIGHT);
            out.pop();
        }
        out.pop();
        out.pop();
    }
}
