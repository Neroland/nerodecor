package za.co.neroland.nerodecor.chroma.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ChromaValidatorTest {

    private static final UUID OWNER = new UUID(1, 1);
    private static final UUID STRANGER = new UUID(2, 2);
    private static final ChromaLimits LIMITS = ChromaLimits.DEFAULTS;

    private static HubNetwork network() {
        ChromaNetworkStore store = new ChromaNetworkStore();
        return store.create(7L, OWNER, 100, 64, 100, 48, 32, 48);
    }

    @Test
    void defaultVolumeIsCentredOnTheHub() {
        HubNetwork n = network();
        assertTrue(n.contains(100 - 24, 64 - 16, 100 - 24));
        assertTrue(n.contains(100 + 23, 64 + 15, 100 + 23));
        assertFalse(n.contains(100 - 25, 64, 100));
        assertFalse(n.contains(100 + 24, 64, 100));
        assertFalse(n.contains(100, 64 + 16, 100));
        assertFalse(n.contains(100, 64 - 17, 100));
    }

    @Test
    void volumeIsHardCappedAt64() {
        assertEquals(ChromaVerdict.OK, ChromaValidator.checkVolumeSize(64, 64, 64));
        assertEquals(ChromaVerdict.VOLUME_TOO_LARGE, ChromaValidator.checkVolumeSize(65, 1, 1));
        assertEquals(ChromaVerdict.VOLUME_TOO_LARGE, ChromaValidator.checkVolumeSize(1, 0, 1));
        HubNetwork clamped = new HubNetwork(9L, OWNER, 500, 500, 500);
        assertEquals(64, clamped.sizeX());
        assertEquals(64, new ChromaLimits(true, 999, 999, 999, 99999, 99, 99, 99999).volumeY());
    }

    @Test
    void linkRules() {
        HubNetwork n = network();
        assertEquals(ChromaVerdict.OK, ChromaValidator.checkLink(n, OWNER, 101, 64, 100, true, 0L, LIMITS));
        assertEquals(ChromaVerdict.NOT_CAPABLE, ChromaValidator.checkLink(n, OWNER, 101, 64, 100, false, 0L, LIMITS));
        assertEquals(ChromaVerdict.OUT_OF_VOLUME, ChromaValidator.checkLink(n, OWNER, 200, 64, 100, true, 0L, LIMITS));
        assertEquals(ChromaVerdict.NO_PERMISSION,
                ChromaValidator.checkLink(n, STRANGER, 101, 64, 100, true, 0L, LIMITS));
        assertEquals(ChromaVerdict.DISABLED,
                ChromaValidator.checkLink(n, OWNER, 101, 64, 100, true, 0L, LIMITS.withEnabled(false)));
        assertEquals(ChromaVerdict.NO_NETWORK, ChromaValidator.checkLink(null, OWNER, 0, 0, 0, true, 0L, LIMITS));
    }

    @Test
    void overlapIsRejected() {
        HubNetwork n = network();
        assertEquals(ChromaVerdict.CLAIMED_BY_OTHER_HUB,
                ChromaValidator.checkLink(n, OWNER, 101, 64, 100, true, 99L, LIMITS));
        assertEquals(ChromaVerdict.OK, ChromaValidator.checkLink(n, OWNER, 101, 64, 100, true, 7L, LIMITS));
    }

    @Test
    void hubCap() {
        assertEquals(ChromaVerdict.OK, ChromaValidator.checkNewHub(7, LIMITS));
        assertEquals(ChromaVerdict.HUB_CAP, ChromaValidator.checkNewHub(8, LIMITS));
        assertEquals(ChromaVerdict.DISABLED, ChromaValidator.checkNewHub(0, LIMITS.withEnabled(false)));
    }

    @Test
    void zoneNamesAreSanitised() {
        assertEquals(Optional.of("Main Hall"), ChromaValidator.sanitiseZoneName("  Main \t\n Hall  "));
        assertEquals(Optional.of("Red"), ChromaValidator.sanitiseZoneName("§cRed\u0000​"));
        assertEquals(Optional.empty(), ChromaValidator.sanitiseZoneName("   "));
        assertEquals(Optional.empty(), ChromaValidator.sanitiseZoneName(null));
        String longName = "x".repeat(100);
        assertEquals(32, ChromaValidator.sanitiseZoneName(longName).orElseThrow().length());
        String emoji = "😀".repeat(40);
        assertEquals(32, ChromaValidator.sanitiseZoneName(emoji).orElseThrow().codePointCount(0,
                ChromaValidator.sanitiseZoneName(emoji).orElseThrow().length()));
    }

    @Test
    void roleMatrix() {
        ChromaNetworkStore store = new ChromaNetworkStore();
        HubNetwork n = store.create(5L, OWNER, 0, 0, 0, 8, 8, 8);
        UUID viewer = new UUID(3, 3);
        UUID painter = new UUID(4, 4);
        UUID admin = new UUID(5, 5);
        assertEquals(ChromaVerdict.OK, store.setTrust(5L, OWNER, viewer, ChromaRole.VIEWER));
        assertEquals(ChromaVerdict.OK, store.setTrust(5L, OWNER, painter, ChromaRole.PAINTER));
        assertEquals(ChromaVerdict.OK, store.setTrust(5L, OWNER, admin, ChromaRole.ADMIN));
        UUID[] who = {STRANGER, viewer, painter, admin, OWNER};
        // rows: VIEW, PAINT, LINK, MANAGE, TRUST; columns: stranger, viewer, painter, admin, owner
        boolean[][] expected = {
                {false, true, true, true, true},
                {false, false, true, true, true},
                {false, false, false, true, true},
                {false, false, false, true, true},
                {false, false, false, false, true},
        };
        ChromaAction[] actions = ChromaAction.values();
        for (int a = 0; a < actions.length; a++) {
            for (int p = 0; p < who.length; p++) {
                assertEquals(expected[a][p], ChromaValidator.checkAction(n, who[p], actions[a]).isOk(),
                        actions[a] + " for column " + p);
            }
        }
        // admins cannot grant; the owner cannot be re-roled; OWNER is not grantable
        assertEquals(ChromaVerdict.NO_PERMISSION, store.setTrust(5L, admin, STRANGER, ChromaRole.VIEWER));
        assertEquals(ChromaVerdict.NO_PERMISSION, store.setTrust(5L, OWNER, OWNER, ChromaRole.VIEWER));
        assertEquals(ChromaVerdict.NO_PERMISSION, store.setTrust(5L, OWNER, STRANGER, ChromaRole.OWNER));
        assertEquals(ChromaVerdict.OK, store.setTrust(5L, OWNER, viewer, ChromaRole.NONE));
        assertEquals(ChromaRole.NONE, n.roleOf(viewer));
    }

    @Test
    void zoneRules() {
        ChromaNetworkStore store = new ChromaNetworkStore();
        store.create(5L, OWNER, 0, 0, 0, 8, 8, 8);
        assertEquals(ChromaVerdict.BAD_NAME, store.addZone(5L, OWNER, " ", 0, LIMITS));
        assertEquals(ChromaVerdict.BAD_CHANNEL, store.addZone(5L, OWNER, "a", 16, LIMITS));
        assertEquals(ChromaVerdict.NO_PERMISSION, store.addZone(5L, STRANGER, "a", 0, LIMITS));
        for (int i = 1; i < 32; i++) {
            assertEquals(ChromaVerdict.OK, store.addZone(5L, OWNER, "zone " + i, i % 16, LIMITS));
        }
        assertEquals(ChromaVerdict.ZONE_CAP, store.addZone(5L, OWNER, "one too many", 0, LIMITS));
    }
}
