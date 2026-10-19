package za.co.neroland.nerodecor.registry;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import za.co.neroland.nerodecor.NeroDecorCommon;
import za.co.neroland.nerodecor.content.item.DecorBlockItem;
import za.co.neroland.nerodecor.registry.RegistrationProvider.RegistryEntry;

/**
 * Block items for every {@link DecorBlocks} entry — one {@link DecorBlockItem} per block
 * (same id), which bridges the paint colour component to the placed state. All are
 * listed in NeroDecor's own {@link ModCreativeTab} in registration order.
 */
public final class ModItems {

    public static final RegistrationProvider<Item> ITEMS =
            RegistrationProvider.get(Registries.ITEM, NeroDecorCommon.MOD_ID);

    /** Block items in registration order (mirrors {@link DecorBlocks#ALL}). */
    public static final List<RegistryEntry<? extends Item>> BLOCK_ITEMS = new ArrayList<>();

    private ModItems() {
    }

    /** Register a block item for each decor block. Call after {@link DecorBlocks#init()}. */
    public static void init() {
        for (RegistryEntry<? extends Block> block : DecorBlocks.ALL) {
            String name = block.id().getPath();
            RegistryEntry<DecorBlockItem> item = ITEMS.register(name,
                    key -> new DecorBlockItem(block.get(), new Item.Properties().setId(key)));
            BLOCK_ITEMS.add(item);
        }
    }
}
