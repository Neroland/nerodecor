package za.co.neroland.nerodecor.content.block;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import za.co.neroland.nerodecor.NeroDecorCommon;
import za.co.neroland.nerodecor.chroma.server.ChromaAction;
import za.co.neroland.nerodecor.chroma.server.ChromaNetworkStore;
import za.co.neroland.nerodecor.chroma.server.ChromaNetworks;
import za.co.neroland.nerodecor.chroma.server.ChromaValidator;
import za.co.neroland.nerodecor.chroma.server.ChromaVerdict;
import za.co.neroland.nerodecor.chroma.server.HubNetwork;
import za.co.neroland.nerodecor.registry.ChromaContent;

/**
 * The Chroma Hub: owns one network ({@link za.co.neroland.nerodecor.chroma.server.HubNetwork}) that controls the
 * colour of chroma-capable blocks in its volume. The block entity stores only the network id; the network lives in
 * the dimension's saved data. Breaking the hub drops a hub item that carries the id (loot {@code copy_components}),
 * and the network stops applying colour until that item is placed again.
 *
 * <p>Use (empty hand): show the network's size. Sneak-use: toggle auto-adopt (admins).
 */
public class ChromaHubBlock extends Block implements EntityBlock {

    public ChromaHubBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ChromaHubBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        super.setPlacedBy(level, pos, state, by, stack);
        if (level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof ChromaHubBlockEntity hub) {
            Long carried = stack.get(ChromaContent.NETWORK_ID.get());
            long id = ChromaNetworks.hubPlaced(server, pos, by instanceof ServerPlayer player ? player : null,
                    carried == null ? 0L : carried);
            hub.setNetworkId(id);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (level instanceof ServerLevel server && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof ChromaHubBlockEntity hub) {
            ChromaNetworkStore store = ChromaNetworks.store(server);
            HubNetwork network = store.get(hub.networkId());
            if (network == null) {
                ChromaNetworks.tell(serverPlayer, ChromaVerdict.NO_NETWORK);
            } else if (player.isShiftKeyDown()) {
                ChromaVerdict verdict = store.setAutoAdopt(network.id(), player.getUUID(), !network.autoAdopt());
                if (verdict.isOk()) {
                    serverPlayer.sendSystemMessage(Component.translatable("message." + NeroDecorCommon.MOD_ID
                            + ".chroma.auto_adopt." + (network.autoAdopt() ? "on" : "off")), true);
                } else {
                    ChromaNetworks.tell(serverPlayer, verdict);
                }
            } else if (ChromaValidator.checkAction(network, player.getUUID(), ChromaAction.VIEW).isOk()) {
                serverPlayer.sendSystemMessage(Component.translatable("message." + NeroDecorCommon.MOD_ID
                        + ".chroma.status", network.size(), network.zones().size()), true);
            } else {
                ChromaNetworks.tell(serverPlayer, ChromaVerdict.NO_PERMISSION);
            }
        }
        return InteractionResult.SUCCESS;
    }

    //? if <1.21.5 {
    /*@Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server
                && level.getBlockEntity(pos) instanceof ChromaHubBlockEntity hub) {
            ChromaNetworks.hubRemoved(server, pos, hub.networkId());
        }
        super.onRemove(state, level, pos, newState, moved);
    }
    *///?}
}
