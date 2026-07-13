package za.co.neroland.nerodecor.platform;

/**
 * The loader-specific behaviour common code may depend on. Each loader module ships exactly
 * one implementation, registered via {@code META-INF/services} so {@link Services} can load
 * it with {@link java.util.ServiceLoader}. Kept small — grow only as stages need it (Stage H
 * uses {@link #isModLoaded(String)} for conditional recipes; Stage K reads the mod version
 * for telemetry).
 */
public interface IPlatformHelper {

    /** Human-readable platform name ("Fabric" / "NeoForge" / "Forge"). */
    String getPlatformName();

    /** True when running in a development (dev/data/test) environment. */
    boolean isDevelopmentEnvironment();

    /** True when the named mod is loaded. */
    boolean isModLoaded(String modId);

    /** True on the physical client (renderers, screens available). */
    boolean isClient();

    /** This mod's version string, or "unknown" if unavailable. */
    String getModVersion();
}
