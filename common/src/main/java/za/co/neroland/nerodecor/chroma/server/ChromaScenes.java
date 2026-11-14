package za.co.neroland.nerodecor.chroma.server;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.nerodecor.chroma.ChromaResult;
import za.co.neroland.nerodecor.chroma.FlashClamp;
import za.co.neroland.nerodecor.chroma.Scene;
import za.co.neroland.nerodecor.chroma.SceneCodec;
import za.co.neroland.nerodecor.chroma.SceneSlots;

/**
 * Server side of Chroma scenes (Stage 4): saving scenes into a hub's slots, activating a slot, configuring the
 * trigger, and evaluating redstone / daylight triggers each tick. Every player action passes the hub role check
 * ({@link ChromaAction#PAINT} for scenes, {@link ChromaAction#MANAGE} for triggers) and the photosensitivity
 * {@link FlashClamp} (more than 3 Hz over a large area is refused, both when saving and before a slot plays).
 *
 * <p>Effects run on the client from {@code (gameTime - startTick, position, seed, params)}; the server only stores the
 * slots and the playing slot + start tick, so a scene change is one small packet ({@code ChromaSceneSync}). Light
 * levels never follow animation. Logging: none.
 */
public final class ChromaScenes {

    /** Triggers are evaluated every this many ticks. */
    public static final int TRIGGER_INTERVAL_TICKS = 2;

    private ChromaScenes() {
    }

    /** The per-hub animation seed (sparkle placement): the network id, so it never carries player data. */
    public static long seed(HubNetwork network) {
        return network.id();
    }

    // --- pure rules -------------------------------------------------------------------------------------------------

    /** Whether {@code actor} may store {@code scene} in {@code slot} of {@code network}. */
    public static ChromaVerdict checkSave(HubNetwork network, UUID actor, int slot, Scene scene) {
        ChromaVerdict role = ChromaValidator.checkAction(network, actor, ChromaAction.PAINT);
        if (!role.isOk()) {
            return role;
        }
        return checkScene(network, slot, scene);
    }

    /** Slot, size and flash-clamp checks for a scene going into {@code network} (no role check). */
    public static ChromaVerdict checkScene(HubNetwork network, int slot, Scene scene) {
        if (network == null) {
            return ChromaVerdict.NO_NETWORK;
        }
        if (!SceneSlots.isSlot(slot)) {
            return ChromaVerdict.BAD_SLOT;
        }
        if (scene == null) {
            return ChromaVerdict.BAD_SCENE;
        }
        ChromaResult<byte[]> bytes = SceneCodec.encode(scene);
        if (!bytes.isOk() || bytes.value().length > SceneCodec.MAX_ENCODED_BYTES) {
            return ChromaVerdict.BAD_SCENE;
        }
        return FlashClamp.check(scene, seed(network)).passed() ? ChromaVerdict.OK : ChromaVerdict.FLASH_REJECTED;
    }

    /** Runs the flash clamp for a stored slot once (restored scenes) and remembers a pass. */
    static boolean clampOk(HubNetwork network, int slot) {
        SceneSlots slots = network.scenes();
        Scene scene = slots.scene(slot);
        if (scene == null) {
            return false;
        }
        if (slots.clampPassed(slot)) {
            return true;
        }
        if (FlashClamp.check(scene, seed(network)).passed()) {
            slots.markClampPassed(slot);
            return true;
        }
        return false;
    }

    // --- store operations (pure Java; GameTests and unit tests call these) ------------------------------------------

    /**
     * Stores a scene ({@link #checkSave}); with {@code activate} it also starts playing at {@code gameTime} (only when
     * the hub's trigger is manual: a redstone or daylight trigger keeps choosing the slot).
     */
    public static ChromaVerdict save(ChromaNetworkStore store, long id, UUID actor, int slot, Scene scene,
            boolean activate, long gameTime) {
        HubNetwork network = store.get(id);
        if (network == null) {
            return ChromaVerdict.NO_NETWORK;
        }
        ChromaVerdict role = ChromaValidator.checkAction(network, actor, ChromaAction.PAINT);
        if (!role.isOk()) {
            return role;
        }
        return store(store, network, slot, scene, activate, gameTime);
    }

