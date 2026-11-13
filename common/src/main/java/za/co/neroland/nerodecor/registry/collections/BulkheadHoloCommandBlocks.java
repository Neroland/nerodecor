package za.co.neroland.nerodecor.registry.collections;

/**
 * Work package: the bulkhead, holo and command collections (resources from the matching modules under
 * {@code tools/collections/}). Owned by one agent; keep every registration for these collections in this class.
 *
 * <ul>
 *   <li>Blocks: {@code CollectionRegistries.block("id", key -> new SomeBlock(Properties.of().setId(key)...))}. The id
 *       must match the collection module's {@code Block(id=...)}.</li>
 *   <li>Block entities: {@code CollectionRegistries.BLOCK_ENTITIES.register(...)} (resolve blocks with {@code .get()}
 *       inside the factory).</li>
 *   <li>Entities: {@code CollectionRegistries.ENTITY_TYPES.register(...)}.</li>
 *   <li>Render types, block entity renderers and entity renderers:
 *       {@link za.co.neroland.nerodecor.client.collections.BulkheadHoloCommandClient}.</li>
 * </ul>
 */
public final class BulkheadHoloCommandBlocks {

    private BulkheadHoloCommandBlocks() {
    }

    /** Called once from {@link CollectionRegistries#init()}. */
    public static void init() {
        // Registrations for this package go here.
    }
}
