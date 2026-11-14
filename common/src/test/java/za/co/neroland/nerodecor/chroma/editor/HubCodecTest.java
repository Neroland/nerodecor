package za.co.neroland.nerodecor.chroma.editor;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerodecor.chroma.ChromaColor;
import za.co.neroland.nerodecor.chroma.RelativePos;

class HubCodecTest {

    private static HubDetail sample() {
        return new HubDetail(42L, HubDetail.ROLE_OWNER, true, false, 48, 32, 48, 4096, 32,
                new int[] {ChromaColor.WHITE.packed(), ChromaColor.of(1, 2, 3, 7).packed()},
                List.of(new HubDetail.Zone("Default", 0), new HubDetail.Zone("Neon", 5)),
                new int[] {RelativePos.pack(1, 2, 3), RelativePos.pack(-4, 0, 9)}, new int[] {1, 1 << 8},
                List.of(new HubDetail.Trust(new UUID(1, 2), HubDetail.ROLE_PAINTER)),
                List.of(new HubDetail.Slot("Aurora", new byte[] {1, 2, 3}), new HubDetail.Slot("", new byte[0])), 0,
                "");
    }

    @Test
    void detailRoundTrips() {
        HubDetail d = sample().withNotice("bad_slot");
        HubDetail back = HubDetail.decode(d.encode()).orElseThrow();
        assertEquals(d.network(), back.network());
        assertArrayEquals(d.palette(), back.palette());
        assertArrayEquals(d.offsets(), back.offsets());
        assertArrayEquals(d.values(), back.values());
        assertEquals(d.zones(), back.zones());
        assertEquals(d.trusted(), back.trusted());
        assertArrayEquals(d.scenes().get(0).scene(), back.scenes().get(0).scene());
        assertEquals("bad_slot", back.notice());
        assertTrue(back.canTrust());
    }

    @Test
    void detailRejectsTruncatedOrTrailingBytes() {
        byte[] bytes = sample().encode();
        assertTrue(HubDetail.decode(Arrays.copyOf(bytes, bytes.length - 1)).isEmpty());
        assertTrue(HubDetail.decode(Arrays.copyOf(bytes, bytes.length + 1)).isEmpty());
        assertTrue(HubDetail.decode(null).isEmpty());
    }

    @Test
    void actionRoundTrips() {
        HubAction a = HubAction.trust(9L, new UUID(3, 4), 2);
        HubAction back = HubAction.decode(a.encode()).orElseThrow();
        assertEquals(a.kind(), back.kind());
        assertEquals(a.player(), back.player());
        HubAction s = HubAction.storeScene(9L, 3, new byte[] {9, 8});
        assertArrayEquals(new byte[] {9, 8}, HubAction.decode(s.encode()).orElseThrow().blob());
        byte[] bad = s.encode();
        bad[8] = 99; // unknown kind
        assertTrue(HubAction.decode(bad).isEmpty());
    }
}
