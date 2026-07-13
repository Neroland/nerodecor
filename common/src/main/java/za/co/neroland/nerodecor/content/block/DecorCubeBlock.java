package za.co.neroland.nerodecor.content.block;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

import za.co.neroland.nerodecor.client.ctm.CtmKey;
import za.co.neroland.nerodecor.client.ctm.CtmStyle;
import za.co.neroland.nerodecor.client.ctm.CtmSurface;
import za.co.neroland.nerodecor.content.DecorColor;
import za.co.neroland.nerodecor.content.DecorProperties;

/**
 * A full-cube decor block (hull, panel, glass, neon base): carries the paintable
 * {@link DecorProperties#COLOR} and participates in connected textures via {@link CtmSurface}.
 * Full cubes are not waterloggable (the shape blocks — slab/stair/wall/pane — inherit
 * waterlogging from their vanilla parents).
 */
public class DecorCubeBlock extends Block implements CtmSurface {

    private final Identifier ctmFamily;
    private final CtmStyle ctmStyle;

    public DecorCubeBlock(Properties properties, Identifier ctmFamily, CtmStyle ctmStyle) {
        super(properties);
        this.ctmFamily = ctmFamily;
        this.ctmStyle = ctmStyle;
        registerDefaultState(defaultBlockState().setValue(DecorProperties.COLOR, DecorColor.NATURAL));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DecorProperties.COLOR);
    }

    @Override
    public CtmKey ctmKey(BlockState state) {
        return new CtmKey(ctmFamily, state.getValue(DecorProperties.COLOR).ordinal());
    }

    @Override
    public CtmStyle ctmStyle() {
        return ctmStyle;
    }
}
