package za.co.neroland.nerodecor.forge;

import java.util.List;

import net.minecraftforge.client.event.RegisterColorHandlersEvent;

import za.co.neroland.nerodecor.client.DecorColorTintSource;
import za.co.neroland.nerodecor.registry.DecorBlocks;

/** Forge client-only wiring (block paint tint). Loaded only behind {@code Dist.CLIENT}. */
public final class ForgeClientSetup {

    private ForgeClientSetup() {
    }

    public static void init() {
        RegisterColorHandlersEvent.Block.BUS.addListener(event ->
                event.register(List.of(DecorColorTintSource.INSTANCE), DecorBlocks.allBlocks()));
    }
}
