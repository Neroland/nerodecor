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
import za.co.neroland.nerodecor.content.block.ChromaCubeBlock;
import za.co.neroland.nerodecor.content.block.ChromaHubBlock;
import za.co.neroland.nerodecor.content.block.ChromaHubBlockEntity;
import za.co.neroland.nerodecor.content.item.ChromaHubItem;
import za.co.neroland.nerodecor.content.item.ChromaLinkerItem;
import za.co.neroland.nerodecor.content.item.LinkerState;
import za.co.neroland.nerodecor.content.item.NeonShimItem;
import za.co.neroland.nerodecor.content.item.PaintGunItem;
import za.co.neroland.nerodecor.content.item.SceneChipData;
import za.co.neroland.nerodecor.content.item.SceneChipItem;
import za.co.neroland.nerodecor.registry.RegistrationProvider.RegistryEntry;

/**
 * Chroma (0.8.0) registrations: the hub block, its block entity, the hub, linker and paint gun items, the Chroma
 * Cube and the legacy neon shim items, and their data
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

    /** Stage 4: the scene a Scene Chip carries (scene-codec bytes only; no author or player id). */
    public static final RegistryEntry<DataComponentType<SceneChipData>> SCENE_CHIP =
            ModDataComponents.COMPONENTS.register("scene_chip", key -> DataComponentType.<SceneChipData>builder()
                    .persistent(SceneChipData.CODEC)
                    .networkSynchronized(SceneChipData.STREAM_CODEC)
                    .build());

    public static RegistryEntry<Block> HUB;
    public static RegistryEntry<BlockEntityType<ChromaHubBlockEntity>> HUB_BLOCK_ENTITY;
    public static RegistryEntry<Item> HUB_ITEM;
    public static RegistryEntry<Item> LINKER;
    /** The Chroma Cube (replaces the 12 neon cubes); tracked in {@link DecorBlocks#ALL} so it gets its block item. */
    public static RegistryEntry<Block> CUBE;
    public static RegistryEntry<Item> PAINT_GUN;
    /** Stage 4: the Scene Chip. */
    public static RegistryEntry<Item> SCENE_CHIP_ITEM;
    /** Items of the hidden neon shims (convert to chroma cubes; no tab, no recipe). */
    public static final java.util.List<RegistryEntry<Item>> NEON_SHIM_ITEMS = new java.util.ArrayList<>();

    private ChromaContent() {
    }

    /** Registers the hub block. Call after the decor blocks. */
    public static void initBlocks() {
        HUB = DecorBlocks.BLOCKS.register("chroma_hub", key -> new ChromaHubBlock(BlockBehaviour.Properties.of()
                .setId(key).mapColor(MapColor.METAL).strength(2.0F, 6.0F).sound(SoundType.METAL)
                .lightLevel(s -> 7)));
        CUBE = DecorBlocks.track(DecorBlocks.BLOCKS.register("chroma_cube", key -> new ChromaCubeBlock(
                BlockBehaviour.Properties.of().setId(key).mapColor(MapColor.SNOW).strength(0.4F)
                        .sound(SoundType.GLASS))));
    }

    /** The chroma cube's block item (registered by {@code ModItems} with the other {@code ALL} entries). */
    public static Item cubeItem() {
        return CUBE.get().asItem();
    }

    /** Registers the block entity type and the items. Call after {@link #initBlocks()}. */
    public static void initItems() {
        HUB_BLOCK_ENTITY = BLOCK_ENTITIES.register("chroma_hub",
                key -> BlockEntityTypeCompat.create(ChromaHubBlockEntity::new, HUB.get()));
        HUB_ITEM = ModItems.ITEMS.register("chroma_hub",
                key -> new ChromaHubItem(HUB.get(), new Item.Properties().setId(key)));
        LINKER = ModItems.ITEMS.register("chroma_linker",
                key -> new ChromaLinkerItem(new Item.Properties().setId(key).stacksTo(1)));
        PAINT_GUN = ModItems.ITEMS.register("paint_gun",
                key -> new PaintGunItem(new Item.Properties().setId(key).stacksTo(1)));
        SCENE_CHIP_ITEM = ModItems.ITEMS.register("scene_chip",
                key -> new SceneChipItem(new Item.Properties().setId(key).stacksTo(16)));
        for (RegistryEntry<Block> shim : DecorBlocks.NEON_SHIMS) {
            NEON_SHIM_ITEMS.add(ModItems.ITEMS.register(shim.id().getPath(),
                    key -> new NeonShimItem(shim.get(), new Item.Properties().setId(key))));
        }
    }

    /** Blocks registered outside {@link DecorBlocks#ALL}: the hub and the neon shims (the registry-cap GameTest adds them). */
    public static int blockCount() {
        return 1 + DecorBlocks.NEON_SHIMS.size();
    }
}
