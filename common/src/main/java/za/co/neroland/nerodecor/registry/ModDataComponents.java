package za.co.neroland.nerodecor.registry;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;

import za.co.neroland.nerodecor.NeroDecorCommon;
import za.co.neroland.nerodecor.content.DecorColor;
import za.co.neroland.nerodecor.registry.RegistrationProvider.RegistryEntry;

/**
 * NeroDecor item data components. {@link #COLOR} carries a painted {@link DecorColor} on the
 * item form; the {@code DecorBlockItem} bridge copies it onto the placed block's
 * {@code COLOR} state (and back, for pick-block / the paint gun). This is how pre-dyed
 * craftable variants exist as JEI-visible stacks without new registry entries (ADR-001).
 */
public final class ModDataComponents {

    public static final RegistrationProvider<DataComponentType<?>> COMPONENTS =
            RegistrationProvider.get(Registries.DATA_COMPONENT_TYPE, NeroDecorCommon.MOD_ID);

    public static final RegistryEntry<DataComponentType<DecorColor>> COLOR =
            COMPONENTS.register("color", key -> DataComponentType.<DecorColor>builder()
                    .persistent(DecorColor.CODEC)
                    .networkSynchronized(DecorColor.STREAM_CODEC)
                    .build());

    private ModDataComponents() {
    }

    /** Force class-load so the component registers. */
    public static void init() {
    }
}
