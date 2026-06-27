package za.co.neroland.nerodecor.neoforge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

import za.co.neroland.nerodecor.NeroDecorCommon;

/** NeoForge entry point for NeroDecor. */
@Mod(NeroDecorCommon.MOD_ID)
public final class NeroDecorNeoForge {

    public NeroDecorNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        NeroDecorCommon.LOGGER.info("[NeroDecor] NeoForge bootstrap");
        NeroDecorCommon.init();
    }
}
