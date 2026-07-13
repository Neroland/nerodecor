package za.co.neroland.nerodecor.registry;

import java.util.function.Function;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/** Fabric {@link RegistrationProvider.Factory}: registers eagerly via {@code Registry.register}. */
public final class FabricRegistrationFactory implements RegistrationProvider.Factory {

    @Override
    @SuppressWarnings("unchecked")
    public <T> RegistrationProvider<T> create(ResourceKey<? extends Registry<T>> registryKey, String modId) {
        Registry<T> registry = (Registry<T>) BuiltInRegistries.REGISTRY.getValue(registryKey.identifier());
        return new Provider<>(registry, registryKey, modId);
    }

    private static final class Provider<T> implements RegistrationProvider<T> {

        private final Registry<T> registry;
        private final ResourceKey<? extends Registry<T>> registryKey;
        private final String modId;

        Provider(Registry<T> registry, ResourceKey<? extends Registry<T>> registryKey, String modId) {
            this.registry = registry;
            this.registryKey = registryKey;
            this.modId = modId;
        }

        @Override
        public <I extends T> RegistryEntry<I> register(String name, Function<ResourceKey<T>, I> factory) {
            Identifier id = Identifier.fromNamespaceAndPath(modId, name);
            ResourceKey<T> key = ResourceKey.create(registryKey, id);
            I value = factory.apply(key);
            Registry.register(registry, key, value);
            return new RegistryEntry<>() {
                @Override
                public I get() {
                    return value;
                }

                @Override
                public Identifier id() {
                    return id;
                }
            };
        }
    }
}
