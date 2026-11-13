package za.co.neroland.nerodecor.client.ctm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class CtmSolverTest {

    @Test
    void cornerMethodYieldsTheClassic47Tiles() {
        assertEquals(47, CtmSolver.distinctFaceCount());
    }

    @Test
    void stripEndsDifferFromTheMiddle() {
        assertNotEquals(CtmSolver.strip(true, true), CtmSolver.strip(false, true));
        assertNotEquals(CtmSolver.strip(true, false), CtmSolver.strip(false, false));
    }
}
