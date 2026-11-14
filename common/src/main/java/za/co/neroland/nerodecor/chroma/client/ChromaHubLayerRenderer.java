package za.co.neroland.nerodecor.chroma.client;

import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

import za.co.neroland.nerodecor.client.render.bhc.BhcRenderer;
import za.co.neroland.nerodecor.client.render.bhc.QuadBuffer;
import za.co.neroland.nerodecor.content.block.ChromaHubBlockEntity;

/**
 * Hosts the Chroma animation layer on the hub's block entity: one emissive pass per hub with the cached quads of its
 * animated blocks ({@link ChromaAnimationClient#build}). The render glue for every supported version range (1.21.1 -
 * 1.21.4 immediate, 1.21.5 - 1.21.8 immediate with camera, 1.21.9+ render-state submit) is {@link BhcRenderer}'s; this
 * class only widens the reach to the hub volume and draws off-screen, since the animated blocks surround the hub.
 */
public class ChromaHubLayerRenderer extends BhcRenderer<ChromaHubBlockEntity> {

    /** Half the largest hub volume: the frustum box covers every block the hub can drive. */
    private static final double REACH = 32.0;

    public ChromaHubLayerRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected void build(ChromaHubBlockEntity be, float partialTick, QuadBuffer out, int light) {
        ChromaAnimationClient.build(be.getBlockPos(), out);
    }

    @Override
    protected int viewDistance() {
        return 96;
    }

    @Override
    protected double reach() {
        return REACH;
    }

    //? if >=1.21.6 {
    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }
    //?} else {
    /*@Override
    public boolean shouldRenderOffScreen(ChromaHubBlockEntity be) {
        return true;
    }
    *///?}
}
