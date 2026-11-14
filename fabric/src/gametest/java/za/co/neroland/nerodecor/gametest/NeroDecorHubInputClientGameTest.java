package za.co.neroland.nerodecor.gametest;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import za.co.neroland.nerodecor.chroma.net.ChromaHubNet;
import za.co.neroland.nerodecor.chroma.server.ChromaNetworks;
import za.co.neroland.nerodecor.chroma.server.HubNetwork;
import za.co.neroland.nerodecor.client.screen.chroma.ChromaHubScreen;
import za.co.neroland.nerodecor.content.block.ChromaHubBlockEntity;
import za.co.neroland.nerodecor.registry.ChromaContent;

/**
 * Regression test for "nothing works when I click inside the Chroma Hub" (26.3 renumbered the mouse buttons and
 * the hub compared against a literal 0): opens the hub (by using the block when the aim lands, else from the server),
 * then drives it through real input (cursor moves, button presses, wheel, typed characters) at GUI scale 2 and 3 and
 * asserts each control reacted: tab switches, a typed zone rename that must reach the server, the colour picker, a
 * brush click in the 3D view and its Ctrl+Z undo, the orbit drag and the wheel zoom. Results go to
 * {@code hub-input.txt} beside the screenshots; any failed check fails the test. Test-only; no player data is written.
 */
@SuppressWarnings("UnstableApiUsage")
public class NeroDecorHubInputClientGameTest implements FabricClientGameTest {

    private static final Logger LOG = LoggerFactory.getLogger("NeroDecorHubInput");
    private static final int LEFT = InputConstants.MOUSE_BUTTON_LEFT;
    private static final int RIGHT = InputConstants.MOUSE_BUTTON_RIGHT;
    private static final String[] TABS = {"zones", "palette", "scenes", "access", "view"};

    private final List<String> report = new ArrayList<>();
    private int failures;
    private long network;
    private BlockPos hubPos = BlockPos.ZERO;

    @Override
    public void runTest(ClientGameTestContext ctx) {
        TestInput in = ctx.getInput();
        in.resizeWindow(1280, 720);
        try (TestSingleplayerContext sp = ctx.worldBuilder().adjustSettings(s -> {
            s.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
            s.setAllowCommands(true);
        }).create()) {
            sp.getConnection().waitForChunksRender();
            TestServerContext server = sp.getServer();
            server.runCommand("clear @a");
            String built = server.computeOnServer(this::build);
            this.log("setup", built);
            if (this.network == 0L) {
                this.check("setup", false, "no hub network");
                return;
            }
            int round = 0;
            for (int scale : new int[] {2, 3}) {
                round++;
                ctx.runOnClient(mc -> {
                    mc.options.guiScale().set(scale);
                    mc.resizeGui();
                });
                ctx.waitTicks(2);
                this.round(ctx, in, server, scale, round);
                ctx.runOnClient(mc -> mc.gui.setScreen(null));
                ctx.waitTicks(5);
            }
        } finally {
            this.write();
        }
        if (this.failures > 0) {
            throw new AssertionError(this.failures + " Chroma Hub input check(s) failed; see hub-input.txt");
        }
    }

    // --- one pass at one GUI scale ---------------------------------------------------------------------------------

