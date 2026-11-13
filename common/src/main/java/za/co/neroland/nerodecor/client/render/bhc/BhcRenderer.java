package za.co.neroland.nerodecor.client.render.bhc;

import com.mojang.blaze3d.vertex.PoseStack;

//? if >=1.21.9 {
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
//?} else {
/*import net.minecraft.client.renderer.MultiBufferSource;
*///?}
//? if >=26.1 {
import net.minecraft.client.renderer.state.level.CameraRenderState;
//?} else if >=1.21.9 {
/*import net.minecraft.client.renderer.state.CameraRenderState;
*///?}
//? if >=1.21.11 {
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
//?} else {
/*import net.minecraft.client.renderer.RenderType;
*///?}
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Version glue for the bulkhead / holo / command block-entity renderers. Subclasses only {@link #build} quads into a
 * {@link QuadBuffer}; this class carries the three render APIs the supported range spans:
 *
 * <ul>
 *   <li>1.21.9+: render-state extraction (quads are recorded during {@code extractRenderState}) and
 *       {@code SubmitNodeCollector.submitCustomGeometry} on submit;</li>
 *   <li>1.21.5 - 1.21.8: immediate {@code render(..., Vec3 camera)} into a {@code MultiBufferSource};</li>
 *   <li>1.21.1 - 1.21.4: the same without the camera argument.</li>
 * </ul>
 *
 * <p>Culling: vanilla's per-renderer distance check uses {@link #viewDistance()} (never blanket-true); a renderer
 * that records nothing (idle doors, empty screens) submits nothing. {@link #getRenderBoundingBox} widens NeoForge's
 * frustum box for parts drawn outside their own block.
 */
//? if >=1.21.9 {
public abstract class BhcRenderer<T extends BlockEntity> implements BlockEntityRenderer<T, BhcRenderer.State> {
//?} else {
/*public abstract class BhcRenderer<T extends BlockEntity> implements BlockEntityRenderer<T> {
*///?}

    /** Records this frame's quads for {@code be}. {@code light} is the packed world light at the block. */
    protected abstract void build(T be, float partialTick, QuadBuffer out, int light);

    /** Blocks; vanilla skips the renderer beyond this distance. */
    protected int viewDistance() {
        return 48;
    }

    /** Extra reach of the drawn geometry beyond the block, in blocks, for the frustum box. */
    protected double reach() {
        return 0.0;
    }

    @Override
    public int getViewDistance() {
        return viewDistance();
    }

    /** NeoForge's per-block-entity frustum box (an unused method on the other loaders). */
    public AABB getRenderBoundingBox(T be) {
        BlockPos p = be.getBlockPos();
        double r = reach();
        return new AABB(p.getX() - r, p.getY() - r, p.getZ() - r, p.getX() + 1 + r, p.getY() + 1 + r, p.getZ() + 1 + r);
    }

    protected static RenderType renderType(QuadBuffer.Mode mode) {
        //? if >=26.1 {
        return mode == QuadBuffer.Mode.CUTOUT ? RenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS)
                : RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS);
        //?} else if >=1.21.11 {
        /*return mode == QuadBuffer.Mode.CUTOUT ? RenderTypes.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS)
                : RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS);
        *///?} else {
        /*return mode == QuadBuffer.Mode.CUTOUT ? RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS)
                : RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS);
        *///?}
    }

    //? if >=1.21.9 {
    /** Render state: the quads recorded at extraction. */
    public static class State extends BlockEntityRenderState {
        final QuadBuffer quads = new QuadBuffer();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(T be, State state, float partialTick, Vec3 camera,
                                   ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTick, camera, breakProgress);
        state.quads.clear();
        build(be, partialTick, state.quads, state.lightCoords);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        for (QuadBuffer.Mode mode : QuadBuffer.Mode.values()) {
            if (!state.quads.isEmpty(mode)) {
                collector.submitCustomGeometry(poseStack, renderType(mode),
                        (pose, consumer) -> state.quads.emit(pose, consumer, mode));
            }
        }
    }
    //?}

    //? if <1.21.9 {
    /*private final QuadBuffer quads = new QuadBuffer();

    private void draw(T be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        quads.clear();
        build(be, partialTick, quads, light);
        for (QuadBuffer.Mode mode : QuadBuffer.Mode.values()) {
            if (!quads.isEmpty(mode)) {
                quads.emit(poseStack.last(), buffers.getBuffer(renderType(mode)), mode);
            }
        }
    }
    *///?}

    //? if >=1.21.5 && <1.21.9 {
    /*@Override
    public void render(T be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay,
                       Vec3 camera) {
        draw(be, partialTick, poseStack, buffers, light);
    }
    *///?}

    //? if <1.21.5 {
    /*@Override
    public void render(T be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        draw(be, partialTick, poseStack, buffers, light);
    }
    *///?}
}
