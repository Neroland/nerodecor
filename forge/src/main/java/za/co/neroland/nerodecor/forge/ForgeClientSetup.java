package za.co.neroland.nerodecor.forge;

import java.util.List;

import net.minecraftforge.client.event.RegisterColorHandlersEvent;
//? if >=1.21.6 {
import net.minecraftforge.eventbus.api.bus.BusGroup;
//?} else {
/*import net.minecraftforge.eventbus.api.IEventBus;
*///?}

import za.co.neroland.nerodecor.client.DecorColorTintSource;
import za.co.neroland.nerodecor.registry.DecorBlocks;

/** Forge client-only wiring (block paint tint). Loaded only behind {@code Dist.CLIENT}. */
public final class ForgeClientSetup {

    private ForgeClientSetup() {
    }

    //? if >=1.21.6 {
    public static void init(BusGroup modBusGroup) {
    //?} else {
    /*public static void init(IEventBus modBusGroup) {
    *///?}
        //? if >=26.1 {
        RegisterColorHandlersEvent.Block.BUS.addListener(event ->
                event.register(List.of(DecorColorTintSource.INSTANCE), DecorBlocks.paintableBlocks()));
        //?} else if >=1.21.9 {
        /*RegisterColorHandlersEvent.Block.BUS.addListener(event ->
                event.register(DecorColorTintSource.INSTANCE, DecorBlocks.paintableBlocks()));
        *///?} else if >=1.21.6 {
        /*RegisterColorHandlersEvent.Block.getBus(modBusGroup).addListener(event ->
                event.register(DecorColorTintSource.INSTANCE, DecorBlocks.paintableBlocks()));
        *///?} else {
        /*// Before Forge's EventBus 7 (1.21.6) mod-bus events are registered on the mod's own event bus.
        modBusGroup.addListener((RegisterColorHandlersEvent.Block event) ->
                event.register(DecorColorTintSource.INSTANCE, DecorBlocks.paintableBlocks()));
        *///?}
    }
}
