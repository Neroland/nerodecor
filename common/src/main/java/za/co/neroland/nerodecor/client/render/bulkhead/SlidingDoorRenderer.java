package za.co.neroland.nerodecor.client.render.bulkhead;

import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import za.co.neroland.nerodecor.client.render.bhc.BhcRenderer;
import za.co.neroland.nerodecor.client.render.bhc.QuadBuffer;
import za.co.neroland.nerodecor.client.render.bhc.Sprites;
import za.co.neroland.nerodecor.content.block.bulkhead.DoorAnimBlockEntity;
import za.co.neroland.nerodecor.content.block.bulkhead.SlidingDoorBlock;

/** The sliding leaf of one door half while it moves (authored hinge=left, facing north; the pocket is on the west). */
public class SlidingDoorRenderer extends BhcRenderer<DoorAnimBlockEntity> {

    public SlidingDoorRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected void build(DoorAnimBlockEntity be, float partialTick, QuadBuffer out, int light) {
        float o = DoorRenders.openness(be, partialTick, SlidingDoorBlock.DURATION);
        BlockState state = be.getBlockState();
        if (o < 0.0F || !(state.getBlock() instanceof SlidingDoorBlock)) {
            return;
        }
        boolean lower = state.getValue(SlidingDoorBlock.HALF) == DoubleBlockHalf.LOWER;
        String half = lower ? "lower" : "upper";
        TextureAtlasSprite leaf = Sprites.get("bulkhead", "sliding_door_leaf_" + half);
        TextureAtlasSprite glow = Sprites.get("bulkhead", "sliding_door_leaf_" + half + "_glow");
        TextureAtlasSprite jamb = Sprites.get("bulkhead", "sliding_door_jamb");
        float y0 = lower ? 1 : 0;
        float y1 = lower ? 16 : 14;
        float x1 = 16.0F - 12.0F * o;           // leading edge: 16 shut, 4 open
        float w = (x1 - 2.0F) / 16.0F;           // visible leaf width as a texture fraction
        float v0 = (16 - y1) / 16.0F;
        float v1 = (16 - y0) / 16.0F;
        out.push().blockRotateY(DoorRenders.yRot(state.getValue(SlidingDoorBlock.FACING)));
        if (state.getValue(SlidingDoorBlock.HINGE) == DoorHingeSide.RIGHT) {
            out.translate(1.0F, 0.0F, 0.0F).scale(-1.0F, 1.0F, 1.0F);
        }
        float ax = DoorRenders.px(2);
        float bx = DoorRenders.px(x1);
        float by = DoorRenders.px(y0);
        float ty = DoorRenders.px(y1);
        for (float z : new float[] {DoorRenders.px(6.5F), DoorRenders.px(9.5F)}) {
            // u runs from the leading edge (x1, u = 0) back into the pocket (x = 2, u = w) on both faces
            out.panelZ(QuadBuffer.Mode.CUTOUT, leaf, bx, ax, by, ty, z, 0.0F, w, v0, v1, -1, light);
            out.panelZ(QuadBuffer.Mode.CUTOUT, glow, bx, ax, by, ty, z + (z < 0.5F ? -0.001F : 0.001F),
                    0.0F, w, v0, v1, -1, QuadBuffer.FULL_BRIGHT);
        }
        out.panelX(QuadBuffer.Mode.CUTOUT, jamb, DoorRenders.px(6.5F), DoorRenders.px(9.5F), by, ty, bx,
                8 / 16.0F, 11 / 16.0F, v0, v1, -1, light);
        out.pop();
    }
}
