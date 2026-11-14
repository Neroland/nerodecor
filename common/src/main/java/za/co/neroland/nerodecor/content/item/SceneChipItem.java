package za.co.neroland.nerodecor.content.item;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import za.co.neroland.nerodecor.NeroDecorCommon;
import za.co.neroland.nerodecor.chroma.ChromaResult;
import za.co.neroland.nerodecor.chroma.Scene;
import za.co.neroland.nerodecor.chroma.SceneAnimator;
import za.co.neroland.nerodecor.chroma.SceneChipCodec;
import za.co.neroland.nerodecor.chroma.SceneSlots;
import za.co.neroland.nerodecor.chroma.server.ChromaAction;
import za.co.neroland.nerodecor.chroma.server.ChromaNetworkStore;
import za.co.neroland.nerodecor.chroma.server.ChromaNetworks;
import za.co.neroland.nerodecor.chroma.server.ChromaScenes;
import za.co.neroland.nerodecor.chroma.server.ChromaValidator;
import za.co.neroland.nerodecor.chroma.server.ChromaVerdict;
import za.co.neroland.nerodecor.chroma.server.HubNetwork;
import za.co.neroland.nerodecor.content.block.ChromaHubBlockEntity;
import za.co.neroland.nerodecor.registry.ChromaContent;

/**
 * The Scene Chip: carries one Chroma scene ({@link SceneChipData}: the Stage 1 codec bytes, no author or player id).
 *
 * <ul>
 *   <li>Use on a hub: loads the scene into the hub's first empty slot (or the playing slot when all are full) and
 *       plays it. Painter role or higher; the scene is validated and must pass the flash clamp.</li>
 *   <li>Sneak-use on a hub: copies the hub's playing scene onto the chip (viewer role or higher).</li>
 * </ul>
 * The stripe layer shows the scene's dominant colour ({@code minecraft:dyed_color}); the tooltip shows the scene's
 * name and zone count. Share-code helpers ({@link #shareCode}, {@link #importShareCode}) are for the hub editor.
 */
public class SceneChipItem extends Item {

    /** Outcome of loading a chip into a hub: the verdict and the slot it went into (or -1). */
    public record Loaded(ChromaVerdict verdict, int slot) {
    }

    public SceneChipItem(Properties properties) {
        super(properties);
    }

    // --- stack helpers ----------------------------------------------------------------------------------------------

    /** The chip's scene, if it holds a valid one (tampered bytes fail the CRC and read as empty). */
    public static Optional<Scene> scene(ItemStack stack) {
        SceneChipData data = stack.get(ChromaContent.SCENE_CHIP.get());
        if (data == null) {
            return Optional.empty();
        }
        ChromaResult<Scene> scene = SceneChipCodec.decode(data.data());
        return scene.isOk() ? Optional.of(scene.value()) : Optional.empty();
    }