    /**
     * {@link #save} for a caller that has already checked the role (the hub editor): slot, size and flash clamp are
     * still enforced here.
     */
    public static ChromaVerdict store(ChromaNetworkStore store, HubNetwork network, int slot, Scene scene,
            boolean activate, long gameTime) {
        ChromaVerdict verdict = checkScene(network, slot, scene);
        if (!verdict.isOk()) {
            return verdict;
        }
        long id = network.id();
        SceneSlots slots = network.scenes();
        boolean replacingActive = slots.active() == slot;
        slots.set(slot, scene, true);
        boolean switched = false;
        if (replacingActive) {
            // The playing slot got a new scene: restart it so every client starts the new program in step.
            slots.activate(SceneSlots.STATIC, gameTime);
            slots.activate(slot, gameTime);
            switched = true;
        } else if (activate && slots.trigger() == SceneSlots.Trigger.MANUAL) {
            switched = slots.activate(slot, gameTime);
        }
        store.scenesChanged(id);
        if (switched) {
            ChromaHooks.sceneChanged(network, slots.active(), false);
        }
        return ChromaVerdict.OK;
    }

    /** Renames a stored scene (role already checked by the caller); the name is sanitised. */
    public static ChromaVerdict rename(ChromaNetworkStore store, HubNetwork network, int slot, String name) {
        if (network == null) {
            return ChromaVerdict.NO_NETWORK;
        }
        Scene scene = network.scenes().scene(slot);
        if (scene == null) {
            return ChromaVerdict.BAD_SLOT;
        }
        String clean = Scene.sanitiseName(name);
        if (clean.isEmpty()) {
            return ChromaVerdict.BAD_NAME;
        }
        boolean passed = network.scenes().clampPassed(slot);
        boolean playing = network.scenes().active() == slot;
        long start = network.scenes().startTick();
        network.scenes().set(slot, new Scene(clean, scene.zones()), passed);
        if (playing) {
            network.scenes().restoreActive(slot, start); // a rename does not restart the scene
        }
        store.scenesChanged(network.id());
        return ChromaVerdict.OK;
    }

    /** Clears a slot (stops it if it was playing). */
    public static ChromaVerdict clear(ChromaNetworkStore store, long id, UUID actor, int slot) {
        HubNetwork network = store.get(id);
        ChromaVerdict role = ChromaValidator.checkAction(network, actor, ChromaAction.PAINT);
        if (!role.isOk()) {
            return role;
        }
        if (!SceneSlots.isSlot(slot)) {
            return ChromaVerdict.BAD_SLOT;
        }
        boolean wasActive = network.scenes().active() == slot;
        network.scenes().set(slot, null, false);
        store.scenesChanged(id);
        if (wasActive) {
            ChromaHooks.sceneChanged(network, SceneSlots.STATIC, false);
        }
        return ChromaVerdict.OK;
    }

    /** Plays a stored slot from {@code gameTime} ({@link SceneSlots#STATIC} stops); the trigger becomes manual. */
    public static ChromaVerdict activate(ChromaNetworkStore store, long id, UUID actor, int slot, long gameTime) {
        HubNetwork network = store.get(id);
        ChromaVerdict role = ChromaValidator.checkAction(network, actor, ChromaAction.PAINT);
        if (!role.isOk()) {
            return role;
        }
        return play(store, network, slot, gameTime);
    }

    /** {@link #activate} for a caller that has already checked the role; the flash clamp still applies. */
    public static ChromaVerdict play(ChromaNetworkStore store, HubNetwork network, int slot, long gameTime) {
        if (network == null) {
            return ChromaVerdict.NO_NETWORK;
        }
        long id = network.id();
        if (slot != SceneSlots.STATIC) {
            if (network.scenes().scene(slot) == null) {
                return ChromaVerdict.BAD_SLOT;
            }
            if (!clampOk(network, slot)) {
                return ChromaVerdict.FLASH_REJECTED;
            }
        }
        network.scenes().setTrigger(SceneSlots.Trigger.MANUAL);
        boolean switched = network.scenes().activate(slot, gameTime);
        store.scenesChanged(id);
        if (switched) {
            ChromaHooks.sceneChanged(network, network.scenes().active(), false);
        }
        return ChromaVerdict.OK;
    }

