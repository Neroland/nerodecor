package za.co.neroland.nerodecor.chroma.server;

/**
 * What a player is trying to do on a hub network, and the lowest {@link ChromaRole} allowed to do it.
 *
 * <ul>
 *   <li>{@link #VIEW}: see the network's blocks, zones and palette.</li>
 *   <li>{@link #PAINT}: change palette entries, colours and effects on already-linked blocks.</li>
 *   <li>{@link #LINK}: add or remove blocks (Chroma Linker), bind a linker to the hub.</li>
 *   <li>{@link #MANAGE}: zones, volume, auto-adopt.</li>
 *   <li>{@link #TRUST}: grant or revoke roles. Owner only.</li>
 * </ul>
 */
public enum ChromaAction {
    VIEW(ChromaRole.VIEWER),
    PAINT(ChromaRole.PAINTER),
    LINK(ChromaRole.ADMIN),
    MANAGE(ChromaRole.ADMIN),
    TRUST(ChromaRole.OWNER);

    private final ChromaRole minimum;

    ChromaAction(ChromaRole minimum) {
        this.minimum = minimum;
    }

    public ChromaRole minimum() {
        return minimum;
    }
}
