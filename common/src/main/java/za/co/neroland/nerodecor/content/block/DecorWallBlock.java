package za.co.neroland.nerodecor.content.block;

import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

import za.co.neroland.nerodecor.client.ctm.CtmKey;
import za.co.neroland.nerodecor.client.ctm.CtmStyle;
import za.co.neroland.nerodecor.client.ctm.CtmSurface;
import za.co.neroland.nerodecor.content.DecorColor;
import za.co.neroland.nerodecor.content.DecorProperties;

/** Paintable, waterloggable decor wall. Connections/waterlog come from {@link WallBlock}. */
public class DecorWallBlock extends WallBlock implements CtmSurface {

    private final Identifier ctmFamily;
    private final CtmStyle ctmStyle;

    public DecorWallBlock(Properties properties, Identifier ctmFamily, CtmStyle ctmStyle) {
        super(properties);
        this.ctmFamily = ctmFamily;
        this.ctmStyle = ctmStyle;
        registerDefaultState(defaultBlockState().setValue(DecorProperties.COLOR, DecorColor.NATURAL));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(DecorProperties.COLOR);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state == null ? null : state.setValue(DecorProperties.COLOR, DecorColor.NATURAL);
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
