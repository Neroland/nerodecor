package za.co.neroland.nerodecor.registry.collections;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import za.co.neroland.nerodecor.content.block.ConnectedGlowBlock;
import za.co.neroland.nerodecor.content.block.DecorPillarBlock;
import za.co.neroland.nerodecor.content.block.ancients.ArchLintelBlock;
import za.co.neroland.nerodecor.content.block.ancients.DormantCoreBlock;
import za.co.neroland.nerodecor.content.block.ancients.GlyphBlock;
import za.co.neroland.nerodecor.content.block.greebles.DeckSignBlock;
import za.co.neroland.nerodecor.content.block.greebles.FloorArrowBlock;
import za.co.neroland.nerodecor.content.block.greebles.HazardStripeBlock;
import za.co.neroland.nerodecor.content.block.greebles.MaintenancePanelBlock;
import za.co.neroland.nerodecor.content.block.greebles.PipeBracketBlock;
import za.co.neroland.nerodecor.content.block.greebles.RivetPlateBlock;
import za.co.neroland.nerodecor.content.block.greebles.WarningPlacardBlock;
import za.co.neroland.nerodecor.content.block.lights.CeilingCofferBlock;
import za.co.neroland.nerodecor.content.block.lights.EdgeTrimBlock;
import za.co.neroland.nerodecor.content.block.lights.FaceMountedBlock;
import za.co.neroland.nerodecor.content.block.lights.FacingShapeBlock;
import za.co.neroland.nerodecor.content.block.lights.LightPaneBlock;
import za.co.neroland.nerodecor.content.block.lights.MountedStripBlock;
import za.co.neroland.nerodecor.content.block.lights.NeonTubeBlock;
import za.co.neroland.nerodecor.content.block.lights.SconceBarBlock;
import za.co.neroland.nerodecor.content.block.lights.ShapedBlock;
import za.co.neroland.nerodecor.content.block.lights.ShapedPillarBlock;
import za.co.neroland.nerodecor.content.block.lights.TileSlabBlock;
import za.co.neroland.nerodecor.content.fx.AmbientFx;
import za.co.neroland.nerodecor.registry.RegistrationProvider.RegistryEntry;

import static za.co.neroland.nerodecor.content.block.lights.ModelShapes.b;

/**
 * Work package: the chroma_lights, seven_worlds, ancients and greebles collections (resources from the matching
 * modules under {@code tools/collections/}). Every registration for these collections lives here; each id matches
 * the module's {@code Block(id=...)} and each block's state definition matches the module's blockstate contract.
 *
 * <p>Twelve Chroma Lights, twenty-four Seven Worlds blocks, seven Ancients and eight Greebles: 51 blocks, no block
 * entities. Hub-controllable ones ({@code chroma=True} in their module) are listed in
 * {@code #nerodecor:chroma_capable}. Render layers: {@code client.collections.LightsWorldsAncientsGreeblesClient}.
 */
public final class LightsWorldsAncientsGreeblesBlocks {

    /** Blocks drawn with cutout (glow layers, holes, non-full fittings). */
    public static final List<RegistryEntry<Block>> CUTOUT = new ArrayList<>();
    /** Blocks drawn translucent. */
    public static final List<RegistryEntry<Block>> TRANSLUCENT = new ArrayList<>();

    private LightsWorldsAncientsGreeblesBlocks() {
    }

    /** Called once from {@link CollectionRegistries#init()}. */
    public static void init() {
        chromaLights();
        sevenWorlds();
        ancients();
        greebles();
    }

    public static List<RegistryEntry<Block>> cutout() {
        return Collections.unmodifiableList(CUTOUT);
    }

    public static List<RegistryEntry<Block>> translucent() {
        return Collections.unmodifiableList(TRANSLUCENT);
    }

