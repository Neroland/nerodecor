package za.co.neroland.nerodecor.chroma.server;

import za.co.neroland.nerodecor.config.NeroDecorConfig;
import za.co.neroland.nerolandcore.config.ConfigValue;

/**
 * The Stage 4 (animation) keys of NeroDecor's config schema. {@code chroma.animations} is server-authoritative (the
 * server's kill-switch, synced to clients); the rest are client-local, purely visual choices each player makes.
 * Class-loaded from {@link ChromaConfig#bootstrap()} before the schema is registered.
 */
public final class ChromaAnimationConfig {

    /** Server kill-switch: when false every hub shows its static colours and triggers do nothing visible. */
    public static final ConfigValue<Boolean> ANIMATIONS = NeroDecorConfig.SCHEMA.bool(
            "chroma.animations", true, true,
            "Allow Chroma scenes to animate (effects, timelines, triggers). When false every hub shows its static "
                    + "colours; stored scenes are kept.");

    /** Client: calmer effects (slower, softer, less sparkle). */
    public static final ConfigValue<Boolean> REDUCED_MOTION = NeroDecorConfig.SCHEMA.bool(
            "chroma.reducedMotion", false, false,
            "Reduced motion: play Chroma effects slower and softer (client-side only).");

    /** Client: static colour only. */
    public static final ConfigValue<Boolean> DISABLE_ANIMATION = NeroDecorConfig.SCHEMA.bool(
            "chroma.disableAnimation", false, false,
            "Disable Chroma animation on this client: hubs show their static colours only.");

    /** Client: note blocks and jukeboxes near a hub modulate its animation. */
    public static final ConfigValue<Boolean> MUSIC_REACTIVE = NeroDecorConfig.SCHEMA.bool(
            "chroma.musicReactive", true, false,
            "Let note blocks and jukeboxes near a hub pulse its animated colours (client-side only; never changes "
                    + "light levels).");

    private ChromaAnimationConfig() {
    }

    /** Force class-load so the keys join the schema before it is registered. */
    public static void bootstrap() {
    }
}
