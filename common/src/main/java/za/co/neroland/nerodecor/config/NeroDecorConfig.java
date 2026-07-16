package za.co.neroland.nerodecor.config;

import za.co.neroland.nerodecor.NeroDecorCommon;
import za.co.neroland.nerolandcore.config.ConfigManager;
import za.co.neroland.nerolandcore.config.ConfigSchema;
import za.co.neroland.nerolandcore.config.ConfigValue;

/**
 * NeroDecor's config schema, registered through Core's {@link ConfigManager} (same file,
 * reload command and server→client sync as every Nero mod). Stage D ships the render
 * kill-switches the CTM/emissive foundations need; later stages (F/G/H/K) add their keys
 * to this same schema.
 *
 * <p>Render toggles are <b>client-local</b> (not server-authoritative): connected
 * textures and emissive rendering are purely visual, so each player may disable them for
 * a resource-pack conflict or a low-end machine without the server dictating it.
 */
public final class NeroDecorConfig {

    public static final ConfigSchema SCHEMA =
            ConfigSchema.create(NeroDecorCommon.MOD_ID, "NeroDecor configuration.");

    // --- Render foundations (Stage D; client-local) -------------------------

    /** The CTM kill-switch — when false, blocks fall back to flat per-block textures. */
    public static final ConfigValue<Boolean> CONNECTED_TEXTURES = SCHEMA.bool(
            "connectedTextures", true, false,
            "Draw NeroDecor hull/panel/glass/neon surfaces with in-house connected textures. "
                    + "Disable if a resource pack conflicts or for maximum performance (falls back to flat tiles).");

    /** Emissive/fullbright rendering for neon, holograms and rack glow. */
    public static final ConfigValue<Boolean> EMISSIVE_RENDERING = SCHEMA.bool(
            "emissiveRendering", true, false,
            "Render neon/holo/rack glow layers fullbright (emissive). Disable on low-end clients.");

    // --- Telemetry (opt-out, no personal data) ------------------------------

    /** Anonymous NeroDecor-only crash reporting (Sentry, EU). Opt out with false. */
    public static final ConfigValue<Boolean> TELEMETRY_ENABLED = SCHEMA.bool(
            "telemetryEnabled", true, false,
            "Send anonymous, NeroDecor-only crash reports (Sentry, EU servers): stack trace, "
                    + "mod/MC/loader/OS/Java versions, your other installed mods, and this mod's config; no IP, "
                    + "username, UUID, world data or chat; file paths are scrubbed of your account name. "
                    + "Set false to opt out (takes effect on restart).");

    private NeroDecorConfig() {
    }

    /** Register the schema with Core. Called once from common init. */
    public static void init() {
        ConfigManager.register(SCHEMA);
    }

    /** Whether connected textures are currently enabled (the render kill-switch). */
    public static boolean connectedTexturesEnabled() {
        return CONNECTED_TEXTURES.get();
    }

    /** Whether emissive rendering is currently enabled. */
    public static boolean emissiveRenderingEnabled() {
        return EMISSIVE_RENDERING.get();
    }

    /** Whether anonymous crash telemetry is enabled (opt-out, default on). */
    public static boolean isTelemetryEnabled() {
        return TELEMETRY_ENABLED.get();
    }
}