    // =============================================================================================
    // Chroma Lights (12): untinted housings, hub-coloured diffusers; light from the module.
    // =============================================================================================
    private static void chromaLights() {
        cut(CollectionRegistries.block("light_strip", key -> new MountedStripBlock(fitting(key, 12),
                new double[][] {b(0, 0, 7, 16, 1, 9)}, new double[][] {b(7, 0, 0, 9, 1, 16)})));
        cut(CollectionRegistries.block("edge_trim", key -> new EdgeTrimBlock(fitting(key, 10))));
        cut(CollectionRegistries.block("floor_tile", key -> new ConnectedGlowBlock(
                props(key, p -> p.mapColor(MapColor.COLOR_BLUE).strength(3.0F, 6.0F).requiresCorrectToolForDrops()
                        .sound(SoundType.METAL).lightLevel(s -> 8)), AmbientFx.NONE, false)));
        cut(CollectionRegistries.block("floor_tile_slab", key -> new TileSlabBlock(
                props(key, p -> p.mapColor(MapColor.COLOR_BLUE).strength(3.0F, 6.0F).requiresCorrectToolForDrops()
                        .sound(SoundType.METAL).lightLevel(s -> 8)))));
        cut(CollectionRegistries.block("step_light", key -> new FacingShapeBlock(fitting(key, 9),
                b(0, 0, 0, 16, 2, 3))));
        cut(CollectionRegistries.block("ceiling_coffer", key -> new CeilingCofferBlock(fitting(key, 15))));
        cut(CollectionRegistries.block("light_pillar", key -> new ShapedPillarBlock(fitting(key, 15),
                AmbientFx.NONE, 4)));
        cut(CollectionRegistries.block("neon_tube", key -> new NeonTubeBlock(
                props(key, p -> p.mapColor(MapColor.NONE).strength(0.5F).sound(SoundType.GLASS).noOcclusion()
                        .lightLevel(s -> 13)))));
        cut(CollectionRegistries.block("light_pane", key -> new LightPaneBlock(
                props(key, p -> p.mapColor(MapColor.SNOW).strength(0.8F).sound(SoundType.GLASS).noOcclusion()
                        .lightLevel(s -> 14)))));
        cut(CollectionRegistries.block("sconce_bar", key -> new SconceBarBlock(fitting(key, 13))));
        cut(CollectionRegistries.block("halo_ring", key -> new ShapedBlock(fitting(key, 15),
                b(1, 7, 1, 15, 9, 15))));
        cut(CollectionRegistries.block("cage_lamp", key -> new FaceMountedBlock(
                props(key, p -> p.mapColor(MapColor.METAL).strength(1.0F).sound(SoundType.LANTERN).noOcclusion()
                        .lightLevel(s -> 14)), false, b(4, 0, 4, 12, 10, 12))));
    }

