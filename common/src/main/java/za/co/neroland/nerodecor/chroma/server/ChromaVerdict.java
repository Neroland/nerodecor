package za.co.neroland.nerodecor.chroma.server;

import java.util.Locale;

/**
 * The outcome of a server-side Chroma check. {@link #OK} and {@link #ALREADY_LINKED} succeed; everything else is a
 * refusal. Verdicts carry no data about the player or the position, so they are safe to log or count.
 */
public enum ChromaVerdict {
    OK,
    ALREADY_LINKED,
    DISABLED,
    INACTIVE,
    NO_NETWORK,
    NO_PERMISSION,
    PROTECTED,
    NOT_CAPABLE,
    OUT_OF_VOLUME,
    VOLUME_TOO_LARGE,
    CLAIMED_BY_OTHER_HUB,
    BLOCK_CAP,
    HUB_CAP,
    ZONE_CAP,
    BAD_ZONE,
    BAD_NAME,
    BAD_CHANNEL,
    NOT_LINKED,
    SELECTION_TOO_LARGE,
    RATE_LIMITED,
    /** An edit batch referenced a palette entry that does not exist (Stage 3 sync ops). */
    BAD_OP;

    public boolean isOk() {
        return this == OK || this == ALREADY_LINKED;
    }

    /** Translation key suffix ({@code message.nerodecor.chroma.<id>}). */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