    /** Writes {@code scene} onto the chip and sets the stripe to its dominant colour. */
    public static boolean write(ItemStack stack, Scene scene) {
        ChromaResult<byte[]> bytes = SceneChipCodec.encode(scene);
        if (!bytes.isOk()) {
            return false;
        }
        stack.set(ChromaContent.SCENE_CHIP.get(), new SceneChipData(bytes.value()));
        int rgb = SceneAnimator.dominantRgb(scene);
        //? if >=1.21.5 {
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb));
        //?} else {
        /*stack.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb, false));
        *///?}
        return true;
    }

    /** The chip's scene as a share code ({@code ND1-...}), for copying from the editor. */
    public static Optional<String> shareCode(ItemStack stack) {
        SceneChipData data = stack.get(ChromaContent.SCENE_CHIP.get());
        if (data == null) {
            return Optional.empty();
        }
        ChromaResult<String> code = SceneChipCodec.toShareCode(data.data());
        return code.isOk() ? Optional.of(code.value()) : Optional.empty();
    }

    /** Imports a share code onto the chip (validated). */
    public static boolean importShareCode(ItemStack stack, String code) {
        ChromaResult<byte[]> bytes = SceneChipCodec.fromShareCode(code);
        if (!bytes.isOk()) {
            return false;
        }
        ChromaResult<Scene> scene = SceneChipCodec.decode(bytes.value());
        return scene.isOk() && write(stack, scene.value());
    }

    // --- hub interaction --------------------------------------------------------------------------------------------

    /** Loads the chip's scene into the hub at {@code hubPos} for {@code player} (role, validation, flash clamp). */
    public static Loaded load(ServerLevel level, ServerPlayer player, BlockPos hubPos, ItemStack stack) {
        if (!(level.getBlockEntity(hubPos) instanceof ChromaHubBlockEntity hub)) {
            return new Loaded(ChromaVerdict.NO_NETWORK, SceneSlots.STATIC);
        }
        Optional<Scene> scene = scene(stack);
        if (scene.isEmpty()) {
            return new Loaded(ChromaVerdict.BAD_SCENE, SceneSlots.STATIC);
        }
        ChromaNetworkStore store = ChromaNetworks.store(level);
        HubNetwork network = store.get(hub.networkId());
        if (network == null) {
            return new Loaded(ChromaVerdict.NO_NETWORK, SceneSlots.STATIC);
        }
        SceneSlots slots = network.scenes();
        int slot = slots.firstEmpty();
        if (slot == SceneSlots.STATIC) {
            slot = slots.active() == SceneSlots.STATIC ? 0 : slots.active();
        }
        ChromaVerdict verdict = ChromaScenes.save(level, player, network.id(), slot, scene.get(), true);
        return new Loaded(verdict, verdict.isOk() ? slot : SceneSlots.STATIC);
    }

    /** Copies the hub's playing scene onto the chip (viewer role or higher). */
    public static ChromaVerdict copy(ServerLevel level, ServerPlayer player, BlockPos hubPos, ItemStack stack) {
        if (!(level.getBlockEntity(hubPos) instanceof ChromaHubBlockEntity hub)) {
            return ChromaVerdict.NO_NETWORK;
        }
        HubNetwork network = ChromaNetworks.store(level).get(hub.networkId());
        ChromaVerdict role = ChromaValidator.checkAction(network, player.getUUID(), ChromaAction.VIEW);
        if (!role.isOk()) {
            return role;
        }
        Scene scene = network.scenes().activeScene();
        if (scene == null) {
            return ChromaVerdict.BAD_SLOT;
        }
        return write(stack, scene) ? ChromaVerdict.OK : ChromaVerdict.BAD_SCENE;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!(level.getBlockEntity(pos) instanceof ChromaHubBlockEntity)) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server && context.getPlayer() instanceof ServerPlayer player) {
            ItemStack stack = context.getItemInHand();
            String prefix = "message." + NeroDecorCommon.MOD_ID + ".scene_chip.";
            if (player.isShiftKeyDown()) {
                ChromaVerdict verdict = copy(server, player, pos, stack);
                if (verdict.isOk()) {
                    player.sendSystemMessage(Component.translatable(prefix + "copied"), true);
                } else if (verdict == ChromaVerdict.BAD_SLOT) {
                    player.sendSystemMessage(Component.translatable(prefix + "nothing_playing"), true);
                } else {
                    ChromaNetworks.tell(player, verdict);
                }
            } else {
                Loaded loaded = load(server, player, pos, stack);
                if (loaded.verdict().isOk()) {
                    player.sendSystemMessage(Component.translatable(prefix + "loaded", loaded.slot() + 1), true);
                } else if (loaded.verdict() == ChromaVerdict.BAD_SCENE && scene(stack).isEmpty()) {
                    player.sendSystemMessage(Component.translatable(prefix + "empty"), true);
                } else {
                    ChromaNetworks.tell(player, loaded.verdict());
                }
            }
        }
        return InteractionResult.SUCCESS;
    }

    //? if >=1.21.5 {
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
            net.minecraft.world.item.component.TooltipDisplay display,
            java.util.function.Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(tooltipLine(stack));
    }
    //?} else {
    /*@Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, java.util.List<Component> tooltip,
            TooltipFlag flag) {
        tooltip.add(tooltipLine(stack));
    }
    *///?}

    private static Component tooltipLine(ItemStack stack) {
        String prefix = "tooltip." + NeroDecorCommon.MOD_ID + ".scene_chip.";
        return scene(stack).<Component>map(s -> Component.translatable(prefix + "scene",
                s.name().isEmpty() ? Component.translatable(prefix + "unnamed") : Component.literal(s.name()),
                s.zones().size())).orElseGet(() -> Component.translatable(prefix + "blank"));
    }
}
