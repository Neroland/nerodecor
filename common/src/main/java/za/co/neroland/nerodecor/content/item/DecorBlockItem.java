package za.co.neroland.nerodecor.content.item;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import za.co.neroland.nerodecor.content.DecorColor;
import za.co.neroland.nerodecor.content.DecorProperties;
import za.co.neroland.nerodecor.registry.ModDataComponents;

/**
 * The block item for a paintable decor block: it bridges the {@code nerodecor:color} data
 * component on the stack to the placed block's {@link DecorProperties#COLOR} state, so a
 * pre-dyed (or paint-gun-copied) stack places already-painted. Unpainted stacks place as
 * {@link DecorColor#NATURAL}.
 */
public class DecorBlockItem extends BlockItem {

    public DecorBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    @Nullable
    protected BlockState getPlacementState(BlockPlaceContext context) {
        BlockState state = super.getPlacementState(context);
        if (state == null || !state.hasProperty(DecorProperties.COLOR)) {
            return state;
        }
        DecorColor colour = context.getItemInHand().get(ModDataComponents.COLOR.get());
        return colour == null ? state : state.setValue(DecorProperties.COLOR, colour);
    }
}
