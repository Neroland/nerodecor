package za.co.neroland.nerodecor.client.render.bulkhead;

import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.level.block.state.BlockState;

import za.co.neroland.nerodecor.client.render.bhc.BhcRenderer;
import za.co.neroland.nerodecor.client.render.bhc.QuadBuffer;
import za.co.neroland.nerodecor.client.render.bhc.Sprites;
import za.co.neroland.nerodecor.content.block.bulkhead.BhcProperties;
import za.co.neroland.nerodecor.content.block.bulkhead.DoorAnimBlockEntity;
import za.co.neroland.nerodecor.content.block.bulkhead.IrisHatchBlock;

/** Six iris blades turning and retracting under the rim while the hatch opens or closes (authored floor-mounted). */
public class IrisHatchRenderer extends BhcRenderer<DoorAnimBlockEntity> {

    public IrisHatchRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected void build(DoorAnimBlockEntity be, float partialTick, QuadBuffer out, int light) {
        float o = DoorRenders.openness(be, partialTick, IrisHatchBlock.DURATION);
        BlockState state = be.getBlockState();
        if (o < 0.0F || !state.hasProperty(BhcProperties.FACE)) {
            return;
        }
        TextureAtlasSprite blade = Sprites.get("bulkhead", "iris_hatch_blade");
        float y = DoorRenders.px(1.5F);
        out.push().mount(state.getValue(BhcProperties.FACE));
        for (int i = 0; i < 6; i++) {
            float angle = i * 60.0F + o * 55.0F;
            double rad = Math.toRadians(i * 60.0F + 30.0F);
            float push = DoorRenders.px(5.5F) * o;
            out.push()
                    .translate(0.5F + (float) Math.cos(rad) * push, 0.0F, 0.5F + (float) Math.sin(rad) * push)
                    .rotateY(angle)
                    .translate(-0.5F, 0.0F, -0.5F);
            out.rectY(QuadBuffer.Mode.CUTOUT, blade, 0.0F, 0.0F, 1.0F, 1.0F, y + i * 0.0005F,
                    0.0F, 0.0F, 1.0F, 1.0F, -1, light, true);
            out.pop();
        }
        out.pop();
    }
}
