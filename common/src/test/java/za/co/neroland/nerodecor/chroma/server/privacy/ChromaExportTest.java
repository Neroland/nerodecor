package za.co.neroland.nerodecor.chroma.server.privacy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import za.co.neroland.nerodecor.chroma.server.ChromaLimits;
import za.co.neroland.nerodecor.chroma.server.ChromaNetworkStore;
import za.co.neroland.nerodecor.chroma.server.ChromaRole;

class ChromaExportTest {

    private static final UUID A = new UUID(0x1234L, 0x5678L);
    private static final UUID B = new UUID(0x9abcL, 0xdef0L);

    private static List<ChromaExport.Located> world() {
        ChromaNetworkStore store = new ChromaNetworkStore();
        store.create(1L, A, 10, 64, -20, 16, 16, 16);
        store.create(2L, B, 500, 70, 500, 16, 16, 16);
        store.link(1L, A, 11, 64, -20, true, ChromaLimits.DEFAULTS);
        store.setTrust(1L, A, B, ChromaRole.PAINTER);
        store.setTrust(2L, B, A, ChromaRole.VIEWER);
        store.addZone(1L, A, "Hall", 2, ChromaLimits.DEFAULTS);
        store.get(1L).scenes().set(0, new za.co.neroland.nerodecor.chroma.Scene("Evening", List.of()), true);
        store.get(2L).scenes().set(0, new za.co.neroland.nerodecor.chroma.Scene("Bobs Disco", List.of()), true);
        return List.of(new ChromaExport.Located("minecraft:overworld", store.get(1L)),
                new ChromaExport.Located("minecraft:overworld", store.get(2L)));
    }

    @Test
    void ownExportHasOwnCoordinatesAndOwnGrantsButNothingElseOfOthers() {
        JsonObject doc = ChromaExport.build(A, world(), 0L, 30, 90);
        String json = ChromaExport.toJson(doc);
        assertFalse(json.contains("Bobs Disco"), "another player's scene leaked");
        // B appears exactly once: as the player A trusted (A's own choice). B's own hub and its owner do not.
        assertEquals(json.indexOf(B.toString()), json.lastIndexOf(B.toString()), "B's id appears outside A's grants");
        JsonArray owned = doc.getAsJsonArray("hubs_owned");
        assertEquals(1, owned.size());
        JsonObject hub = owned.get(0).getAsJsonObject();
        assertEquals(10, hub.getAsJsonArray("anchor").get(0).getAsInt());
        assertEquals(1, hub.getAsJsonObject("trusted_players_per_role").get("painter").getAsInt());
        JsonObject grant = hub.getAsJsonArray("trusted_players").get(0).getAsJsonObject();
        assertEquals(B.toString(), grant.get("player").getAsString());
        assertEquals("painter", grant.get("role").getAsString());
        assertEquals("Hall", hub.getAsJsonArray("zones").get(1).getAsJsonObject().get("name").getAsString());
        JsonObject scene = hub.getAsJsonArray("scenes").get(0).getAsJsonObject();
        assertEquals("Evening", scene.get("name").getAsString());
        assertTrue(scene.get("share_code").getAsString().startsWith("ND1-"));
        JsonArray roles = doc.getAsJsonArray("roles_held");
        assertEquals(1, roles.size());
        JsonObject role = roles.get(0).getAsJsonObject();
        assertEquals("viewer", role.get("role").getAsString());
        assertFalse(role.has("anchor"));
        assertFalse(role.has("owner"));
        assertEquals(30, doc.getAsJsonObject("retention").get("orphaned_network_days").getAsInt());
    }

    @Test
    void filesStayInsideTheFolderAndAreDeleted(@TempDir Path dir) throws Exception {
        ChromaExport.write(dir, A, ChromaExport.build(A, world(), 0L, 30, 90));
        assertTrue(Files.exists(ChromaExport.file(dir, A)));
        assertTrue(ChromaExport.delete(dir, A));
        assertFalse(Files.exists(ChromaExport.file(dir, A)));
        assertThrows(IllegalStateException.class, () -> ChromaExport.file(dir, "../escape"));
    }

    @Test
    void cooldownAppliesAndErasureForgetsIt() {
        ChromaExport.Cooldown cooldown = new ChromaExport.Cooldown();
        assertEquals(0L, cooldown.tryAcquire(A, 1_000L));
        assertTrue(cooldown.tryAcquire(A, 2_000L) > 0L);
        cooldown.forget(A);
        assertEquals(0L, cooldown.tryAcquire(A, 2_000L));
    }
}
