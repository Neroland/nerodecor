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
 * none, which leaves the texture unchanged). Faces opt in with {@code "tintindex": 0}.
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

    public static final ChromaTintBridge INSTANCE = new ChromaTintBridge();

    private ChromaTintBridge() {
    }

    /** Every decor block (paintable families, Luminous, collections, the chroma cube). */
    public static Block[] blocks() {
        return DecorBlocks.allBlocks();
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
            return state.getValue(DecorProperties.COLOR).tint();
        }
        return 0xFFFFFF;
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
        return Set.of(DecorProperties.COLOR);
    }
    //?} else {
    /*@Override
    public int getColor(BlockState state, @Nullable BlockAndTintGetter level, @Nullable BlockPos pos, int tintIndex) {
        return tint(state, pos);
    }
    *///?}
}
