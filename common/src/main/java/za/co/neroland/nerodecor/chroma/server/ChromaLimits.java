package za.co.neroland.nerodecor.chroma.server;

/**
 * The server's Chroma caps, as one immutable value so the pure validator can be tested without a config file.
 * {@link #fromConfig()} reads the live values; {@link #DEFAULTS} is the shipped configuration.
 *
 * @param enabled           master switch ({@code chroma.enabled})
 * @param volumeX           default hub volume width (X), blocks
 * @param volumeY           default hub volume height (Y), blocks
 * @param volumeZ           default hub volume depth (Z), blocks
 * @param maxBlocksPerHub   linked blocks per hub
 * @param maxHubsPerPlayer  networks one player may own (placed or carried as a hub item)
 * @param maxZones          zones per hub
 * @param maxSelection      blocks one box or flood selection may visit
 */
public record ChromaLimits(boolean enabled, int volumeX, int volumeY, int volumeZ, int maxBlocksPerHub,
        int maxHubsPerPlayer, int maxZones, int maxSelection) {

    /** No hub volume axis may exceed this, whatever the config says. */
    public static final int HARD_VOLUME_CAP = 64;
    /** Most trusted players one hub keeps (the hub screen shows all of them). */
    public static final int MAX_TRUSTED = 64;
    /** Hard ceiling for {@link #maxBlocksPerHub}. */
    public static final int HARD_BLOCK_CAP = 4096;
    /** Hard ceiling for {@link #maxZones} (matches {@code ZoneAssignment.MAX_ZONES}). */
    public static final int HARD_ZONE_CAP = 32;

    public static final ChromaLimits DEFAULTS = new ChromaLimits(true, 48, 32, 48, 4096, 8, 32, 4096);

    public ChromaLimits {
        volumeX = clamp(volumeX, 1, HARD_VOLUME_CAP);
        volumeY = clamp(volumeY, 1, HARD_VOLUME_CAP);
        volumeZ = clamp(volumeZ, 1, HARD_VOLUME_CAP);
        maxBlocksPerHub = clamp(maxBlocksPerHub, 1, HARD_BLOCK_CAP);
        maxHubsPerPlayer = clamp(maxHubsPerPlayer, 0, 64);
        maxZones = clamp(maxZones, 1, HARD_ZONE_CAP);
        maxSelection = clamp(maxSelection, 1, HARD_BLOCK_CAP);
    }

    /** A copy with a different per-hub block cap (tests). */
    public ChromaLimits withMaxBlocks(int blocks) {
        return new ChromaLimits(enabled, volumeX, volumeY, volumeZ, blocks, maxHubsPerPlayer, maxZones, maxSelection);
    }

    /** A copy with the master switch set. */
    public ChromaLimits withEnabled(boolean on) {
        return new ChromaLimits(on, volumeX, volumeY, volumeZ, maxBlocksPerHub, maxHubsPerPlayer, maxZones,
                maxSelection);
    }

    /** The live values from {@code nerodecor.properties}. */
    public static ChromaLimits fromConfig() {
        return new ChromaLimits(ChromaConfig.ENABLED.get(), ChromaConfig.VOLUME_X.get(), ChromaConfig.VOLUME_Y.get(),
                ChromaConfig.VOLUME_Z.get(), ChromaConfig.MAX_BLOCKS_PER_HUB.get(),
                ChromaConfig.MAX_HUBS_PER_PLAYER.get(), ChromaConfig.MAX_ZONES_PER_HUB.get(),
                ChromaConfig.MAX_SELECTION.get());
    }

    static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