    private void round(ClientGameTestContext ctx, TestInput in, TestServerContext server, int scale, int round) {
        String p = "s" + scale + ".";
        // Open by using the block, the way a player does.
        server.runCommand(String.format(Locale.ROOT, "tp @a %.1f %d %.1f facing %.1f %.1f %.1f",
                this.hubPos.getX() + 0.5, this.hubPos.getY(), this.hubPos.getZ() - 2.5,
                this.hubPos.getX() + 0.5, this.hubPos.getY() + 0.5, this.hubPos.getZ() + 0.5));
        ctx.waitTicks(10);
        in.pressKey(o -> o.keyUse);
        ctx.waitTicks(20);
        boolean open = ctx.computeOnClient(mc -> mc.gui.screen() instanceof ChromaHubScreen);
        this.log(p + "open_by_use", "opened=" + open + " (aiming in a test world is not under test)");
        if (!open) {
            server.runOnServer(s -> {
                HubNetwork n = ChromaNetworks.store(s.overworld()).get(this.network);
                ChromaHubNet.open(s.getPlayerList().getPlayers().get(0), s.overworld(), n);
            });
            ctx.waitTicks(20);
            open = ctx.computeOnClient(mc -> mc.gui.screen() instanceof ChromaHubScreen);
        }
        this.check(p + "open", open, "screen=" + this.screenName(ctx));
        if (!open) {
            return;
        }
        this.log(p + "gui", ctx.computeOnClient(mc -> "scale=" + mc.getWindow().getGuiScale() + " gui="
                + mc.getWindow().getGuiScaledWidth() + "x" + mc.getWindow().getGuiScaledHeight() + " window="
                + mc.getWindow().getScreenWidth() + "x" + mc.getWindow().getScreenHeight()));
        ctx.waitTicks(2); // at least one frame drawn, so the click regions exist
        this.log(p + "regions", ctx.computeOnClient(mc -> {
            List<?> regions = (List<?>) this.field(mc.gui.screen(), "regions");
            List<?> drawing = (List<?>) this.field(mc.gui.screen(), "drawing");
            return "regions=" + regions.size() + " drawing=" + drawing.size() + " first="
                    + (regions.isEmpty() ? "-" : regions.get(0)) + " x0=" + this.intField(mc, "x0") + " y0="
                    + this.intField(mc, "y0");
        }));

        // Tabs: every one, in a non-trivial order.
        for (String tab : new String[] {"palette", "scenes", "access", "view", "zones"}) {
            this.clickGui(ctx, in, this.tabCentreX(ctx, tab), this.intField(ctx, "y0") + 24 + 7, LEFT);
            this.check(p + "tab_" + tab, tab.equalsIgnoreCase(this.tabName(ctx)), "tab=" + this.tabName(ctx));
        }
        ctx.takeScreenshot("hub_input_s" + scale + "_zones");

        // Zones: type a new name into the name box and press Rename; the server must get it.
        String name = "Lobby" + round;
        int cx = this.intField(ctx, "contentX");
        int bb = this.intField(ctx, "bodyBottom");
        ctx.runOnClient(mc -> ((EditBox) this.field(mc.gui.screen(), "nameBox")).setValue(""));
        this.clickGui(ctx, in, cx + 30, bb - 32 + 7, LEFT);
        boolean focused = ctx.computeOnClient(mc -> ((EditBox) this.field(mc.gui.screen(), "nameBox")).isFocused());
        this.check(p + "name_box_focus", focused, "focused=" + focused);
        in.typeChars(name);
        ctx.waitTick();
        String typed = ctx.computeOnClient(mc -> ((EditBox) this.field(mc.gui.screen(), "nameBox")).getValue());
        this.check(p + "name_box_typed", name.equals(typed), "value=" + typed);
        this.clickGui(ctx, in, cx + 124 + 28, bb - 32 + 7, LEFT);
        ctx.waitTicks(10);
        String onServer = server.computeOnServer(s -> {
            HubNetwork n = ChromaNetworks.store(s.overworld()).get(this.network);
            return n == null || n.zones().isEmpty() ? "<none>" : n.zones().get(0).name();
        });
        this.check(p + "rename_reaches_server", name.equals(onServer), "server zone 0 = " + onServer);

        // Colour picker: a click in the saturation/value square changes the primary colour.
        int px = this.intField(ctx, "pickerX");
        int bt = this.intField(ctx, "bodyTop");
        String before = this.primary(ctx);
        this.clickGui(ctx, in, px + 10, bt + 50, LEFT);
        String after = this.primary(ctx);
        this.check(p + "picker_sv_click", !before.equals(after), before + " -> " + after);
        // Hue bar drag.
        before = this.primary(ctx);
        this.cursorAtGui(ctx, in, px + 105, bt + 5);
        in.holdMouse(LEFT);
        ctx.waitTick();
        this.cursorAtGui(ctx, in, px + 105, bt + 40);
        in.releaseMouse(LEFT);
        ctx.waitTick();
        after = this.primary(ctx);
        this.check(p + "picker_hue_drag", !before.equals(after), before + " -> " + after);

        // 3D view: right-drag orbits, the wheel zooms.
        this.clickGui(ctx, in, this.tabCentreX(ctx, "view"), this.intField(ctx, "y0") + 24 + 7, LEFT);
        ctx.waitTicks(2);
        int[] v = ctx.computeOnClient(mc -> {
            Object view = this.field(mc.gui.screen(), "view");
            return new int[] {(int) this.field(view, "x"), (int) this.field(view, "y"), (int) this.field(view, "w"),
                    (int) this.field(view, "h")};
        });
        this.log(p + "view", "x=" + v[0] + " y=" + v[1] + " w=" + v[2] + " h=" + v[3]);
        // Brush (the default tool): a left click on a block stages a paint; Ctrl+Z takes it back.
        int ops0 = this.pendingOps(ctx);
        this.clickGui(ctx, in, v[0] + v[2] / 2.0, v[1] + v[3] / 2.0, LEFT);
        int ops1 = this.pendingOps(ctx);
        this.check(p + "view_brush_click", ops1 > ops0, "pending ops " + ops0 + " -> " + ops1);
        // The 26.3 test harness's holdControl() presses the Ctrl key but sends the next key without the Ctrl
        // modifier, so the shortcut is delivered as the key event a real Ctrl+Z produces (scancode, layout key code
        // and modifiers from InputConstants) straight to the screen's keyPressed.
        ctx.runOnClient(mc -> mc.gui.screen().keyPressed(
                new KeyEvent(InputConstants.KEY_Z, 'z', InputConstants.MOD_CONTROL)));
        ctx.waitTick();
        int ops2 = this.pendingOps(ctx);
        this.check(p + "ctrl_z_undo", ops2 == ops0, "pending ops " + ops1 + " -> " + ops2);
        double yaw0 = this.camera(ctx, "yaw");
        this.cursorAtGui(ctx, in, v[0] + v[2] / 2.0, v[1] + v[3] / 2.0);
        in.holdMouse(RIGHT);
        ctx.waitTick();
        this.cursorAtGui(ctx, in, v[0] + v[2] / 2.0 + 30, v[1] + v[3] / 2.0 + 10);
        in.releaseMouse(RIGHT);
        ctx.waitTick();
        double yaw1 = this.camera(ctx, "yaw");
        this.check(p + "view_orbit_drag", yaw0 != yaw1, "yaw " + yaw0 + " -> " + yaw1);
        double d0 = this.camera(ctx, "distance");
        in.scroll(1.0D);
        ctx.waitTick();
        double d1 = this.camera(ctx, "distance");
        this.check(p + "view_wheel_zoom", d0 != d1, "distance " + d0 + " -> " + d1);
        ctx.takeScreenshot("hub_input_s" + scale + "_view");
    }

