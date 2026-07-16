package za.co.neroland.nerodecor.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import za.co.neroland.nerodecor.NeroDecorCommon;
import za.co.neroland.nerodecor.client.NeroDecorClient;
import za.co.neroland.nerodecor.command.NeroDecorCommands;
import za.co.neroland.nerodecor.registry.NeoForgeRegistrationFactory;

/** NeoForge entry point for NeroDecor. */
@Mod(NeroDecorCommon.MOD_ID)
public final class NeroDecorNeoForge {

    public NeroDecorNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        NeroDecorCommon.LOGGER.info("[NeroDecor] NeoForge bootstrap");
        // Common init builds the DeferredRegisters via the RegistrationProvider seam...
        NeroDecorCommon.init();
        // ...then attach every collected register to the mod event bus.
        NeoForgeRegistrationFactory.registerAll(modEventBus);
        // Showcase command (/nerodecor gallery) on the game bus.
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) ->
                NeroDecorCommands.register(event.getDispatcher()));
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            NeroDecorClient.initClient();
            NeoForgeClientSetup.init(modEventBus);
        }
    }
}
