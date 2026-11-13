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
        return tintIndex == 0 ? argb(stack) : 0xFFFFFFFF;
    }
    *///?}
}
