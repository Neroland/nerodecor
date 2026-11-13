package za.co.neroland.nerodecor.fabric;

import java.util.List;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
//? if >=26.1 {
import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry;
//?} else {
/*import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
*///?}
//? if >=1.21.4 {
import net.minecraft.client.color.item.ItemTintSources;
//?}

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.BlockEntityType;

import za.co.neroland.nerodecor.NeroDecorCommon;
import za.co.neroland.nerodecor.client.ClientRenderRegistry;
import za.co.neroland.nerodecor.client.DecorColorItemTint;
import za.co.neroland.nerodecor.client.DecorColorTintSource;
import za.co.neroland.nerodecor.client.NeroDecorClient;
import za.co.neroland.nerodecor.registry.DecorBlocks;

/** Fabric client entry point for NeroDecor. */
public final class NeroDecorFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        NeroDecorCommon.LOGGER.info("[NeroDecor] Fabric client bootstrap");
        NeroDecorClient.initClient();
        //? if >=26.1 {
        BlockColorRegistry.register(List.of(DecorColorTintSource.INSTANCE), DecorBlocks.paintableBlocks());
        //?} else {
        /*ColorProviderRegistry.BLOCK.register(DecorColorTintSource.INSTANCE, DecorBlocks.paintableBlocks());
        *///?}
        //? if >=1.21.4 {
        // Item paint tint. Fabric API has no item tint source registry, but vanilla's
        // ItemTintSources.ID_MAPPER is public on every 1.21.4+ version (no mixin or access widener
        // needed); client init runs before the first resource reload parses the item definitions.
        ItemTintSources.ID_MAPPER.put(DecorColorItemTint.ID, DecorColorItemTint.MAP_CODEC);
        //?} else {
        /*ColorProviderRegistry.ITEM.register(DecorColorItemTint.INSTANCE, DecorBlocks.paintableBlocks());
        *///?}
        registerCollectionRenderers();
    }

    /** Iterates {@link ClientRenderRegistry} once (after NeroDecorClient.initClient() ran the packages' register()). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerCollectionRenderers() {
        // Chunk layers: 26.x picks the layer per quad from the texture alpha (vanilla has no per-block map any more);
        // Fabric 1.21.x has no model render_type, so the declared layers go to BlockRenderLayerMap.
        //? if >=26.1 {
        //?} else if >=1.21.6 {
        /*for (ClientRenderRegistry.BlockLayer e : ClientRenderRegistry.blockLayers()) {
            net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap.putBlock(e.block().get(),
                    e.layer() == ClientRenderRegistry.Layer.CUTOUT
                            ? net.minecraft.client.renderer.chunk.ChunkSectionLayer.CUTOUT
                            : net.minecraft.client.renderer.chunk.ChunkSectionLayer.TRANSLUCENT);
        }
        *///?} else {
        /*for (ClientRenderRegistry.BlockLayer e : ClientRenderRegistry.blockLayers()) {
            net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlock(e.block().get(),
                    e.layer() == ClientRenderRegistry.Layer.CUTOUT
                            ? net.minecraft.client.renderer.RenderType.cutout()
                            : net.minecraft.client.renderer.RenderType.translucent());
        }
        *///?}
        for (ClientRenderRegistry.BlockEntityRendererEntry e : ClientRenderRegistry.blockEntityRenderers()) {
            BlockEntityRendererRegistry.register((BlockEntityType) e.type().get(), e.provider());
        }
        for (ClientRenderRegistry.EntityRendererEntry e : ClientRenderRegistry.entityRenderers()) {
            EntityRendererRegistry.register((EntityType) e.type().get(), (EntityRendererProvider) e.provider());
        }
    }
}
