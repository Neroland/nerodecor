package za.co.neroland.nerodecor.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class DecorColorTest {

    @Test
    void naturalIsTheMultiplyIdentity() {
        assertEquals(0xFFFFFF, DecorColor.NATURAL.tint());
        assertTrue(DecorColor.NATURAL.isNatural());
    }

    @Test
    void seventeenUniqueSerializedNames() {
        Set<String> names = new HashSet<>();
        for (DecorColor c : DecorColor.values()) {
            names.add(c.getSerializedName());
        }
        assertEquals(17, names.size());
    }

    /** Every neon colour folded into chroma_cube in 0.8.0 must have a matching dye. */
    @Test
    void everyNeonColourHasADecorColor() {
        String[] neon = {"red", "orange", "yellow", "lime", "green", "cyan",
                "light_blue", "blue", "purple", "magenta", "pink", "white"};
        for (String n : neon) {
            boolean found = false;
            for (DecorColor c : DecorColor.values()) {
                found |= c.getSerializedName().equals(n);
            }
            assertTrue(found, n);
        }
    }
}
