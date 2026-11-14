package za.co.neroland.nerodecor.client.capture;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.brigadier.tree.CommandNode;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;

import za.co.neroland.nerodecor.NeroDecorCommon;
import za.co.neroland.nerodecor.command.showroom.CameraPresets;

/**
 * One run of {@code /nerodecor capture}: walks the camera through the {@link CameraPresets} of the showroom and/or the
 * block grid (or stays put for {@code here}) and saves a screenshot at each, to
 * {@code screenshots/nerodecor/<yyyyMMdd-HHmmss>_<view>.png} in the game directory. File names carry the time and the
 * view id only, never a player name.
 *
 * <p>Per view: the HUD is hidden (F1), the field of view is set, the player is moved with the operator-only server
 * command {@code /nerodecor capture goto <view>} (which resolves the preset against the remembered showroom or grid
 * origin), the world gets a few seconds to load and settle, then the frame is grabbed with vanilla's
 * {@link Screenshot}. Operators also get spectator mode (no falling, no hand) and night for the run, so glows read;
 * afterwards the HUD, field of view, game mode, time and the original position and rotation are restored. Without
 * operator rights only {@code here} works, and it captures the world as it is.
 *
 * <p>The sequence runs on its own daemon thread that sleeps between steps and hands every game interaction to the
 * client thread, so the game keeps rendering while it waits. Client-only: loaded only from the client command.
 */
public final class CaptureSession {

    public static final String SHOWROOM = CameraPresets.SHOWROOM;
    public static final String GRID = CameraPresets.GRID;
    public static final String ALL = "all";
    public static final String HERE = "here";

