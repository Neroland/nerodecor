package za.co.neroland.nerodecor.platform;

import java.util.ServiceLoader;

import za.co.neroland.nerodecor.NeroDecorCommon;

/**
 * Loads loader-specific service implementations via {@link ServiceLoader} — the
 * dependency-free MultiLoader-Template alternative to Architectury. Common code calls
 * {@code Services.PLATFORM.xxx()}; the correct Fabric / NeoForge / Forge implementation is
 * resolved at runtime from each loader module's {@code META-INF/services} entry.
 */
public final class Services {

    public static final IPlatformHelper PLATFORM = load(IPlatformHelper.class);

    private Services() {
    }

    public static <T> T load(Class<T> clazz) {
        final T loaded = ServiceLoader.load(clazz)
                .findFirst()
                .orElseThrow(() -> new NullPointerException(
                        "No implementation found for service " + clazz.getName()));
        NeroDecorCommon.LOGGER.debug("Loaded service {} -> {}", clazz.getSimpleName(), loaded.getClass().getName());
        return loaded;
    }
}
