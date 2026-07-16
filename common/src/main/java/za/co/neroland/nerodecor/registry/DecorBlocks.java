package za.co.neroland.nerodecor.registry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import za.co.neroland.nerodecor.NeroDecorCommon;
import za.co.neroland.nerodecor.client.ctm.CtmStyle;
import za.co.neroland.nerodecor.content.block.DecorCubeBlock;
import za.co.neroland.nerodecor.content.block.DecorPaneBlock;
import za.co.neroland.nerodecor.content.block.DecorSlabBlock;
import za.co.neroland.nerodecor.content.block.DecorStairBlock;
import za.co.neroland.nerodecor.content.block.DecorWallBlock;
import za.co.neroland.nerodecor.registry.RegistrationProvider.RegistryEntry;

/**
 * The Stage-E static block families (hull, industrial panel, reinforced glass, neon),
 * registered cross-loader through {@link RegistrationProvider}. Each block carries the
 * paintable {@code COLOR} property and its CTM family id. This set is representative of the
 * four families and every shape type (cube/slab/stair/wall/pane); it grows toward the
 * ADR-001 budget by adding entries here (datagen + gen_textures read the same set).
 *
 * <p>{@link #ALL} keeps registration order so {@code ModItems} and datagen iterate the same
 * list. CTM family ids are {@code nerodecor:<family>}; two faces connect on same family +
 * same painted colour.
 */
public final class DecorBlocks {

    public static final RegistrationProvider<Block> BLOCKS =
            RegistrationProvider.get(Registries.BLOCK, NeroDecorCommon.MOD_ID);

    /** Every registered decor block, in registration order. */
    public static final List<RegistryEntry<? extends Block>> ALL = new ArrayList<>();

    private static final Identifier FAM_HULL = fam("hull");
    private static final Identifier FAM_PANEL = fam("panel");
    private static final Identifier FAM_GLASS = fam("glass");
    private static final Identifier FAM_NEON = fam("neon");

    // Finish sets — keep in LOCKSTEP with tools/gen_textures.py + tools/gen_resources.py.
    private static final String[] STRUCT_MATERIALS = {"nero_alloy", "starsteel", "void_crystal"};
    private static final String[] GLASS_FINISHES = {"plasma_glass", "cyan", "light_blue"};
    private static final String[] NEON_COLOURS = {
            "red", "orange", "yellow", "lime", "green", "cyan",
            "light_blue", "blue", "purple", "magenta", "pink", "white"};

    /** A hull block used by the {@code /nerodecor gallery} paint row; assigned in {@link #init()}. */
    public static RegistryEntry<Block> HULL_NERO_ALLOY;

    private DecorBlocks() {
    }

    /** Register every decor block. Called once from {@code DecorRegistries.init()}. */
    public static void init() {
        // Hull / structural: cube + slab + stairs + wall per material.
        for (String m : STRUCT_MATERIALS) {
            RegistryEntry<Block> cube = cube("hull_" + m, FAM_HULL, DecorBlocks::metal);
            slab("hull_" + m + "_slab", FAM_HULL, DecorBlocks::metal);
            stairs("hull_" + m + "_stairs", cube, FAM_HULL, DecorBlocks::metal);
            wall("hull_" + m + "_wall", FAM_HULL, DecorBlocks::metal);
            if ("nero_alloy".equals(m)) {
                HULL_NERO_ALLOY = cube;
            }
        }
        // Industrial panels: cube + slab + stairs per material.
        for (String m : STRUCT_MATERIALS) {
            RegistryEntry<Block> cube = cube("panel_" + m, FAM_PANEL, DecorBlocks::metal);
            slab("panel_" + m + "_slab", FAM_PANEL, DecorBlocks::metal);
            stairs("panel_" + m + "_stairs", cube, FAM_PANEL, DecorBlocks::metal);
        }
        // Reinforced glass: cube + pane + slab per tint (no stairs, so no base-cube capture needed).
        for (String f : GLASS_FINISHES) {
            cube("glass_" + f, FAM_GLASS, CtmStyle.GLASS, DecorBlocks::glass);
            pane("glass_" + f + "_pane", FAM_GLASS, DecorBlocks::glass);
            slab("glass_" + f + "_slab", FAM_GLASS, CtmStyle.GLASS, DecorBlocks::glass);
        }
        // Neon light strips: one per colour.
        for (String c : NEON_COLOURS) {
            cube("neon_" + c, FAM_NEON, CtmStyle.STRIP, DecorBlocks::neon);
        }
    }

    /** Every registered block instance — for client colour/render registration. */
    public static Block[] allBlocks() {
        return ALL.stream().map(RegistryEntry::get).toArray(Block[]::new);
    }

    // --- property presets ---------------------------------------------------
    private static BlockBehaviour.Properties metal(BlockBehaviour.Properties p) {
        return p.mapColor(MapColor.METAL).strength(4.0F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.METAL);
    }

    private static BlockBehaviour.Properties glass(BlockBehaviour.Properties p) {
        return p.mapColor(MapColor.NONE).strength(0.5F).sound(SoundType.GLASS).noOcclusion();
    }

    private static BlockBehaviour.Properties neon(BlockBehaviour.Properties p) {
        return p.mapColor(MapColor.COLOR_LIGHT_BLUE).strength(0.4F).sound(SoundType.GLASS).lightLevel(s -> 15);
    }

    // --- register helpers ---------------------------------------------------
    private static RegistryEntry<Block> cube(String name, Identifier family, UnaryOperator<BlockBehaviour.Properties> props) {
        return cube(name, family, CtmStyle.FULL, props);
    }

    private static RegistryEntry<Block> cube(String name, Identifier family, CtmStyle style,
                                             UnaryOperator<BlockBehaviour.Properties> props) {
        return track(BLOCKS.register(name, key -> new DecorCubeBlock(props.apply(base(key)), family, style)));
    }

    private static RegistryEntry<Block> slab(String name, Identifier family, UnaryOperator<BlockBehaviour.Properties> props) {
        return slab(name, family, CtmStyle.FULL, props);
    }

    private static RegistryEntry<Block> slab(String name, Identifier family, CtmStyle style,
                                             UnaryOperator<BlockBehaviour.Properties> props) {
        return track(BLOCKS.register(name, key -> new DecorSlabBlock(props.apply(base(key)), family, style)));
    }

    private static RegistryEntry<Block> stairs(String name, RegistryEntry<Block> baseCube, Identifier family,
                                               UnaryOperator<BlockBehaviour.Properties> props) {
        return track(BLOCKS.register(name,
                key -> new DecorStairBlock(baseCube.get().defaultBlockState(), props.apply(base(key)), family, CtmStyle.FULL)));
    }

    private static RegistryEntry<Block> wall(String name, Identifier family, UnaryOperator<BlockBehaviour.Properties> props) {
        return track(BLOCKS.register(name, key -> new DecorWallBlock(props.apply(base(key)), family, CtmStyle.FULL)));
    }

    private static RegistryEntry<Block> pane(String name, Identifier family, UnaryOperator<BlockBehaviour.Properties> props) {
        return track(BLOCKS.register(name, key -> new DecorPaneBlock(props.apply(base(key)), family)));
    }

    private static BlockBehaviour.Properties base(ResourceKey<Block> key) {
        return BlockBehaviour.Properties.of().setId(key);
    }

    private static RegistryEntry<Block> track(RegistryEntry<Block> entry) {
        ALL.add(entry);
        return entry;
    }

    private static Identifier fam(String path) {
        return Identifier.fromNamespaceAndPath(NeroDecorCommon.MOD_ID, path);
    }
}
