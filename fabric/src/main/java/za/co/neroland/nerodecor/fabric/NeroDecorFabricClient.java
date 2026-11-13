package za.co.neroland.nerodecor.fabric;

import java.util.List;

import net.fabricmc.api.ClientModInitializer;
//? if >=26.1 {
import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry;
//?} else {
/*import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
*///?}
//? if >=1.21.4 {
import net.minecraft.client.color.item.ItemTintSources;
//?}

import za.co.neroland.nerodecor.NeroDecorCommon;
import za.co.neroland.nerodecor.client.DecorColorItemTint;
import za.co.neroland.nerodecor.client.DecorColorTintSource;
import za.co.neroland.nerodecor.client.NeroDecorClient;
import za.co.neroland.nerodecor.registry.DecorBlocks;

/** Fabric client entry point for NeroDecor. */
public final class NeroDecorFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        NeroDecorCommon.LOGGER.info("[NeroDecor] Fabric client bootstrap");
        NeroDecorClient.initClient();
        //? if >=26.1 {
        BlockColorRegistry.register(List.of(DecorColorTintSource.INSTANCE), DecorBlocks.paintableBlocks());
        //?} else {
        /*ColorProviderRegistry.BLOCK.register(DecorColorTintSource.INSTANCE, DecorBlocks.paintableBlocks());
        *///?}
        //? if >=1.21.4 {
        // Item paint tint. Fabric API has no item tint source registry, but vanilla's
        // ItemTintSources.ID_MAPPER is public on every 1.21.4+ version (no mixin or access widener
        // needed); client init runs before the first resource reload parses the item definitions.
        ItemTintSources.ID_MAPPER.put(DecorColorItemTint.ID, DecorColorItemTint.MAP_CODEC);
        //?} else {
        /*ColorProviderRegistry.ITEM.register(DecorColorItemTint.INSTANCE, DecorBlocks.paintableBlocks());
        *///?}
    }
}
