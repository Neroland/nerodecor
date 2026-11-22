package za.co.neroland.nerodecor.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

class DecorMilestonesTest {

    @Test
    void rungsOpenOnlyWhenCrossed() {
        assertFalse(DecorMilestones.crosses(14, 15, 16));
        assertTrue(DecorMilestones.crosses(15, 16, 16));
        assertFalse(DecorMilestones.crosses(16, 17, 16), "already past the rung");
        assertEquals(List.of(), DecorMilestones.forGrowth(3, 4));
        assertEquals(List.of(DecorMilestones.HUB_LINKED_16), DecorMilestones.forGrowth(15, 16));
        assertEquals(List.of(DecorMilestones.HUB_LINKED_64), DecorMilestones.forGrowth(63, 64));
        assertEquals(List.of(DecorMilestones.HUB_LINKED_16, DecorMilestones.HUB_LINKED_64),
                DecorMilestones.forGrowth(0, 64), "one big jump opens both");
        assertEquals(List.of(), DecorMilestones.forGrowth(64, 64));
    }

    @Test
    void everyGateShipsAsAPlayerRecord() throws IOException, URISyntaxException {
        Path root = Path.of(DecorMilestonesTest.class.getResource("/data/nerodecor/neroland_gates").toURI());
        for (Identifier gate : DecorMilestones.ALL) {
            Path file = root.resolve(gate.getPath() + ".json");
            assertTrue(Files.isRegularFile(file), gate + " ships no gate file");
            JsonObject def = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            assertEquals("player", def.get("scope").getAsString(), gate + " must be player-scope");
            assertFalse(def.has("requires"), gate + " must not be a lock");
        }
    }
}
