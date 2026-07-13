package za.co.neroland.nerodecor.forge;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

import za.co.neroland.nerodecor.NeroDecorCommon;
import za.co.neroland.nerodecor.client.NeroDecorClient;
import za.co.neroland.nerodecor.registry.ForgeRegistrationFactory;

/** MinecraftForge entry point for NeroDecor. */
@Mod(NeroDecorCommon.MOD_ID)
public final class NeroDecorForge {

    public NeroDecorForge(FMLJavaModLoadingContext context) {
        NeroDecorCommon.LOGGER.info("[NeroDecor] Forge bootstrap");
        BusGroup modBusGroup = context.getModBusGroup();
        // Common init builds the DeferredRegisters via the RegistrationProvider seam...
        NeroDecorCommon.init();
        // ...then attach every collected register to the mod bus group.
        ForgeRegistrationFactory.registerAll(modBusGroup);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            NeroDecorClient.initClient();
        }
    }
}
