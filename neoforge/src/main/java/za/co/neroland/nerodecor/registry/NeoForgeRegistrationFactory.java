package za.co.neroland.nerodecor.registry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * NeoForge {@link RegistrationProvider.Factory}: each provider wraps a {@link DeferredRegister}.
 * Registers are collected during {@code NeroDecorCommon.init()} and attached to the mod event
 * bus by the loader entry point via {@link #registerAll(IEventBus)}.
 */
public final class NeoForgeRegistrationFactory implements RegistrationProvider.Factory {

    private static final List<DeferredRegister<?>> REGISTERS = new ArrayList<>();

    /** Attach every DeferredRegister created so far to the mod event bus. */
    public static void registerAll(IEventBus modEventBus) {
        REGISTERS.forEach(register -> register.register(modEventBus));
    }

    @Override
    public <T> RegistrationProvider<T> create(ResourceKey<? extends Registry<T>> registryKey, String modId) {
        DeferredRegister<T> register = DeferredRegister.create(registryKey, modId);
        REGISTERS.add(register);
        return new Provider<>(register, registryKey, modId);
    }

    private static final class Provider<T> implements RegistrationProvider<T> {

        private final DeferredRegister<T> register;
        private final ResourceKey<? extends Registry<T>> registryKey;
        private final String modId;

        Provider(DeferredRegister<T> register, ResourceKey<? extends Registry<T>> registryKey, String modId) {
            this.register = register;
            this.registryKey = registryKey;
            this.modId = modId;
        }

        @Override
        public <I extends T> RegistryEntry<I> register(String name, Function<ResourceKey<T>, I> factory) {
            Identifier id = Identifier.fromNamespaceAndPath(modId, name);
            ResourceKey<T> key = ResourceKey.create(registryKey, id);
            Supplier<I> supplier = () -> factory.apply(key);
            DeferredHolder<T, I> holder = register.register(name, supplier);
            return new RegistryEntry<>() {
                @Override
                public I get() {
                    return holder.get();
                }

                @Override
                public Identifier id() {
                    return id;
                }
            };
        }
    }
}
