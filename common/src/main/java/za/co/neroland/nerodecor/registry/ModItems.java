package za.co.neroland.nerodecor.registry;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import za.co.neroland.nerodecor.NeroDecorCommon;
import za.co.neroland.nerodecor.content.item.DecorBlockItem;
import za.co.neroland.nerodecor.registry.RegistrationProvider.RegistryEntry;
import za.co.neroland.nerolandcore.registry.CoreCreativeTab;

/**
 * Block items for every {@link DecorBlocks} entry — one {@link DecorBlockItem} per block
 * (same id), which bridges the paint colour component to the placed state. All are
 * contributed to Core's shared <b>Neroland Decor</b> creative tab (Core 1.9.0) in
 * registration order.
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

    /** Contribute every block item to Core's decor creative tab. Call after {@link #init()}. */
    public static void addToCreativeTab() {
        for (RegistryEntry<? extends Item> item : BLOCK_ITEMS) {
            CoreCreativeTab.addDecor(item);
        }
    }
}
