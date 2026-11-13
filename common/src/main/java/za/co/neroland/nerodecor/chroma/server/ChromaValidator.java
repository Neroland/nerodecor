package za.co.neroland.nerodecor.chroma.server;

import java.util.Optional;
import java.util.UUID;

import za.co.neroland.nerodecor.chroma.RelativePos;
import za.co.neroland.nerodecor.chroma.ZoneAssignment;

/**
 * Every server-side Chroma rule that does not need the world, as pure functions: volume bounds, caps, ownership and
 * roles, overlap (a block belongs to exactly one hub) and zone names. The game layer adds only what needs the
 * world: whether the block is chroma-capable and whether the loader's protection allows the edit.
 */
public final class ChromaValidator {

    /** Longest zone name, in code points, after sanitising. */
    public static final int MAX_ZONE_NAME = 32;

    private ChromaValidator() {
    }

    /** Whether {@code player} may perform {@code action} on {@code network}. */
    public static ChromaVerdict checkAction(HubNetwork network, UUID player, ChromaAction action) {
        if (network == null) {
            return ChromaVerdict.NO_NETWORK;
        }
        return network.roleOf(player).atLeast(action.minimum()) ? ChromaVerdict.OK : ChromaVerdict.NO_PERMISSION;
    }

    /** A hub volume request: every axis 1 .. {@link ChromaLimits#HARD_VOLUME_CAP}. */
    public static ChromaVerdict checkVolumeSize(int sizeX, int sizeY, int sizeZ) {
        boolean ok = sizeX >= 1 && sizeY >= 1 && sizeZ >= 1 && sizeX <= ChromaLimits.HARD_VOLUME_CAP
                && sizeY <= ChromaLimits.HARD_VOLUME_CAP && sizeZ <= ChromaLimits.HARD_VOLUME_CAP;
        return ok ? ChromaVerdict.OK : ChromaVerdict.VOLUME_TOO_LARGE;
    }

    /** Whether a player who already owns {@code owned} networks may start another. */
    public static ChromaVerdict checkNewHub(int owned, ChromaLimits limits) {
        if (!limits.enabled()) {
            return ChromaVerdict.DISABLED;
        }
        return owned < limits.maxHubsPerPlayer() ? ChromaVerdict.OK : ChromaVerdict.HUB_CAP;
    }

    /**
     * Linking the block at an absolute position into {@code network}.
     *
     * @param capable   whether the block there is in {@code #nerodecor:chroma_capable}
     * @param claimedBy id of the active network that already controls the block, or 0
     */
    public static ChromaVerdict checkLink(HubNetwork network, UUID player, int x, int y, int z, boolean capable,
            long claimedBy, ChromaLimits limits) {
        if (!limits.enabled()) {
            return ChromaVerdict.DISABLED;
        }
        if (network == null) {
            return ChromaVerdict.NO_NETWORK;
        }
        if (!network.active()) {
            return ChromaVerdict.INACTIVE;
        }
        ChromaVerdict role = checkAction(network, player, ChromaAction.LINK);
        if (!role.isOk()) {
            return role;
        }
        if (!capable) {
            return ChromaVerdict.NOT_CAPABLE;
        }
        if (!network.contains(x, y, z)) {
            return ChromaVerdict.OUT_OF_VOLUME;
        }
        if (claimedBy != 0L && claimedBy != network.id()) {
            return ChromaVerdict.CLAIMED_BY_OTHER_HUB;
        }
        int dx = x - network.anchorX();
        int dy = y - network.anchorY();
        int dz = z - network.anchorZ();
        if (!RelativePos.inRange(dx, dy, dz)) {
            return ChromaVerdict.OUT_OF_VOLUME;
        }
        if (network.isLinkedOffset(RelativePos.pack(dx, dy, dz))) {
            return ChromaVerdict.ALREADY_LINKED;
        }
        if (network.size() >= limits.maxBlocksPerHub()) {
            return ChromaVerdict.BLOCK_CAP;
        }
        return ChromaVerdict.OK;
    }

