package za.co.neroland.nerodecor.link;

import java.util.List;

import za.co.neroland.nerolandcore.link.LinkModuleInfo;
import za.co.neroland.nerolandcore.link.NeroLinkRegistry;

import za.co.neroland.nerodecor.NeroDecorCommon;
import za.co.neroland.nerodecor.config.NeroDecorConfig;
import za.co.neroland.nerodecor.platform.Services;

/**
 * NeroDecor's plug into Neroland Core's link API — the declaration half, and a deliberately thin one.
 *
 * <p>NeroDecor already <em>consumes</em> Core 1.9's decor and palette contracts ({@code DecorTags},
 * {@code PaletteRegistry}); this module completes the loop by publishing the resulting catalogue, so a
 * companion client can browse the palette and the block set — "which neon colours exist", "what does
 * starsteel hull actually look like", "which shapes does the panel family come in" — without the game
 * open.
 *
 * <h2>Snapshot-only, on purpose</h2>
 *
 * <p><b>There is no {@code LinkActionHandler} here and there should not be.</b> Everything NeroDecor
 * can change is a block in the world: painting one, placing one, breaking one. Doing any of that from
 * a phone would let a player edit the world without being in it, and no amount of gating makes
 * "recolour that block over there" a status operation. There is likewise nothing to schedule, nothing
 * to acknowledge and nothing to claim, so the template's usual {@code acknowledge_alert} would be an
 * action with no alerts behind it. The module registers a snapshot provider and nothing else.
 *
 * <p><b>And no events.</b> A live delta exists to tell you something changed while you were away.
 * NeroDecor's entire state is "which blocks are registered" and "what colour is that block", the first
 * of which never changes at runtime and the second of which only changes because a player was standing
 * there with a paint gun. Neither is worth a push notification, so nothing is published on Core's event
 * bus.
 *
 * <h2>Privacy (POPIA/GDPR)</h2>
 *
 * <p><b>NeroDecor stores no player-attributable data at all.</b> A block's paint colour is world state
 * — a {@code COLOR} blockstate property mirrored on the item stack through the {@code nerodecor:color}
 * data component — and records nothing about who applied it. There is no owner, no history, no
 * attribution table and nothing registered with Core's {@code PlayerDataErasure} hook, because there
 * would be nothing for it to erase.
 *
 * <p>Both sections are therefore <b>public catalogue data, identical for every requester</b>: the
 * shared palette Core exports and the block set this mod registers. The requesting UUID is used for
 * nothing at all, which is the strongest form of the own-data-only contract this SPI can have. See
 * {@code PRIVACY.md} and {@code wiki/Link-Module.md}.
 *
 * <p><b>Schema version 1.</b> Bump {@link #SCHEMA_VERSION} whenever the shape of a section changes, so
 * a companion client can tell what it is parsing. Note that a new decor <em>block</em> is not a schema
 * change — the {@code blocks} section is a list whose length is expected to grow.
 *
 * @see NeroDecorLinkSnapshots
 */
public final class NeroDecorLinkModule {

    /** The link module id — the same string as the mod id, as the ecosystem convention requires. */
    public static final String MODULE_ID = NeroDecorCommon.MOD_ID;

    /** The snapshot schema revision. Bump on any change to a section's shape. */
    public static final int SCHEMA_VERSION = 1;

    /** Section: the shared Neroland palette plus NeroDecor's own paint vocabulary. */
    public static final String SECTION_PALETTE = "palette";

    /** Section: every registered decor block with its family, shape and connected-texture style. */
    public static final String SECTION_BLOCKS = "blocks";

    private NeroDecorLinkModule() {
    }

    /**
     * Register the read surface with Core's link registry. Called <b>last</b> from
     * {@code NeroDecorCommon.init()}, after the blocks the {@code blocks} section describes have been
     * declared.
     *
     * <p>Wrapped end to end, including {@link LinkageError}: an older Neroland Core without the link
     * package would otherwise abort mod construction with {@code NoClassDefFoundError} rather than
     * merely losing the companion integration. The blocks themselves are unaffected either way — the
     * worst outcome is a companion app that reports NeroDecor as absent, which is also exactly what
     * {@code enableLinkModule=false} produces.
     */
    public static void init() {
        try {
            if (!NeroDecorConfig.linkModuleEnabled()) {
                NeroDecorCommon.LOGGER.info(
                        "[NeroDecor] The NeroLink module is disabled by config; companion clients will "
                                + "not see the NeroDecor catalogue.");
                return;
            }
            LinkModuleInfo info = new LinkModuleInfo(MODULE_ID, modVersion(), SCHEMA_VERSION,
                    List.of(SECTION_PALETTE, SECTION_BLOCKS),
                    // No actions: see the class notes. Core renders an empty list as a read-only module.
                    List.of());
            NeroLinkRegistry.registerSnapshotProvider(new NeroDecorLinkSnapshots(), info);
            NeroDecorCommon.LOGGER.info("[NeroDecor] NeroLink module registered (schema v{}, read-only).",
                    SCHEMA_VERSION);
        } catch (RuntimeException | LinkageError e) {
            NeroDecorCommon.LOGGER.warn(
                    "[NeroDecor] Could not register the NeroLink module; companion clients will not see "
                            + "the NeroDecor catalogue. The blocks themselves are unaffected.", e);
        }
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
