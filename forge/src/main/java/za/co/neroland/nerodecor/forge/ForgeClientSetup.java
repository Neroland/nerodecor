package za.co.neroland.nerodecor.forge;

import java.util.List;

import net.minecraftforge.client.event.RegisterColorHandlersEvent;
//? if <1.21.6 {
/*import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
*///?}

import za.co.neroland.nerodecor.client.DecorColorTintSource;
import za.co.neroland.nerodecor.registry.DecorBlocks;

/** Forge client-only wiring (block paint tint). Loaded only behind {@code Dist.CLIENT}. */
public final class ForgeClientSetup {

    private ForgeClientSetup() {
    }

    public static void init() {
        //? if >=26.1 {
        RegisterColorHandlersEvent.Block.BUS.addListener(event ->
                event.register(List.of(DecorColorTintSource.INSTANCE), DecorBlocks.paintableBlocks()));
        //?} else if >=1.21.6 {
        /*RegisterColorHandlersEvent.Block.BUS.addListener(event ->
                event.register(DecorColorTintSource.INSTANCE, DecorBlocks.paintableBlocks()));
        *///?} else {
        /*// Before Forge's EventBus 7 (1.21.6) mod-bus events are registered on the mod's own event bus.
        FMLJavaModLoadingContext.get().getModEventBus().addListener((RegisterColorHandlersEvent.Block event) ->
                event.register(DecorColorTintSource.INSTANCE, DecorBlocks.paintableBlocks()));
        *///?}
    }
}