    /**
     * Sets the trigger and, when given (non-null), its redstone map (16 entries) and daylight map (4 entries).
     * Admin and owner only.
     */
    public static ChromaVerdict setTrigger(ChromaNetworkStore store, long id, UUID actor, SceneSlots.Trigger trigger,
            int[] redstone, int[] daylight) {
        HubNetwork network = store.get(id);
        ChromaVerdict role = ChromaValidator.checkAction(network, actor, ChromaAction.MANAGE);
        if (!role.isOk()) {
            return role;
        }
        if ((redstone != null && redstone.length != SceneSlots.REDSTONE_LEVELS)
                || (daylight != null && daylight.length != SceneSlots.DaylightBand.values().length)) {
            return ChromaVerdict.BAD_OP;
        }
        SceneSlots slots = network.scenes();
        slots.setTrigger(trigger);
        if (redstone != null) {
            slots.setRedstoneMap(redstone);
        }
        if (daylight != null) {
            slots.setDaylightMap(daylight);
        }
        store.scenesChanged(id);
        return ChromaVerdict.OK;
    }

    /**
     * Applies the hub's trigger for the given inputs. A slot whose scene fails the flash clamp never plays.
     *
     * @return whether the playing slot changed
     */
    public static boolean evaluate(ChromaNetworkStore store, HubNetwork network, int redstone, long dayTime,
            long gameTime) {
        SceneSlots slots = network.scenes();
        if (slots.trigger() == SceneSlots.Trigger.MANUAL) {
            return false;
        }
        int want = slots.desiredSlot(redstone, dayTime);
        if (want != SceneSlots.STATIC && !clampOk(network, want)) {
            want = SceneSlots.STATIC;
        }
        if (want == slots.active() || !slots.activate(want, gameTime)) {
            return false;
        }
        store.scenesChanged(network.id());
        ChromaHooks.triggerFired(network, slots.trigger(), slots.active());
        ChromaHooks.sceneChanged(network, slots.active(), true);
        return true;
    }

    // --- game side --------------------------------------------------------------------------------------------------

    /** {@link #save} for a player in a level (master switch, rate limiter). */
    public static ChromaVerdict save(ServerLevel level, ServerPlayer player, long id, int slot, Scene scene,
            boolean activate) {
        ChromaVerdict pre = precheck(player);
        if (!pre.isOk()) {
            return pre;
        }
        return save(ChromaNetworks.store(level), id, player.getUUID(), slot, scene, activate, level.getGameTime());
    }

    /** {@link #activate} for a player in a level. */
    public static ChromaVerdict activate(ServerLevel level, ServerPlayer player, long id, int slot) {
        ChromaVerdict pre = precheck(player);
        if (!pre.isOk()) {
            return pre;
        }
        return activate(ChromaNetworks.store(level), id, player.getUUID(), slot, level.getGameTime());
    }

    private static ChromaVerdict precheck(ServerPlayer player) {
        if (!ChromaConfig.ENABLED.get()) {
            return ChromaVerdict.DISABLED;
        }
        return ChromaNetworks.limiter().tryAcquire(player.getUUID(), 1, System.nanoTime());
    }

    /** The level's day time (0-23999 within a day) on every supported version. */
    public static long dayTime(ServerLevel level) {
        //? if >=26.1 {
        return level.getDefaultClockTime();
        //?} else {
        /*return level.getDayTime();
        *///?}
    }

    /** The redstone strength (0-15) the hub block receives. */
    public static int redstone(ServerLevel level, HubNetwork network) {
        BlockPos anchor = new BlockPos(network.anchorX(), network.anchorY(), network.anchorZ());
        return level.isLoaded(anchor) ? level.getBestNeighborSignal(anchor) : 0;
    }

    /** Server tick for one level: redstone and daylight triggers of placed hubs whose chunk is loaded. */
    public static void tick(ServerLevel level, boolean force) {
        long gameTime = level.getGameTime();
        if (!force && gameTime % TRIGGER_INTERVAL_TICKS != 0) {
            return;
        }
        ChromaNetworkStore store = ChromaNetworks.store(level);
        long day = -1L;
        for (HubNetwork network : store.all()) {
            if (!network.active() || network.scenes().trigger() == SceneSlots.Trigger.MANUAL) {
                continue;
            }
            BlockPos anchor = new BlockPos(network.anchorX(), network.anchorY(), network.anchorZ());
            if (!level.isLoaded(anchor)) {
                continue;
            }
            if (day < 0L) {
                day = dayTime(level);
            }
            evaluate(store, network, level.getBestNeighborSignal(anchor), day, gameTime);
        }
    }
}
