package za.co.neroland.nerodecor.chroma.server;

import java.util.Locale;
import java.util.Optional;

/**
 * A player's standing on one Chroma hub network, lowest to highest. {@link #OWNER} is implicit (the network's
 * owner UUID) and is never stored in the trusted list; {@link #NONE} is "not trusted".
 */
public enum ChromaRole {
    NONE,
    VIEWER,
    PAINTER,
    ADMIN,
    OWNER;

    /** True when this role is {@code other} or higher. */
    public boolean atLeast(ChromaRole other) {
        return this.ordinal() >= other.ordinal();
    }

    /** Whether this role may be granted through the trusted list (viewer, painter or admin). */
    public boolean isGrantable() {
        return this == VIEWER || this == PAINTER || this == ADMIN;
    }

    /** Stable lower-case id used in saved data. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The grantable role with this id, if any. */
    public static Optional<ChromaRole> grantableById(String id) {
        if (id == null) {
            return Optional.empty();
        }
        for (ChromaRole role : values()) {
            if (role.isGrantable() && role.id().equals(id)) {
                return Optional.of(role);
            }
        }
        return Optional.empty();
    }
}
