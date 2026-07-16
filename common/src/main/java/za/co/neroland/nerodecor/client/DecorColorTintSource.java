package za.co.neroland.nerodecor.client;

import java.util.Set;

import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import za.co.neroland.nerodecor.content.DecorColor;
import za.co.neroland.nerodecor.content.DecorProperties;

/**
 * The 26.x block tint that renders a decor block's paintable {@link DecorColor}. Registered
 * per loader (Fabric {@code BlockColorRegistry}, NeoForge/Forge
 * {@code RegisterColorHandlersEvent}) against every {@link za.co.neroland.nerodecor.registry.DecorBlocks}
 * entry; the block's model must carry {@code "tintindex": 0} for the colour to show.
 * {@link DecorColor#NATURAL} = white = no visible tint.
 *
 * <p>Client-only (the {@link BlockTintSource} type is client), so it is referenced only from
 * the loader client entry points and never loaded on a dedicated server.
 */
public final class DecorColorTintSource implements BlockTintSource {

    public static final DecorColorTintSource INSTANCE = new DecorColorTintSource();

    private DecorColorTintSource() {
    }

    @Override
    public int color(BlockState state) {
        if (state.hasProperty(DecorProperties.COLOR)) {
            return state.getValue(DecorProperties.COLOR).tint();
        }
        return 0xFFFFFF;
    }

    @Override
    public Set<Property<?>> relevantProperties() {
        return Set.of(DecorProperties.COLOR);
    }
}
