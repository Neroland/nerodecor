package za.co.neroland.nerodecor.client.render.bulkhead;

import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

import za.co.neroland.nerodecor.client.render.bhc.BhcRenderer;
import za.co.neroland.nerodecor.client.render.bhc.QuadBuffer;
import za.co.neroland.nerodecor.client.render.bhc.Sprites;
import za.co.neroland.nerodecor.content.block.bulkhead.BhcProperties;
import za.co.neroland.nerodecor.content.block.bulkhead.DoorAnimBlockEntity;
import za.co.neroland.nerodecor.content.block.bulkhead.PressureTrapdoorBlock;

/**
 * The hatch plate swinging on its south hinge (authored facing north, bottom half). The open blockstate has no static
 * plate, so this also draws the settled open plate; closed, the static model carries the look and this draws nothing.
 */
public class TrapdoorRenderer extends BhcRenderer<DoorAnimBlockEntity> {

    public TrapdoorRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected void build(DoorAnimBlockEntity be, float partialTick, QuadBuffer out, int light) {
        float o = DoorRenders.openness(be, partialTick, PressureTrapdoorBlock.DURATION);
        BlockState state = be.getBlockState();
        if (!state.hasProperty(PressureTrapdoorBlock.HALF)) {
            return;
        }
        if (o < 0.0F) {
            if (!state.getValue(PressureTrapdoorBlock.OPEN)) {
                return;
            }
            o = 1.0F;
        }
        TextureAtlasSprite top = Sprites.get("bulkhead", "pressure_trapdoor_top");
        TextureAtlasSprite bottom = Sprites.get("bulkhead", "pressure_trapdoor_bottom");
        TextureAtlasSprite side = Sprites.get("bulkhead", "pressure_trapdoor_side");
        out.push().blockRotateY(DoorRenders.yRot(state.getValue(BhcProperties.FACING)));
        if (state.getValue(PressureTrapdoorBlock.HALF) == Half.TOP) {
            out.translate(0.0F, 1.0F, 0.0F).scale(1.0F, -1.0F, 1.0F);
        }
        out.translate(0.0F, 0.0F, 1.0F).rotateX(90.0F * o).translate(0.0F, 0.0F, -1.0F);
        float h = DoorRenders.px(3);
        out.rectY(QuadBuffer.Mode.CUTOUT, top, 0, 0, 1, 1, h, 0, 0, 1, 1, -1, light, false);
        out.rectY(QuadBuffer.Mode.CUTOUT, bottom, 0, 0, 1, 1, 0, 0, 0, 1, 1, -1, light, true);
        float sv = 3 / 16.0F;
        out.panelZ(QuadBuffer.Mode.CUTOUT, side, 0, 1, 0, h, 1, 0, 1, 0, sv, -1, light);
        out.panelZ(QuadBuffer.Mode.CUTOUT, side, 1, 0, 0, h, 0, 0, 1, 0, sv, -1, light);
        out.panelX(QuadBuffer.Mode.CUTOUT, side, 0, 1, 0, h, 0, 0, 1, 0, sv, -1, light);
        out.panelX(QuadBuffer.Mode.CUTOUT, side, 1, 0, 0, h, 1, 0, 1, 0, sv, -1, light);
        out.pop();
    }
}
