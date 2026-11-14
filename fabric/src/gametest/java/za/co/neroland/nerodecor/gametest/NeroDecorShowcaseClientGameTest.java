package za.co.neroland.nerodecor.gametest;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import za.co.neroland.nerodecor.chroma.ChromaColor;
import za.co.neroland.nerodecor.chroma.EffectParams;
import za.co.neroland.nerodecor.chroma.EffectProgram;
import za.co.neroland.nerodecor.chroma.EffectType;
import za.co.neroland.nerodecor.chroma.Scene;
import za.co.neroland.nerodecor.chroma.ZoneAssignment;
import za.co.neroland.nerodecor.chroma.editor.SceneDraft;
import za.co.neroland.nerodecor.chroma.net.ChromaHubNet;
import za.co.neroland.nerodecor.chroma.server.ChromaNetworkStore;
import za.co.neroland.nerodecor.chroma.server.ChromaNetworks;
import za.co.neroland.nerodecor.chroma.server.ChromaScenes;
import za.co.neroland.nerodecor.chroma.server.ChromaVerdict;
import za.co.neroland.nerodecor.chroma.server.HubNetwork;
import za.co.neroland.nerodecor.client.capture.CaptureSession;
import za.co.neroland.nerodecor.client.screen.chroma.ChromaHubScreen;
import za.co.neroland.nerodecor.command.showroom.BlockGrid;
import za.co.neroland.nerodecor.command.showroom.CameraPresets;
import za.co.neroland.nerodecor.command.showroom.Showroom;
import za.co.neroland.nerodecor.command.showroom.ShowroomScenes;
import za.co.neroland.nerodecor.content.block.ChromaHubBlockEntity;
import za.co.neroland.nerodecor.content.block.bulkhead.AnimatedDoorBlock;
import za.co.neroland.nerodecor.registry.ChromaContent;

/**
 * Showcase screenshots for reviewers. Builds, in a superflat creative world: a closed room lit by Chroma cubes linked
 * to a hub playing a scene (entered through an open sliding door), a 3x2 wall screen and the holo projector / table /
 * seven world globes, a 2x2 wall screen; then runs {@code /nerodecor gallery grid} and {@code /nerodecor gallery} and
 * shoots the block grid and the showroom from the {@link CameraPresets} viewpoints (the same ones
 * {@code /nerodecor capture} uses, with the same file names), runs a short {@code /nerodecor capture grid 2} through
 * {@link CaptureSession}, then opens the Chroma Hub screen and screenshots each tab and the Scenes composer. World shots hide the HUD and are taken in spectator mode (no hand). Every shot is also copied to {@code tools/collections/.preview/screens/} (gitignored). Test-only: lives in the {@code gametest} source set and never ships in the mod jar.
 *
 * <p>Every shot is taken at midnight with the daylight cycle stopped, and the block grid stands on a dark
 * deepslate/blackstone floor, so glow layers and hub colours read against the background.
 *
 * <p>Screenshots land in {@code <run dir>/screenshots}; {@code showcase-report.txt} beside them lists each shot and
 * any step that failed. Privacy: the report records block ids, verdicts and counts only, never a player name or UUID.
 */
@SuppressWarnings("UnstableApiUsage")
public class NeroDecorShowcaseClientGameTest implements FabricClientGameTest {

    private static final Logger LOG = LoggerFactory.getLogger("NeroDecorShowcase");
    private static final String NS = "nerodecor";
    private static final String[] TABS = {"ZONES", "PALETTE", "SCENES", "ACCESS", "VIEW"};

    private final List<String> report = new ArrayList<>();
    private int shot;
    /** Feet position of the player at spawn; everything is built relative to it. */
    private BlockPos base = BlockPos.ZERO;
    private long hubNetwork;

