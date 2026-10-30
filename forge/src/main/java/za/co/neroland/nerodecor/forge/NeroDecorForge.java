package za.co.neroland.nerodecor.forge;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.RegisterCommandsEvent;
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
import za.co.neroland.nerodecor.command.NeroDecorCommands;
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
        if (FMLEnvironment.dist == Dist.CLIENT) {
            NeroDecorClient.initClient();
            ForgeClientSetup.init(modBusGroup);
        }
    }
}
