package za.co.neroland.nerodecor.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

import za.co.neroland.nerodecor.NeroDecorCommon;
import za.co.neroland.nerodecor.client.NeroDecorClient;
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
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            NeroDecorClient.initClient();
        }
    }
}
