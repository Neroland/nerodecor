package za.co.neroland.nerodecor.content.item;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import za.co.neroland.nerodecor.NeroDecorCommon;
import za.co.neroland.nerodecor.chroma.server.ChromaGuard;
import za.co.neroland.nerodecor.chroma.server.ChromaNetworks;
import za.co.neroland.nerodecor.chroma.server.ChromaVerdict;
import za.co.neroland.nerodecor.content.DecorColor;
import za.co.neroland.nerodecor.content.DecorProperties;
import za.co.neroland.nerodecor.registry.ModDataComponents;

/**
 * The Paint Gun: rewrites a decor block's {@code COLOR} state (its static colour, no hub needed).
 *
 * <ul>
 *   <li>Use on a paintable block: paint it the gun's colour. The edit passes the same protection check as every
 *       Chroma edit ({@link ChromaGuard}: build permission, spawn protection, world border, claim mods).</li>
 *   <li>Sneak-use on a paintable block: copy its colour into the gun.</li>
 *   <li>Use in the air: next colour; sneak-use in the air: previous colour.</li>
 * </ul>
 *
 * The selected colour is the gun's {@code nerodecor:color} component, which also tints its canister layer.
 */
public class PaintGunItem extends Item {

    public PaintGunItem(Properties properties) {
        super(properties);
    }

    /** The gun's selected colour ({@link DecorColor#NATURAL} = strip paint). */
    public static DecorColor colour(ItemStack gun) {
        return gun.getOrDefault(ModDataComponents.COLOR.get(), DecorColor.NATURAL);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        BlockState state = context.getLevel().getBlockState(context.getClickedPos());
        if (!state.hasProperty(DecorProperties.COLOR)) {
            return InteractionResult.PASS;
        }
        if (!(context.getLevel() instanceof ServerLevel level) || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        ItemStack gun = context.getItemInHand();
        if (player.isSecondaryUseActive()) {
            DecorColor picked = state.getValue(DecorProperties.COLOR);
            gun.set(ModDataComponents.COLOR.get(), picked);
            say(player, "picked", picked);
            return InteractionResult.SUCCESS;
        }
        ChromaVerdict verdict = paint(level, player, context.getClickedPos(), colour(gun));
        if (!verdict.isOk()) {
            ChromaNetworks.tell(player, verdict);
            return InteractionResult.FAIL;
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Paints the block at {@code pos} {@code colour} if {@code player} may modify it.
     *
     * @return {@link ChromaVerdict#OK}, {@link ChromaVerdict#NOT_CAPABLE} (no paint property) or
     *         {@link ChromaVerdict#PROTECTED}
     */
    public static ChromaVerdict paint(ServerLevel level, ServerPlayer player, BlockPos pos, DecorColor colour) {
        BlockState state = level.getBlockState(pos);
        if (!state.hasProperty(DecorProperties.COLOR)) {
            return ChromaVerdict.NOT_CAPABLE;
        }
        if (!ChromaGuard.mayEdit(player, level, pos, state)) {
            return ChromaVerdict.PROTECTED;
        }
        if (state.getValue(DecorProperties.COLOR) != colour) {
            level.setBlock(pos, state.setValue(DecorProperties.COLOR, colour), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 0.6F, 1.4F);
        }
        return ChromaVerdict.OK;
    }

    //? if >=1.21.2 {
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        cycle(level, player, player.getItemInHand(hand));
        return InteractionResult.SUCCESS;
    }
    //?} else {
    /*@Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        cycle(level, player, stack);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
    *///?}

    private static void cycle(Level level, Player player, ItemStack gun) {
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        DecorColor[] all = DecorColor.values();
        int step = player.isSecondaryUseActive() ? all.length - 1 : 1;
        DecorColor next = all[(colour(gun).ordinal() + step) % all.length];
        gun.set(ModDataComponents.COLOR.get(), next);
        say(serverPlayer, "selected", next);
    }

    private static void say(ServerPlayer player, String key, DecorColor colour) {
        player.sendSystemMessage(Component.translatable("message." + NeroDecorCommon.MOD_ID + ".paint_gun." + key,
                Component.translatable("color." + NeroDecorCommon.MOD_ID + "." + colour.getSerializedName())), true);
    }
}
