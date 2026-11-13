package za.co.neroland.nerodecor.content.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

import za.co.neroland.nerodecor.chroma.server.ChromaNetworks;
import za.co.neroland.nerodecor.chroma.server.ChromaVerdict;
import za.co.neroland.nerodecor.registry.ChromaContent;

/**
 * The Chroma Hub's item. A hub item that carries a network ({@code nerodecor:chroma_network}) re-links it when
 * placed; a blank one starts a new network, refused up front (nothing placed) when the player is at the hub cap.
 */
public class ChromaHubItem extends BlockItem {

    public ChromaHubItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        if (!context.getLevel().isClientSide() && context.getPlayer() instanceof ServerPlayer player
                && context.getItemInHand().get(ChromaContent.NETWORK_ID.get()) == null) {
            ChromaVerdict verdict = ChromaNetworks.canStartNetwork(player);
            if (verdict == ChromaVerdict.HUB_CAP) {
                ChromaNetworks.tell(player, verdict);
                return InteractionResult.FAIL;
            }
        }
        return super.place(context);
    }
}
