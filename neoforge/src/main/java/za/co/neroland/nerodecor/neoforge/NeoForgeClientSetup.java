package za.co.neroland.nerodecor.neoforge;

import java.util.List;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;

import za.co.neroland.nerodecor.client.DecorColorItemTint;
import za.co.neroland.nerodecor.client.ClientRenderRegistry;
import za.co.neroland.nerodecor.client.ChromaTintBridge;
import za.co.neroland.nerodecor.chroma.client.ChromaClientSync;
import za.co.neroland.nerodecor.client.capture.CaptureCommand;

/**
 * NeoForge client-only wiring (block + item paint tint, collection renderers). Loaded only behind
 * {@code Dist.CLIENT}. Render layers come from the model JSON {@code render_type} (1.21.x) or the texture alpha (26.x),
 * so {@link ClientRenderRegistry#blockLayers()} needs no registration here.
 */
public final class NeoForgeClientSetup {

    private NeoForgeClientSetup() {
    }

    public static void init(IEventBus modEventBus) {
        //? if >=26.1 {
        modEventBus.addListener((RegisterColorHandlersEvent.BlockTintSources event) -> {
            event.register(List.of(ChromaTintBridge.PAINTED), ChromaTintBridge.paintedBlocks());
            event.register(List.of(ChromaTintBridge.PLAIN), ChromaTintBridge.plainBlocks());
        });
        //?} else {
        /*modEventBus.addListener((RegisterColorHandlersEvent.Block event) -> {
            event.register(ChromaTintBridge.PAINTED, ChromaTintBridge.paintedBlocks());
            event.register(ChromaTintBridge.PLAIN, ChromaTintBridge.plainBlocks());
        });
        *///?}
        // Item paint tint: 1.21.4+ registers the nerodecor:decor_color tint source type that the paintable
        // blocks' item definitions name; 1.21.1 (no item definitions) registers a per-item ItemColor.
        //? if >=1.21.4 {
        modEventBus.addListener((RegisterColorHandlersEvent.ItemTintSources event) ->
                event.register(DecorColorItemTint.ID, DecorColorItemTint.MAP_CODEC));
        //?} else {
        /*modEventBus.addListener((RegisterColorHandlersEvent.Item event) ->
                event.register(DecorColorItemTint.INSTANCE, DecorColorItemTint.legacyItems()));
        *///?}
        modEventBus.addListener(NeoForgeClientSetup::onRegisterRenderers);
        // Chroma: release budgeted section re-meshes after hub colour changes.
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> ChromaClientSync.tick());
        // /nerodecor capture (client-side screenshots of the showroom and block grid).
        NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent event) ->
                event.getDispatcher().register(CaptureCommand.<CommandSourceStack>node()));
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
