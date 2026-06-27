package za.co.neroland.nerodecor.forge;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import za.co.neroland.nerodecor.NeroDecorCommon;

/** MinecraftForge entry point for NeroDecor. */
@Mod(NeroDecorCommon.MOD_ID)
public final class NeroDecorForge {

    public NeroDecorForge(FMLJavaModLoadingContext context) {
        NeroDecorCommon.LOGGER.info("[NeroDecor] Forge bootstrap");
        NeroDecorCommon.init();
    }
}
