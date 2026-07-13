package za.co.neroland.nerodecor.forge;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

import za.co.neroland.nerodecor.NeroDecorCommon;
import za.co.neroland.nerodecor.client.NeroDecorClient;

/** MinecraftForge entry point for NeroDecor. */
@Mod(NeroDecorCommon.MOD_ID)
public final class NeroDecorForge {

    public NeroDecorForge(FMLJavaModLoadingContext context) {
        NeroDecorCommon.LOGGER.info("[NeroDecor] Forge bootstrap");
        NeroDecorCommon.init();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            NeroDecorClient.initClient();
        }
    }
}