    @Override
    public void runTest(ClientGameTestContext ctx) {
        TestInput in = ctx.getInput();
        in.resizeWindow(1600, 900);
        try (TestSingleplayerContext sp = ctx.worldBuilder().adjustSettings(s -> {
            s.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
            s.setAllowCommands(true);
        }).create()) {
            sp.getConnection().waitForChunksRender();
            TestServerContext server = sp.getServer();
            this.base = server.computeOnServer(s -> player(s).blockPosition());
            this.log("base", "spawn feet y=" + this.base.getY());
            // World shots: no HUD, and spectator mode so no first-person hand or held item is drawn.
            server.runCommand("clear @a");
            // Build while still in creative: a spectator may not link the Chroma room's blocks (PROTECTED).
            this.step("build", () -> server.runOnServer(this::build));
            server.runCommand("gamemode spectator @a");
            ctx.runOnClient(mc -> {
                setHudHidden(mc, true);
                mc.player.getAbilities().flying = true;
            });
            ctx.waitTicks(60); // scene sync (twice a second), light schedule, door swing
            sp.getConnection().waitForChunksRender();

            int y = this.base.getY();
            this.view(ctx, sp, server, "chroma_room_inside", this.base.getX() - 12.5, y + 1, this.base.getZ() + 9.5,
                    124.0F, 25.0F);
            this.view(ctx, sp, server, "chroma_room_door", this.base.getX() - 16.5, y + 1, this.base.getZ() - 4,
                    0.0F, 10.0F);
            this.view(ctx, sp, server, "screens_and_holo", this.base.getX() - 2.5, y + 2, this.base.getZ() - 1,
                    180.0F, 15.0F);
            this.view(ctx, sp, server, "globe_closeup", this.base.getX() + 2.5, y + 1, this.base.getZ() - 6.5,
                    180.0F, 35.0F);
            this.view(ctx, sp, server, "globes_seven_worlds", this.base.getX() + 5.5, y + 1.5,
                    this.base.getZ() - 6.0, 180.0F, 30.0F);
            this.view(ctx, sp, server, "wall_screen_3x2", this.base.getX() - 6.5, y, this.base.getZ() - 6.0,
                    180.0F, 12.0F);
            this.view(ctx, sp, server, "wall_screen_2x2", this.base.getX() - 13.0, y, this.base.getZ() - 7.0,
                    180.0F, 12.0F);
            this.grid(ctx, sp, server);
            this.showroom(ctx, sp, server);
            this.capture(ctx, server);
            server.runCommand("gamemode creative @a");
            ctx.waitTicks(5);
            ctx.runOnClient(mc -> setHudHidden(mc, false)); // HUD back for the screens
            this.hubScreen(ctx, server);
        } finally {
            this.writeReport();
        }
    }

    // --- building -------------------------------------------------------------------------------------------------

