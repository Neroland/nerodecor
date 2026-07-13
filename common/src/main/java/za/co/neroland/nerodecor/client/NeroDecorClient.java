package za.co.neroland.nerodecor.client;

import za.co.neroland.nerodecor.NeroDecorCommon;
import za.co.neroland.nerodecor.config.NeroDecorConfig;

/**
 * Loader-agnostic client bootstrap for NeroDecor. Each loader's client entry point
 * (Fabric {@code ClientModInitializer}, NeoForge/Forge client-setup event) calls
 * {@link #initClient()} once, so shared client wiring lives here instead of being copied
 * three times.
 *
 * <p>Stage D wires the connected-texture foundation's client state (the kill-switch is
 * read here). The concrete per-loader CTM model binding — wrapping a block's baked model
 * so {@link za.co.neroland.nerodecor.client.ctm.CtmSolver} picks sub-tiles from neighbour
 * state — attaches in Stage E, once the first {@link za.co.neroland.nerodecor.client.ctm.CtmSurface}
 * block exists and it can be verified in a running client. The loader render hooks stay
 * behind this common entry point so {@code common/} never imports loader render types.
 */
public final class NeroDecorClient {

    private static boolean initialised;

    private NeroDecorClient() {
    }

    /** Called once per loader during client init. Idempotent. */
    public static void initClient() {
        if (initialised) {
            return;
        }
        initialised = true;
        NeroDecorCommon.LOGGER.info("[NeroDecor] client init — connected textures: {}, emissive: {}",
                NeroDecorConfig.connectedTexturesEnabled(), NeroDecorConfig.emissiveRenderingEnabled());
    }
}
