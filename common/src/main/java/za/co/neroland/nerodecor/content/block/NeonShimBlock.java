package za.co.neroland.nerodecor.content.block;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;

import za.co.neroland.nerodecor.client.ctm.CtmStyle;
import za.co.neroland.nerodecor.content.DecorColor;
import za.co.neroland.nerodecor.content.NeonFold;

/**
 * A legacy 0.7.x {@code neon_<colour>} block, kept registered (hidden) only so old worlds and inventories load. It
 * keeps the old {@code COLOR} state so saved states parse, and is swapped for a same-colour
 * {@code chroma_cube} as soon as its chunk loads ({@link NeonFold}); placing one places a chroma cube instead.
 */
public class NeonShimBlock extends DecorCubeBlock {

    private final DecorColor dye;

    public NeonShimBlock(Properties properties, Identifier ctmFamily, DecorColor dye) {
        super(properties, ctmFamily, CtmStyle.STRIP);
        this.dye = dye;
    }

    /** The colour the old neon block showed. */
    public DecorColor dye() {
        return dye;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return NeonFold.cubeFor(dye, context.getLevel(), context.getClickedPos());
    }
}
