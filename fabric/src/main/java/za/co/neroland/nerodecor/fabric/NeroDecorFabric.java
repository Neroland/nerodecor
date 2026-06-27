package za.co.neroland.nerodecor.fabric;

import net.fabricmc.api.ModInitializer;

import za.co.neroland.nerodecor.NeroDecorCommon;

/** Fabric entry point for NeroDecor. */
public final class NeroDecorFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        NeroDecorCommon.LOGGER.info("[NeroDecor] Fabric bootstrap");
        NeroDecorCommon.init();
    }
}
