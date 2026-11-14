package za.co.neroland.nerodecor.forge;

import java.util.List;

//? if >=1.21.4 {
import net.minecraft.client.color.item.ItemTintSources;
//?}
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.event.TickEvent;
//? if <1.21.6 {
/*import net.minecraftforge.common.MinecraftForge;
*///?}
//? if >=1.21.6 {
import net.minecraftforge.eventbus.api.bus.BusGroup;
//?} else {
/*import net.minecraftforge.eventbus.api.IEventBus;
*///?}

import za.co.neroland.nerodecor.client.DecorColorItemTint;
import za.co.neroland.nerodecor.client.ClientRenderRegistry;
import za.co.neroland.nerodecor.client.ChromaTintBridge;
import za.co.neroland.nerodecor.chroma.client.ChromaClientSync;
import za.co.neroland.nerodecor.client.capture.CaptureCommand;

/**
 * Forge client-only wiring (block + item paint tint, collection renderers). Loaded only behind {@code Dist.CLIENT}.
 * Render layers come from the model JSON {@code render_type} (1.21.x) or the texture alpha (26.x), so
 * {@link ClientRenderRegistry#blockLayers()} needs no registration here.
 */
public final class ForgeClientSetup {

    private ForgeClientSetup() {
    }

    //? if >=1.21.6 {
    public static void init(BusGroup modBusGroup) {
    //?} else {
    /*public static void init(IEventBus modBusGroup) {
    *///?}
        //? if >=26.1 {
        RegisterColorHandlersEvent.Block.BUS.addListener(event -> {
            event.register(List.of(ChromaTintBridge.PAINTED), ChromaTintBridge.paintedBlocks());
            event.register(List.of(ChromaTintBridge.PLAIN), ChromaTintBridge.plainBlocks());
        });
        //?} else if >=1.21.9 {
        /*RegisterColorHandlersEvent.Block.BUS.addListener(event -> {
            event.register(ChromaTintBridge.PAINTED, ChromaTintBridge.paintedBlocks());
            event.register(ChromaTintBridge.PLAIN, ChromaTintBridge.plainBlocks());
        });
        *///?} else if >=1.21.6 {
        /*RegisterColorHandlersEvent.Block.getBus(modBusGroup).addListener(event -> {
            event.register(ChromaTintBridge.PAINTED, ChromaTintBridge.paintedBlocks());
            event.register(ChromaTintBridge.PLAIN, ChromaTintBridge.plainBlocks());
        });
        *///?} else {
        /*// Before Forge's EventBus 7 (1.21.6) mod-bus events are registered on the mod's own event bus.
        modBusGroup.addListener((RegisterColorHandlersEvent.Block event) -> {
            event.register(ChromaTintBridge.PAINTED, ChromaTintBridge.paintedBlocks());
            event.register(ChromaTintBridge.PLAIN, ChromaTintBridge.plainBlocks());
        });
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
                event.register(DecorColorItemTint.INSTANCE, DecorColorItemTint.legacyItems()));
        *///?}
        // Chroma: release budgeted section re-meshes after hub colour changes.
        //? if >=1.21.6 {
        TickEvent.ClientTickEvent.Post.BUS.addListener(event -> ChromaClientSync.tick());
        //?} else {
        /*MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent.Post event) -> ChromaClientSync.tick());
        *///?}
        // /nerodecor capture (client-side screenshots of the showroom and block grid).
        //? if >=1.21.6 {
        RegisterClientCommandsEvent.BUS.addListener(event ->
                event.getDispatcher().register(CaptureCommand.<CommandSourceStack>node()));
        //?} else {
        /*MinecraftForge.EVENT_BUS.addListener((RegisterClientCommandsEvent event) ->
                event.getDispatcher().register(CaptureCommand.<CommandSourceStack>node()));
        *///?}
        //? if >=1.21.9 {
        EntityRenderersEvent.RegisterRenderers.BUS.addListener(ForgeClientSetup::onRegisterRenderers);
        //?} else if >=1.21.6 {
        /*EntityRenderersEvent.RegisterRenderers.getBus(modBusGroup).addListener(ForgeClientSetup::onRegisterRenderers);
        *///?} else {
        /*modBusGroup.addListener(ForgeClientSetup::onRegisterRenderers);
        *///?}
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        for (ClientRenderRegistry.BlockEntityRendererEntry e : ClientRenderRegistry.blockEntityRenderers()) {
            event.registerBlockEntityRenderer((BlockEntityType) e.type().get(), e.provider());
        }
        for (ClientRenderRegistry.EntityRendererEntry e : ClientRenderRegistry.entityRenderers()) {
            event.registerEntityRenderer((EntityType) e.type().get(), (EntityRendererProvider) e.provider());
        }
    }
}
