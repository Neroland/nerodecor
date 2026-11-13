package za.co.neroland.nerodecor.neoforge;

import java.util.List;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

import za.co.neroland.nerodecor.client.DecorColorItemTint;
import za.co.neroland.nerodecor.client.DecorColorTintSource;
import za.co.neroland.nerodecor.registry.DecorBlocks;

/** NeoForge client-only wiring (block + item paint tint). Loaded only behind {@code Dist.CLIENT}. */
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
        // Item paint tint: 1.21.4+ registers the nerodecor:decor_color tint source type that the paintable
        // blocks' item definitions name; 1.21.1 (no item definitions) registers a per-item ItemColor.
        //? if >=1.21.4 {
        modEventBus.addListener((RegisterColorHandlersEvent.ItemTintSources event) ->
                event.register(DecorColorItemTint.ID, DecorColorItemTint.MAP_CODEC));
        //?} else {
        /*modEventBus.addListener((RegisterColorHandlersEvent.Item event) ->
                event.register(DecorColorItemTint.INSTANCE, DecorBlocks.paintableBlocks()));
        *///?}
    }
}
