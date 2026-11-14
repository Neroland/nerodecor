package za.co.neroland.nerodecor.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class NeroDecorTelemetryScrubTest {

    @Test
    void masksEveryUuidForm() {
        assertEquals("owner <uuid> left", NeroDecorTelemetry.scrub("owner 123e4567-e89b-12d3-a456-426614174000 left"));
        assertEquals("{who:<uuid>}", NeroDecorTelemetry.scrub("{who:[I; 1, -2, 3, -4]}"));
        assertEquals("{who:<uuid>}", NeroDecorTelemetry.scrub("{who:[I;1,-2,3,-4]}"));
    }
}
