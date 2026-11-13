package za.co.neroland.nerodecor.content.item;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

import za.co.neroland.nerodecor.chroma.server.ChromaNetworks;

/**
 * A decor block item that also offers the placed block to the placer's auto-adopt hubs
 * ({@link ChromaNetworks#autoAdopt}). Only blocks in {@code #nerodecor:chroma_capable} are ever adopted.
 */
public class ChromaCapableBlockItem extends DecorBlockItem {

    public ChromaCapableBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        InteractionResult result = super.place(context);
        if (result.consumesAction() && context.getLevel() instanceof ServerLevel level
                && context.getPlayer() instanceof ServerPlayer player) {
            ChromaNetworks.autoAdopt(level, context.getClickedPos(), player);
        }
        return result;
    }
}
