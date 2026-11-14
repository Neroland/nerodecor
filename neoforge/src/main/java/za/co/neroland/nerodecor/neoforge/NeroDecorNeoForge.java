package za.co.neroland.nerodecor.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

import za.co.neroland.nerodecor.NeroDecorCommon;
import za.co.neroland.nerodecor.client.NeroDecorClient;
import za.co.neroland.nerodecor.chroma.net.ChromaSync;
import za.co.neroland.nerodecor.command.NeroDecorCommands;
import za.co.neroland.nerodecor.content.NeonFold;
import za.co.neroland.nerodecor.platform.NeoForgeChromaNet;
import za.co.neroland.nerodecor.registry.NeoForgeRegistrationFactory;
import za.co.neroland.nerodecor.telemetry.NeroDecorTelemetry;

/** NeoForge entry point for NeroDecor. */
@Mod(NeroDecorCommon.MOD_ID)
public final class NeroDecorNeoForge {

    public NeroDecorNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        NeroDecorCommon.LOGGER.info("[NeroDecor] NeoForge bootstrap");
        // Common init builds the DeferredRegisters via the RegistrationProvider seam...
        NeroDecorCommon.init();
        // Anonymous, NeroDecor-only crash reporting (opt-out via config; off if opted out).
        NeroDecorTelemetry.init();
        // ...then attach every collected register to the mod event bus.
        NeoForgeRegistrationFactory.registerAll(modEventBus);
        // Showcase command (/nerodecor gallery) on the game bus.
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) ->
                NeroDecorCommands.register(event.getDispatcher()));
        // Chroma 0.8.0: packets, static sync + light (server tick), neon fold on chunk load.
        modEventBus.addListener(NeoForgeChromaNet::register);
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> ChromaSync.tick(event.getServer()));
        NeoForge.EVENT_BUS.addListener((ChunkEvent.Load event) -> {
            if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) {
                NeonFold.onChunkLoad(level, chunk, event.isNewChunk());
            }
        });
        //? if >=1.21.9 {
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
        //?} else {
        /*if (FMLEnvironment.dist == Dist.CLIENT) {
        *///?}
            NeroDecorClient.initClient();
            NeoForgeClientSetup.init(modEventBus);
        }
    }
}
