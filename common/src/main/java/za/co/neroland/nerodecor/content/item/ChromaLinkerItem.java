package za.co.neroland.nerodecor.content.item;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
//? if <1.21.2 {
/*import net.minecraft.world.InteractionResultHolder;
*///?}
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import za.co.neroland.nerodecor.NeroDecorCommon;
import za.co.neroland.nerodecor.chroma.server.ChromaAction;
import za.co.neroland.nerodecor.chroma.server.ChromaNetworks;
import za.co.neroland.nerodecor.chroma.server.ChromaValidator;
import za.co.neroland.nerodecor.chroma.server.ChromaVerdict;
import za.co.neroland.nerodecor.chroma.server.HubNetwork;
import za.co.neroland.nerodecor.content.block.ChromaHubBlockEntity;
import za.co.neroland.nerodecor.registry.ChromaContent;

/**
 * The Chroma Linker: chooses which blocks a hub controls.
 *
 * <ul>
 *   <li>Use on a hub: bind to that hub's network (needs the admin role).</li>
 *   <li>Use on a block: link it, or unlink it if it is already in the bound network.</li>
 *   <li>Box mode (use in the air to toggle): first use marks a corner, second links every capable block in the
 *       box, capped.</li>
 *   <li>Sneak-use on a block: flood-select the connected capable blocks, capped.</li>
 * </ul>
 */
public class ChromaLinkerItem extends Item {

    public ChromaLinkerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level) || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        ItemStack stack = context.getItemInHand();
        LinkerState linker = stack.getOrDefault(ChromaContent.LINKER_STATE.get(), LinkerState.EMPTY);
        BlockPos pos = context.getClickedPos();
        if (level.getBlockEntity(pos) instanceof ChromaHubBlockEntity hub) {
            HubNetwork network = ChromaNetworks.store(level).get(hub.networkId());
            ChromaVerdict verdict = ChromaValidator.checkAction(network, player.getUUID(), ChromaAction.LINK);
            if (verdict.isOk()) {
                stack.set(ChromaContent.LINKER_STATE.get(), linker.bound(network.id()));
                say(player, "bound");
            } else {
                ChromaNetworks.tell(player, verdict);
            }
            return InteractionResult.SUCCESS;
        }
        if (linker.network() == 0L) {
            say(player, "unbound");
            return InteractionResult.SUCCESS;
        }
        if (player.isSecondaryUseActive()) {
            ChromaNetworks.tell(player, ChromaNetworks.linkFlood(level, player, linker.network(), pos));
        } else if (linker.boxMode()) {
            if (linker.corner().isEmpty()) {
                stack.set(ChromaContent.LINKER_STATE.get(), linker.withCorner(pos));
                say(player, "corner");
            } else {
                ChromaNetworks.tell(player, ChromaNetworks.linkBox(level, player, linker.network(),
                        linker.corner().get(), pos));
                stack.set(ChromaContent.LINKER_STATE.get(), linker.clearedCorner());
            }
        } else {
            ChromaNetworks.tell(player, ChromaNetworks.toggle(level, player, linker.network(), pos));
        }
        return InteractionResult.SUCCESS;
    }

    //? if >=1.21.2 {
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        toggleMode(level, player, player.getItemInHand(hand));
        return InteractionResult.SUCCESS;
    }
    //?} else {
    /*@Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        toggleMode(level, player, stack);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
    *///?}

    private static void toggleMode(Level level, Player player, ItemStack stack) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            LinkerState next = stack.getOrDefault(ChromaContent.LINKER_STATE.get(), LinkerState.EMPTY).toggledMode();
            stack.set(ChromaContent.LINKER_STATE.get(), next);
            say(serverPlayer, next.boxMode() ? "mode_box" : "mode_single");
        }
    }

    private static void say(ServerPlayer player, String key) {
        player.sendSystemMessage(Component.translatable("message." + NeroDecorCommon.MOD_ID + ".chroma.linker." + key),
                true);
    }
}