    /** Unlinking a block: the player needs LINK, and the block must belong to this network. */
    public static ChromaVerdict checkUnlink(HubNetwork network, UUID player, int x, int y, int z) {
        if (network == null) {
            return ChromaVerdict.NO_NETWORK;
        }
        ChromaVerdict role = checkAction(network, player, ChromaAction.LINK);
        if (!role.isOk()) {
            return role;
        }
        int dx = x - network.anchorX();
        int dy = y - network.anchorY();
        int dz = z - network.anchorZ();
        if (!RelativePos.inRange(dx, dy, dz) || !network.isLinkedOffset(RelativePos.pack(dx, dy, dz))) {
            return ChromaVerdict.NOT_LINKED;
        }
        return ChromaVerdict.OK;
    }

    /** Adding a zone: MANAGE role, zone cap, a valid name and channel. */
    public static ChromaVerdict checkAddZone(HubNetwork network, UUID player, String rawName, int channel,
            ChromaLimits limits) {
        ChromaVerdict role = checkAction(network, player, ChromaAction.MANAGE);
        if (!role.isOk()) {
            return role;
        }
        if (network.zones().size() >= Math.min(limits.maxZones(), ZoneAssignment.MAX_ZONES)) {
            return ChromaVerdict.ZONE_CAP;
        }
        if (channel < 0 || channel >= ZoneAssignment.MAX_CHANNELS) {
            return ChromaVerdict.BAD_CHANNEL;
        }
        return sanitiseZoneName(rawName).isPresent() ? ChromaVerdict.OK : ChromaVerdict.BAD_NAME;
    }

    /** Granting {@code role} to {@code target}: owner only, never to the owner, grantable roles or NONE. */
    public static ChromaVerdict checkTrust(HubNetwork network, UUID actor, UUID target, ChromaRole role) {
        ChromaVerdict allowed = checkAction(network, actor, ChromaAction.TRUST);
        if (!allowed.isOk()) {
            return allowed;
        }
        if (target == null || target.equals(network.owner())) {
            return ChromaVerdict.NO_PERMISSION;
        }
        return role == ChromaRole.NONE || role.isGrantable() ? ChromaVerdict.OK : ChromaVerdict.NO_PERMISSION;
    }

    /** A selection (box or flood) that would visit {@code blocks} positions. */
    public static ChromaVerdict checkSelection(long blocks, ChromaLimits limits) {
        return blocks <= (long) ChromaLimits.HARD_VOLUME_CAP * ChromaLimits.HARD_VOLUME_CAP
                * ChromaLimits.HARD_VOLUME_CAP ? ChromaVerdict.OK : ChromaVerdict.SELECTION_TOO_LARGE;
    }

    /**
     * A zone name made safe to store and show: formatting codes ({@code §x}) and control/format characters removed,
     * whitespace runs collapsed to one space, trimmed, cut to {@value #MAX_ZONE_NAME} code points. Empty after that
     * means rejected.
     */
    public static Optional<String> sanitiseZoneName(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        StringBuilder out = new StringBuilder();
        boolean pendingSpace = false;
        int count = 0;
        int[] cps = raw.codePoints().toArray();
        for (int i = 0; i < cps.length && count < MAX_ZONE_NAME; i++) {
            int cp = cps[i];
            if (cp == 0x00A7) {
                i++; // drop the formatting code and its argument
                continue;
            }
            if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) {
                pendingSpace = out.length() > 0;
                continue;
            }
            int type = Character.getType(cp);
            if (Character.isISOControl(cp) || type == Character.FORMAT || type == Character.PRIVATE_USE
                    || type == Character.SURROGATE || type == Character.UNASSIGNED) {
                continue;
            }
            if (pendingSpace) {
                if (count + 1 >= MAX_ZONE_NAME) {
                    break;
                }
                out.append(' ');
                count++;
                pendingSpace = false;
            }
            out.appendCodePoint(cp);
            count++;
        }
        String name = out.toString().trim();
        return name.isEmpty() ? Optional.empty() : Optional.of(name);
    }
}