    private static final long SETTLE_MS = 2500L;
    private static final long STILL_MS = 1000L;
    private static final long SAVE_MS = 700L;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT);

    private static final AtomicBoolean RUNNING = new AtomicBoolean();

    private final Minecraft mc;
    private final String target;
    private final int limit;
    private final boolean op;
    private final AtomicInteger saved = new AtomicInteger();

    private CaptureSession(Minecraft mc, String target, int limit, boolean op) {
        this.mc = mc;
        this.target = target;
        this.limit = limit;
        this.op = op;
    }

    /** Whether a capture is in progress. */
    public static boolean running() {
        return RUNNING.get();
    }

    /**
     * Starts a capture of {@code target} ({@link #SHOWROOM}, {@link #GRID}, {@link #ALL} or {@link #HERE}).
     * {@code count} limits the number of viewpoints (for {@code here}: the number of frames, a second apart); 0 means
     * every viewpoint (one frame for {@code here}). Call on the client thread. Returns 1 when a capture started.
     */
    public static int start(String target, int count) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) {
            return 0;
        }
        if (RUNNING.get()) {
            say(mc, "A capture is already running.");
            return 0;
        }
        boolean op = isOperator(mc);
        if (!HERE.equals(target) && !op) {
            say(mc, "Capturing the showroom or grid moves you to each viewpoint, which needs operator rights. "
                    + "/nerodecor capture here works without them.");
            return 0;
        }
        CaptureSession session = new CaptureSession(mc, target, Math.max(0, count), op);
        RUNNING.set(true);
        Thread thread = new Thread(session::run, "NeroDecor capture");
        thread.setDaemon(true);
        thread.start();
        return 1;
    }

    /**
     * Whether the server lets this player use the operator-only {@code capture goto} (the server only sends command
     * nodes the player may use). {@code time} must be there too, for the night switch.
     */
    private static boolean isOperator(Minecraft mc) {
        var root = mc.getConnection().getCommands().getRoot();
        CommandNode<?> nerodecor = root.getChild("nerodecor");
        CommandNode<?> capture = nerodecor == null ? null : nerodecor.getChild("capture");
        return capture != null && capture.getChild("goto") != null && root.getChild("time") != null;
    }

    // --- the run ----------------------------------------------------------------------------------------------------

    private record State(double x, double y, double z, float yaw, float pitch, boolean hudHidden, int fov,
                         GameType mode, long dayTime) {
    }

    private void run() {
        State before = null;
        try {
            before = this.call(this::snapshot);
            if (before == null) {
                return;
            }
            List<CameraPresets.Preset> views = new ArrayList<>();
            if (SHOWROOM.equals(this.target) || ALL.equals(this.target)) {
                views.addAll(CameraPresets.showroom());
            }
            if (GRID.equals(this.target) || ALL.equals(this.target)) {
                views.addAll(CameraPresets.grid());
            }
            if (this.limit > 0 && views.size() > this.limit) {
                views = views.subList(0, this.limit);
            }
            int frames = HERE.equals(this.target) ? Math.max(1, this.limit) : views.size();
            this.message("Capturing " + frames + " view(s)" + (this.op ? " at night" : " as the world is (night "
                    + "needs operator rights)") + "; the HUD comes back when it is done.");
            if (this.op) {
                long day = before.dayTime();
                this.command("time set " + (day - Math.floorMod(day, 24000L) + 18000L));
                if (!HERE.equals(this.target)) {
                    this.command("gamemode spectator");
                }
            }
            this.onClient(() -> setHudHidden(this.mc, true));
            String stamp = LocalDateTime.now().format(STAMP);
            if (HERE.equals(this.target)) {
                Thread.sleep(STILL_MS);
                for (int i = 0; i < frames; i++) {
                    this.grab(stamp + "_here" + (frames > 1 ? "_" + (i + 1) : ""));
                    Thread.sleep(STILL_MS);
                }
            } else {
                String skipped = null;
                for (int i = 0; i < views.size(); i++) {
                    CameraPresets.Preset view = views.get(i);
                    if (view.target().equals(skipped) || this.mc.player == null) {
                        continue;
                    }
                    double[] at = this.call(() -> new double[] {this.mc.player.getX(), this.mc.player.getY(),
                            this.mc.player.getZ()});
                    this.onClient(() -> this.mc.options.fov().set(view.fov()));
                    this.command("nerodecor capture goto " + view.id());
                    Thread.sleep(SETTLE_MS);
                    boolean moved = this.call(() -> this.mc.player != null
                            && this.mc.player.distanceToSqr(at[0], at[1], at[2]) > 1.0E-4);
                    if (!moved) {
                        skipped = view.target();
                        this.message("No " + view.target() + " is remembered in this dimension (build it with "
                                + "/nerodecor gallery" + (GRID.equals(view.target()) ? " grid" : "")
                                + " first); skipping its views.");
                        continue;
                    }
                    Thread.sleep(STILL_MS);
                    this.grab(stamp + "_" + view.id());
                    this.message((i + 1) + "/" + views.size() + " " + view.id());
                }
            }
            Thread.sleep(SAVE_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            NeroDecorCommon.LOGGER.warn("[NeroDecor] capture stopped: {}", e.toString());
            this.message("The capture stopped early: " + e.getClass().getSimpleName());
        } finally {
            try {
                if (before != null) {
                    this.restore(before);
                }
            } catch (Exception e) {
                NeroDecorCommon.LOGGER.warn("[NeroDecor] capture could not restore the view: {}", e.toString());
            }
            RUNNING.set(false);
        }
    }

    private State snapshot() {
        if (this.mc.player == null || this.mc.level == null || this.mc.gameMode == null) {
            return null;
        }
        //? if >=26.1 {
        long day = this.mc.level.getOverworldClockTime();
        //?} else {
        /*long day = this.mc.level.getDayTime();
        *///?}
        return new State(this.mc.player.getX(), this.mc.player.getY(), this.mc.player.getZ(),
                this.mc.player.getYRot(), this.mc.player.getXRot(), hudHidden(this.mc), this.mc.options.fov().get(),
                this.mc.gameMode.getPlayerMode(), day);
    }

    private void restore(State s) throws Exception {
        if (this.mc.player == null) {
            return;
        }
        this.onClient(() -> {
            setHudHidden(this.mc, s.hudHidden());
            this.mc.options.fov().set(s.fov());
        });
        if (this.op) {
            if (!HERE.equals(this.target)) {
                this.command(String.format(Locale.ROOT, "tp @s %.3f %.3f %.3f %.2f %.2f", s.x(), s.y(), s.z(),
                        s.yaw(), s.pitch()));
                this.command("gamemode " + s.mode().getName());
            }
            this.command("time set " + s.dayTime());
        }
        this.message("Capture done: " + this.saved.get() + " screenshot(s) in screenshots/nerodecor.");
    }

    // --- client-thread helpers --------------------------------------------------------------------------------------

    private void grab(String view) throws Exception {
        String name = "nerodecor/" + view.replaceAll("[^A-Za-z0-9_.-]", "_") + ".png";
        this.onClient(() -> {
            File dir = new File(new File(this.mc.gameDirectory, "screenshots"), "nerodecor");
            if (!dir.isDirectory() && !dir.mkdirs()) {
                return;
            }
            //? if >=26.2 {
            RenderTarget target = this.mc.gameRenderer.mainRenderTarget();
            //?} else {
            /*RenderTarget target = this.mc.getMainRenderTarget();
            *///?}
            //? if >=1.21.6 {
            Screenshot.grab(this.mc.gameDirectory, name, target, 1, message -> this.saved.incrementAndGet());
            //?} else {
            /*Screenshot.grab(this.mc.gameDirectory, name, target, message -> this.saved.incrementAndGet());
            *///?}
        });
    }

    private void command(String command) throws Exception {
        this.onClient(() -> {
            if (this.mc.getConnection() != null) {
                this.mc.getConnection().sendCommand(command);
            }
        });
        Thread.sleep(150L);
    }

    private void message(String text) {
        this.mc.execute(() -> say(this.mc, text));
    }

    private static void say(Minecraft mc, String text) {
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal("[NeroDecor] " + text));
        }
    }

    private void onClient(Runnable task) throws Exception {
        this.call(() -> {
            task.run();
            return Boolean.TRUE;
        });
    }

    private <T> T call(Supplier<T> task) throws Exception {
        CompletableFuture<T> future = this.mc.submit(task);
        return future.get();
    }

    static boolean hudHidden(Minecraft mc) {
        //? if >=26.2 {
        return mc.gui.hud.isHidden();
        //?} else {
        /*return mc.options.hideGui;
        *///?}
    }

    static void setHudHidden(Minecraft mc, boolean hidden) {
        //? if >=26.2 {
        if (mc.gui.hud.isHidden() != hidden) {
            mc.gui.hud.toggle();
        }
        //?} else {
        /*mc.options.hideGui = hidden;
        *///?}
    }
}
