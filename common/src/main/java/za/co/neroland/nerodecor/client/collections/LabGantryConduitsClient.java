package za.co.neroland.nerodecor.client.collections;

import net.minecraft.client.renderer.entity.NoopRenderer;

import za.co.neroland.nerodecor.client.ClientRenderRegistry;
import za.co.neroland.nerodecor.client.ClientRenderRegistry.Layer;
import za.co.neroland.nerodecor.registry.collections.LabGantryConduitsBlocks;

/**
 * Client half of {@link LabGantryConduitsBlocks}: chunk layers for the non-solid blocks (matching the
 * {@code render_type} the collection modules write into the models) and the seat entity's no-op renderer. Client only;
 * never referenced from common code.
 */
public final class LabGantryConduitsClient {

    private LabGantryConduitsClient() {
    }

    /** Called once from {@link ClientRenderRegistry#bootstrap()}. */
    public static void register() {
        // lab_habitat: glass sash, glass cylinder, fridge windows, plant planes.
        ClientRenderRegistry.blockLayer(LabGantryConduitsBlocks.FUME_HOOD, Layer.CUTOUT);
        ClientRenderRegistry.blockLayer(LabGantryConduitsBlocks.SPECIMEN_TANK, Layer.CUTOUT);
        ClientRenderRegistry.blockLayer(LabGantryConduitsBlocks.SAMPLE_FRIDGE, Layer.CUTOUT);
        ClientRenderRegistry.blockLayer(LabGantryConduitsBlocks.PLANTER, Layer.CUTOUT);
        // gantry: mesh, grates, treads, rails, rungs and lattice are cutout textures.
        ClientRenderRegistry.blockLayer(LabGantryConduitsBlocks.CATWALK, Layer.CUTOUT);
        ClientRenderRegistry.blockLayer(LabGantryConduitsBlocks.CATWALK_STAIRS, Layer.CUTOUT);
        ClientRenderRegistry.blockLayer(LabGantryConduitsBlocks.RAILING, Layer.CUTOUT);
        ClientRenderRegistry.blockLayer(LabGantryConduitsBlocks.GANTRY_LADDER, Layer.CUTOUT);
        ClientRenderRegistry.blockLayer(LabGantryConduitsBlocks.FLOOR_GRATE, Layer.CUTOUT);
        ClientRenderRegistry.blockLayer(LabGantryConduitsBlocks.FLOOR_GRATE_SLAB, Layer.CUTOUT);
        ClientRenderRegistry.blockLayer(LabGantryConduitsBlocks.TRUSS_BEAM, Layer.CUTOUT);
        // conduits: valve handwheel spokes, cable planes; emissive glow overlays are cutout too.
        ClientRenderRegistry.blockLayer(LabGantryConduitsBlocks.PIPE_VALVE, Layer.CUTOUT);
        ClientRenderRegistry.blockLayer(LabGantryConduitsBlocks.PIPE_GAUGE, Layer.CUTOUT);
        ClientRenderRegistry.blockLayer(LabGantryConduitsBlocks.HANGING_CABLES, Layer.CUTOUT);
        ClientRenderRegistry.blockLayer(LabGantryConduitsBlocks.COOLANT_TUBE, Layer.CUTOUT);
        ClientRenderRegistry.blockLayer(LabGantryConduitsBlocks.BEAM_EMITTER_PROP, Layer.CUTOUT);

        ClientRenderRegistry.entityRenderer(LabGantryConduitsBlocks.SEAT, NoopRenderer::new);
    }
}
