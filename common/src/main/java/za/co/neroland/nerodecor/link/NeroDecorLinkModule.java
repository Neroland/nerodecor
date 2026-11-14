package za.co.neroland.nerodecor.link;

import java.lang.ref.WeakReference;
import java.util.List;

import net.minecraft.server.MinecraftServer;

import za.co.neroland.nerolandcore.link.LinkModuleInfo;
import za.co.neroland.nerolandcore.link.NeroLinkRegistry;

import za.co.neroland.nerodecor.NeroDecorCommon;
import za.co.neroland.nerodecor.chroma.server.ChromaHooks;
import za.co.neroland.nerodecor.config.NeroDecorConfig;
import za.co.neroland.nerodecor.platform.Services;

/**
 * NeroDecor's plug into Neroland Core's link API.
 *
 * <h2>Schema 2 (0.8.0 "Chroma")</h2>
 *
 * <ul>
 *   <li>Public catalogue sections, identical for every requester: {@code palette} and {@code blocks} (now with
 *       {@code chroma} and {@code model} per block; the folded neon ids are gone, {@code chroma_cube} is listed).</li>
 *   <li>Requester-scoped Chroma sections: {@code chroma_hubs}, {@code chroma_zones} and {@code chroma_scenes}, showing
 *       only hubs the requester owns or is trusted on, with no coordinates and no roster
 *       ({@link ChromaLinkSections}).</li>
 *   <li>Four remote actions for painters and up, one per second per requester, through the GUI's own validation
 *       path ({@link ChromaLinkActions}).</li>
 *   <li>Live events {@code scene_changed}, {@code trigger_fired} and {@code hub_broken}, delivered only to the hub's
 *       owner and trusted members ({@link ChromaLinkEvents}).</li>
 * </ul>
 *
 * <p>Section and action ids are {@code snake_case} because the NeroLink bridge only accepts lower-case topic and
 * path segments.
 *
 * <h2>Privacy (POPIA/GDPR)</h2>
 *
 * <p>The Chroma data the module reads is the data the Chroma Hub already holds and already shows to the same players
 * in game (see {@code PRIVACY.md}); the module stores nothing of its own except, in memory, a one-second rate-limit
 * bucket per requester and the last-played slot per hub. It logs no UUIDs, names, coordinates or scene text.
 * {@code enableLinkModule=false} leaves the whole module unregistered.
 */
public final class NeroDecorLinkModule {

    /** The link module id - the same string as the mod id, as the ecosystem convention requires. */
    public static final String MODULE_ID = NeroDecorCommon.MOD_ID;

    /** The snapshot schema revision. Bump on any change to a section's shape. */
    public static final int SCHEMA_VERSION = 2;

    /** Section: the shared Neroland palette plus NeroDecor's own paint vocabulary. */
    public static final String SECTION_PALETTE = "palette";

    /** Section: every visible decor block with its shape, model kind and whether a hub can drive it. */
    public static final String SECTION_BLOCKS = "blocks";

    /** Section: the Chroma hubs the requester owns or is trusted on. */
    public static final String SECTION_CHROMA_HUBS = "chroma_hubs";

    /** Section: one hub's zones ({@code ?hub=<id>}). */
    public static final String SECTION_CHROMA_ZONES = "chroma_zones";

    /** Section: one hub's scene slots ({@code ?hub=<id>}). */
    public static final String SECTION_CHROMA_SCENES = "chroma_scenes";

    public static final List<String> SECTIONS = List.of(SECTION_PALETTE, SECTION_BLOCKS, SECTION_CHROMA_HUBS,
            SECTION_CHROMA_ZONES, SECTION_CHROMA_SCENES);

    private static volatile WeakReference<MinecraftServer> server = new WeakReference<>(null);
    private static volatile ChromaLinkActions actions;
    private static volatile ChromaLinkEvents events;

    private NeroDecorLinkModule() {
    }

    /**
     * Register with Core's link registry. Called <b>last</b> from {@code NeroDecorCommon.init()}, after the blocks the
     * {@code blocks} section describes have been declared.
     *
     * <p>Wrapped end to end, including {@link LinkageError}: an older Neroland Core without the link package would
     * otherwise abort mod construction rather than merely losing the companion integration.
     */
    public static void init() {
        try {
            if (!NeroDecorConfig.linkModuleEnabled()) {
                NeroDecorCommon.LOGGER.info(
                        "[NeroDecor] The NeroLink module is disabled by config; companion clients will "
                                + "not see NeroDecor.");
                return;
            }
            LinkModuleInfo info = new LinkModuleInfo(MODULE_ID, modVersion(), SCHEMA_VERSION, SECTIONS,
                    ChromaLinkActions.ACTIONS);
            NeroLinkRegistry.registerSnapshotProvider(new NeroDecorLinkSnapshots(NeroDecorLinkModule::source), info);
            ChromaLinkActions handler = new ChromaLinkActions(NeroDecorLinkModule::source);
            NeroLinkRegistry.registerActionHandler(handler, info);
            actions = handler;
            ChromaLinkEvents listener = new ChromaLinkEvents(NeroLinkRegistry.eventBus()::publish, System::nanoTime);
            ChromaHooks.register(listener);
            events = listener;
            NeroDecorCommon.LOGGER.info("[NeroDecor] NeroLink module registered (schema v{}, {} actions).",
                    SCHEMA_VERSION, ChromaLinkActions.ACTIONS.size());
        } catch (RuntimeException | LinkageError e) {
            NeroDecorCommon.LOGGER.warn(
                    "[NeroDecor] Could not register the NeroLink module; companion clients will not see "
                            + "NeroDecor. The blocks themselves are unaffected.", e);
        }
    }

    /** Server tick: remembers the running server so sections and actions can reach the Chroma stores. */
    public static void serverTick(MinecraftServer running) {
        if (running != null && server.get() != running) {
            server = new WeakReference<>(running);
            ChromaLinkActions handler = actions;
            if (handler != null) {
                handler.clear(); // a new server: no rate-limit buckets or remembered slots carry over
            }
            ChromaLinkEvents listener = events;
            if (listener != null) {
                listener.clear();
            }
        }
    }

    /** The live Chroma source, or {@code null} when no server is running. */
    static ChromaLinkSource source() {
        MinecraftServer running = server.get();
        return running == null || !running.isRunning() ? null : new LiveChromaLink(running);
    }

    /** This mod's public version string for discovery, or {@code "unknown"} if the seam is unhappy. */
    private static String modVersion() {
        try {
            String version = Services.PLATFORM.getModVersion();
            return version == null || version.isBlank() ? "unknown" : version;
        } catch (RuntimeException e) {
            return "unknown";
        }
    }
}