    private void build(MinecraftServer server) {
        ServerLevel level = server.overworld();
        ServerPlayer owner = player(server);
        int y = this.base.getY();
        // Night, held: glows read. Both gamerule spellings are tried (renamed in 1.21.11); the wrong one is a no-op.
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "gamerule advance_time false");
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                "gamerule doDaylightCycle false");
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set midnight");
        this.chromaRoom(level, owner, this.base.getX() - 20, y, this.base.getZ() + 2);
        this.screensAndHolo(server, level, this.base.getX() - 8, y, this.base.getZ() - 10);
    }

    /** A closed 9x6x9 room: Chroma cubes in the ceiling and wall bands, linked to a hub playing a rainbow scene. */
    private void chromaRoom(ServerLevel level, ServerPlayer owner, int x0, int y, int z0) {
        Block hull = block("hull_starsteel");
        Block floor = block("floor_tile");
        Block cube = block("chroma_cube");
        for (int dx = 0; dx < 9; dx++) {
            for (int dz = 0; dz < 9; dz++) {
                for (int dy = -1; dy < 6; dy++) {
                    boolean wall = dx == 0 || dx == 8 || dz == 0 || dz == 8;
                    BlockPos p = new BlockPos(x0 + dx, y + dy, z0 + dz);
                    BlockState s;
                    if (dy == -1) {
                        s = floor.defaultBlockState();
                    } else if (dy == 5) {
                        s = (dx % 2 == 0 && dz % 2 == 0 ? cube : hull).defaultBlockState();
                    } else if (wall) {
                        s = (dy == 2 ? cube : hull).defaultBlockState();
                    } else {
                        s = Blocks.AIR.defaultBlockState();
                    }
                    level.setBlock(p, s, Block.UPDATE_ALL);
                }
            }
        }
        // Doorway in the north wall (dz = 0) at dx = 4, closed by an open sliding door.
        BlockPos door = new BlockPos(x0 + 4, y, z0);
        this.command(level.getServer(), "setblock %d %d %d nerodecor:sliding_door[facing=north,half=lower]", door);
        this.command(level.getServer(), "setblock %d %d %d nerodecor:sliding_door[facing=north,half=upper]",
                door.above());
        BlockState doorState = level.getBlockState(door);
        if (doorState.getBlock() instanceof AnimatedDoorBlock animated) {
            animated.toggle(level, door, doorState, true);
        } else {
            this.log("FAIL door", "sliding door did not place: " + doorState);
        }

        BlockPos hubPos = new BlockPos(x0 + 4, y, z0 + 5);
        BlockState hubState = ChromaContent.HUB.get().defaultBlockState();
        level.setBlock(hubPos, hubState, Block.UPDATE_ALL);
        hubState.getBlock().setPlacedBy(level, hubPos, hubState, owner, new ItemStack(ChromaContent.HUB_ITEM.get()));
        if (!(level.getBlockEntity(hubPos) instanceof ChromaHubBlockEntity hub) || hub.networkId() == 0L) {
            this.log("FAIL hub", "hub did not start a network");
            return;
        }
        this.hubNetwork = hub.networkId();
        var selection = ChromaNetworks.linkBox(level, owner, this.hubNetwork, new BlockPos(x0, y - 1, z0),
                new BlockPos(x0 + 8, y + 5, z0 + 8));
        this.log("link", "linked=" + selection.linked() + " refused=" + selection.refused() + " verdict="
                + selection.verdict());
        Scene scene = new Scene("Showcase", List.of(new ZoneAssignment(0, 0, new EffectProgram(EffectType.RAINBOW,
                EffectParams.builder().periodTicks(160).build()))));
        ChromaNetworkStore store = ChromaNetworks.store(level);
        ChromaVerdict saved = ChromaScenes.save(store, this.hubNetwork, owner.getUUID(), 0, scene, true,
                level.getGameTime());
        this.log("scene", "save+play slot 1: " + saved);
    }

    /** A 3x2 wall screen on a hull backing, plus the holo projector, holo table and world globe. */
    private void screensAndHolo(MinecraftServer server, ServerLevel level, int x0, int y, int z0) {
        Block hull = block("hull_nero_alloy");
        for (int dx = -1; dx <= 3; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                level.setBlock(new BlockPos(x0 + dx, y + dy, z0 - 1), hull.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        List<BlockPos> screen = new ArrayList<>();
        for (int dx = 0; dx < 3; dx++) {
            for (int dy = 0; dy < 2; dy++) {
                BlockPos p = new BlockPos(x0 + dx, y + dy, z0);
                this.command(server, "setblock %d %d %d nerodecor:wall_screen[facing=south]", p);
                screen.add(p);
            }
        }
        for (BlockPos p : screen) { // the last-placed screens never saw their later neighbours
            BlockState s = level.getBlockState(p);
            level.setBlock(p, Block.updateFromNeighbourShapes(s, level, p), Block.UPDATE_CLIENTS);
        }
        this.command(server, "setblock %d %d %d nerodecor:holo_projector", new BlockPos(x0 + 6, y, z0 + 1));
        this.command(server, "setblock %d %d %d nerodecor:holo_table", new BlockPos(x0 + 8, y, z0 + 1));
        String[] worlds = {"station", "luna", "mars", "cindara", "glacira", "greenxertz", "nyxara"};
        for (int k = 0; k < worlds.length; k++) {
            this.command(server, "setblock %d %d %d nerodecor:world_globe[world=" + worlds[k] + "]",
                    new BlockPos(x0 + 10 + k, y, z0 + 1));
        }
        // A 2x2 wall screen to the west, on its own hull backing.
        for (int dx = -7; dx <= -4; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                level.setBlock(new BlockPos(x0 + dx, y + dy, z0 - 1), hull.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        List<BlockPos> small = new ArrayList<>();
        for (int dx = -6; dx <= -5; dx++) {
            for (int dy = 0; dy < 2; dy++) {
                BlockPos p = new BlockPos(x0 + dx, y + dy, z0);
                this.command(server, "setblock %d %d %d nerodecor:wall_screen[facing=south]", p);
                small.add(p);
            }
        }
        for (BlockPos p : small) {
            BlockState s = level.getBlockState(p);
            level.setBlock(p, Block.updateFromNeighbourShapes(s, level, p), Block.UPDATE_CLIENTS);
        }
    }

    // --- showroom -------------------------------------------------------------------------------------------------

    /**
     * Runs {@code /nerodecor gallery} as the player from a point well away from the other builds, then shoots each
     * bay from the corridor at night. The bay geometry comes from {@link Showroom} so the cameras follow layout
     * changes.
     */
    private void showroom(ClientGameTestContext ctx, TestSingleplayerContext sp, TestServerContext server) {
        BlockPos at = this.base.offset(-50, 0, -60);
        BlockPos origin = Showroom.originFor(at);
        server.runCommand("gamemode creative @a"); // linking the corridor needs a player who may build
        this.step("showroom build", () -> server.runOnServer(s -> {
            ServerPlayer player = player(s);
            String command = String.format(Locale.ROOT, "execute positioned %d %d %d run nerodecor gallery",
                    at.getX(), at.getY(), at.getZ());
            s.getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
        }));
        server.runCommand("gamemode spectator @a");
        ctx.waitTicks(80); // hub link + scene sync, door swing
        sp.getConnection().waitForChunksRender();
        this.presets(ctx, sp, server, origin, CameraPresets.showroom());
        this.log("showroom", ShowroomScenes.ALL.size() + " bays, " + CameraPresets.showroom().size() + " viewpoints");
    }

    // --- block grid -----------------------------------------------------------------------------------------------

    /** Where the grid command runs from; the grid itself starts {@link BlockGrid#OFFSET} east/south of it. */
    private BlockPos gridAt() {
        return this.base.offset(30, 0, 30);
    }

    /** Runs {@code /nerodecor gallery grid} as the player and shoots it from every grid preset. */
    private void grid(ClientGameTestContext ctx, TestSingleplayerContext sp, TestServerContext server) {
        BlockPos at = this.gridAt();
        BlockPos origin = BlockGrid.originFor(at);
        server.runCommand("gamemode creative @a"); // linking the Chroma demo needs a player who may build
        this.step("grid build", () -> server.runOnServer(s -> {
            ServerPlayer player = player(s);
            String command = String.format(Locale.ROOT, "execute positioned %d %d %d run nerodecor gallery grid",
                    at.getX(), at.getY(), at.getZ());
            s.getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
        }));
        server.runCommand("gamemode spectator @a");
        ctx.waitTicks(60); // hub link + scene sync
        sp.getConnection().waitForChunksRender();
        BlockGrid.Layout layout = BlockGrid.layout();
        this.log("grid", BlockGrid.visibleBlocks().size() + " visible blocks in " + layout.rows().size()
                + " rows, " + layout.width() + " x " + layout.depth() + (layout.unsorted().isEmpty() ? ""
                : ", UNSORTED " + layout.unsorted()));
        if (!layout.unsorted().isEmpty()) {
            this.log("FAIL grid", "blocks with no collection: " + layout.unsorted());
        }
        this.presets(ctx, sp, server, origin, CameraPresets.grid(layout));
    }

    /** Shoots each preset relative to {@code origin}, with the preset's field of view. */
    private void presets(ClientGameTestContext ctx, TestSingleplayerContext sp, TestServerContext server,
                         BlockPos origin, List<CameraPresets.Preset> presets) {
        int fov = ctx.computeOnClient(mc -> mc.options.fov().get());
        for (CameraPresets.Preset p : presets) {
            ctx.runOnClient(mc -> mc.options.fov().set(p.fov()));
            this.view(ctx, sp, server, p.id(), origin.getX() + p.dx(), origin.getY() + p.dy(),
                    origin.getZ() + p.dz(), p.yaw(), p.pitch());
        }
        ctx.runOnClient(mc -> mc.options.fov().set(fov));
    }

    /**
     * Runs the real {@code /nerodecor capture grid 2} flow ({@link CaptureSession}) and checks it saved two shots under
     * {@code screenshots/nerodecor}. Restores spectator mode and the HUD state the rest of the test expects.
     */
    private void capture(ClientGameTestContext ctx, TestServerContext server) {
        this.step("capture", () -> {
            Path dir = FabricLoader.getInstance().getGameDir().resolve("screenshots").resolve("nerodecor");
            int before = count(dir);
            ctx.runOnClient(mc -> CaptureSession.start(CaptureSession.GRID, 2));
            ctx.waitFor(mc -> !CaptureSession.running(), 20 * 60);
            ctx.waitTicks(20);
            int after = count(dir);
            this.log(after - before == 2 ? "capture" : "FAIL capture", "capture grid 2 saved " + (after - before)
                    + " screenshot(s)");
            ctx.runOnClient(mc -> setHudHidden(mc, true));
        });
    }

    private static int count(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        try (var files = Files.list(dir)) {
            return (int) files.filter(f -> f.getFileName().toString().endsWith(".png")).count();
        }
    }

    // --- shots ----------------------------------------------------------------------------------------------------

    private void view(ClientGameTestContext ctx, TestSingleplayerContext sp, TestServerContext server, String name,
                      double x, double y, double z, float yaw, float pitch) {
        this.step("view " + name, () -> {
            // A barrier under the camera keeps the player still (flying alone can lapse on teleport).
            server.runOnServer(s -> {
                BlockPos below = BlockPos.containing(x, y - 1, z);
                if (s.overworld().getBlockState(below).isAir()) {
                    s.overworld().setBlock(below, Blocks.BARRIER.defaultBlockState(), Block.UPDATE_ALL);
                }
            });
            server.runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", x, y, z, yaw, pitch));
            ctx.runOnClient(mc -> mc.player.getAbilities().flying = true);
            ctx.waitTicks(20);
            sp.getConnection().waitForChunksRender();
            ctx.waitTicks(10);
            this.shot(ctx, name);
        });
    }

    private void hubScreen(ClientGameTestContext ctx, TestServerContext server) {
        if (this.hubNetwork == 0L) {
            this.log("FAIL hub screen", "no hub network");
            return;
        }
        this.step("hub screen open", () -> {
            server.runOnServer(s -> {
                ServerLevel level = s.overworld();
                HubNetwork network = ChromaNetworks.store(level).get(this.hubNetwork);
                ChromaHubNet.open(player(s), level, network);
            });
            ctx.waitTicks(20);
        });
        boolean open = ctx.computeOnClient(mc -> mc.gui.screen() instanceof ChromaHubScreen);
        this.log(open ? "hub screen" : "FAIL hub screen", "open=" + open);
        if (!open) {
            return;
        }
        for (String tab : TABS) {
            this.step("hub tab " + tab, () -> {
                ctx.runOnClient(mc -> setTab((ChromaHubScreen) mc.gui.screen(), tab));
                ctx.waitTicks(10);
                this.shot(ctx, "hub_" + tab.toLowerCase(Locale.ROOT));
            });
        }
        // The Scenes tab's composer: an effect zone, then a keyframe timeline.
        this.step("hub composer", () -> {
            ctx.runOnClient(mc -> {
                ChromaHubScreen screen = (ChromaHubScreen) mc.gui.screen();
                setTab(screen, "SCENES");
                invoke(screen, "setComposing", new Class<?>[] {boolean.class}, true);
            });
            ctx.waitTicks(10);
            this.shot(ctx, "hub_scenes_compose_effect");
            ctx.runOnClient(mc -> {
                ChromaHubScreen screen = (ChromaHubScreen) mc.gui.screen();
                SceneDraft draft = (SceneDraft) invoke(screen, "draft", new Class<?>[0]);
                SceneDraft.ZoneDraft zone = draft.zone(0);
                zone.setKind(SceneDraft.Kind.TIMELINE);
                zone.addKeyAfter(zone.keyCount() - 1, ChromaColor.of(0xFF, 0x6B, 0x8A));
                zone.addKeyAfter(zone.keyCount() - 1, ChromaColor.of(0x5C, 0xFF, 0xB0));
                zone.cycleEasing(1, 2);
            });
            ctx.waitTicks(10);
            this.shot(ctx, "hub_scenes_compose_timeline");
        });
    }

    /** Switches the hub screen's tab through its private {@code setTab} (test-only reflection). */
    private static void setTab(ChromaHubScreen screen, String tab) throws ReflectiveOperationException {
        for (Class<?> inner : ChromaHubScreen.class.getDeclaredClasses()) {
            if (inner.isEnum() && "Tab".equals(inner.getSimpleName())) {
                Object value = null;
                for (Object constant : inner.getEnumConstants()) {
                    if (((Enum<?>) constant).name().equals(tab)) {
                        value = constant;
                    }
                }
                Method set = ChromaHubScreen.class.getDeclaredMethod("setTab", inner);
                set.setAccessible(true);
                set.invoke(screen, value);
                return;
            }
        }
        throw new IllegalStateException("ChromaHubScreen has no Tab enum");
    }

    /** Hides or shows the HUD (F1), which also hides the first-person hand. */
    static void setHudHidden(net.minecraft.client.Minecraft mc, boolean hidden) {
        if (mc.gui.hud.isHidden() != hidden) {
            mc.gui.hud.toggle();
        }
    }

    /** Calls a private screen method (test-only reflection). */
    private static Object invoke(ChromaHubScreen screen, String name, Class<?>[] types, Object... args)
            throws ReflectiveOperationException {
        Method m = ChromaHubScreen.class.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(screen, args);
    }

    // --- helpers --------------------------------------------------------------------------------------------------

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().get(0);
    }

    private static Block block(String path) {
        for (Block block : BuiltInRegistries.BLOCK) {
            var key = BuiltInRegistries.BLOCK.getKey(block);
            if (NS.equals(key.getNamespace()) && path.equals(key.getPath())) {
                return block;
            }
        }
        throw new IllegalStateException("no block " + NS + ":" + path);
    }

    private void command(MinecraftServer server, String format, BlockPos pos) {
        String command = String.format(Locale.ROOT, format, pos.getX(), pos.getY(), pos.getZ());
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
    }

    private interface Step {
        void run() throws Throwable;
    }

    private void step(String name, Step step) {
        try {
            step.run();
        } catch (Throwable t) {
            this.log("FAIL " + name, t.getClass().getSimpleName() + ": " + t.getMessage());
            LOG.error("[showcase] step failed: {}", name, t);
        }
    }

    private void shot(ClientGameTestContext ctx, String name) {
        this.shot++;
        String file = name.startsWith("showroom_") || name.startsWith("grid_") ? name
                : String.format(Locale.ROOT, "%02d_%s", this.shot, name);
        Path path = ctx.takeScreenshot(file);
        this.report.add(String.format(Locale.ROOT, "SHOT    %-28s %s", file, path.getFileName()));
        this.copyToPreview(path, file + ".png");
    }

    /**
     * Copies a shot to {@code tools/collections/.preview/screens/} in the repo (gitignored) so reviewers find every
     * screenshot in one place. The repo root is the first parent of the run directory that has {@code tools/collections}.
     */
    private void copyToPreview(Path shot, String name) {
        Path dir = FabricLoader.getInstance().getGameDir().toAbsolutePath();
        while (dir != null && !Files.isDirectory(dir.resolve("tools").resolve("collections"))) {
            dir = dir.getParent();
        }
        if (dir == null || shot == null) {
            return;
        }
        try {
            Path out = dir.resolve("tools").resolve("collections").resolve(".preview").resolve("screens");
            Files.createDirectories(out);
            Files.copy(shot, out.resolve(name), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOG.warn("[showcase] could not copy {} to the preview folder", name, e);
        }
    }

    private void log(String what, String detail) {
        String line = String.format(Locale.ROOT, "%-7s %-20s %s", what.startsWith("FAIL") ? "FAIL" : "INFO",
                what.replace("FAIL ", ""), detail);
        this.report.add(line);
        LOG.info("[showcase] {}", line);
    }

    private void writeReport() {
        Path dir = FabricLoader.getInstance().getGameDir().resolve("screenshots");
        try {
            Files.createDirectories(dir);
            Files.write(dir.resolve("showcase-report.txt"), this.report, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.error("[showcase] could not write the report", e);
        }
    }
}