    // =============================================================================================
    // Seven Worlds (24): 3-4 per Nerospace world; random wear variants are model-driven.
    // =============================================================================================
    private static void sevenWorlds() {
        // Station
        CollectionRegistries.block("station_plating", key -> new ConnectedGlowBlock(
                stone(key, MapColor.METAL, 3.0F, SoundType.METAL), AmbientFx.NONE, false));
        CollectionRegistries.block("station_tread_plate", key -> new Block(stone(key, MapColor.METAL, 3.0F,
                SoundType.METAL)));
        CollectionRegistries.block("station_thermal_blanket", key -> new Block(stone(key, MapColor.GOLD, 1.0F,
                SoundType.WOOL)));
        // Luna
        CollectionRegistries.block("luna_regolith_brick", key -> new Block(stone(key, MapColor.COLOR_LIGHT_GRAY, 2.0F,
                SoundType.TUFF_BRICKS)));
        CollectionRegistries.block("luna_bootprint_tile", key -> new Block(stone(key, MapColor.COLOR_LIGHT_GRAY, 1.5F,
                SoundType.TUFF)));
        CollectionRegistries.block("luna_mare_column", key -> new DecorPillarBlock(stone(key, MapColor.COLOR_GRAY,
                2.0F, SoundType.BASALT), AmbientFx.NONE));
        CollectionRegistries.block("luna_bead_glass", key -> new Block(
                props(key, p -> p.mapColor(MapColor.COLOR_ORANGE).strength(1.0F).sound(SoundType.GLASS))));
        // Mars
        CollectionRegistries.block("mars_rust_plate", key -> new Block(stone(key, MapColor.TERRACOTTA_ORANGE, 3.0F,
                SoundType.COPPER)));
        CollectionRegistries.block("mars_wind_strata_brick", key -> new Block(stone(key, MapColor.COLOR_ORANGE, 2.0F,
                SoundType.MUD_BRICKS)));
        CollectionRegistries.block("mars_spherule_stone", key -> new Block(stone(key, MapColor.TERRACOTTA_RED, 1.5F,
                SoundType.STONE)));
        CollectionRegistries.block("mars_dust_drift_tile", key -> new FacingShapeBlock(stone(key,
                MapColor.COLOR_ORANGE, 1.5F, SoundType.STONE)));
        // Cindara
        cut(CollectionRegistries.block("cindara_ember_basalt", key -> new Block(stone(key, MapColor.COLOR_BLACK, 2.0F,
                SoundType.BASALT))));
        CollectionRegistries.block("cindara_slag_brick", key -> new Block(stone(key, MapColor.NETHER, 2.0F,
                SoundType.NETHER_BRICKS)));
        CollectionRegistries.block("cindara_ash_tile", key -> new Block(stone(key, MapColor.COLOR_GRAY, 1.5F,
                SoundType.TUFF)));
        // Glacira
        TRANSLUCENT.add(CollectionRegistries.block("glacira_bubble_ice", key -> new ConnectedGlowBlock(
                BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS).setId(key).mapColor(MapColor.ICE).strength(0.5F)
                        .sound(SoundType.GLASS), AmbientFx.NONE, true)));
        CollectionRegistries.block("glacira_cryo_fin", key -> new ShapedPillarBlock(
                props(key, p -> p.mapColor(MapColor.ICE).strength(2.0F, 6.0F).requiresCorrectToolForDrops()
                        .sound(SoundType.METAL).noOcclusion()), AmbientFx.NONE, 1));
        CollectionRegistries.block("glacira_frost_plate", key -> new Block(stone(key, MapColor.ICE, 1.5F,
                SoundType.CALCITE)));
        CollectionRegistries.block("glacira_snow_brick", key -> new Block(stone(key, MapColor.SNOW, 1.5F,
                SoundType.CALCITE)));
        // Greenxertz
        CollectionRegistries.block("greenxertz_woven_chitin", key -> new Block(stone(key, MapColor.COLOR_GREEN, 1.5F,
                SoundType.BONE_BLOCK)));
        cut(CollectionRegistries.block("greenxertz_spore_tile", key -> new Block(stone(key, MapColor.WARPED_NYLIUM,
                1.5F, SoundType.NYLIUM))));
        CollectionRegistries.block("greenxertz_hive_comb", key -> new Block(stone(key, MapColor.COLOR_YELLOW, 1.5F,
                SoundType.CORAL_BLOCK)));
        // Nyxara
        CollectionRegistries.block("nyxara_faceted_crystal", key -> new Block(stone(key, MapColor.COLOR_PURPLE, 1.5F,
                SoundType.AMETHYST)));
        CollectionRegistries.block("nyxara_shard_mosaic", key -> new Block(stone(key, MapColor.COLOR_PURPLE, 1.5F,
                SoundType.AMETHYST)));
        CollectionRegistries.block("nyxara_geode_block", key -> new Block(stone(key, MapColor.COLOR_PURPLE, 2.0F,
                SoundType.CALCITE)));
    }

    // =============================================================================================
    // Ancients (7): weathered precursor stone; the dormant core wakes on redstone.
    // =============================================================================================
    private static void ancients() {
        cut(CollectionRegistries.block("glyph_block", key -> new GlyphBlock(ancient(key))));
        CollectionRegistries.block("ancient_brick", key -> new Block(ancient(key)));
        CollectionRegistries.block("cracked_ancient_brick", key -> new Block(ancient(key)));
        CollectionRegistries.block("ancient_pillar", key -> new DecorPillarBlock(ancient(key), AmbientFx.NONE));
        cut(CollectionRegistries.block("dormant_core", key -> new DormantCoreBlock(
                props(key, p -> p.mapColor(MapColor.COLOR_GRAY).strength(3.0F, 6.0F).requiresCorrectToolForDrops()
                        .sound(SoundType.DEEPSLATE_BRICKS)
                        .lightLevel(s -> s.getValue(DormantCoreBlock.POWERED) ? 12 : 0)))));
        cut(CollectionRegistries.block("glyph_strip", key -> new MountedStripBlock(
                props(key, p -> p.mapColor(MapColor.COLOR_GRAY).strength(1.5F, 6.0F).requiresCorrectToolForDrops()
                        .sound(SoundType.DEEPSLATE_BRICKS).noOcclusion()),
                new double[][] {b(0, 0, 6, 16, 1, 10)}, new double[][] {b(6, 0, 0, 10, 1, 16)})));
        CollectionRegistries.block("arch_lintel", key -> new ArchLintelBlock(
                props(key, p -> p.mapColor(MapColor.COLOR_GRAY).strength(3.0F, 6.0F).requiresCorrectToolForDrops()
                        .sound(SoundType.DEEPSLATE_BRICKS).noOcclusion())));
    }

    // =============================================================================================
    // Greebles (8): surface detail; empty-hand use cycles each piece's variant.
    // =============================================================================================
    private static void greebles() {
        cut(CollectionRegistries.block("louvre_vent", key -> new FaceMountedBlock(plate(key, 1.5F), true,
                b(0, 0, 0, 16, 16, 2))));
        cut(CollectionRegistries.block("maintenance_panel", key -> new MaintenancePanelBlock(plate(key, 1.5F))));
        cut(CollectionRegistries.block("hazard_stripe", key -> new HazardStripeBlock(plate(key, 0.5F))));
        cut(CollectionRegistries.block("deck_sign", key -> new DeckSignBlock(plate(key, 1.0F))));
        cut(CollectionRegistries.block("warning_placard", key -> new WarningPlacardBlock(plate(key, 1.0F))));
        cut(CollectionRegistries.block("rivet_plate", key -> new RivetPlateBlock(plate(key, 1.5F))));
        cut(CollectionRegistries.block("pipe_bracket", key -> new PipeBracketBlock(plate(key, 1.5F))));
        cut(CollectionRegistries.block("floor_arrow", key -> new FloorArrowBlock(
                props(key, p -> p.mapColor(MapColor.COLOR_YELLOW).strength(0.3F).sound(SoundType.METAL)
                        .noOcclusion()))));
    }

    // --- property presets ------------------------------------------------------------------------
    private static BlockBehaviour.Properties props(ResourceKey<Block> key,
                                                   Function<BlockBehaviour.Properties, BlockBehaviour.Properties> f) {
        return f.apply(BlockBehaviour.Properties.of().setId(key));
    }

    /** A light fitting: metal housing, pickaxe, not a full block, fixed light level. */
    private static BlockBehaviour.Properties fitting(ResourceKey<Block> key, int light) {
        return props(key, p -> p.mapColor(MapColor.METAL).strength(1.5F, 6.0F).requiresCorrectToolForDrops()
                .sound(SoundType.METAL).noOcclusion().lightLevel(s -> light));
    }

    /** A full building block of a world material. */
    private static BlockBehaviour.Properties stone(ResourceKey<Block> key, MapColor colour, float hardness,
                                                  SoundType sound) {
        return props(key, p -> p.mapColor(colour).strength(hardness, 6.0F).requiresCorrectToolForDrops().sound(sound));
    }

    /** Precursor stone. */
    private static BlockBehaviour.Properties ancient(ResourceKey<Block> key) {
        return props(key, p -> p.mapColor(MapColor.COLOR_GRAY).strength(3.0F, 6.0F).requiresCorrectToolForDrops()
                .sound(SoundType.DEEPSLATE_BRICKS));
    }

    /** A thin metal greeble. */
    private static BlockBehaviour.Properties plate(ResourceKey<Block> key, float hardness) {
        return props(key, p -> p.mapColor(MapColor.METAL).strength(hardness, 6.0F).requiresCorrectToolForDrops()
                .sound(SoundType.METAL).noOcclusion());
    }

    private static RegistryEntry<Block> cut(RegistryEntry<Block> entry) {
        CUTOUT.add(entry);
        return entry;
    }
}
