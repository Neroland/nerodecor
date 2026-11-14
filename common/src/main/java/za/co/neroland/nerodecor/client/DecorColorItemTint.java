package za.co.neroland.nerodecor.client;

//? if >=1.21.4 {
import com.mojang.serialization.MapCodec;

import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerodecor.NeroDecorCommon;
//?} else {
/*import net.minecraft.client.color.item.ItemColor;
*///?}
import net.minecraft.world.item.ItemStack;

import za.co.neroland.nerodecor.content.DecorColor;
import za.co.neroland.nerodecor.registry.ModDataComponents;

/**
 * The item-side counterpart of {@link ChromaTintBridge}: tints a paintable block item from the
 * stack's {@code nerodecor:color} data component. A stack without the component, or with
 * {@link DecorColor#NATURAL}, renders opaque white (the base texture unchanged).
 *
 * <p>1.21.4+ is a data-driven {@link ItemTintSource} of type {@code nerodecor:decor_color}, referenced
 * from the paintable blocks' item definitions ({@code assets/nerodecor/items/*.json}) and registered
 * per loader (NeoForge {@code RegisterColorHandlersEvent.ItemTintSources}; Fabric and Forge put
 * {@link #ID}/{@link #MAP_CODEC} straight into the public {@code ItemTintSources.ID_MAPPER}).
 * 1.21.1 has no item definitions, so there it is an {@code ItemColor} registered against the
 * paintable block items. Client-only: referenced from the loader client entry points only.
 */
// Before 1.21.4 item tints are an ItemColor (stack + tint index) registered per item.
//? if >=1.21.4 {
public final class DecorColorItemTint implements ItemTintSource {
//?} else {
/*public final class DecorColorItemTint implements ItemColor {
*///?}

    public static final DecorColorItemTint INSTANCE = new DecorColorItemTint();

    //? if >=1.21.4 {
    /** The tint source type id the item definitions use: {@code nerodecor:decor_color}. */
    public static final Identifier ID = Identifier.fromNamespaceAndPath(NeroDecorCommon.MOD_ID, "decor_color");

    /** Field-less codec: the colour comes from the stack, so the JSON entry carries only its type. */
    public static final MapCodec<DecorColorItemTint> MAP_CODEC = MapCodec.unit(INSTANCE);
    //?}

    private DecorColorItemTint() {
    }

    /** The opaque ARGB tint for a stack: its painted colour, or white when unpainted. */
    public static int argb(ItemStack stack) {
        DecorColor colour = stack.get(ModDataComponents.COLOR.get());
        int rgb = colour == null ? DecorColor.NATURAL.tint() : colour.tint();
        return 0xFF000000 | rgb;
    }

    //? if >=1.21.4 {
    @Override
    public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity) {
        return argb(stack);
    }

    @Override
    public MapCodec<DecorColorItemTint> type() {
        return MAP_CODEC;
    }
    //?} else {
    /*@Override
    public int getColor(ItemStack stack, int tintIndex) {
        // Scene Chip (Chroma Stage 4): the stripe (layer 1) shows minecraft:dyed_color, default house plasma.
        if (stack.getItem() instanceof za.co.neroland.nerodecor.content.item.SceneChipItem) {
            net.minecraft.world.item.component.DyedItemColor dyed =
                    stack.get(net.minecraft.core.component.DataComponents.DYED_COLOR);
            return tintIndex == 1 ? 0xFF000000 | (dyed == null ? 0x3FD0E0 : dyed.rgb()) : 0xFFFFFFFF;
        }
        // The paint gun tints its canister (layer 1); paintable block items tint layer 0.
        int layer = stack.getItem() instanceof za.co.neroland.nerodecor.content.item.PaintGunItem ? 1 : 0;
        return tintIndex == layer ? argb(stack) : 0xFFFFFFFF;
    }

    // 1.21.1: the items this ItemColor is registered for (paintable block items and the paint gun).
    public static net.minecraft.world.level.ItemLike[] legacyItems() {
        java.util.List<net.minecraft.world.level.ItemLike> items = new java.util.ArrayList<>(
                java.util.List.of(za.co.neroland.nerodecor.registry.DecorBlocks.paintableBlocks()));
        items.add(za.co.neroland.nerodecor.registry.ChromaContent.PAINT_GUN.get());
        items.add(za.co.neroland.nerodecor.registry.ChromaContent.SCENE_CHIP_ITEM.get());
        return items.toArray(net.minecraft.world.level.ItemLike[]::new);
    }
    *///?}
}
