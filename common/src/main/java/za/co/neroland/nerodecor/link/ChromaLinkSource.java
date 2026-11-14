package za.co.neroland.nerodecor.link;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongPredicate;

import za.co.neroland.nerodecor.chroma.server.ChromaNetworkStore;
import za.co.neroland.nerodecor.chroma.server.ChromaVerdict;
import za.co.neroland.nerodecor.chroma.server.HubNetwork;

/**
 * What the Chroma half of the link module reads and changes: the running server ({@link LiveChromaLink}) or, in
 * tests, an in-memory store. Server thread only (NeroLink marshals every snapshot and action onto it).
 *
 * <p>The seam is deliberately narrow: the sections and actions only ever see {@link HubNetwork}s, decide visibility
 * from {@link HubNetwork#roleOf}, and edit through the same store, scene and guard calls the Chroma Hub GUI uses.
 */
public interface ChromaLinkSource {

    /** One hub and the store (dimension) it lives in, with that dimension's game time. */
    record Hub(HubNetwork network, ChromaNetworkStore store, long gameTime) {
    }

    /** The Chroma master switch ({@code chroma.enabled}). Off: sections are empty and every action is refused. */
    boolean enabled();

    /** Every hub network in every loaded dimension. Filtering by requester is the caller's job. */
    List<HubNetwork> networks();

    /** The hub with this id, wherever it is. */
    Optional<Hub> hub(long id);

    /**
     * The world-side "may this player modify the block at this packed position" test for {@code hub}, exactly as the
     * GUI applies it ({@code ChromaGuard}: game mode, spawn protection, world border, claim mods), or {@code null}
     * when the player is not online (the guard needs the player).
     */
    LongPredicate editor(UUID player, Hub hub);

    /** Charges the shared Chroma edit limiter (one edit, {@code ops} block ops), as every GUI edit does. */
    ChromaVerdict charge(UUID player, int ops);
}
