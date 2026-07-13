package za.co.neroland.nerodecor.fabric;

import net.fabricmc.api.ClientModInitializer;

import za.co.neroland.nerodecor.NeroDecorCommon;
import za.co.neroland.nerodecor.client.NeroDecorClient;

/** Fabric client entry point for NeroDecor. */
public final class NeroDecorFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        NeroDecorCommon.LOGGER.info("[NeroDecor] Fabric client bootstrap");
        NeroDecorClient.initClient();
    }
}
