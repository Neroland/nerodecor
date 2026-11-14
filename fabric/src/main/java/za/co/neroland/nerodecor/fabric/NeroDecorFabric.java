package za.co.neroland.nerodecor.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import za.co.neroland.nerodecor.NeroDecorCommon;
import za.co.neroland.nerodecor.chroma.net.ChromaSync;
import za.co.neroland.nerodecor.command.NeroDecorCommands;
import za.co.neroland.nerodecor.content.NeonFold;
import za.co.neroland.nerodecor.platform.FabricChromaNet;
import za.co.neroland.nerodecor.telemetry.NeroDecorTelemetry;

/** Fabric entry point for NeroDecor. */
public final class NeroDecorFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        NeroDecorCommon.LOGGER.info("[NeroDecor] Fabric bootstrap");
        NeroDecorCommon.init();
        // Anonymous, NeroDecor-only crash reporting (opt-out via config; off if opted out).
        NeroDecorTelemetry.init();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                NeroDecorCommands.register(dispatcher));
        // Chroma 0.8.0: packets, static sync + light (server tick), neon fold on chunk load.
        FabricChromaNet.registerCommon();
        ServerTickEvents.END_SERVER_TICK.register(ChromaSync::tick);
        //? if >=26.1 {
        ServerChunkEvents.CHUNK_LOAD.register(NeonFold::onChunkLoad);
        //?} else {
        /*ServerChunkEvents.CHUNK_LOAD.register(NeonFold::onChunkLoad);
        *///?}
    }
}
