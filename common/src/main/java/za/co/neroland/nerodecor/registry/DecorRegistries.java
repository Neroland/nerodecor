package za.co.neroland.nerodecor.registry;

/**
 * Aggregates NeroDecor's cross-loader registrations, called once from
 * {@code NeroDecorCommon.init()}. Order matters on the eager (Fabric) loader: blocks before
 * their block items; the creative tab registers last.
 */
public final class DecorRegistries {

    private DecorRegistries() {
    }

    public static void init() {
        ModSounds.init();
        DecorBlocks.init();
        LuminousBlocks.init();
        ChromaContent.initBlocks();
        // 0.8.0 collection work packages (registry/collections): blocks join DecorBlocks.ALL before the items.
        za.co.neroland.nerodecor.registry.collections.CollectionRegistries.init();
        ModDataComponents.init();
        ModItems.init();
        ChromaContent.initItems();
        ModCreativeTab.init();
    }
}
