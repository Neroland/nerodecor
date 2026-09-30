package za.co.neroland.nerodecor.registry;

import java.util.function.Function;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import za.co.neroland.nerodecor.content.block.ConnectedGlowBlock;
import za.co.neroland.nerodecor.content.block.DecorPillarBlock;
import za.co.neroland.nerodecor.content.block.FusionLampBlock;
import za.co.neroland.nerodecor.content.block.GlowDecorBlock;
import za.co.neroland.nerodecor.content.fx.AmbientFx;
import za.co.neroland.nerodecor.content.fx.AmbientFx.Motes;
import za.co.neroland.nerodecor.registry.RegistrationProvider.RegistryEntry;

/**
 * The <b>Luminous collection</b> (0.4.0) — NeroDecor's signature set: fourteen blocks, plus three
 * slabs, that glow at night, animate, connect into seamless surfaces and hum, chirp or chime
 * softly. All model-only (no block entities). Keep in LOCKSTEP with the {@code LUMINOUS} spec in
 * {@code tools/gen_resources.py} and the designs in {@code tools/gen_luminous.py}.
 *
 * <p>Light levels are deliberately moderate except for the two light sources (fusion lamp, lumen
 * panel): the collection is meant to <i>glow</i>, not to replace torches.
 */
public final class LuminousBlocks {

    private LuminousBlocks() {
    }

    public static void init() {
        // --- connected surfaces (bezel drawn only on outer edges) ---------------------------
        connected("circuit_plating", panel(MapColor.COLOR_CYAN, 4), AmbientFx.builder()
                .sound(ModSounds.CIRCUIT_CHIRP, 12, 0.22F, 120).build(), false);
        slab("circuit_plating_slab", panel(MapColor.COLOR_CYAN, 4));

        connected("holo_grid_floor", panel(MapColor.COLOR_BLACK, 6), AmbientFx.NONE, false);
        slab("holo_grid_floor_slab", panel(MapColor.COLOR_BLACK, 6));

        connected("starfield_panel", panel(MapColor.COLOR_BLUE, 3), AmbientFx.builder()
                .motes(Motes.STAR_GLINT, 7).build(), false);

        connected("data_stream_panel", panel(MapColor.COLOR_BLACK, 5), AmbientFx.builder()
                .sound(ModSounds.CIRCUIT_CHIRP, 14, 0.18F, 120).pitch(0.84F).build(), false);

        DecorBlocks.track(DecorBlocks.BLOCKS.register("aurora_glass", key -> new ConnectedGlowBlock(
                glass(key, 7), AmbientFx.NONE, true)));

        connected("lumen_panel", p -> p.mapColor(MapColor.SNOW).strength(0.8F).sound(SoundType.GLASS)
                .lightLevel(s -> 15), AmbientFx.NONE, false);
        slab("lumen_panel_slab", p -> p.mapColor(MapColor.SNOW).strength(0.8F).sound(SoundType.GLASS)
                .lightLevel(s -> 15));

        // --- feature blocks -----------------------------------------------------------------
        plain("void_rift", p -> p.mapColor(MapColor.COLOR_PURPLE).strength(2.0F, 6.0F)
                .requiresCorrectToolForDrops().sound(SoundType.AMETHYST_CLUSTER).lightLevel(s -> 8),
                AmbientFx.builder().motes(Motes.RIFT_PULL, 1)
                        .sound(ModSounds.VOID_RIFT_HUM, 3, 0.4F, 80).build());

        plain("crystal_lattice", p -> p.mapColor(MapColor.COLOR_PURPLE).strength(1.5F)
                .requiresCorrectToolForDrops().sound(SoundType.AMETHYST).lightLevel(s -> 6),
                AmbientFx.builder().motes(Motes.CRYSTAL_GLINT, 4)
                        .sound(ModSounds.CRYSTAL_CHIME, 14, 0.3F, 140).build());

        plain("capacitor_bank", panel(MapColor.METAL, 7), AmbientFx.builder()
                .sound(ModSounds.CAPACITOR_CHARGE, 12, 0.22F, 160).build());

        plain("xeno_bloom", p -> p.mapColor(MapColor.WARPED_NYLIUM).strength(3.0F, 6.0F)
                .requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE).lightLevel(s -> 6),
                AmbientFx.builder().motes(Motes.SPORE_GLOW, 8).build());

