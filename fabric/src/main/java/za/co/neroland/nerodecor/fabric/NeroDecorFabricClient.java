package za.co.neroland.nerodecor.fabric;

import java.util.List;

import net.fabricmc.api.ClientModInitializer;
//? if >=26.1 {
import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry;
//?} else {
/*import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
*///?}

import za.co.neroland.nerodecor.NeroDecorCommon;
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
    }
}
