package za.co.neroland.nerodecor.chroma.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerodecor.chroma.Scene;

/** Stage 7: erasure orphans owned networks, removes trust everywhere, and orphans expire on their own clock. */
class ChromaErasureTest {

    private static final UUID A = new UUID(11, 11);
    private static final UUID B = new UUID(22, 22);
    private static final UUID OP = new UUID(33, 33);
    private static final long DAY = 86_400_000L;

    private static ChromaNetworkStore twoHubs() {
        ChromaNetworkStore store = new ChromaNetworkStore();
        store.create(1L, A, 0, 0, 0, 16, 16, 16);
        store.create(2L, A, 100, 0, 0, 16, 16, 16);
        store.link(1L, A, 1, 0, 0, true, ChromaLimits.DEFAULTS);
        assertEquals(ChromaVerdict.OK, store.setTrust(1L, A, B, ChromaRole.ADMIN));
        assertEquals(ChromaVerdict.OK, store.addZone(1L, A, "Alice's bedroom", 3, ChromaLimits.DEFAULTS));
        store.get(1L).scenes().set(2, new Scene("Alice's party", List.of()), true);
        return store;
    }

    @Test
    void erasingATrustedPlayerRemovesThemOnly() {
        ChromaNetworkStore store = twoHubs();
        assertTrue(store.eraseFor(B, 1_000L));
        assertFalse(store.get(1L).trusted().containsKey(B));
        assertFalse(store.get(1L).orphaned());
        assertEquals(A, store.get(1L).owner());
        assertEquals("Alice's bedroom", store.get(1L).zones().get(1).name());
    }

    @Test
    void erasingTheOwnerOrphansBlanksAndLocks() {
        ChromaNetworkStore store = twoHubs();
        store.setAutoAdopt(1L, A, true);
        assertEquals(ChromaVerdict.OK, ChromaHubAdmin.renameZone(store.get(1L), A, 0, "Alice's den"));
        assertTrue(store.eraseFor(A, 2_000L));
        assertEquals(ChromaVerdict.NO_PERMISSION, ChromaHubAdmin.renameZone(store.get(1L), A, 0, "Mine again"));
        HubNetwork network = store.get(1L);
        assertTrue(network.orphaned());
        assertEquals(HubNetwork.NO_OWNER, network.owner());
        assertTrue(network.trusted().isEmpty(), "trusted list must be emptied");
        assertFalse(network.autoAdopt());
        assertEquals(HubNetwork.DEFAULT_ZONE, network.zones().get(0).name());
        assertEquals("Zone 2", network.zones().get(1).name());
        assertEquals(3, network.zones().get(1).channel(), "channels are decor and stay");
        assertEquals("Scene 3", network.scenes().scene(2).name());
        // nobody can do anything, not even someone presenting the sentinel id
        for (UUID who : new UUID[] {A, B, OP, HubNetwork.NO_OWNER, null}) {
            assertEquals(ChromaRole.NONE, network.roleOf(who));
            assertFalse(ChromaValidator.checkAction(network, who, ChromaAction.PAINT).isOk());
        }
        assertFalse(store.eraseFor(HubNetwork.NO_OWNER, 3_000L));
        assertTrue(store.get(2L).orphaned());
    }

    @Test
    void orphansExpireAfterTheirRetentionAndClaimsStopTheClock() {
        ChromaNetworkStore store = twoHubs();
        long t0 = 10 * DAY;
        store.eraseFor(A, t0);
        assertEquals(0, store.purgeOrphaned(t0 + 29 * DAY, 30 * DAY));
        assertEquals(ChromaVerdict.OK, store.claimOrphan(2L, OP));
        assertEquals(ChromaRole.OWNER, store.get(2L).roleOf(OP));
        assertFalse(store.get(2L).orphaned());
        assertEquals(ChromaVerdict.NO_PERMISSION, store.claimOrphan(2L, B), "only orphans can be claimed");
        assertEquals(1, store.purgeOrphaned(t0 + 31 * DAY, 30 * DAY));
        assertNull(store.get(1L));
        assertEquals(0L, store.claimant(1, 0, 0), "a purged orphan releases its blocks");
        assertNotNull(store.get(2L));
    }

    @Test
    void orphanedNetworksSaveWithoutOwnerOrTrust() {
        ChromaNetworkStore store = twoHubs();
        store.eraseFor(A, 7_000L);
        ChromaNetworkData.NetDto dto = ChromaNetworkData.toDto(store.get(1L));
        assertTrue(dto.owner().isEmpty());
        assertTrue(dto.trust().isEmpty());
        assertEquals(7_000L, dto.orphan());
        HubNetwork back = ChromaNetworkData.fromDto(dto);
        assertTrue(back.orphaned());
        assertEquals(7_000L, back.orphanedSince());
        assertEquals(1, back.size());
    }

    @Test
    void ownerlessDtoWithoutOrphanTimeIsTreatedAsLongExpired() {
        ChromaNetworkStore store = twoHubs();
        ChromaNetworkData.NetDto owned = ChromaNetworkData.toDto(store.get(2L));
        ChromaNetworkData.NetDto broken = new ChromaNetworkData.NetDto(owned.id(), java.util.Optional.empty(),
                owned.trust(), owned.anchor(), owned.size(), owned.active(), owned.auto(), owned.since(),
                owned.zones(), owned.pos(), owned.val(), owned.palette(), 0L);
        HubNetwork back = ChromaNetworkData.fromDto(broken);
        assertTrue(back.orphaned());
        ChromaNetworkStore other = new ChromaNetworkStore();
        other.adopt(back);
        assertEquals(1, other.purgeOrphaned(DAY * 31, DAY * 30));
    }
}