        plain("ion_vent", panel(MapColor.METAL, 5), AmbientFx.builder().motes(Motes.VENT_PLUME, 2)
                .sound(ModSounds.VENT_HISS, 8, 0.2F, 100).build());

        // --- pillars (connect along their axis) ---------------------------------------------
        pillar("plasma_conduit", panel(MapColor.COLOR_CYAN, 10), AmbientFx.builder()
                .motes(Motes.CONDUIT_SPARK, 5).sound(ModSounds.CONDUIT_CRACKLE, 10, 0.25F, 100).build());
        pillar("starsteel_pillar", panel(MapColor.METAL, 3), AmbientFx.NONE);

        // --- light source -------------------------------------------------------------------
        DecorBlocks.track(DecorBlocks.BLOCKS.register("fusion_lamp", key -> new FusionLampBlock(
                props(key, p -> p.mapColor(MapColor.METAL).strength(0.6F).sound(SoundType.COPPER_BULB)
                        .lightLevel(s -> s.getValue(FusionLampBlock.LIT) ? 15 : 0)),
                AmbientFx.builder().motes(Motes.LAMP_MOTE, 6).sound(ModSounds.LAMP_HUM, 5, 0.16F, 60).build(),
                ModSounds.LAMP_POWER_ON, ModSounds.LAMP_POWER_OFF)));
    }

    // --- property presets -----------------------------------------------------------------
    /** Metal-panel feel: mined with a pickaxe, soft glow of {@code light}. */
    private static Function<BlockBehaviour.Properties, BlockBehaviour.Properties> panel(MapColor colour, int light) {
        return p -> p.mapColor(colour).strength(3.0F, 6.0F).requiresCorrectToolForDrops()
                .sound(SoundType.METAL).lightLevel(s -> light);
    }

    /**
     * Vanilla glass behaviour (no spawning, not a redstone conductor, not suffocating or
     * view-blocking) copied from {@link Blocks#GLASS}, so it stays right across MC versions whose
     * predicate signatures differ (26.3 changed {@code isViewBlocking}).
     */
    private static BlockBehaviour.Properties glass(ResourceKey<Block> key, int light) {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS).setId(key)
                .mapColor(MapColor.NONE).strength(0.5F).sound(SoundType.GLASS).lightLevel(s -> light);
    }

    // --- register helpers -----------------------------------------------------------------
    private static BlockBehaviour.Properties props(ResourceKey<Block> key,
                                                   Function<BlockBehaviour.Properties, BlockBehaviour.Properties> f) {
        return f.apply(BlockBehaviour.Properties.of().setId(key));
    }

    private static RegistryEntry<Block> connected(String name,
                                                  Function<BlockBehaviour.Properties, BlockBehaviour.Properties> f,
                                                  AmbientFx fx, boolean glassy) {
        return DecorBlocks.track(DecorBlocks.BLOCKS.register(name,
                key -> new ConnectedGlowBlock(props(key, f), fx, glassy)));
    }

    private static RegistryEntry<Block> plain(String name,
                                              Function<BlockBehaviour.Properties, BlockBehaviour.Properties> f,
                                              AmbientFx fx) {
        return DecorBlocks.track(DecorBlocks.BLOCKS.register(name, key -> new GlowDecorBlock(props(key, f), fx, false)));
    }

    private static RegistryEntry<Block> pillar(String name,
                                               Function<BlockBehaviour.Properties, BlockBehaviour.Properties> f,
                                               AmbientFx fx) {
        return DecorBlocks.track(DecorBlocks.BLOCKS.register(name, key -> new DecorPillarBlock(props(key, f), fx)));
    }

    private static RegistryEntry<Block> slab(String name,
                                             Function<BlockBehaviour.Properties, BlockBehaviour.Properties> f) {
        return DecorBlocks.track(DecorBlocks.BLOCKS.register(name, key -> new SlabBlock(props(key, f))));
    }
}
