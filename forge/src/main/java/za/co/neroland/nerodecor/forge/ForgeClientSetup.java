package za.co.neroland.nerodecor.forge;

import java.util.List;

//? if >=1.21.4 {
import net.minecraft.client.color.item.ItemTintSources;
//?}
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
//? if >=1.21.6 {
import net.minecraftforge.eventbus.api.bus.BusGroup;
//?} else {
/*import net.minecraftforge.eventbus.api.IEventBus;
*///?}

import za.co.neroland.nerodecor.client.DecorColorItemTint;
import za.co.neroland.nerodecor.client.DecorColorTintSource;
import za.co.neroland.nerodecor.registry.DecorBlocks;

/** Forge client-only wiring (block + item paint tint). Loaded only behind {@code Dist.CLIENT}. */
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
        //? if >=1.21.4 {
        // Item paint tint. Forge has no event for item tint source types (NeoForge's ItemTintSources has no
        // Forge equivalent through 26.3), but vanilla's ItemTintSources.ID_MAPPER is a public static field
        // on every 1.21.4+ version, so register the nerodecor:decor_color type there directly. This runs in
        // the mod constructor, before the first resource reload parses the item definitions that name it;
        // the lock guards against another mod doing the same during parallel mod construction.
        synchronized (ItemTintSources.ID_MAPPER) {
            ItemTintSources.ID_MAPPER.put(DecorColorItemTint.ID, DecorColorItemTint.MAP_CODEC);
        }
        //?} else {
        /*// 1.21.1 has no item definitions: register a per-item ItemColor on the mod event bus.
        modBusGroup.addListener((RegisterColorHandlersEvent.Item event) ->
                event.register(DecorColorItemTint.INSTANCE, DecorBlocks.paintableBlocks()));
        *///?}
    }
}
