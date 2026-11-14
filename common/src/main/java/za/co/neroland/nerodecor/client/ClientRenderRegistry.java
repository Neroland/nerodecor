package za.co.neroland.nerodecor.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

import za.co.neroland.nerodecor.client.collections.BulkheadHoloCommandClient;
import za.co.neroland.nerodecor.client.collections.LabGantryConduitsClient;
import za.co.neroland.nerodecor.client.collections.LightsWorldsAncientsGreeblesClient;

/**
 * Loader-agnostic client render registrations. Work packages append from their own client classes (under
 * {@code client.collections}); each loader's client setup iterates the lists once:
 *
 * <ul>
 *   <li>{@link #blockLayers()}: the chunk layer a block's model needs. On 26.x vanilla picks the layer per quad from
 *       the texture's alpha, so no loader registers anything. On 1.21.x NeoForge and Forge read the model JSON's
 *       {@code render_type} (set it in the collection module: {@code Block(render_type=...)}, emissive blocks default
 *       to cutout); Fabric 1.21.x has no such field, so it feeds this list to {@code BlockRenderLayerMap}. Declare the
 *       layer here AND in the model.</li>
 *   <li>{@link #blockEntityRenderers()}: registered in each loader's renderer event / registry.</li>
 *   <li>{@link #entityRenderers()}: likewise.</li>
 * </ul>
 *
 * <p>Suppliers are resolved only when the loader iterates, after registration. The renderer providers are raw types
 * because {@code BlockEntityRendererProvider} gained a render-state type parameter in 1.21.9.
 */
public final class ClientRenderRegistry {

    /** Non-solid chunk layers a block can ask for. */
    public enum Layer {
        CUTOUT,
        TRANSLUCENT
    }

    public record BlockLayer(Supplier<? extends Block> block, Layer layer) {
    }

    @SuppressWarnings("rawtypes")
    public record BlockEntityRendererEntry(Supplier<? extends BlockEntityType<?>> type,
                                           BlockEntityRendererProvider provider) {
    }

    @SuppressWarnings("rawtypes")
    public record EntityRendererEntry(Supplier<? extends EntityType<?>> type, EntityRendererProvider provider) {
    }

    private static final List<BlockLayer> BLOCK_LAYERS = new ArrayList<>();
    private static final List<BlockEntityRendererEntry> BLOCK_ENTITY_RENDERERS = new ArrayList<>();
    private static final List<EntityRendererEntry> ENTITY_RENDERERS = new ArrayList<>();
    private static boolean bootstrapped;

    private ClientRenderRegistry() {
    }

    /** Runs every package's client {@code register()} once. Called from {@link NeroDecorClient#initClient()}. */
    public static void bootstrap() {
        if (bootstrapped) {
            return;
        }
        bootstrapped = true;
        LightsWorldsAncientsGreeblesClient.register();
        BulkheadHoloCommandClient.register();
        LabGantryConduitsClient.register();
        za.co.neroland.nerodecor.chroma.client.ChromaAnimationClient.register(); // Chroma Stage 4 animation layer
    }

    public static void blockLayer(Supplier<? extends Block> block, Layer layer) {
        BLOCK_LAYERS.add(new BlockLayer(block, layer));
    }

    @SuppressWarnings("rawtypes")
    public static void blockEntityRenderer(Supplier<? extends BlockEntityType<?>> type, BlockEntityRendererProvider provider) {
        BLOCK_ENTITY_RENDERERS.add(new BlockEntityRendererEntry(type, provider));
    }

    @SuppressWarnings("rawtypes")
    public static void entityRenderer(Supplier<? extends EntityType<?>> type, EntityRendererProvider provider) {
        ENTITY_RENDERERS.add(new EntityRendererEntry(type, provider));
    }

    public static List<BlockLayer> blockLayers() {
        return Collections.unmodifiableList(BLOCK_LAYERS);
    }

    public static List<BlockEntityRendererEntry> blockEntityRenderers() {
        return Collections.unmodifiableList(BLOCK_ENTITY_RENDERERS);
    }

    public static List<EntityRendererEntry> entityRenderers() {
        return Collections.unmodifiableList(ENTITY_RENDERERS);
    }
}
