package za.co.neroland.nerodecor.progression;

import java.util.List;
import java.util.UUID;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.progression.ProgressionGates;

import za.co.neroland.nerodecor.NeroDecorCommon;

/**
 * The personal milestones NeroDecor records as <b>player-scope Core gates</b>
 * ({@code data/nerodecor/neroland_gates/*.json}). Other mods (trophies, quests) read them; nothing here is a lock.
 *
 * <p>Opening an already-open gate is a no-op in Core, so every call may be made on every occurrence.
 *
 * <p><b>Privacy (POPIA/GDPR):</b> only the player's UUID goes into {@link ProgressionGates#open}, whose store Core
 * documents and erases. No coordinates, network ids, scene names or player names are logged or kept here.
 */
public final class DecorMilestones {

    /** The player saved a scene on a Chroma hub for the first time. */
    public static final Identifier SCENE_AUTHORED = gate("scene_authored");
    /** A Chroma network the player owns reached {@link #RUNG_SMALL} linked blocks. */
    public static final Identifier HUB_LINKED_16 = gate("hub_linked/16");
    /** A Chroma network the player owns reached {@link #RUNG_LARGE} linked blocks. */
    public static final Identifier HUB_LINKED_64 = gate("hub_linked/64");

    /** Linked-block count for {@link #HUB_LINKED_16}. */
    public static final int RUNG_SMALL = 16;
    /** Linked-block count for {@link #HUB_LINKED_64}. */
    public static final int RUNG_LARGE = 64;

    /** Every milestone gate this jar ships. */
    public static final List<Identifier> ALL = List.of(SCENE_AUTHORED, HUB_LINKED_16, HUB_LINKED_64);

    private DecorMilestones() {
    }

    private static Identifier gate(String path) {
        return Identifier.fromNamespaceAndPath(NeroDecorCommon.MOD_ID, path);
    }

    /** Whether a network growing from {@code before} to {@code after} linked blocks crosses {@code rung}. */
    public static boolean crosses(int before, int after, int rung) {
        return before < rung && after >= rung;
    }

    /** The gates a network growing from {@code before} to {@code after} linked blocks opens for its owner. */
    public static List<Identifier> forGrowth(int before, int after) {
        if (crosses(before, after, RUNG_LARGE)) {
            return crosses(before, after, RUNG_SMALL) ? List.of(HUB_LINKED_16, HUB_LINKED_64) : List.of(HUB_LINKED_64);
        }
        return crosses(before, after, RUNG_SMALL) ? List.of(HUB_LINKED_16) : List.of();
    }

    /** A scene was saved on a hub by {@code actor}. */
    public static void sceneAuthored(@Nullable ServerPlayer actor) {
        if (actor != null) {
            ProgressionGates.open(actor, SCENE_AUTHORED);
        }
    }

    /** A network owned by {@code owner} grew from {@code before} to {@code after} linked blocks. */
    public static void networkGrew(@Nullable MinecraftServer server, @Nullable UUID owner, int before, int after) {
        if (server == null || owner == null) {
            return;
        }
        for (Identifier gate : forGrowth(before, after)) {
            ProgressionGates.open(server, owner, gate);
        }
    }
}
