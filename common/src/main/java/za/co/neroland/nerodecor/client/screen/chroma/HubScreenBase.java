package za.co.neroland.nerodecor.client.screen.chroma;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
//? if >=1.21.9 {
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
//?}
import net.minecraft.network.chat.Component;

/**
 * Version seam for the Chroma Hub screens: every per-line difference of the screen API (render entry points, input
 * events, pose stack) is absorbed here, so the screens above it draw with fills and text only. Drawing is
 * immediate-mode: controls are drawn and registered as click regions in the same pass. Client only.
 */
abstract class HubScreenBase extends Screen {

    // Input codes come from InputConstants, never literals: 26.3 renumbered them (mouse left/middle/right are 1/2/3,
    // key events carry a scancode plus a separate layout keycode, Ctrl and Shift are two-bit masks), and a literal 0
    // for "left button" made every click in the hub miss.

    /** The left mouse button on this Minecraft line. */
    protected static final int LEFT = InputConstants.MOUSE_BUTTON_LEFT;
    /** The right mouse button on this Minecraft line. */
    protected static final int RIGHT = InputConstants.MOUSE_BUTTON_RIGHT;
    /** The middle mouse button on this Minecraft line. */
    protected static final int MIDDLE = InputConstants.MOUSE_BUTTON_MIDDLE;
    // The layout key codes onKey receives for Z and Y (26.3+: the key event's shortcut key, a lower-case character).
    //? if >=26.3 {
    /*protected static final int KEY_Z = 'z';
    protected static final int KEY_Y = 'y';
    *///?} else {
    protected static final int KEY_Z = InputConstants.KEY_Z;
    protected static final int KEY_Y = InputConstants.KEY_Y;
    //?}

    /** A clickable control registered during the last draw. */
    private record Region(int x, int y, int w, int h, boolean enabled, Runnable action) {
    }

    private final List<Region> regions = new ArrayList<>();
    private final List<Region> drawing = new ArrayList<>();
    protected String hoverHint;
    private String pendingHint;

    protected HubScreenBase(Component title) {
        super(title);
    }

    // --- subclass hooks ------------------------------------------------------------------------------------------

    /** Draws everything below the vanilla widgets (the text boxes). */
    protected abstract void drawPanel(GuiGraphicsExtractor g, int mx, int my, float partialTick);

    /** Draws on top of the vanilla widgets. */
    protected void drawOverlay(GuiGraphicsExtractor g, int mx, int my) {
    }

    /** A click not taken by a registered control ({@code button} is {@link #LEFT} etc.); true when handled. */
    protected boolean onClick(double mx, double my, int button) {
        return false;
    }

    protected boolean onDrag(double mx, double my, int button, double dx, double dy) {
        return false;
    }

    protected boolean onRelease(double mx, double my, int button) {
        return false;
    }

    protected boolean onScroll(double mx, double my, double amount) {
        return false;
    }

    /**
     * A key press while no text box has focus; {@code key} is the layout key code (compare with {@link #KEY_Z}),
     * {@code ctrl} is Ctrl (Cmd on macOS where the line supports it). True when handled.
     */
    protected boolean onKey(int key, boolean ctrl, boolean shift) {
        return false;
    }

    // --- immediate-mode controls ---------------------------------------------------------------------------------

    private void beginFrame() {
        drawing.clear();
        pendingHint = null;
    }

    private void endFrame() {
        regions.clear();
        regions.addAll(drawing);
        hoverHint = pendingHint;
    }

    /** Registers a click region ({@code action} null = hover hint only); returns whether the mouse is over it. */
    protected boolean region(int x, int y, int w, int h, boolean enabled, int mx, int my, String hint,
            Runnable action) {
        drawing.add(new Region(x, y, w, h, enabled, action));
        boolean hot = inside(mx, my, x, y, w, h);
        if (hot && hint != null) {
            pendingHint = hint;
        }
        return hot;
    }

