package za.co.neroland.nerodecor.chroma.server;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import za.co.neroland.nerodecor.chroma.SceneSlots;

/**
 * Observer seam for things outside the Chroma core that want to hear about a hub's scene changing, a trigger firing or
 * a hub being broken (the NeroLink module's live events). Listeners run on the server thread, synchronously, after
 * the change has been applied; one that throws is skipped and never breaks the edit that called it. Nothing here
 * carries a position or a player: listeners get the {@link HubNetwork} and read what they are allowed to.
 */
public final class ChromaHooks {

    /** What a listener can hear. Every method defaults to a no-op. */
    public interface Listener {

        /** The hub's playing scene changed ({@link SceneSlots#STATIC} = static colours). */
        default void sceneChanged(HubNetwork network, int slot, boolean byTrigger) {
        }

        /** A redstone or daylight trigger switched the hub to {@code slot}. */
        default void triggerFired(HubNetwork network, SceneSlots.Trigger trigger, int slot) {
        }

        /** The hub block was removed; its network stopped controlling blocks. */
        default void hubBroken(HubNetwork network) {
        }
    }

    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();

    private ChromaHooks() {
    }

    public static void register(Listener listener) {
        if (listener != null && !LISTENERS.contains(listener)) {
            LISTENERS.add(listener);
        }
    }

    public static void unregister(Listener listener) {
        LISTENERS.remove(listener);
    }

    static void sceneChanged(HubNetwork network, int slot, boolean byTrigger) {
        if (network == null) {
            return;
        }
        for (Listener listener : LISTENERS) {
            try {
                listener.sceneChanged(network, slot, byTrigger);
            } catch (RuntimeException ignored) {
                // An observer must never break the edit that notified it.
            }
        }
    }

    static void triggerFired(HubNetwork network, SceneSlots.Trigger trigger, int slot) {
        if (network == null) {
            return;
        }
        for (Listener listener : LISTENERS) {
            try {
                listener.triggerFired(network, trigger, slot);
            } catch (RuntimeException ignored) {
                // see sceneChanged
            }
        }
    }

    static void hubBroken(HubNetwork network) {
        if (network == null) {
            return;
        }
        for (Listener listener : LISTENERS) {
            try {
                listener.hubBroken(network);
            } catch (RuntimeException ignored) {
                // see sceneChanged
            }
        }
    }
}
