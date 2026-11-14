package za.co.neroland.nerodecor.chroma.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class ChromaNetworkStoreTest {

    private static final UUID A = new UUID(1, 1);
    private static final UUID B = new UUID(2, 2);
    private static final ChromaLimits LIMITS = ChromaLimits.DEFAULTS;

    @Test
    void blockCapIsEnforced() {
        ChromaNetworkStore store = new ChromaNetworkStore();
        store.create(1L, A, 0, 0, 0, 64, 64, 64);
        int linked = 0;
        outer:
        for (int x = -32; x < 32; x++) {
            for (int y = -32; y < 32; y++) {
                for (int z = -32; z < 32; z++) {
                    if (linked == 4096) {
                        break outer;
                    }
                    assertEquals(ChromaVerdict.OK, store.link(1L, A, x, y, z, true, LIMITS));
                    linked++;
                }
            }
        }
        assertEquals(4096, store.get(1L).size());
        assertEquals(ChromaVerdict.BLOCK_CAP, store.link(1L, A, 31, 31, 31, true, LIMITS));
        assertEquals(ChromaVerdict.BLOCK_CAP,
                store.link(1L, A, 31, 31, 30, true, LIMITS.withMaxBlocks(10)));
    }

    @Test
    void aBlockBelongsToOneHub() {
        ChromaNetworkStore store = new ChromaNetworkStore();
        store.create(1L, A, 0, 0, 0, 48, 32, 48);
        store.create(2L, B, 10, 0, 0, 48, 32, 48);
        assertEquals(ChromaVerdict.OK, store.link(1L, A, 5, 0, 0, true, LIMITS));
        assertEquals(ChromaVerdict.CLAIMED_BY_OTHER_HUB, store.link(2L, B, 5, 0, 0, true, LIMITS));
        assertEquals(ChromaVerdict.ALREADY_LINKED, store.link(1L, A, 5, 0, 0, true, LIMITS));
        assertEquals(1L, store.claimant(5, 0, 0));
        // after unlinking, the other hub may take it
        assertEquals(ChromaVerdict.OK, store.unlink(1L, A, 5, 0, 0));
        assertEquals(ChromaVerdict.NOT_LINKED, store.unlink(1L, A, 5, 0, 0));
        assertEquals(ChromaVerdict.OK, store.link(2L, B, 5, 0, 0, true, LIMITS));
    }

    @Test
    void deactivateReleasesAndReactivateReclaims() {
        ChromaNetworkStore store = new ChromaNetworkStore();
        store.create(1L, A, 0, 0, 0, 48, 32, 48);
        store.link(1L, A, 3, 1, 2, true, LIMITS);
        store.link(1L, A, 4, 1, 2, true, LIMITS);
        store.deactivate(1L, 1000L);
        assertFalse(store.get(1L).active());
        assertEquals(0L, store.claimant(3, 1, 2));
        assertEquals(ChromaVerdict.INACTIVE, store.link(1L, A, 5, 1, 2, true, LIMITS));
        // another hub takes one of the blocks while the first is in someone's pocket
        store.create(2L, B, 0, 0, 0, 48, 32, 48);
        store.link(2L, B, 4, 1, 2, true, LIMITS);
        int dropped = store.activate(1L, 0, 0, 0, pos -> true);
        assertEquals(1, dropped);
        assertEquals(1L, store.claimant(3, 1, 2));
        assertEquals(2L, store.claimant(4, 1, 2));
        // re-placed elsewhere: the layout travels with the hub
        store.activate(1L, 100, 0, 0, pos -> true);
        assertEquals(0L, store.claimant(3, 1, 2));
        assertEquals(1L, store.claimant(103, 1, 2));
    }

    @Test
    void relinkDropsBlocksThatAreNoLongerCapable() {
        ChromaNetworkStore store = new ChromaNetworkStore();
        store.create(1L, A, 0, 0, 0, 48, 32, 48);
        store.link(1L, A, 1, 0, 0, true, LIMITS);
        store.link(1L, A, 2, 0, 0, true, LIMITS);
        store.deactivate(1L, 1L);
        long gone = ChromaPos.pack(2, 0, 0);
        assertEquals(1, store.activate(1L, 0, 0, 0, pos -> pos != gone));
        assertEquals(1, store.get(1L).size());
    }

    @Test
    void erasureOrphansOwnedNetworksAndDropsTrust() {
        ChromaNetworkStore store = new ChromaNetworkStore();
        store.create(1L, A, 0, 0, 0, 48, 32, 48);
        store.create(2L, B, 0, 0, 0, 48, 32, 48);
        store.link(1L, A, 1, 0, 0, true, LIMITS);
        store.setTrust(2L, B, A, ChromaRole.PAINTER);
        assertEquals(1, store.ownedBy(A));
        assertTrue(store.eraseFor(A, 5_000L));
        HubNetwork orphan = store.get(1L);
        assertTrue(orphan.orphaned());
        assertEquals(5_000L, orphan.orphanedSince());
        assertEquals(HubNetwork.NO_OWNER, orphan.owner());
        assertEquals(0, store.ownedBy(A));
        // the build keeps its colour: still active and still claiming its block
        assertEquals(1L, store.claimant(1, 0, 0));
        assertEquals(ChromaRole.NONE, store.get(2L).roleOf(A));
        assertFalse(store.eraseFor(A, 6_000L));
    }

    @Test
    void retentionPurgesOnlyLongInactiveNetworks() {
        ChromaNetworkStore store = new ChromaNetworkStore();
        store.create(1L, A, 0, 0, 0, 8, 8, 8);
        store.create(2L, A, 0, 0, 0, 8, 8, 8);
        store.create(3L, A, 0, 0, 0, 8, 8, 8);
        store.deactivate(1L, 1_000L);
        store.deactivate(2L, 9_000L);
        assertEquals(1, store.purgeInactive(10_000L, 5_000L));
        assertNull(store.get(1L));
        assertTrue(store.get(2L) != null && store.get(3L) != null);
    }

    @Test
    void shrinkingTheVolumeUnlinksOutsideBlocks() {
        ChromaNetworkStore store = new ChromaNetworkStore();
        store.create(1L, A, 0, 0, 0, 48, 32, 48);
        store.link(1L, A, 20, 0, 0, true, LIMITS);
        store.link(1L, A, 1, 0, 0, true, LIMITS);
        assertEquals(ChromaVerdict.VOLUME_TOO_LARGE, store.setVolume(1L, A, 65, 8, 8));
        assertEquals(ChromaVerdict.NO_PERMISSION, store.setVolume(1L, B, 8, 8, 8));
        assertEquals(ChromaVerdict.OK, store.setVolume(1L, A, 8, 8, 8));
        assertEquals(1, store.get(1L).size());
        assertEquals(0L, store.claimant(20, 0, 0));
    }

    @Test
    void positionsPackLikeBlockPos() {
        int[][] samples = {{0, 0, 0}, {-1, -64, -1}, {30_000_000, 319, -30_000_000}, {-123, 2047, 456}};
        for (int[] s : samples) {
            long p = ChromaPos.pack(s[0], s[1], s[2]);
            assertEquals(s[0], ChromaPos.x(p));
            assertEquals(s[1], ChromaPos.y(p));
            assertEquals(s[2], ChromaPos.z(p));
        }
    }
}
