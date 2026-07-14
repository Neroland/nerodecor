package za.co.neroland.nerodecor.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import za.co.neroland.nerodecor.NeroDecorCommon;
import za.co.neroland.nerodecor.command.NeroDecorCommands;

/** Fabric entry point for NeroDecor. */
public final class NeroDecorFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        NeroDecorCommon.LOGGER.info("[NeroDecor] Fabric bootstrap");
        NeroDecorCommon.init();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                NeroDecorCommands.register(dispatcher));
    }
}
