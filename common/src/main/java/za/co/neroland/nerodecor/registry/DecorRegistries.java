package za.co.neroland.nerodecor.registry;

/**
 * Aggregates NeroDecor's cross-loader registrations, called once from
 * {@code NeroDecorCommon.init()}. Order matters on the eager (Fabric) loader: blocks before
 * their block items; the creative-tab contribution runs last.
 */
public final class DecorRegistries {

    private DecorRegistries() {
    }

    public static void init() {
        DecorBlocks.init();
        ModDataComponents.init();
        ModItems.init();
        ModItems.addToCreativeTab();
    }
}
