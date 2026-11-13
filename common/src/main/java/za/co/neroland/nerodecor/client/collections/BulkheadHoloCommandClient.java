package za.co.neroland.nerodecor.client.collections;

/**
 * Client half of {@link za.co.neroland.nerodecor.registry.collections.BulkheadHoloCommandBlocks}: append this package's render
 * types, block entity renderers and entity renderers to
 * {@link za.co.neroland.nerodecor.client.ClientRenderRegistry}. Client only; never referenced from common code.
 */
public final class BulkheadHoloCommandClient {

    private BulkheadHoloCommandClient() {
    }

    /** Called once from {@link za.co.neroland.nerodecor.client.ClientRenderRegistry#bootstrap()}. */
    public static void register() {
        // e.g. ClientRenderRegistry.blockLayer(LabGantryConduitsBlocks.SOME_GLASS, ClientRenderRegistry.Layer.TRANSLUCENT);
        //      ClientRenderRegistry.blockEntityRenderer(SOME_BE, ctx -> new SomeRenderer(ctx));
        //      ClientRenderRegistry.entityRenderer(SOME_ENTITY, SomeEntityRenderer::new);
    }
}
