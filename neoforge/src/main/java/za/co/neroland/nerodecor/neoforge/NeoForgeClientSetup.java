package za.co.neroland.nerodecor.neoforge;

import java.util.List;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

import za.co.neroland.nerodecor.client.DecorColorTintSource;
import za.co.neroland.nerodecor.registry.DecorBlocks;

/** NeoForge client-only wiring (block paint tint). Loaded only behind {@code Dist.CLIENT}. */
public final class NeoForgeClientSetup {

    private NeoForgeClientSetup() {
    }

    public static void init(IEventBus modEventBus) {
        //? if >=26.1 {
        modEventBus.addListener((RegisterColorHandlersEvent.BlockTintSources event) ->
                event.register(List.of(DecorColorTintSource.INSTANCE), DecorBlocks.paintableBlocks()));
        //?} else {
        /*modEventBus.addListener((RegisterColorHandlersEvent.Block event) ->
                event.register(DecorColorTintSource.INSTANCE, DecorBlocks.paintableBlocks()));
        *///?}
    }
}
