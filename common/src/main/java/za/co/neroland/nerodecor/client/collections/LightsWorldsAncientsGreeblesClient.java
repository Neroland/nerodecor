package za.co.neroland.nerodecor.client.collections;

import net.minecraft.world.level.block.Block;

import za.co.neroland.nerodecor.client.ClientRenderRegistry;
import za.co.neroland.nerodecor.registry.RegistrationProvider.RegistryEntry;
import za.co.neroland.nerodecor.registry.collections.LightsWorldsAncientsGreeblesBlocks;

/**
 * Client half of {@link LightsWorldsAncientsGreeblesBlocks}: the chunk layers for its non-solid blocks (every light
 * fitting, glow overlay and thin greeble is cutout; the bubble ice is translucent). No block entity or entity
 * renderers. Client only; never referenced from common code.
 */
public final class LightsWorldsAncientsGreeblesClient {

    private LightsWorldsAncientsGreeblesClient() {
    }

    /** Called once from {@link ClientRenderRegistry#bootstrap()}. */
    public static void register() {
        for (RegistryEntry<Block> block : LightsWorldsAncientsGreeblesBlocks.cutout()) {
            ClientRenderRegistry.blockLayer(block, ClientRenderRegistry.Layer.CUTOUT);
        }
        for (RegistryEntry<Block> block : LightsWorldsAncientsGreeblesBlocks.translucent()) {
            ClientRenderRegistry.blockLayer(block, ClientRenderRegistry.Layer.TRANSLUCENT);
        }
    }
}
