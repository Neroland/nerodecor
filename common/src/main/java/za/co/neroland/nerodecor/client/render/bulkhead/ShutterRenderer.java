package za.co.neroland.nerodecor.client.render.bulkhead;

import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import za.co.neroland.nerodecor.client.render.bhc.BhcRenderer;
import za.co.neroland.nerodecor.client.render.bhc.QuadBuffer;
import za.co.neroland.nerodecor.client.render.bhc.Sprites;
import za.co.neroland.nerodecor.content.block.bulkhead.BhcProperties;
import za.co.neroland.nerodecor.content.block.bulkhead.DoorAnimBlockEntity;
import za.co.neroland.nerodecor.content.block.bulkhead.ShutterBlock;

/** The roller curtain of one shutter block rolling up into (or down from) the housing (authored axis = x). */
public class ShutterRenderer extends BhcRenderer<DoorAnimBlockEntity> {

    public ShutterRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected void build(DoorAnimBlockEntity be, float partialTick, QuadBuffer out, int light) {
        float o = DoorRenders.openness(be, partialTick, ShutterBlock.DURATION);
        BlockState state = be.getBlockState();
        if (o < 0.0F || !state.hasProperty(ShutterBlock.AXIS)) {
            return;
        }
        boolean top = !state.getValue(BhcProperties.POS_LINK);
        boolean foot = !state.getValue(BhcProperties.NEG_LINK);
        float h = top ? 12.0F : 16.0F;
        float y0 = h * o;
        if (h - y0 < 0.05F) {
            return;
        }
        TextureAtlasSprite curtain = Sprites.get("bulkhead", foot ? "shutter_foot" : "shutter");
        out.push().blockRotateY(state.getValue(ShutterBlock.AXIS) == Direction.Axis.X ? 0.0F : 90.0F);
        // the curtain's lower edge rises; the texture moves with it, so the visible part is its bottom
        float vTop = 1.0F - (h - y0) / 16.0F;
        for (float z : new float[] {DoorRenders.px(7), DoorRenders.px(9)}) {
            out.panelZ(QuadBuffer.Mode.CUTOUT, curtain, DoorRenders.px(1), DoorRenders.px(15),
                    DoorRenders.px(y0), DoorRenders.px(h), z, 1 / 16.0F, 15 / 16.0F, vTop, 1.0F, -1, light);
        }
        out.pop();
    }
}
