package za.co.neroland.nerodecor.chroma;

/**
 * Binds a zone (0-31) to a program. {@code channel} (0-15) is the hub channel that drives the zone.
 */
public record ZoneAssignment(int zone, int channel, ZoneProgram program) {

    public static final int MAX_ZONES = 32;
    public static final int MAX_CHANNELS = 16;

    public ZoneAssignment {
        if (zone < 0 || zone >= MAX_ZONES || channel < 0 || channel >= MAX_CHANNELS || program == null) {
            throw new IllegalArgumentException("zone 0-31, channel 0-15 and a program are required");
        }
    }
}
