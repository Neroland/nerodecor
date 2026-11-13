package za.co.neroland.nerodecor.registry;

import com.mojang.serialization.Codec;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import za.co.neroland.nerolandcore.registry.BlockEntityTypeCompat;

import za.co.neroland.nerodecor.NeroDecorCommon;
import za.co.neroland.nerodecor.content.block.ChromaHubBlock;
import za.co.neroland.nerodecor.content.block.ChromaHubBlockEntity;
import za.co.neroland.nerodecor.content.item.ChromaHubItem;
import za.co.neroland.nerodecor.content.item.ChromaLinkerItem;
import za.co.neroland.nerodecor.content.item.LinkerState;
import za.co.neroland.nerodecor.registry.RegistrationProvider.RegistryEntry;

/**
 * Chroma (0.8.0) registrations: the hub block, its block entity, the hub and linker items and their data
 * components. Kept out of {@link DecorBlocks#ALL} (that list drives the generated decor resources); the hub's and
 * linker's assets are hand-authored.
 */
public final class ChromaContent {

    public static final RegistrationProvider<BlockEntityType<?>> BLOCK_ENTITIES =
            RegistrationProvider.get(Registries.BLOCK_ENTITY_TYPE, NeroDecorCommon.MOD_ID);

    /** The network a hub item carries (0/absent = blank hub). */
    public static final RegistryEntry<DataComponentType<Long>> NETWORK_ID =
            ModDataComponents.COMPONENTS.register("chroma_network", key -> DataComponentType.<Long>builder()
                    .persistent(Codec.LONG)
                    .networkSynchronized(ByteBufCodecs.VAR_LONG)
                    .build());

    /** The Chroma Linker's binding and mode. */
    public static final RegistryEntry<DataComponentType<LinkerState>> LINKER_STATE =
            ModDataComponents.COMPONENTS.register("chroma_linker", key -> DataComponentType.<LinkerState>builder()
                    .persistent(LinkerState.CODEC)
                    .networkSynchronized(LinkerState.STREAM_CODEC)
                    .build());

    public static RegistryEntry<Block> HUB;
    public static RegistryEntry<BlockEntityType<ChromaHubBlockEntity>> HUB_BLOCK_ENTITY;
    public static RegistryEntry<Item> HUB_ITEM;
    public static RegistryEntry<Item> LINKER;

    private ChromaContent() {
    }

    /** Registers the hub block. Call after the decor blocks. */
    public static void initBlocks() {
        HUB = DecorBlocks.BLOCKS.register("chroma_hub", key -> new ChromaHubBlock(BlockBehaviour.Properties.of()
                .setId(key).mapColor(MapColor.METAL).strength(2.0F, 6.0F).sound(SoundType.METAL)
                .lightLevel(s -> 7)));
    }

    /** Registers the block entity type and the items. Call after {@link #initBlocks()}. */
    public static void initItems() {
        HUB_BLOCK_ENTITY = BLOCK_ENTITIES.register("chroma_hub",
                key -> BlockEntityTypeCompat.create(ChromaHubBlockEntity::new, HUB.get()));
        HUB_ITEM = ModItems.ITEMS.register("chroma_hub",
                key -> new ChromaHubItem(HUB.get(), new Item.Properties().setId(key)));
        LINKER = ModItems.ITEMS.register("chroma_linker",
                key -> new ChromaLinkerItem(new Item.Properties().setId(key).stacksTo(1)));
    }

    /** Blocks registered here (the registry-cap GameTest adds them to the decor count). */
    public static int blockCount() {
        return 1;
    }
}
