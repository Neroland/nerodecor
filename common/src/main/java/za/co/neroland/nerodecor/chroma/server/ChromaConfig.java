package za.co.neroland.nerodecor.chroma.server;

import za.co.neroland.nerodecor.config.NeroDecorConfig;
import za.co.neroland.nerolandcore.config.ConfigValue;

/**
 * The {@code chroma.*} keys of NeroDecor's Core config schema. All server-authoritative: the hub network is server
 * state, so its caps are the server's call. Class-loaded by {@link NeroDecorConfig#init()} before the schema is
 * registered with Core.
 */
public final class ChromaConfig {

    public static final ConfigValue<Boolean> ENABLED = NeroDecorConfig.SCHEMA.bool(
            "chroma.enabled", true, true,
            "Enable Chroma hub networks. When false, hubs and linkers do nothing and every block shows its own "
                    + "painted colour; stored networks are kept.");

    public static final ConfigValue<Integer> VOLUME_X = NeroDecorConfig.SCHEMA.intRange(
            "chroma.hubVolumeX", ChromaLimits.DEFAULTS.volumeX(), 1, ChromaLimits.HARD_VOLUME_CAP, true,
            "Width (X) of a new hub's volume, centred on the hub. Hard cap 64.");
    public static final ConfigValue<Integer> VOLUME_Y = NeroDecorConfig.SCHEMA.intRange(
            "chroma.hubVolumeY", ChromaLimits.DEFAULTS.volumeY(), 1, ChromaLimits.HARD_VOLUME_CAP, true,
            "Height (Y) of a new hub's volume, centred on the hub. Hard cap 64.");
    public static final ConfigValue<Integer> VOLUME_Z = NeroDecorConfig.SCHEMA.intRange(
            "chroma.hubVolumeZ", ChromaLimits.DEFAULTS.volumeZ(), 1, ChromaLimits.HARD_VOLUME_CAP, true,
            "Depth (Z) of a new hub's volume, centred on the hub. Hard cap 64.");

    public static final ConfigValue<Integer> MAX_BLOCKS_PER_HUB = NeroDecorConfig.SCHEMA.intRange(
            "chroma.maxBlocksPerHub", ChromaLimits.DEFAULTS.maxBlocksPerHub(), 1, ChromaLimits.HARD_BLOCK_CAP, true,
            "Most blocks one hub may control.");
    public static final ConfigValue<Integer> MAX_HUBS_PER_PLAYER = NeroDecorConfig.SCHEMA.intRange(
            "chroma.maxHubsPerPlayer", ChromaLimits.DEFAULTS.maxHubsPerPlayer(), 0, 64, true,
            "Most hub networks one player may own (placed hubs and hub items that carry a network).");
    public static final ConfigValue<Integer> MAX_ZONES_PER_HUB = NeroDecorConfig.SCHEMA.intRange(
            "chroma.maxZonesPerHub", ChromaLimits.DEFAULTS.maxZones(), 1, ChromaLimits.HARD_ZONE_CAP, true,
            "Most named zones per hub.");
    public static final ConfigValue<Integer> MAX_SELECTION = NeroDecorConfig.SCHEMA.intRange(
            "chroma.maxSelection", ChromaLimits.DEFAULTS.maxSelection(), 1, ChromaLimits.HARD_BLOCK_CAP, true,
            "Most blocks one Chroma Linker box or flood selection may link.");

    public static final ConfigValue<Integer> EDITS_PER_SECOND = NeroDecorConfig.SCHEMA.intRange(
            "chroma.editsPerSecond", ChromaRateLimiter.DEFAULT_EDITS_PER_SECOND, 1, 100, true,
            "Chroma edit requests one player may send per second.");
    public static final ConfigValue<Integer> BLOCK_OPS_PER_MINUTE = NeroDecorConfig.SCHEMA.intRange(
            "chroma.blockOpsPerMinute", ChromaRateLimiter.DEFAULT_BLOCK_OPS_PER_MINUTE, 100, 200_000, true,
            "Chroma block operations (link, unlink, recolour) one player may perform per minute.");

    public static final ConfigValue<Integer> INACTIVE_RETENTION_DAYS = NeroDecorConfig.SCHEMA.intRange(
            "chroma.inactiveRetentionDays", 90, 1, 3650, true,
            "Days a network whose hub has been broken is kept for re-placement before it is deleted "
                    + "(data minimisation; the hub item then starts a fresh network).");

    public static final ConfigValue<Integer> ORPHAN_RETENTION_DAYS = NeroDecorConfig.SCHEMA.intRange(
            "chroma.orphanRetentionDays", 30, 1, 3650, true,
            "Days a network whose owner's data was erased (orphaned: colours kept, nobody may edit) is kept for an "
                    + "operator to claim before it is deleted. Separate from chroma.inactiveRetentionDays.");

    private ChromaConfig() {
    }

    /** Force class-load so the keys join the schema before it is registered. */
    public static void bootstrap() {
        // Stage 4 animation keys (chroma.animations, client options) live in their own class.
        ChromaAnimationConfig.bootstrap();
    }
}
