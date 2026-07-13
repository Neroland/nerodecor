package za.co.neroland.nerodecor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import za.co.neroland.nerodecor.config.NeroDecorConfig;

/**
 * Loader-agnostic entry point for NeroDecor. Each loader entry point
 * (Fabric / Forge / NeoForge) calls {@link #init()} once during mod
 * construction. Loader-specific behaviour is reached through a platform seam;
 * client-only wiring lives in {@link za.co.neroland.nerodecor.client.NeroDecorClient}.
 */
public final class NeroDecorCommon {

    public static final String MOD_ID = "nerodecor";
    public static final Logger LOGGER = LoggerFactory.getLogger("NeroDecor");

    private NeroDecorCommon() {
    }

    /** Called once per loader during mod construction. */
    public static void init() {
        LOGGER.info("[NeroDecor] common init");

        // Register NeroDecor's config schema with Core (render kill-switches + later keys).
        NeroDecorConfig.init();
    }
}