    /** A flat sci-fi button. */
    protected void button(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, boolean enabled,
            boolean selected, int mx, int my, String hint, Runnable action) {
        boolean hot = region(x, y, w, h, enabled, mx, my, hint, action) && enabled;
        int edge = !enabled ? HubTheme.LINE : selected ? HubTheme.CYAN : hot ? HubTheme.CYAN_DIM : HubTheme.BUTTON_EDGE;
        int body = !enabled ? HubTheme.DISABLED : selected ? 0xFF123049 : hot ? HubTheme.BUTTON_HOVER : HubTheme.BUTTON;
        g.fill(x, y, x + w, y + h, edge);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, body);
        if (selected) {
            g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, HubTheme.CYAN);
        }
        String fitted = fit(label, w - 4);
        int colour = !enabled ? HubTheme.DISABLED_TEXT : selected || hot ? 0xFFFFFFFF : HubTheme.TEXT;
        text(g, fitted, x + (w - font.width(fitted)) / 2, y + (h - 8) / 2, colour);
    }

    /**
     * A primary action button: clearly lit (cyan edge, teal body, white label) when enabled, so it never reads as
     * disabled against the dark rows; when disabled it draws exactly like a disabled {@link #button}.
     */
    protected void accentButton(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, boolean enabled,
            int mx, int my, String hint, Runnable action) {
        if (!enabled) {
            button(g, x, y, w, h, label, false, false, mx, my, hint, action);
            return;
        }
        boolean hot = region(x, y, w, h, true, mx, my, hint, action);
        g.fill(x, y, x + w, y + h, hot ? HubTheme.CYAN : HubTheme.CYAN_DIM);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, hot ? 0xFF1D6178 : 0xFF124A5E);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, HubTheme.alpha(HubTheme.CYAN, 0x60));
        String fitted = fit(label, w - 4);
        text(g, fitted, x + (w - font.width(fitted)) / 2, y + (h - 8) / 2, 0xFFFFFFFF);
    }

    /** A panel with a 1-px edge and a soft outer glow. */
    protected static void glowPanel(GuiGraphicsExtractor g, int x, int y, int w, int h, int edge, int body) {
        g.fill(x - 3, y - 3, x + w + 3, y + h + 3, HubTheme.alpha(edge, 0x10));
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, HubTheme.alpha(edge, 0x1C));
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, HubTheme.alpha(edge, 0x38));
        g.fill(x, y, x + w, y + h, edge);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, body);
    }

    /** Sci-fi corner brackets inside a rectangle. */
    protected static void brackets(GuiGraphicsExtractor g, int x, int y, int w, int h, int len, int colour) {
        g.fill(x, y, x + len, y + 1, colour);
        g.fill(x, y, x + 1, y + len, colour);
        g.fill(x + w - len, y, x + w, y + 1, colour);
        g.fill(x + w - 1, y, x + w, y + len, colour);
        g.fill(x, y + h - 1, x + len, y + h, colour);
        g.fill(x, y + h - len, x + 1, y + h, colour);
        g.fill(x + w - len, y + h - 1, x + w, y + h, colour);
        g.fill(x + w - 1, y + h - len, x + w, y + h, colour);
    }

    /** A recessed well behind a text box. */
    protected static void well(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean focused) {
        g.fill(x, y, x + w, y + h, focused ? HubTheme.CYAN_DIM : HubTheme.BUTTON_EDGE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, HubTheme.WELL);
    }

    protected void text(GuiGraphicsExtractor g, String s, int x, int y, int colour) {
        g.text(this.font, s, x, y, colour, false);
    }

    protected void textRight(GuiGraphicsExtractor g, String s, int right, int y, int colour) {
        g.text(this.font, s, right - font.width(s), y, colour, false);
    }

    /** {@code s} cut to {@code width} pixels with an ellipsis. */
    protected String fit(String s, int width) {
        if (s == null) {
            return "";
        }
        if (font.width(s) <= width) {
            return s;
        }
        return font.plainSubstrByWidth(s, Math.max(0, width - font.width("..."))) + "...";
    }

    protected static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    /** Text box styled for the hub: borderless, drawn over a {@link #well}. */
    protected EditBox box(int x, int y, int w, int maxLength, String value, String hint) {
        EditBox box = new EditBox(font, x + 3, y + 2, w - 6, 10, Component.literal(hint));
        box.setBordered(false);
        box.setMaxLength(maxLength);
        box.setTextColor(HubTheme.TEXT);
        box.setValue(value == null ? "" : value);
        box.setHint(Component.literal(hint).withStyle(s -> s.withColor(HubTheme.FAINT & 0xFFFFFF)));
        return addRenderableWidget(box);
    }

    // --- pixel-space drawing (viewport) --------------------------------------------------------------------------

    /** Physical pixels per GUI unit. */
    protected double pixelScale() {
        double scaled = minecraft.getWindow().getGuiScaledWidth();
        return scaled <= 0 ? 1.0 : minecraft.getWindow().getWidth() / scaled;
    }

    /** Moves drawing to physical pixels with origin at GUI point {@code (x, y)}. */
    protected static void pushPixels(GuiGraphicsExtractor g, float x, float y, float inverseScale) {
        //? if >=1.21.6 {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(inverseScale, inverseScale);
        //?} else {
        /*g.pose().pushPose();
        g.pose().translate(x, y, 0.0F);
        g.pose().scale(inverseScale, inverseScale, 1.0F);
        *///?}
    }

    protected static void popPixels(GuiGraphicsExtractor g) {
        //? if >=1.21.6 {
        g.pose().popMatrix();
        //?} else {
        /*g.pose().popPose();
        *///?}
    }

    // --- per-line entry points -----------------------------------------------------------------------------------

    private boolean typing() {
        return getFocused() instanceof EditBox box && box.isFocused();
    }

    private boolean clickRegions(double mx, double my, int button) {
        if (button != LEFT) {
            return false;
        }
        for (int i = regions.size() - 1; i >= 0; i--) {
            Region r = regions.get(i);
            if (r.action() == null) {
                continue; // hint-only region (sliders and pickers take the click in onClick)
            }
            if (inside(mx, my, r.x(), r.y(), r.w(), r.h())) {
                if (r.enabled()) {
                    setFocused(null);
                    r.action().run();
                }
                return true;
            }
        }
        return false;
    }

    private boolean clickHook(double mx, double my, int button) {
        if (clickRegions(mx, my, button)) {
            return true;
        }
        for (var child : children()) {
            if (child instanceof EditBox box && box.visible && box.isMouseOver(mx, my)) {
                return false;
            }
        }
        if (onClick(mx, my, button)) {
            setFocused(null);
            return true;
        }
        return false;
    }

    //? if >=26.1 {
    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        beginFrame();
        drawPanel(g, mouseX, mouseY, partialTick);
        endFrame();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        drawOverlay(g, mouseX, mouseY);
    }
    //?} else {
    /*@Override
    public void renderBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        beginFrame();
        drawPanel(g, mouseX, mouseY, partialTick);
        endFrame();
    }

    @Override
    public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        drawOverlay(g, mouseX, mouseY);
    }
    *///?}

    //? if >=1.21.9 {
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (clickHook(event.x(), event.y(), event.button())) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (onDrag(event.x(), event.y(), event.button(), dx, dy)) {
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        boolean mine = onRelease(event.x(), event.y(), event.button());
        return super.mouseReleased(event) || mine;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        //? if >=26.3 {
        /*boolean mine = !typing()
                && onKey(event.shortcutKey(), event.hasControlDownWithQuirk(), event.hasShiftDown());
        *///?} else {
        boolean mine = !typing() && onKey(event.key(), event.hasControlDown(), event.hasShiftDown());
        //?}
        if (mine) {
            return true;
        }
        return super.keyPressed(event);
    }
    //?} else {
    /*@Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (clickHook(mx, my, button)) {
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (onDrag(mx, my, button, dx, dy)) {
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        boolean mine = onRelease(mx, my, button);
        return super.mouseReleased(mx, my, button) || mine;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (!typing() && onKey(key, (modifiers & InputConstants.MOD_CONTROL) != 0, (modifiers & 1) != 0)) {
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }
    *///?}

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (onScroll(mx, my, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
