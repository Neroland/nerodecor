package za.co.neroland.nerodecor.registry;

import com.mojang.serialization.Codec;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import za.co.neroland.nerodecor.content.DecorColor;

//? if >=1.20.5 {
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import za.co.neroland.nerodecor.NeroDecorCommon;
//?} else {
/*import net.minecraft.nbt.NbtOps;
*///?}

/**
 * NeroDecor item data components. {@link #COLOR} carries a painted {@link DecorColor} on the
 * item form; the {@code DecorBlockItem} bridge copies it onto the placed block's
 * {@code COLOR} state (and back, for pick-block / the paint gun). This is how pre-dyed
 * craftable variants exist as JEI-visible stacks without new registry entries (keeps the registry small).
 */
public final class ModDataComponents {

    //? if >=1.20.5 {
    public static final RegistrationProvider<DataComponentType<?>> COMPONENTS =
            RegistrationProvider.get(Registries.DATA_COMPONENT_TYPE, NeroDecorCommon.MOD_ID);

    public static final StackData<DecorColor> COLOR =
            new StackData<>(COMPONENTS.register("color", key -> DataComponentType.<DecorColor>builder()
                    .persistent(DecorColor.CODEC)
                    .networkSynchronized(DecorColor.STREAM_CODEC)
                    .build()));

    //?} else {
    /*public static final StackData<DecorColor> COLOR = new StackData<>("nerodecor:color", DecorColor.CODEC);
    *///?}

    //? if >=1.20.5 {
    public record StackData<T>(RegistrationProvider.RegistryEntry<DataComponentType<T>> entry) {
        public @Nullable T get(ItemStack stack) {
            return stack.get(entry.get());
        }

        public void set(ItemStack stack, T value) {
            stack.set(entry.get(), value);
        }

        public void remove(ItemStack stack) {
            stack.remove(entry.get());
        }
    //?} else {
    /*public record StackData<T>(String key, Codec<T> codec) {
        public @Nullable T get(ItemStack stack) {
            var tag = stack.getTag();
            if (tag == null || !tag.contains(key)) {
                return null;
            }
            return codec.parse(NbtOps.INSTANCE, tag.get(key)).result().orElse(null);
        }

        public void set(ItemStack stack, T value) {
            var encoded = codec.encodeStart(NbtOps.INSTANCE, value).result()
                    .orElseThrow(() -> new IllegalArgumentException("Cannot encode NeroDecor item data: " + key));
            stack.getOrCreateTag().put(key, encoded);
        }

        public void remove(ItemStack stack) {
            stack.removeTagKey(key);
        }
    *///?}

        public T getOrDefault(ItemStack stack, T fallback) {
            T current = get(stack);
            return current == null ? fallback : current;
        }
    }

    private ModDataComponents() {
    }

    /** Force class-load so the component registers. */
    public static void init() {
    }
}
