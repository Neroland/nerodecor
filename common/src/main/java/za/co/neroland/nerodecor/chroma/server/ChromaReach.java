package za.co.neroland.nerodecor.chroma.server;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * The "are you at the hub" rule for hub-screen edits sent from the client (zone, trust and scene management, colour
 * ops): the player must stand inside the hub's volume grown by {@value #VOLUME_MARGIN} blocks, or within
 * {@value #HUB_RADIUS} blocks of the hub itself, and pass {@link ChromaGuard} at the hub. NeroLink actions are remote
 * by design and checked by role only, so they do not come through here. Also answers whether a trust target is a
 * player the server knows. Nothing here logs.
 */
public final class ChromaReach {

    /** Blocks the hub volume is grown by on every side. */
    public static final int VOLUME_MARGIN = 8;
    /** Distance from the hub block that always counts as near. */
    public static final int HUB_RADIUS = 16;

    private ChromaReach() {
    }

    /** Pure rule: whether a player at {@code (x, y, z)} is near {@code network}. */
    public static boolean near(HubNetwork network, double x, double y, double z) {
        double dx = x - (network.anchorX() + 0.5);
        double dy = y - (network.anchorY() + 0.5);
        double dz = z - (network.anchorZ() + 0.5);
        if (dx * dx + dy * dy + dz * dz <= (double) HUB_RADIUS * HUB_RADIUS) {
            return true;
        }
        return within(Math.floor(x) - network.anchorX(), network.sizeX())
                && within(Math.floor(y) - network.anchorY(), network.sizeY())
                && within(Math.floor(z) - network.anchorZ(), network.sizeZ());
    }

    private static boolean within(double offset, int size) {
        return offset >= HubNetwork.minOffset(size) - VOLUME_MARGIN
                && offset <= HubNetwork.maxOffset(size) + VOLUME_MARGIN;
    }

    /** Reach only: {@link ChromaVerdict#OK} or {@link ChromaVerdict#TOO_FAR}. */
    public static ChromaVerdict reach(ServerPlayer player, HubNetwork network) {
        return network != null && near(network, player.getX(), player.getY(), player.getZ()) ? ChromaVerdict.OK
                : ChromaVerdict.TOO_FAR;
    }

    /**
     * For anything that changes the network from the screen: the role for {@code action} first (so a stranger is told
     * {@link ChromaVerdict#NO_PERMISSION} wherever they stand), then reach, then {@link ChromaGuard} at the hub block.
     */
    public static ChromaVerdict mayEditFromScreen(ServerPlayer player, ServerLevel level, HubNetwork network,
            ChromaAction action) {
        if (network == null) {
            return ChromaVerdict.NO_NETWORK;
        }
        ChromaVerdict role = ChromaValidator.checkAction(network, player.getUUID(), action);
        if (!role.isOk()) {
            return role;
        }
        ChromaVerdict near = reach(player, network);
        if (!near.isOk()) {
            return near;
        }
        BlockPos hub = new BlockPos(network.anchorX(), network.anchorY(), network.anchorZ());
        return ChromaGuard.mayEdit(player, level, hub, level.getBlockState(hub)) ? ChromaVerdict.OK
                : ChromaVerdict.PROTECTED;
    }

    /** Whether {@code player} is online or in the server's profile cache (trust may only name a real player). */
    public static boolean isKnownPlayer(MinecraftServer server, UUID player) {
        if (server == null || player == null) {
            return false;
        }
        if (server.getPlayerList().getPlayer(player) != null) {
            return true;
        }
        try {
            //? if >=1.21.9 {
            return server.services().nameToIdCache().get(player).isPresent();
            //?} else {
            /*net.minecraft.server.players.GameProfileCache cache = server.getProfileCache();
            return cache != null && cache.get(player).isPresent();
            *///?}
        } catch (RuntimeException unavailable) {
            return false;
        }
    }
}
