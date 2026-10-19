package za.co.neroland.nerodecor.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

import za.co.neroland.nerodecor.NeroDecorCommon;
import za.co.neroland.nerodecor.registry.RegistrationProvider.RegistryEntry;

/**
 * NeroDecor's own creative tab, registered cross-loader via {@link RegistrationProvider} over the
 * vanilla {@code CREATIVE_MODE_TAB} registry. Every Nero mod owns its tab; nothing is added to Core's
 * "Neroland" tab, which lists Core's own items only. Contents are the decor block items in
 * registration order (mirrors {@link DecorBlocks#ALL}), resolved lazily when the tab is built.
 */
public final class ModCreativeTab {

    public static final RegistrationProvider<CreativeModeTab> TABS =
            RegistrationProvider.get(Registries.CREATIVE_MODE_TAB, NeroDecorCommon.MOD_ID);

    // NOTE: vanilla CreativeModeTab.builder takes (Row, column); the no-arg overload and
    // withTabsBefore/After are NeoForge-only extensions, so they are avoided here (common = raw vanilla).
    public static final RegistryEntry<CreativeModeTab> NERODECOR = TABS.register("nerodecor",
            key -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                    .title(Component.translatable("itemGroup.nerodecor"))
                    .icon(() -> ModItems.BLOCK_ITEMS.isEmpty()
                            ? ItemStack.EMPTY
                            : new ItemStack(ModItems.BLOCK_ITEMS.get(0).get()))
                    .displayItems((params, output) -> ModItems.BLOCK_ITEMS.forEach(item -> output.accept(item.get())))
                    .build());

    private ModCreativeTab() {
    }

    /** Force class-load so the static tab registration runs. Call after {@link ModItems#init()}. */
    public static void init() {
    }
}
