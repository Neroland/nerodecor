package za.co.neroland.nerodecor.client;

import java.util.Set;

import net.minecraft.core.BlockPos;
//? if >=26.1 {
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
//?} else {
/*import net.minecraft.client.color.block.BlockColor;
import net.minecraft.world.level.BlockAndTintGetter;
*///?}
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerodecor.chroma.client.ChromaClientSync;
import za.co.neroland.nerodecor.content.DecorColor;
import za.co.neroland.nerodecor.content.DecorProperties;
import za.co.neroland.nerodecor.registry.DecorBlocks;

/**
 * The block tint for every NeroDecor block: the static Chroma colour a hub gives the block at that position
 * ({@link ChromaClientSync#colors()}), falling back to the block's own paintable {@link DecorColor} (white when it has
 * none, which leaves the texture unchanged). Chroma lights, whose glow layer is authored near-white so a hub colour
 * multiplies cleanly, would glow flat white when unpainted and unlinked; for {@link DecorColor#NATURAL} they take the
 * house cyan at reduced value ({@link #IDLE_GLOW}) instead. Paintable hulls and panels stay untinted when natural.
 * Faces opt in with {@code "tintindex": 0}.
 *
 * <p>Per version range:
 * <ul>
 *   <li><b>26.x</b>: a {@link BlockTintSource}. Its position-aware hook is
 *       {@code colorInWorld(BlockState, BlockAndTintGetter, BlockPos)} (used by {@code ModelBlockRenderer} when
 *       meshing); {@code color(BlockState)} is the position-less form (items, particles without a level).</li>
 *   <li><b>1.21.x</b>: a {@code BlockColor}, whose {@code getColor(state, level, pos, tintIndex)} already gets the
 *       position (null for position-less callers).</li>
 * </ul>
 * Registered per loader (Fabric {@code BlockColorRegistry} / {@code ColorProviderRegistry}, NeoForge/Forge
 * {@code RegisterColorHandlersEvent}) for {@link #blocks()}. Client-only.
 */
//? if >=26.1 {
public final class ChromaTintBridge implements BlockTintSource {
//?} else {
/*public final class ChromaTintBridge implements BlockColor {
*///?}

    /** For blocks with the {@code COLOR} property (its value is a tint input). */
    public static final ChromaTintBridge PAINTED = new ChromaTintBridge(true);
    /**
     * For decor blocks without {@code COLOR} (Luminous, collection lights): 26.x reads every property a tint source
     * declares relevant off each state while grouping models, so this one must declare none.
     */
    public static final ChromaTintBridge PLAIN = new ChromaTintBridge(false);

    /** The idle tint of an unpainted, unlinked Chroma light: house cyan (0x22E8FF) at about 70% value. */
    public static final int IDLE_GLOW = 0x18A3B3;

    /** Ids of the blocks whose tinted layer is a near-white glow (chroma_lights collection plus the Chroma cube). */
    private static final Set<String> GLOW_IDS = Set.of("chroma_cube", "light_strip", "edge_trim", "floor_tile",
            "floor_tile_slab", "step_light", "ceiling_coffer", "light_pillar", "neon_tube", "light_pane", "sconce_bar",
            "halo_ring", "cage_lamp");

    private static volatile Set<Block> glowBlocks;

    private final boolean painted;

    private ChromaTintBridge(boolean painted) {
        this.painted = painted;
    }

    /** Decor blocks that carry {@code COLOR} (register {@link #PAINTED}). */
    public static Block[] paintedBlocks() {
        return DecorBlocks.paintableBlocks();
    }

    /** Every other decor block (register {@link #PLAIN}). */
    public static Block[] plainBlocks() {
        return java.util.Arrays.stream(DecorBlocks.allBlocks())
                .filter(b -> !b.defaultBlockState().hasProperty(DecorProperties.COLOR))
                .toArray(Block[]::new);
    }

    /** The opaque tint at {@code pos}: hub colour, else the block's paint. */
    public static int tint(BlockState state, @Nullable BlockPos pos) {
        if (pos != null) {
            int hub = ChromaClientSync.colors().argbAt(pos.getX(), pos.getY(), pos.getZ());
            if (hub != 0) {
                return hub;
            }
        }
        return 0xFF000000 | ownColour(state);
    }

    private static int ownColour(BlockState state) {
        if (state.hasProperty(DecorProperties.COLOR)) {
            DecorColor colour = state.getValue(DecorProperties.COLOR);
            return colour == DecorColor.NATURAL && isGlow(state.getBlock()) ? IDLE_GLOW : colour.tint();
        }
        return isGlow(state.getBlock()) ? IDLE_GLOW : 0xFFFFFF;
    }

    /** Whether {@code block} is a Chroma light (near-white glow layer). Built once, after registration. */
    public static boolean isGlow(Block block) {
        Set<Block> known = glowBlocks;
        if (known == null) {
            Set<Block> built = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            for (Block b : DecorBlocks.allBlocks()) {
                var key = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b);
                if (za.co.neroland.nerodecor.NeroDecorCommon.MOD_ID.equals(key.getNamespace())
                        && GLOW_IDS.contains(key.getPath())) {
                    built.add(b);
                }
            }
            glowBlocks = known = built;
        }
        return known.contains(block);
    }

    //? if >=26.1 {
    @Override
    public int color(BlockState state) {
        return 0xFF000000 | ownColour(state);
    }

    @Override
    public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
        return tint(state, pos);
    }

    @Override
    public Set<Property<?>> relevantProperties() {
        return painted ? Set.of(DecorProperties.COLOR) : Set.of();
    }
    //?} else {
    /*@Override
    public int getColor(BlockState state, @Nullable BlockAndTintGetter level, @Nullable BlockPos pos, int tintIndex) {
        return tint(state, pos);
    }
    *///?}
}
