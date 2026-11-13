package za.co.neroland.nerodecor.registry.collections;

import java.util.function.Function;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

import za.co.neroland.nerodecor.NeroDecorCommon;
import za.co.neroland.nerodecor.registry.ChromaContent;
import za.co.neroland.nerodecor.registry.DecorBlocks;
import za.co.neroland.nerodecor.registry.RegistrationProvider;
import za.co.neroland.nerodecor.registry.RegistrationProvider.RegistryEntry;

/**
 * Shared registration helpers for the 0.8.0 collection work packages
 * ({@link LightsWorldsAncientsGreeblesBlocks}, {@link BulkheadHoloCommandBlocks}, {@link LabGantryConduitsBlocks}).
 * Each package registers only from its own class, so packages never edit a shared file.
 *
 * <p>Blocks registered through {@link #block} are added to {@link DecorBlocks#ALL}, which is what gives them a block
 * item ({@code ModItems}), a creative-tab entry ({@code ModCreativeTab}), the paint tint when they carry
 * {@code DecorProperties.COLOR}, and a line in the link catalogue ({@code NeroDecorLinkSnapshots}). The block ids must
 * match the ids the collection modules under {@code tools/collections/} declare, which generate their resources.
 * Client renderers go through {@code client.ClientRenderRegistry} from each package's client class.
 */
public final class CollectionRegistries {

    /** Block entity types (the same register the Chroma hub uses). */
    public static final RegistrationProvider<BlockEntityType<?>> BLOCK_ENTITIES = ChromaContent.BLOCK_ENTITIES;

    /** Entity types (seats, hologram carriers and the like). */
    public static final RegistrationProvider<EntityType<?>> ENTITY_TYPES =
            RegistrationProvider.get(Registries.ENTITY_TYPE, NeroDecorCommon.MOD_ID);

    private CollectionRegistries() {
    }

    /**
     * Calls every package's {@code init()} once. Runs from {@code DecorRegistries.init()} after the decor, Luminous
     * and Chroma blocks and before the block items are created.
     */
    public static void init() {
        LightsWorldsAncientsGreeblesBlocks.init();
        BulkheadHoloCommandBlocks.init();
        LabGantryConduitsBlocks.init();
    }

    /**
     * Registers a decor block and tracks it in {@link DecorBlocks#ALL}. The factory receives the block's registry key;
     * pass it to {@code BlockBehaviour.Properties.setId(key)}.
     */
    public static RegistryEntry<Block> block(String name, Function<ResourceKey<Block>, Block> factory) {
        return DecorBlocks.track(DecorBlocks.BLOCKS.register(name, factory));
    }
}
