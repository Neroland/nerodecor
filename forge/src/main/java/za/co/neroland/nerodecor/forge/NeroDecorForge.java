package za.co.neroland.nerodecor.forge;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
//? if >=1.21.6 {
import net.minecraftforge.eventbus.api.bus.BusGroup;
//?} else {
/*import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
*///?}
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

import za.co.neroland.nerodecor.NeroDecorCommon;
import za.co.neroland.nerodecor.client.NeroDecorClient;
import za.co.neroland.nerodecor.chroma.net.ChromaSync;
import za.co.neroland.nerodecor.command.NeroDecorCommands;
import za.co.neroland.nerodecor.content.NeonFold;
import za.co.neroland.nerodecor.platform.ForgeChromaNet;
import za.co.neroland.nerodecor.registry.ForgeRegistrationFactory;
import za.co.neroland.nerodecor.telemetry.NeroDecorTelemetry;

/** MinecraftForge entry point for NeroDecor. */
@Mod(NeroDecorCommon.MOD_ID)
public final class NeroDecorForge {

    public NeroDecorForge(FMLJavaModLoadingContext context) {
        NeroDecorCommon.LOGGER.info("[NeroDecor] Forge bootstrap");
        //? if >=1.21.6 {
        BusGroup modBusGroup = context.getModBusGroup();
        //?} else {
        /*IEventBus modBusGroup = context.getModEventBus();
        *///?}
        // Common init builds the DeferredRegisters via the RegistrationProvider seam...
        NeroDecorCommon.init();
        // Anonymous, NeroDecor-only crash reporting (opt-out via config; off if opted out).
        NeroDecorTelemetry.init();
        // ...then attach every collected register to the mod bus group.
        ForgeRegistrationFactory.registerAll(modBusGroup);
        // Showcase command (/nerodecor gallery) on the game bus.
        //? if >=1.21.6 {
        RegisterCommandsEvent.BUS.addListener(event -> NeroDecorCommands.register(event.getDispatcher()));
        //?} else {
        /*MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> NeroDecorCommands.register(event.getDispatcher()));
        *///?}
        // Chroma 0.8.0: packets, static sync + light (server tick), neon fold on chunk load.
        ForgeChromaNet.register();
        //? if >=1.21.9 {
        TickEvent.ServerTickEvent.Post.BUS.addListener(event -> ChromaSync.tick(event.server()));
        //?} else if >=1.21.6 {
        /*TickEvent.ServerTickEvent.Post.BUS.addListener(event -> ChromaSync.tick(event.getServer()));
        *///?} else {
        /*MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent.Post event) -> ChromaSync.tick(event.getServer()));
        *///?}
        //? if >=1.21.6 {
        ChunkEvent.Load.BUS.addListener(NeroDecorForge::onChunkLoad);
        //?} else {
        /*MinecraftForge.EVENT_BUS.addListener(NeroDecorForge::onChunkLoad);
        *///?}
        if (FMLEnvironment.dist == Dist.CLIENT) {
            NeroDecorClient.initClient();
            ForgeClientSetup.init(modBusGroup);
        }
    }

    private static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) {
            NeonFold.onChunkLoad(level, chunk);
        }
    }
}