    // --- world ----------------------------------------------------------------------------------------------------

    private String build(MinecraftServer server) {
        ServerLevel level = server.overworld();
        ServerPlayer owner = server.getPlayerList().getPlayers().get(0);
        BlockPos base = owner.blockPosition();
        this.hubPos = base.offset(0, 0, 4);
        Block cube = ChromaContent.CUBE.get();
        for (int dx = 1; dx <= 3; dx++) {
            for (int dy = 0; dy < 3; dy++) {
                level.setBlock(this.hubPos.offset(dx, dy, 1), cube.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        BlockState hubState = ChromaContent.HUB.get().defaultBlockState();
        level.setBlock(this.hubPos, hubState, Block.UPDATE_ALL);
        hubState.getBlock().setPlacedBy(level, this.hubPos, hubState, owner,
                new ItemStack(ChromaContent.HUB_ITEM.get()));
        if (!(level.getBlockEntity(this.hubPos) instanceof ChromaHubBlockEntity hub) || hub.networkId() == 0L) {
            return "FAIL hub did not start a network";
        }
        this.network = hub.networkId();
        var selection = ChromaNetworks.linkBox(level, owner, this.network, this.hubPos.offset(1, 0, 1),
                this.hubPos.offset(3, 2, 1));
        return "linked=" + selection.linked() + " refused=" + selection.refused() + " verdict="
                + selection.verdict();
    }

    // --- input helpers --------------------------------------------------------------------------------------------

    private void cursorAtGui(ClientGameTestContext ctx, TestInput in, double gx, double gy) {
        double sx = ctx.computeOnClient(
                mc -> (double) mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth());
        double sy = ctx.computeOnClient(
                mc -> (double) mc.getWindow().getScreenHeight() / mc.getWindow().getGuiScaledHeight());
        in.setCursorPos(gx * sx + sx / 2.0D, gy * sy + sy / 2.0D);
        ctx.waitTick();
    }

    private void clickGui(ClientGameTestContext ctx, TestInput in, double gx, double gy, int button) {
        this.cursorAtGui(ctx, in, gx, gy);
        in.pressMouse(button);
        ctx.waitTicks(2);
    }

    /** The GUI x at the middle of {@code tab}'s header, by the screen's own layout rule (16 px pad, 2 px gap). */
    private int tabCentreX(ClientGameTestContext ctx, String tab) {
        return ctx.computeOnClient(mc -> {
            int x = this.intField(mc, "x0") + 10;
            for (String t : TABS) {
                int w = mc.font.width(Component.translatable("screen.nerodecor.chroma_hub.tab." + t).getString())
                        + 16;
                if (t.equals(tab)) {
                    return x + w / 2;
                }
                x += w + 2;
            }
            throw new IllegalArgumentException(tab);
        });
    }

    // --- screen state (read-only reflection) ----------------------------------------------------------------------

    private String screenName(ClientGameTestContext ctx) {
        return ctx.computeOnClient(mc -> mc.gui.screen() == null ? "none" : mc.gui.screen().getClass().getName());
    }

    private String tabName(ClientGameTestContext ctx) {
        return ctx.computeOnClient(mc -> mc.gui.screen() instanceof ChromaHubScreen s
                ? String.valueOf(this.field(s, "tab")) : "<closed>");
    }

    private int intField(ClientGameTestContext ctx, String name) {
        return ctx.computeOnClient(mc -> this.intField(mc, name));
    }

    private int intField(Minecraft mc, String name) {
        return (int) this.field(mc.gui.screen(), name);
    }

    private String primary(ClientGameTestContext ctx) {
        return ctx.computeOnClient(mc -> {
            Object picker = this.field(mc.gui.screen(), "picker");
            try {
                java.lang.reflect.Method m = picker.getClass().getDeclaredMethod("primary");
                m.setAccessible(true);
                return String.valueOf(m.invoke(picker));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private int pendingOps(ClientGameTestContext ctx) {
        return ctx.computeOnClient(mc -> ((za.co.neroland.nerodecor.chroma.editor.EditorModel) this.field(
                mc.gui.screen(), "model")).pendingOps());
    }

    private double camera(ClientGameTestContext ctx, String name) {
        return ctx.computeOnClient(mc -> (double) this.field(this.field(this.field(mc.gui.screen(), "view"), "camera"),
                name));
    }

    private Object field(Object owner, String name) {
        if (owner == null) {
            throw new IllegalStateException("no screen open reading " + name);
        }
        for (Class<?> c = owner.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(owner);
            } catch (NoSuchFieldException e) {
                // look in the superclass
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        throw new IllegalStateException("no field " + name + " on " + owner.getClass().getName());
    }

    // --- report ---------------------------------------------------------------------------------------------------

    private void check(String name, boolean ok, String detail) {
        if (!ok) {
            this.failures++;
        }
        this.log((ok ? "PASS " : "FAIL ") + name, detail);
    }

    private void log(String what, String detail) {
        String line = what + "  " + detail;
        this.report.add(line);
        LOG.info("[hub-input] {}", line);
    }

    private void write() {
        Path dir = FabricLoader.getInstance().getGameDir().resolve("screenshots");
        try {
            Files.createDirectories(dir);
            Files.write(dir.resolve("hub-input.txt"), this.report, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.error("[hub-input] could not write the report", e);
        }
    }
}
