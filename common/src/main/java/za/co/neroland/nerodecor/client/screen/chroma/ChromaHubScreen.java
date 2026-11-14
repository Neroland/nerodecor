package za.co.neroland.nerodecor.client.screen.chroma;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import za.co.neroland.nerodecor.chroma.ChromaColor;
import za.co.neroland.nerodecor.chroma.ChromaGradient;
import za.co.neroland.nerodecor.chroma.ChromaOpBatch;
import za.co.neroland.nerodecor.chroma.ChromaResult;
import za.co.neroland.nerodecor.chroma.EffectParams;
import za.co.neroland.nerodecor.chroma.EffectProgram;
import za.co.neroland.nerodecor.chroma.EffectType;
import za.co.neroland.nerodecor.chroma.FlashClamp;
import za.co.neroland.nerodecor.chroma.RelativePos;
import za.co.neroland.nerodecor.chroma.Scene;
import za.co.neroland.nerodecor.chroma.SceneChipCodec;
import za.co.neroland.nerodecor.chroma.SceneCodec;
import za.co.neroland.nerodecor.chroma.Timeline;
import za.co.neroland.nerodecor.chroma.ZoneAssignment;
import za.co.neroland.nerodecor.chroma.client.ChromaClientSync;
import za.co.neroland.nerodecor.chroma.editor.EditorModel;
import za.co.neroland.nerodecor.chroma.editor.HubAction;
import za.co.neroland.nerodecor.chroma.editor.HubDetail;
import za.co.neroland.nerodecor.chroma.editor.SceneDraft;
import za.co.neroland.nerodecor.chroma.editor.Selections;
import za.co.neroland.nerodecor.client.ChromaTintBridge;

/**
 * The Chroma Hub screen. Five tabs over one hub: Zones (list, rename, channel, paint a zone), Palette (every entry),
 * Scenes (slots, quick effect, share codes, and a composer that gives each zone an effect or a keyframe timeline
 * with a live preview), Access (trusted players, owner and admins) and 3D (rotatable view with
 * brush, box, flood fill, gradients and eyedropper). A colour picker (HSV square, hue, light level, hex) sits on the
 * right. Colour edits stay local, with a 32-step undo, until Apply sends them as op batches; zone, trust and scene
 * changes are sent at once. The server re-checks everything. Player names come only from the client's own
 * online-player list. Client only.
 */
public class ChromaHubScreen extends HubScreenBase {

    private enum Tab {
        ZONES, PALETTE, SCENES, ACCESS, VIEW
    }

    private enum Tool {
        ORBIT, BRUSH, BOX, FILL, GRADIENT, AXIS, PICK
    }

    private enum Drag {
        NONE, SV, HUE, LIGHT, SLICE, ORBIT, PAN, BRUSH
    }

    private static final int ROW = 14;
    private static final int PICKER_W = 114;
    private static final int GRADIENT_STEPS = 16;
    private static final int NO_BLOCK = Integer.MIN_VALUE;
    private static final String[] ROLE_KEYS = {"none", "viewer", "painter", "admin", "owner"};

    private HubDetail detail;
    private EditorModel model;
    private final HubViewport view = new HubViewport();
    private final ColourPicker picker = new ColourPicker();

    private Tab tab = Tab.ZONES;
    private Tool tool = Tool.BRUSH;
    private boolean zoneMode;
    private Selections.FloodMatch match = Selections.FloodMatch.ZONE;
    private int axis = 1;
    private EffectType effect = EffectType.BREATHE;
    private int period = 40;

    private int selZone;
    private int selEntry = -1;
    private int selSlot;
    private boolean composing;
    private SceneDraft draft;
    private int draftZone;
    private int selKey;
    private int keyScroll;
    /** The last scene sent from this screen, or the server's answer, failed the flash limit. */
    private boolean flashRejected;
    private int zoneScroll;
    private int paletteScroll;
    private int slotScroll;
    private int trustScroll;
    private int onlineScroll;

    private int anchor = NO_BLOCK;
    private int lastBrushed = NO_BLOCK;
    private Drag drag = Drag.NONE;
    private boolean applied;
    private boolean syncingHex;

    private String status = "";
    private int statusColour = HubTheme.SUBTLE;
    private int statusTicks;

    private EditBox nameBox;
    private EditBox hexBox;
    private EditBox slotBox;
    private EditBox shareBox;
    private EditBox draftBox;

    // Layout, refreshed every frame.
    private int x0;
    private int y0;
    private int pw;
    private int ph;
    private int bodyTop;
    private int bodyBottom;
    private int contentX;
    private int contentW;
    private int pickerX;

    public ChromaHubScreen(HubDetail detail) {
        super(Component.translatable("screen.nerodecor.chroma_hub"));
        this.detail = detail;
        this.model = modelOf(detail);
        this.view.bind(model, detail, true);
    }

    public long network() {
        return detail.network();
    }

    /** A fresh detail from the server. Unsent edits are kept unless they were just applied. */
    public void refresh(HubDetail next) {
        boolean reframe = next.sizeX() != detail.sizeX() || next.sizeY() != detail.sizeY()
                || next.sizeZ() != detail.sizeZ();
        this.detail = next;
        if (!next.notice().isEmpty()) {
            say(Component.translatable("message.nerodecor.chroma." + next.notice()).getString(), HubTheme.BAD);
            if ("flash_rejected".equals(next.notice())) {
                flashRejected = true;
            }
        }
        if (applied || !model.isDirty()) {
            model = modelOf(next);
            applied = false;
        } else {
            say(tr("status.changed_elsewhere"), HubTheme.WARN);
        }
        view.bind(model, next, reframe);
        selZone = Math.max(0, Math.min(selZone, next.zones().size() - 1));
        if (selEntry >= model.paletteSize()) {
            selEntry = -1;
        }
        selSlot = Math.max(0, Math.min(selSlot, Math.max(0, next.scenes().size() - 1)));
    }

    /**
     * A working copy of {@code d} whose blocks without a hub palette entry show what the world shows: the block's own
     * tint (paint, or the hub colour the client already has), else its map colour. Needs the hub's anchor, which the
     * client knows while it watches the hub's chunk; without it those blocks draw as unpainted.
     */
    private static EditorModel modelOf(HubDetail d) {
        EditorModel m = new EditorModel(d);
        int[] anchor = ChromaClientSync.anchorOf(d.network());
        net.minecraft.client.multiplayer.ClientLevel level = net.minecraft.client.Minecraft.getInstance().level;
        if (anchor != null && level != null) {
            m.setFallbackColours(rel -> {
                net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(anchor[0] + RelativePos.x(rel),
                        anchor[1] + RelativePos.y(rel), anchor[2] + RelativePos.z(rel));
                if (!level.hasChunkAt(pos)) {
                    return -1;
                }
                net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
                if (state.isAir()) {
                    return -1;
                }
                int tint = ChromaTintBridge.tint(state, pos) & 0xFFFFFF;
                return tint != 0xFFFFFF ? tint : state.getMapColor(level, pos).col & 0xFFFFFF;
            });
        }
        return m;
    }

    static String tr(String key, Object... args) {
        return Component.translatable("screen.nerodecor.chroma_hub." + key, args).getString();
    }

    private void say(String message, int colour) {
        status = message;
        statusColour = colour;
        statusTicks = 100;
    }

    // =============================================================================================================
    // Lifecycle and layout
    // =============================================================================================================

    @Override
    protected void init() {
        ChromaHubClient.opened(this);
        layout();
        String name = nameBox == null ? zoneName(selZone) : nameBox.getValue();
        String slot = slotBox == null ? "" : slotBox.getValue();
        String share = shareBox == null ? "" : shareBox.getValue();
        String draftName = draftBox == null ? "" : draftBox.getValue();
        nameBox = box(contentX, bodyBottom - 32, 120, 32, name, tr("hint.zone_name"));
        slotBox = box(contentX, bodyBottom - 50, 120, Scene.MAX_NAME_LENGTH, slot, tr("hint.slot_name"));
        shareBox = box(contentX, bodyBottom - 33, Math.max(60, contentW - 112), SceneCodec.MAX_SHARE_CODE_LENGTH, share,
                tr("hint.share_code"));
        draftBox = box(contentX, bodyBottom - 16, 100, Scene.MAX_NAME_LENGTH, draftName, tr("hint.scene_name"));
        hexBox = box(pickerX, bodyTop + 94, 58, 7, picker.primary().toHex(), "#RRGGBB");
        hexBox.setResponder(this::hexTyped);
        showBoxes();
    }

    @Override
    public void removed() {
        ChromaHubClient.closed(this);
        super.removed();
    }

    @Override
    public void tick() {
        super.tick();
        if (statusTicks > 0 && --statusTicks == 0) {
            status = "";
        }
    }

    private boolean pickerShown() {
        return tab != Tab.ACCESS;
    }

    private void layout() {
        pw = Math.min(width - 12, 468);
        ph = Math.min(height - 12, 276);
        x0 = (width - pw) / 2;
        y0 = (height - ph) / 2;
        bodyTop = y0 + 44;
        bodyBottom = y0 + ph - 22;
        pickerX = x0 + pw - PICKER_W - 10;
        contentX = x0 + 10;
        contentW = (pickerShown() ? pickerX - 10 : x0 + pw - 10) - contentX;
    }

    private void showBoxes() {
        if (nameBox == null) {
            return;
        }
        nameBox.visible = tab == Tab.ZONES;
        boolean scenes = tab == Tab.SCENES && !detail.scenes().isEmpty();
        slotBox.visible = scenes && !composing;
        shareBox.visible = scenes && !composing;
        draftBox.visible = scenes && composing;
        draftBox.setEditable(detail.canPaint());
        hexBox.visible = pickerShown();
        nameBox.setEditable(detail.canManage());
        slotBox.setEditable(detail.canPaint());
    }

    private void setTab(Tab next) {
        tab = next;
        anchor = NO_BLOCK;
        view.setMarks(Set.of());
        setFocused(null);
        layout();
        rebuildWidgets();
    }

    // =============================================================================================================
    // Drawing
    // =============================================================================================================

    @Override
    protected void drawPanel(GuiGraphicsExtractor g, int mx, int my, float partialTick) {
        layout();
        g.fill(0, 0, width, height, HubTheme.SHADE);
        glowPanel(g, x0, y0, pw, ph, HubTheme.CYAN_DIM, HubTheme.PANEL);
        g.fillGradient(x0 + 1, y0 + 1, x0 + pw - 1, y0 + 22, 0xFF13213A, HubTheme.alpha(HubTheme.PANEL, 0xF2));
        brackets(g, x0 + 3, y0 + 3, pw - 6, ph - 6, 7, HubTheme.CYAN);
        drawHeader(g, mx, my);
        drawTabs(g, mx, my);
        switch (tab) {
            case ZONES -> drawZones(g, mx, my);
            case PALETTE -> drawPalette(g, mx, my);
            case SCENES -> drawScenes(g, mx, my);
            case ACCESS -> drawAccess(g, mx, my);
            case VIEW -> drawView(g, mx, my);
        }
        if (pickerShown()) {
            drawPicker(g, mx, my);
        }
        drawFooter(g, mx, my);
    }

    @Override
    protected void drawOverlay(GuiGraphicsExtractor g, int mx, int my) {
        if (tab == Tab.VIEW) {
            int hover = view.hover();
            if (hover != NO_BLOCK && model.contains(hover)) {
                ChromaColor c = model.colourAt(hover);
                String line = tr("view.hover", RelativePos.x(hover), RelativePos.y(hover), RelativePos.z(hover),
                        zoneName(model.zoneAt(hover)), c == null ? "-" : c.toHex());
                int tw = font.width(line) + 8;
                int tx = Math.min(mx + 10, width - tw - 2);
                int ty = Math.max(2, my - 16);
                g.fill(tx, ty, tx + tw, ty + 12, 0xE0070B15);
                g.fill(tx, ty + 11, tx + tw, ty + 12, HubTheme.CYAN_DIM);
                text(g, line, tx + 4, ty + 2, HubTheme.TEXT);
            }
        }
    }

    private void drawHeader(GuiGraphicsExtractor g, int mx, int my) {
        text(g, tr("title"), x0 + 12, y0 + 7, HubTheme.CYAN);
        int tw = font.width(tr("title"));
        g.fill(x0 + 12, y0 + 17, x0 + 12 + tw, y0 + 18, HubTheme.alpha(HubTheme.CYAN, 0x80));
        int right = x0 + pw - 12;
        right = chip(g, right, tr("role." + ROLE_KEYS[Math.min(4, detail.role())]), HubTheme.VIOLET);
        String scene = detail.activeScene() >= 0 && detail.activeScene() < detail.scenes().size()
                ? detail.scenes().get(detail.activeScene()).name() : tr("scene.static");
        right = chip(g, right, tr("chip.scene", scene), HubTheme.CYAN_DIM);
        right = chip(g, right, tr("chip.zones", detail.zones().size(), detail.maxZones()), HubTheme.CYAN_DIM);
        right = chip(g, right, tr("chip.blocks", detail.blockCount(), detail.maxBlocks()), HubTheme.CYAN_DIM);
        chip(g, right, detail.active() ? tr("chip.online") : tr("chip.offline"),
                detail.active() ? HubTheme.OK : HubTheme.BAD);
    }

    /** A small labelled chip ending at {@code right}; returns the next right edge. */
    private int chip(GuiGraphicsExtractor g, int right, String label, int colour) {
        int w = font.width(label) + 8;
        int x = right - w;
        if (x < x0 + 24 + font.width(tr("title"))) {
            return right;
        }
        g.fill(x, y0 + 5, right, y0 + 17, HubTheme.alpha(colour, 0x30));
        g.fill(x, y0 + 16, right, y0 + 17, colour);
        text(g, label, x + 4, y0 + 7, HubTheme.TEXT);
        return x - 4;
    }

    private void drawTabs(GuiGraphicsExtractor g, int mx, int my) {
        int x = x0 + 10;
        int y = y0 + 24;
        g.fill(x0 + 6, y + 15, x0 + pw - 6, y + 16, HubTheme.LINE);
        for (Tab t : Tab.values()) {
            String label = tr("tab." + t.name().toLowerCase(java.util.Locale.ROOT));
            int w = font.width(label) + 16;
            boolean selected = t == tab;
            boolean hot = region(x, y, w, 15, true, mx, my, null, () -> setTab(t));
            if (selected) {
                g.fill(x, y, x + w, y + 15, 0xFF102540);
                g.fill(x, y + 13, x + w, y + 16, HubTheme.CYAN);
                g.fill(x - 1, y + 12, x + w + 1, y + 13, HubTheme.alpha(HubTheme.CYAN, 0x50));
            } else if (hot) {
                g.fill(x, y, x + w, y + 15, HubTheme.ROW_HOVER);
            }
            text(g, label, x + 8, y + 4, selected ? 0xFFFFFFFF : hot ? HubTheme.TEXT : HubTheme.SUBTLE);
            x += w + 2;
        }
    }

    private void sectionLabel(GuiGraphicsExtractor g, String label, int x, int y) {
        sectionLabel(g, label, x, y, Integer.MAX_VALUE);
    }

    /** A section label whose trailing rule stops 4 px short of {@code right} (and is left out if it would not fit). */
    private void sectionLabel(GuiGraphicsExtractor g, String label, int x, int y, int right) {
        text(g, label, x, y, HubTheme.VIOLET);
        int from = x + font.width(label) + 4;
        int to = Math.min(from + 20, right - 4);
        if (to - from >= 6) {
            g.fill(from, y + 4, to, y + 5, HubTheme.VIOLET_DIM);
        }
    }

    /** {@code s} word-wrapped into at most {@code lines} lines of {@code width} pixels; the last one is fitted. */
    private List<String> wrap(String s, int width, int lines) {
        List<String> out = new ArrayList<>();
        String rest = s == null ? "" : s.trim();
        while (!rest.isEmpty() && out.size() < lines - 1 && font.width(rest) > width) {
            int cut = font.plainSubstrByWidth(rest, width).length();
            int space = rest.lastIndexOf(' ', cut);
            int end = space > 0 ? space : Math.max(1, cut);
            out.add(rest.substring(0, end));
            rest = rest.substring(end).trim();
        }
        if (!rest.isEmpty()) {
            out.add(fit(rest, width));
        }
        return out;
    }

    private void textWrapped(GuiGraphicsExtractor g, String s, int x, int y, int width, int colour) {
        textWrapped(g, s, x, y, width, colour, 2);
    }

    private void textWrapped(GuiGraphicsExtractor g, String s, int x, int y, int width, int colour, int maxLines) {
        List<String> lines = wrap(s, width, maxLines);
        for (int i = 0; i < lines.size(); i++) {
            text(g, lines.get(i), x, y + i * 10, colour);
        }
    }

    private static void swatch(GuiGraphicsExtractor g, int x, int y, int w, int h, ChromaColor c) {
        g.fill(x, y, x + w, y + h, 0xFF2A3550);
        if (c == null) {
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF0A0E18);
            g.fill(x + 1, y + h / 2, x + w - 1, y + h / 2 + 1, 0xFF3A2030);
        } else {
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, HubTheme.opaque(c.rgb()));
        }
    }

    // --- zones ---------------------------------------------------------------------------------------------------

    private String zoneName(int zone) {
        return zone >= 0 && zone < detail.zones().size() ? detail.zones().get(zone).name() : "-";
    }

    private void drawZones(GuiGraphicsExtractor g, int mx, int my) {
        int listY = bodyTop + 12;
        int listBottom = bodyBottom - 38;
        sectionLabel(g, tr("zones.header"), contentX, bodyTop);
        textRight(g, tr("zones.columns"), contentX + contentW, bodyTop, HubTheme.FAINT);
        g.fill(contentX, listY, contentX + contentW, listBottom, HubTheme.WELL);
        int[] counts = new int[ZoneAssignment.MAX_ZONES];
        for (int i = 0; i < model.blockCount(); i++) {
            int z = model.zoneAt(model.offset(i));
            if (z >= 0 && z < counts.length) {
                counts[z]++;
            }
        }
        int visible = Math.max(1, (listBottom - listY - 2) / ROW);
        zoneScroll = Math.max(0, Math.min(zoneScroll, detail.zones().size() - visible));
        for (int row = 0; row < visible && zoneScroll + row < detail.zones().size(); row++) {
            int zone = zoneScroll + row;
            HubDetail.Zone z = detail.zones().get(zone);
            int ry = listY + 1 + row * ROW;
            boolean hot = region(contentX + 1, ry, contentW - 2, ROW - 1, true, mx, my, null, () -> {
                selZone = zone;
                nameBox.setValue(z.name());
            });
            g.fill(contentX + 1, ry, contentX + contentW - 1, ry + ROW - 1,
                    zone == selZone ? HubTheme.ROW_SELECTED : hot ? HubTheme.ROW_HOVER : HubTheme.ROW);
            if (zone == selZone) {
                g.fill(contentX + 1, ry, contentX + 3, ry + ROW - 1, HubTheme.CYAN);
            }
            swatch(g, contentX + 6, ry + 2, 10, 9, model.dominantColour(zone));
            text(g, fit(z.name(), contentW - 110), contentX + 21, ry + 3, HubTheme.TEXT);
            textRight(g, tr("zones.blocks", counts[zone]), contentX + contentW - 6, ry + 3, HubTheme.SUBTLE);
            textRight(g, String.format(java.util.Locale.ROOT, "CH %02d", z.channel()), contentX + contentW - 56,
                    ry + 3, HubTheme.CYAN_DIM);
        }
        scrollbar(g, contentX + contentW - 3, listY, listBottom - listY, zoneScroll, visible, detail.zones().size());

        boolean manage = detail.canManage();
        int y1 = bodyBottom - 32;
        well(g, contentX, y1, 120, 14, nameBox.isFocused());
        button(g, contentX + 124, y1, 56, 14, tr("zones.rename"), manage, false, mx, my, tr("hint.rename"),
                () -> ChromaHubClient.send(HubAction.named(network(), HubAction.Kind.RENAME_ZONE, selZone, 0,
                        nameBox.getValue())));
        button(g, contentX + 184, y1, 56, 14, tr("zones.add"),
                manage && detail.zones().size() < detail.maxZones(), false, mx, my, tr("hint.add_zone"),
                () -> ChromaHubClient.send(HubAction.named(network(), HubAction.Kind.ADD_ZONE, 0, 0,
                        nameBox.getValue())));
        int y2 = bodyBottom - 15;
        int channel = selZone < detail.zones().size() ? detail.zones().get(selZone).channel() : 0;
        text(g, tr("zones.channel"), contentX, y2 + 3, HubTheme.SUBTLE);
        int cx = contentX + font.width(tr("zones.channel")) + 6;
        button(g, cx, y2, 14, 14, "-", manage && channel > 0, false, mx, my, null,
                () -> ChromaHubClient.send(HubAction.simple(network(), HubAction.Kind.SET_CHANNEL, selZone,
                        channel - 1)));
        String ch = String.format(java.util.Locale.ROOT, "%02d", channel);
        text(g, ch, cx + 20, y2 + 3, HubTheme.CYAN);
        button(g, cx + 34, y2, 14, 14, "+", manage && channel < ZoneAssignment.MAX_CHANNELS - 1, false, mx, my, null,
                () -> ChromaHubClient.send(HubAction.simple(network(), HubAction.Kind.SET_CHANNEL, selZone,
                        channel + 1)));
        if (!manage) {
            textRight(g, tr("zones.manage_only"), contentX + contentW, y2 + 3, HubTheme.FAINT);
        }
    }

    private void scrollbar(GuiGraphicsExtractor g, int x, int y, int h, int scroll, int visible, int total) {
        if (total <= visible) {
            return;
        }
        int thumb = Math.max(8, h * visible / total);
        int ty = y + (h - thumb) * scroll / Math.max(1, total - visible);
        g.fill(x, y, x + 2, y + h, HubTheme.LINE);
        g.fill(x, ty, x + 2, ty + thumb, HubTheme.CYAN_DIM);
    }

    // --- palette -------------------------------------------------------------------------------------------------

    private void drawPalette(GuiGraphicsExtractor g, int mx, int my) {
        sectionLabel(g, tr("palette.header", model.paletteSize()), contentX, bodyTop);
        int gridY = bodyTop + 12;
        int gridBottom = bodyBottom - 26;
        g.fill(contentX, gridY, contentX + contentW, gridBottom, HubTheme.WELL);
        int step = 14;
        int cols = Math.max(1, (contentW - 6) / step);
        int rows = Math.max(1, (gridBottom - gridY - 4) / step);
        int totalRows = (model.paletteSize() + cols - 1) / cols;
        paletteScroll = Math.max(0, Math.min(paletteScroll, totalRows - rows));
        int[] use = model.paletteUse();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int index = (paletteScroll + r) * cols + c;
                if (index >= model.paletteSize()) {
                    break;
                }
                int sx = contentX + 4 + c * step;
                int sy = gridY + 3 + r * step;
                boolean hot = region(sx, sy, 12, 12, true, mx, my, tr("palette.entry_hint", index, use[index]), () -> {
                    selEntry = index;
                    setPrimary(model.paletteEntry(index));
                });
                if (index == selEntry) {
                    g.fill(sx - 2, sy - 2, sx + 14, sy + 14, HubTheme.CYAN);
                } else if (hot) {
                    g.fill(sx - 1, sy - 1, sx + 13, sy + 13, HubTheme.CYAN_DIM);
                }
                swatch(g, sx, sy, 12, 12, model.paletteEntry(index));
                if (use[index] == 0) {
                    g.fill(sx + 9, sy + 1, sx + 11, sy + 3, 0xFF05070F);
                }
            }
        }
        scrollbar(g, contentX + contentW - 3, gridY, gridBottom - gridY, paletteScroll, rows, totalRows);
        if (model.paletteSize() == 0) {
            int mid = gridY + (gridBottom - gridY) / 2;
            List<String> lines = wrap(tr("palette.empty"), contentW - 24, 2);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                text(g, line, contentX + (contentW - font.width(line)) / 2, mid - lines.size() * 5 + i * 10,
                        HubTheme.SUBTLE);
            }
        }
        String info;
        if (selEntry >= 0 && selEntry < model.paletteSize()) {
            ChromaColor e = model.paletteEntry(selEntry);
            info = tr("palette.info", selEntry, e.toHex(), e.brightness(), use[selEntry]);
        } else {
            info = tr("palette.pick");
        }
        text(g, fit(info, contentW), contentX, bodyBottom - 20, HubTheme.SUBTLE);
        text(g, fit(tr("palette.legend"), contentW), contentX, bodyBottom - 9, HubTheme.FAINT);
    }

    // --- scenes --------------------------------------------------------------------------------------------------

    private void drawScenes(GuiGraphicsExtractor g, int mx, int my) {
        List<HubDetail.Slot> slots = detail.scenes();
        if (slots.isEmpty()) {
            sectionLabel(g, tr("scenes.header"), contentX, bodyTop);
            g.fill(contentX, bodyTop + 12, contentX + contentW, bodyTop + 52, HubTheme.WELL);
            text(g, fit(tr("scenes.none"), contentW - 10), contentX + 6, bodyTop + 20, HubTheme.SUBTLE);
            text(g, fit(tr("scenes.none2"), contentW - 10), contentX + 6, bodyTop + 32, HubTheme.FAINT);
            return;
        }
        // Mode switch: the slot list, or the per-zone composer.
        String composeLabel = tr("scenes.mode.compose");
        String slotsLabel = tr("scenes.mode.slots");
        int cw = font.width(composeLabel) + 12;
        int sw = font.width(slotsLabel) + 12;
        int right = contentX + contentW;
        button(g, right - cw, bodyTop - 3, cw, 13, composeLabel, true, composing, mx, my, tr("hint.compose"),
                () -> setComposing(true));
        button(g, right - cw - 2 - sw, bodyTop - 3, sw, 13, slotsLabel, true, !composing, mx, my, null,
                () -> setComposing(false));
        int labelRight = right - cw - sw - 6;
        if (composing) {
            drawCompose(g, mx, my, labelRight);
        } else {
            drawSlots(g, mx, my, labelRight);
        }
    }

    private void setComposing(boolean on) {
        composing = on;
        setFocused(null);
        showBoxes();
    }

    private void drawSlots(GuiGraphicsExtractor g, int mx, int my, int labelRight) {
        sectionLabel(g, tr("scenes.header"), contentX, bodyTop, labelRight);
        List<HubDetail.Slot> slots = detail.scenes();
        boolean paint = detail.canPaint();
        int listY = bodyTop + 12;
        int listBottom = bodyBottom - 72;
        g.fill(contentX, listY, contentX + contentW, listBottom, HubTheme.WELL);
        int visible = Math.max(1, (listBottom - listY - 2) / ROW);
        slotScroll = Math.max(0, Math.min(slotScroll, slots.size() - visible));
        for (int row = 0; row < visible && slotScroll + row < slots.size(); row++) {
            int slot = slotScroll + row;
            HubDetail.Slot s = slots.get(slot);
            int ry = listY + 1 + row * ROW;
            boolean active = slot == detail.activeScene();
            boolean hot = region(contentX + 1, ry, contentW - 50, ROW - 1, true, mx, my, null, () -> {
                selSlot = slot;
                slotBox.setValue(s.name());
            });
            int body = active ? 0xFF113A33 : slot == selSlot ? HubTheme.ROW_SELECTED
                    : hot ? HubTheme.ROW_HOVER : HubTheme.ROW;
            g.fill(contentX + 1, ry, contentX + contentW - 1, ry + ROW - 1, body);
            if (active) {
                g.fill(contentX + 1, ry, contentX + 3, ry + ROW - 1, HubTheme.OK);
            }
            g.fill(contentX + 6, ry + 4, contentX + 11, ry + 9, active ? HubTheme.OK
                    : s.scene().length > 0 ? HubTheme.CYAN_DIM : HubTheme.FAINT);
            String name = s.name().isEmpty() ? tr("scenes.slot", slot + 1) : s.name();
            text(g, fit(name, contentW - 70), contentX + 15, ry + 3, active ? 0xFFFFFFFF
                    : s.scene().length == 0 ? HubTheme.SUBTLE : HubTheme.TEXT);
            int bx = contentX + contentW - 46;
            if (active) {
                // The live slot: a solid green "LIVE" badge where its Play button would be.
                g.fill(bx, ry + 1, bx + 44, ry + ROW - 2, HubTheme.OK);
                g.fill(bx + 1, ry + 2, bx + 43, ry + ROW - 3, 0xFF145A44);
                String live = tr("scenes.active");
                text(g, live, bx + (44 - font.width(live)) / 2, ry + 3, 0xFFFFFFFF);
            } else {
                accentButton(g, bx, ry + 1, 44, ROW - 3, tr("scenes.play"), paint && s.scene().length > 0, mx, my,
                        null, () -> ChromaHubClient.send(HubAction.simple(network(), HubAction.Kind.SCENE_ACTIVATE,
                                slot, 0)));
            }
        }
        if (flashRejected) {
            flashBanner(g, contentX, listBottom - 24, contentW, 24);
        }
        int y1 = bodyBottom - 68;
        button(g, contentX, y1, 90, 14, tr("scenes.static"), paint && detail.activeScene() >= 0, false, mx, my,
                tr("hint.static"),
                () -> ChromaHubClient.send(HubAction.simple(network(), HubAction.Kind.SCENE_ACTIVATE, -1, 0)));
        int y2 = bodyBottom - 50;
        well(g, contentX, y2, 120, 14, slotBox.isFocused());
        button(g, contentX + 124, y2, 56, 14, tr("scenes.rename"), paint, false, mx, my, null,
                () -> ChromaHubClient.send(HubAction.named(network(), HubAction.Kind.SCENE_RENAME, selSlot, 0,
                        slotBox.getValue())));
        int y3 = bodyBottom - 33;
        int shareW = Math.max(60, contentW - 112);
        well(g, contentX, y3, shareW, 14, shareBox.isFocused());
        button(g, contentX + shareW + 4, y3, 52, 14, tr("scenes.copy"),
                selSlot < slots.size() && slots.get(selSlot).scene().length > 0, false, mx, my, tr("hint.copy"),
                this::copyShareCode);
        button(g, contentX + shareW + 58, y3, 52, 14, tr("scenes.import"), paint, false, mx, my, tr("hint.import"),
                this::importShareCode);
        // Quick effect: one effect on every zone, from the picker's two colours.
        int y4 = bodyBottom - 16;
        int x = contentX;
        button(g, x, y4, 12, 14, "<", true, false, mx, my, null, () -> cycleEffect(-1));
        String label = tr("effect." + effect.name().toLowerCase(java.util.Locale.ROOT));
        int lw = 70;
        g.fill(x + 14, y4, x + 14 + lw, y4 + 14, HubTheme.WELL);
        text(g, fit(label, lw - 4), x + 14 + (lw - font.width(fit(label, lw - 4))) / 2, y4 + 3, HubTheme.CYAN);
        button(g, x + 16 + lw, y4, 12, 14, ">", true, false, mx, my, null, () -> cycleEffect(1));
        x += 32 + lw;
        button(g, x, y4, 12, 14, "-", period > 10, false, mx, my, null, () -> period = Math.max(10, period - 10));
        String secs = String.format(java.util.Locale.ROOT, "%.1fs", period / 20.0);
        text(g, secs, x + 15, y4 + 3, HubTheme.TEXT);
        button(g, x + 40, y4, 12, 14, "+", period < 1200, false, mx, my, null,
                () -> period = Math.min(1200, period + 10));
        x += 56;
        int storeW = contentX + contentW - x;
        if (storeW > 30) {
            accentButton(g, x, y4, storeW, 14, tr("scenes.store"), paint, mx, my, tr("hint.store"),
                    this::storeEffect);
        }
    }

    /** A red banner saying the last scene was refused by the photosensitivity (flash) limit. */
    private void flashBanner(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, HubTheme.BAD);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xF0300A16);
        List<String> lines = wrap(tr("compose.flash_banner"), w - 8, Math.max(1, (h - 2) / 10));
        for (int i = 0; i < lines.size(); i++) {
            text(g, lines.get(i), x + 4, y + 3 + i * 10, 0xFFFFD0DA);
        }
    }

    private void cycleEffect(int dir) {
        EffectType[] all = EffectType.values();
        effect = all[Math.floorMod(effect.ordinal() + dir, all.length)];
    }

    private void storeEffect() {
        List<ZoneAssignment> zones = new ArrayList<>();
        try {
            EffectParams params = EffectParams.builder().primary(picker.primary()).secondary(picker.secondary())
                    .periodTicks(period).build();
            for (int i = 0; i < detail.zones().size() && i < ZoneAssignment.MAX_ZONES; i++) {
                zones.add(new ZoneAssignment(i, detail.zones().get(i).channel(), new EffectProgram(effect, params)));
            }
            Scene scene = new Scene(slotBox.getValue().isBlank() ? label(effect) : slotBox.getValue(), zones);
            sendScene(scene, tr("status.stored"));
        } catch (IllegalArgumentException invalid) {
            say(tr("status.bad_effect"), HubTheme.BAD);
        }
    }

    /**
     * Sends {@code scene} to the selected slot after the same flash check the server runs (same seed: the network
     * id), so a refusal shows at once; the server still re-checks and answers with its own verdict.
     */
    private void sendScene(Scene scene, String okMessage) {
        if (!FlashClamp.check(scene, network()).passed()) {
            flashRejected = true;
            say(Component.translatable("message.nerodecor.chroma.flash_rejected").getString(), HubTheme.BAD);
            return;
        }
        ChromaResult<byte[]> bytes = SceneCodec.encode(scene);
        if (!bytes.isOk()) {
            say(tr("status.too_big"), HubTheme.BAD);
            return;
        }
        flashRejected = false;
        ChromaHubClient.send(HubAction.storeScene(network(), selSlot, bytes.value()));
        say(okMessage, HubTheme.OK);
    }

    private static String label(EffectType type) {
        return tr("effect." + type.name().toLowerCase(java.util.Locale.ROOT));
    }

    // --- scene composer ------------------------------------------------------------------------------------------

    private List<Integer> zoneChannels() {
        List<Integer> out = new ArrayList<>();
        for (HubDetail.Zone z : detail.zones()) {
            out.add(z.channel());
        }
        return out;
    }

    /** The composer's draft, rebuilt when the hub's zone count changed. */
    private SceneDraft draft() {
        int zones = Math.max(1, Math.min(detail.zones().size(), ZoneAssignment.MAX_ZONES));
        if (draft == null || draft.zoneCount() != zones) {
            draft = new SceneDraft(zoneChannels());
            draftZone = 0;
            selKey = 0;
            keyScroll = 0;
        }
        return draft;
    }

    private void edited() {
        flashRejected = false;
    }

    private void selectZone(int dir) {
        draftZone = Math.floorMod(draftZone + dir, draft().zoneCount());
        selKey = 0;
        keyScroll = 0;
    }

    private void drawCompose(GuiGraphicsExtractor g, int mx, int my, int labelRight) {
        SceneDraft d = draft();
        sectionLabel(g, tr("compose.header"), contentX, bodyTop, labelRight);
        draftZone = Math.max(0, Math.min(draftZone, d.zoneCount() - 1));
        SceneDraft.ZoneDraft z = d.zone(draftZone);
        int y = bodyTop + 13;
        button(g, contentX, y, 12, 14, "<", d.zoneCount() > 1, false, mx, my, null, () -> selectZone(-1));
        int zw = 104;
        g.fill(contentX + 14, y, contentX + 14 + zw, y + 14, HubTheme.WELL);
        String zl = fit(tr("compose.zone", draftZone + 1, d.zoneCount(), zoneName(draftZone)), zw - 4);
        text(g, zl, contentX + 14 + (zw - font.width(zl)) / 2, y + 3, HubTheme.CYAN);
        button(g, contentX + 16 + zw, y, 12, 14, ">", d.zoneCount() > 1, false, mx, my, null, () -> selectZone(1));
        int kx = contentX + 32 + zw;
        button(g, kx, y, 44, 14, tr("compose.effect"), true, z.kind() == SceneDraft.Kind.EFFECT, mx, my, null,
                () -> {
                    z.setKind(SceneDraft.Kind.EFFECT);
                    edited();
                });
        button(g, kx + 46, y, 52, 14, tr("compose.timeline"), true, z.kind() == SceneDraft.Kind.TIMELINE, mx, my,
                null, () -> {
                    z.setKind(SceneDraft.Kind.TIMELINE);
                    edited();
                });
        int ax = kx + 102;
        int aw = contentX + contentW - ax;
        if (aw >= 30) {
            button(g, ax, y, aw, 14, tr("compose.all_zones"), d.zoneCount() > 1, false, mx, my, tr("hint.all_zones"),
                    () -> {
                        d.copyToAll(draftZone);
                        edited();
                        say(tr("status.copied_all"), HubTheme.OK);
                    });
        }
        y += 18;
        if (z.kind() == SceneDraft.Kind.EFFECT) {
            drawEffectEditor(g, mx, my, z, y);
        } else {
            drawTimelineEditor(g, mx, my, z, y, bodyBottom - 54);
        }
        drawPreview(g, mx, my, z, bodyBottom - 50);
        drawComposeFooter(g, mx, my);
    }

    /** A "label - value +" stepper; returns the x after it. */
    private int stepper(GuiGraphicsExtractor g, int mx, int my, int x, int y, String label, String value,
            int valueW, boolean canDec, boolean canInc, String hint, Runnable dec, Runnable inc) {
        if (!label.isEmpty()) {
            region(x, y, font.width(label), 14, true, mx, my, hint, null);
            text(g, label, x, y + 3, HubTheme.SUBTLE);
            x += font.width(label) + 3;
        }
        button(g, x, y, 12, 14, "-", canDec, false, mx, my, hint, () -> {
            dec.run();
            edited();
        });
        g.fill(x + 13, y, x + 15 + valueW, y + 14, HubTheme.WELL);
        String v = fit(value, valueW);
        text(g, v, x + 14 + (valueW - font.width(v)) / 2, y + 3, HubTheme.TEXT);
        button(g, x + 16 + valueW, y, 12, 14, "+", canInc, false, mx, my, hint, () -> {
            inc.run();
            edited();
        });
        return x + 32 + valueW;
    }

    private static String seconds(int ticks) {
        return String.format(java.util.Locale.ROOT, "%.2fs", ticks / 20.0);
    }

    private static int periodStep(int ticks) {
        return ticks < 20 ? 1 : ticks < 100 ? 5 : 20;
    }

    private void drawEffectEditor(GuiGraphicsExtractor g, int mx, int my, SceneDraft.ZoneDraft z, int y) {
        int x = contentX;
        text(g, tr("compose.type"), x, y + 3, HubTheme.SUBTLE);
        x += font.width(tr("compose.type")) + 3;
        button(g, x, y, 12, 14, "<", true, false, mx, my, null, () -> {
            z.cycleType(-1);
            edited();
        });
        int lw = 70;
        g.fill(x + 14, y, x + 14 + lw, y + 14, HubTheme.WELL);
        String type = fit(label(z.type()), lw - 4);
        text(g, type, x + 14 + (lw - font.width(type)) / 2, y + 3, HubTheme.CYAN);
        button(g, x + 16 + lw, y, 12, 14, ">", true, false, mx, my, null, () -> {
            z.cycleType(1);
            edited();
        });
        x += 34 + lw;
        int p = z.period();
        stepper(g, mx, my, x, y, tr("compose.period"), seconds(p), 36, p > EffectParams.MIN_PERIOD,
                p < EffectParams.MAX_PERIOD, tr("hint.period"), () -> z.setPeriod(p - periodStep(p - 1)),
                () -> z.setPeriod(p + periodStep(p)));
        y += 18;
        x = stepper(g, mx, my, contentX, y, tr("compose.amplitude"), Integer.toString(z.amplitude()), 24,
                z.amplitude() > 0, z.amplitude() < EffectParams.MAX_UNIT, tr("hint.amplitude"),
                () -> z.setAmplitude(z.amplitude() - 15), () -> z.setAmplitude(z.amplitude() + 15));
        String axisLabel = tr("compose.axis", "XYZ".charAt(z.axis()));
        int axw = font.width(axisLabel) + 12;
        button(g, x + 6, y, axw, 14, axisLabel, true, false, mx, my, tr("hint.axis"), () -> {
            z.cycleAxis();
            edited();
        });
        y += 18;
        x = stepper(g, mx, my, contentX, y, tr("compose.wavelength"), Integer.toString(z.wavelength()), 24,
                z.wavelength() > EffectParams.MIN_WAVELENGTH, z.wavelength() < EffectParams.MAX_WAVELENGTH,
                tr("hint.wavelength"), () -> z.setWavelength(z.wavelength() - 1),
                () -> z.setWavelength(z.wavelength() + 1));
        stepper(g, mx, my, x + 6, y, tr("compose.density"), Integer.toString(z.density()), 24, z.density() > 0,
                z.density() < EffectParams.MAX_UNIT, tr("hint.density"), () -> z.setDensity(z.density() - 15),
                () -> z.setDensity(z.density() + 15));
        y += 18;
        String coloursLabel = tr("compose.colours");
        int cbw = font.width(coloursLabel) + 14;
        accentButton(g, contentX, y, cbw, 14, coloursLabel, true, mx, my, tr("hint.colours"), () -> {
            z.setColours(picker.primary(), picker.secondary());
            edited();
        });
        int sx = contentX + cbw + 6;
        text(g, "A", sx, y + 3, HubTheme.SUBTLE);
        swatch(g, sx + 8, y, 18, 14, z.primary());
        text(g, "B", sx + 32, y + 3, HubTheme.SUBTLE);
        swatch(g, sx + 40, y, 18, 14, z.secondary());
    }

    private void drawTimelineEditor(GuiGraphicsExtractor g, int mx, int my, SceneDraft.ZoneDraft z, int y,
            int bottom) {
        int listW = Math.min(150, contentW / 2);
        int rows = Math.max(1, (bottom - y - 2) / ROW);
        selKey = Math.max(0, Math.min(selKey, z.keyCount() - 1));
        keyScroll = Math.max(0, Math.min(keyScroll, z.keyCount() - rows));
        g.fill(contentX, y, contentX + listW, y + rows * ROW + 2, HubTheme.WELL);
        for (int row = 0; row < rows && keyScroll + row < z.keyCount(); row++) {
            int k = keyScroll + row;
            SceneDraft.Key key = z.key(k);
            int ry = y + 1 + row * ROW;
            boolean hot = region(contentX + 1, ry, listW - 2, ROW - 1, true, mx, my, null, () -> {
                selKey = k;
                setPrimary(z.key(k).colour());
            });
            g.fill(contentX + 1, ry, contentX + listW - 1, ry + ROW - 1,
                    k == selKey ? HubTheme.ROW_SELECTED : hot ? HubTheme.ROW_HOVER : HubTheme.ROW);
            text(g, Integer.toString(k + 1), contentX + 4, ry + 3, HubTheme.FAINT);
            swatch(g, contentX + 18, ry + 2, 14, 9, key.colour());
            text(g, fit(seconds(key.tick()), 40), contentX + 36, ry + 3, HubTheme.TEXT);
            String ease = fit(tr("easing." + key.easing().name().toLowerCase(java.util.Locale.ROOT)), listW - 84);
            textRight(g, ease, contentX + listW - 4, ry + 3, HubTheme.SUBTLE);
        }
        scrollbar(g, contentX + listW - 3, y + 1, rows * ROW, keyScroll, rows, z.keyCount());
        int x = contentX + listW + 6;
        int w = contentX + contentW - x;
        int k = selKey;
        // Row 0: which key, and reorder.
        text(g, fit(tr("compose.key", k + 1, z.keyCount()), w - 64), x, y + 3, HubTheme.VIOLET);
        button(g, x + w - 60, y, 29, 14, tr("compose.up"), k > 0, false, mx, my, tr("hint.move_key"), () -> {
            selKey = Math.max(0, z.moveKey(selKey, -1));
            edited();
        });
        button(g, x + w - 29, y, 29, 14, tr("compose.down"), k < z.keyCount() - 1, false, mx, my, tr("hint.move_key"),
                () -> {
                    int moved = z.moveKey(selKey, 1);
                    selKey = moved < 0 ? selKey : moved;
                    edited();
                });
        y += 16;
        int tick = z.key(k).tick();
        button(g, x, y, 14, 14, "<<", true, false, mx, my, tr("hint.tick10"), () -> {
            z.setTick(selKey, tick - 10);
            edited();
        });
        stepper(g, mx, my, x + 16, y, "", seconds(tick), Math.max(30, w - 64), true, true, tr("hint.tick"),
                () -> z.setTick(selKey, tick - 1), () -> z.setTick(selKey, tick + 1));
        button(g, x + w - 14, y, 14, 14, ">>", true, false, mx, my, tr("hint.tick10"), () -> {
            z.setTick(selKey, tick + 10);
            edited();
        });
        y += 16;
        String easeLabel = tr("compose.easing");
        text(g, easeLabel, x, y + 3, HubTheme.SUBTLE);
        int ex = x + font.width(easeLabel) + 3;
        int elw = Math.max(30, w - (ex - x) - 28);
        button(g, ex, y, 12, 14, "<", true, false, mx, my, tr("hint.easing"), () -> {
            z.cycleEasing(selKey, -1);
            edited();
        });
        g.fill(ex + 14, y, ex + 14 + elw, y + 14, HubTheme.WELL);
        String eased = fit(tr("easing." + z.key(k).easing().name().toLowerCase(java.util.Locale.ROOT)), elw - 4);
        text(g, eased, ex + 14 + (elw - font.width(eased)) / 2, y + 3, HubTheme.CYAN);
        button(g, ex + 16 + elw, y, 12, 14, ">", true, false, mx, my, tr("hint.easing"), () -> {
            z.cycleEasing(selKey, 1);
            edited();
        });
        y += 16;
        accentButton(g, x, y, w, 14, tr("compose.key_colour"), true, mx, my, tr("hint.key_colour"), () -> {
            z.setKeyColour(selKey, picker.primary());
            edited();
        });
        y += 16;
        int half = (w - 2) / 2;
        button(g, x, y, half, 14, tr("compose.add_key"), z.keyCount() < Timeline.MAX_KEYS, false, mx, my,
                tr("hint.add_key"), () -> {
                    int added = z.addKeyAfter(selKey, picker.primary());
                    if (added < 0) {
                        say(tr("status.key_full"), HubTheme.WARN);
                    } else {
                        selKey = added;
                        edited();
                    }
                });
        button(g, x + half + 2, y, w - half - 2, 14, tr("compose.remove_key"), z.keyCount() > 1, false, mx, my,
                null, () -> {
                    z.removeKey(selKey);
                    selKey = Math.max(0, selKey - 1);
                    edited();
                });
        y += 16;
        int len = z.length();
        stepper(g, mx, my, x, y, tr("compose.length"), seconds(len), Math.max(30, w - 32 - font.width(
                tr("compose.length")) - 3), len > 1, len < Timeline.MAX_TICKS, tr("hint.length"),
                () -> z.setLength(len - 10), () -> z.setLength(len + 10));
        y += 16;
        button(g, x, y, w, 14, z.loops() ? tr("compose.loop_on") : tr("compose.loop_off"), true, z.loops(), mx, my,
                tr("hint.loop"), () -> {
                    z.setLoop(!z.loops());
                    edited();
                });
    }

    /** Swatch strips sampled from the zone's program (the same code the blocks run), plus a live swatch. */
    private void drawPreview(GuiGraphicsExtractor g, int mx, int my, SceneDraft.ZoneDraft z, int y) {
        int cycle = Math.max(1, z.cycleTicks());
        String header = tr("compose.preview");
        text(g, header, contentX, y, HubTheme.VIOLET);
        text(g, tr("compose.cycle", seconds(cycle)), contentX + font.width(header) + 6, y, HubTheme.FAINT);
        int stripX = contentX;
        int stripW = contentW - 26;
        int cells = Math.max(8, stripW / 4);
        long seed = network();
        long now = System.nanoTime() / 50_000_000L; // one game tick per 50 ms, independent of the pause state
        long phase = Math.floorMod(now, (long) cycle);
        ChromaColor[] time = z.timeStrip(cells, seed);
        ChromaColor[] space = z.spaceStrip(cells, now, seed);
        int cw = stripW / cells;
        for (int i = 0; i < cells; i++) {
            fillCell(g, stripX + i * cw, y + 10, cw, 8, time[i]);
            fillCell(g, stripX + i * cw, y + 20, cw, 8, space[i]);
        }
        int marker = stripX + (int) (phase * (cells * cw) / cycle);
        g.fill(marker, y + 9, marker + 1, y + 19, 0xFFFFFFFF);
        region(stripX, y + 10, cells * cw, 8, true, mx, my, tr("hint.time_strip"), null);
        region(stripX, y + 20, cells * cw, 8, true, mx, my, tr("hint.space_strip"), null);
        ChromaColor live = liveColour(z, now, seed);
        swatch(g, contentX + contentW - 22, y + 9, 22, 20, live);
        region(contentX + contentW - 22, y + 9, 22, 20, true, mx, my, tr("hint.live"), null);
        if (flashRejected) {
            flashBanner(g, contentX, y + 8, contentW, 22);
        }
    }

    private static ChromaColor liveColour(SceneDraft.ZoneDraft z, long now, long seed) {
        try {
            return z.program().colourAt(now, 0, 0, 0, seed);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    private static void fillCell(GuiGraphicsExtractor g, int x, int y, int w, int h, ChromaColor c) {
        g.fill(x, y, x + w, y + h, c == null ? 0xFF0A0E18 : HubTheme.opaque(c.rgb()));
    }

    private void drawComposeFooter(GuiGraphicsExtractor g, int mx, int my) {
        boolean paint = detail.canPaint();
        List<HubDetail.Slot> slots = detail.scenes();
        selSlot = Math.max(0, Math.min(selSlot, slots.size() - 1));
        int y = bodyBottom - 16;
        int x = contentX;
        well(g, x, y, 100, 14, draftBox.isFocused());
        x += 104;
        button(g, x, y, 12, 14, "<", slots.size() > 1, false, mx, my, null,
                () -> selSlot = Math.floorMod(selSlot - 1, slots.size()));
        String slot = fit(tr("compose.slot", selSlot + 1), 40);
        text(g, slot, x + 14 + (40 - font.width(slot)) / 2, y + 3, HubTheme.TEXT);
        button(g, x + 56, y, 12, 14, ">", slots.size() > 1, false, mx, my, null,
                () -> selSlot = Math.floorMod(selSlot + 1, slots.size()));
        x += 72;
        boolean hasScene = slots.get(selSlot).scene().length > 0;
        button(g, x, y, 36, 14, tr("compose.load"), hasScene, false, mx, my, tr("hint.load"), this::loadDraft);
        x += 40;
        int storeW = contentX + contentW - x;
        if (storeW > 24) {
            accentButton(g, x, y, storeW, 14, tr("compose.store"), paint, mx, my, tr("hint.store_scene"),
                    this::storeDraft);
        }
    }

    private void loadDraft() {
        List<HubDetail.Slot> slots = detail.scenes();
        if (selSlot >= slots.size() || slots.get(selSlot).scene().length == 0) {
            return;
        }
        ChromaResult<Scene> scene = SceneCodec.decode(slots.get(selSlot).scene());
        if (!scene.isOk()) {
            say(tr("status.bad_code"), HubTheme.BAD);
            return;
        }
        draft = SceneDraft.fromScene(scene.value(), zoneChannels());
        draftZone = 0;
        selKey = 0;
        keyScroll = 0;
        draftBox.setValue(scene.value().name());
        edited();
        say(tr("status.loaded"), HubTheme.OK);
    }

    private void storeDraft() {
        SceneDraft d = draft();
        d.setName(draftBox.getValue());
        if (d.validate() != null) {
            say(tr("status.bad_effect"), HubTheme.BAD);
            return;
        }
        try {
            sendScene(d.build(tr("compose.default_name")), tr("status.stored_scene", selSlot + 1));
        } catch (IllegalArgumentException invalid) {
            say(tr("status.bad_effect"), HubTheme.BAD);
        }
    }

    private void copyShareCode() {
        List<HubDetail.Slot> slots = detail.scenes();
        if (selSlot >= slots.size()) {
            return;
        }
        ChromaResult<String> code = SceneChipCodec.toShareCode(slots.get(selSlot).scene());
        if (code.isOk()) {
            minecraft.keyboardHandler.setClipboard(code.value());
            shareBox.setValue(code.value());
            say(tr("status.copied"), HubTheme.OK);
        } else {
            say(tr("status.bad_code"), HubTheme.BAD);
        }
    }

    private void importShareCode() {
        String text = shareBox.getValue().isBlank() ? minecraft.keyboardHandler.getClipboard() : shareBox.getValue();
        ChromaResult<byte[]> bytes = SceneChipCodec.fromShareCode(text);
        if (bytes.isOk()) {
            ChromaHubClient.send(HubAction.storeScene(network(), selSlot, bytes.value()));
            say(tr("status.imported"), HubTheme.OK);
        } else {
            say(tr("status.bad_code"), HubTheme.BAD);
        }
    }

    // --- access --------------------------------------------------------------------------------------------------

    private Map<UUID, String> onlineNames() {
        Map<UUID, String> out = new LinkedHashMap<>();
        ClientPacketListener connection = minecraft.getConnection();
        if (connection == null) {
            return out;
        }
        for (PlayerInfo info : connection.getOnlinePlayers()) {
            //? if >=1.21.9 {
            out.put(info.getProfile().id(), info.getProfile().name());
            //?} else {
            /*out.put(info.getProfile().getId(), info.getProfile().getName());
            *///?}
        }
        return out;
    }

    private void drawAccess(GuiGraphicsExtractor g, int mx, int my) {
        if (!detail.canManage()) {
            sectionLabel(g, tr("access.header"), contentX, bodyTop);
            g.fill(contentX, bodyTop + 12, contentX + contentW, bodyTop + 40, HubTheme.WELL);
            text(g, fit(tr("access.manage_only"), contentW - 12), contentX + 6, bodyTop + 22, HubTheme.SUBTLE);
            return;
        }
        boolean trust = detail.canTrust();
        button(g, contentX + contentW - 120, bodyTop - 2, 120, 13,
                tr(detail.autoAdopt() ? "access.auto_on" : "access.auto_off"), true, detail.autoAdopt(), mx, my,
                tr("hint.auto_adopt"), () -> ChromaHubClient.send(HubAction.simple(network(),
                        HubAction.Kind.SET_AUTO_ADOPT, 0, detail.autoAdopt() ? 0 : 1)));
        Map<UUID, String> names = onlineNames();
        int colW = (contentW - 8) / 2;
        int listY = bodyTop + 14;
        int listBottom = bodyBottom - 4;
        int visible = Math.max(1, (listBottom - listY - 2) / ROW);

        int ruleEnd = contentX + contentW - 124; // the Auto-adopt button
        List<HubDetail.Trust> trusted = detail.trusted();
        sectionLabel(g, tr("access.trusted_count", trusted.size(), HubDetail.MAX_TRUSTED), contentX, bodyTop,
                Math.min(ruleEnd, contentX + colW));
        g.fill(contentX, listY, contentX + colW, listBottom, HubTheme.WELL);
        trustScroll = Math.max(0, Math.min(trustScroll, trusted.size() - visible));
        if (trusted.isEmpty()) {
            text(g, fit(tr("access.nobody"), colW - 10), contentX + 6, listY + 5, HubTheme.FAINT);
        }
        for (int row = 0; row < visible && trustScroll + row < trusted.size(); row++) {
            HubDetail.Trust t = trusted.get(trustScroll + row);
            int ry = listY + 1 + row * ROW;
            g.fill(contentX + 1, ry, contentX + colW - 1, ry + ROW - 1, HubTheme.ROW);
            // Offline players have no name here; a short id tells entries apart so each can be revoked.
            String name = names.getOrDefault(t.player(),
                    tr("access.offline_id", t.player().toString().substring(0, 8)));
            text(g, fit(name, colW - 84), contentX + 5, ry + 3, names.containsKey(t.player()) ? HubTheme.TEXT
                    : HubTheme.SUBTLE);
            roleButtons(g, contentX + colW - 78, ry + 1, t.player(), t.role(), trust, true, mx, my);
        }
        int ox = contentX + colW + 8;
        sectionLabel(g, tr("access.online"), ox, bodyTop, ruleEnd);
        g.fill(ox, listY, ox + colW, listBottom, HubTheme.WELL);
        List<Map.Entry<UUID, String>> others = new ArrayList<>();
        UUID self = minecraft.player == null ? null : minecraft.player.getUUID();
        for (Map.Entry<UUID, String> e : names.entrySet()) {
            boolean known = trusted.stream().anyMatch(t -> t.player().equals(e.getKey()));
            if (!e.getKey().equals(self) && !known) {
                others.add(e);
            }
        }
        onlineScroll = Math.max(0, Math.min(onlineScroll, others.size() - visible));
        if (others.isEmpty()) {
            text(g, fit(tr("access.no_one_online"), colW - 10), ox + 6, listY + 5, HubTheme.FAINT);
        }
        for (int row = 0; row < visible && onlineScroll + row < others.size(); row++) {
            Map.Entry<UUID, String> e = others.get(onlineScroll + row);
            int ry = listY + 1 + row * ROW;
            g.fill(ox + 1, ry, ox + colW - 1, ry + ROW - 1, HubTheme.ROW);
            text(g, fit(e.getValue(), colW - 70), ox + 5, ry + 3, HubTheme.TEXT);
            roleButtons(g, ox + colW - 64, ry + 1, e.getKey(), 0, trust, false, mx, my);
        }
        if (!trust) {
            textRight(g, tr("access.owner_only"), contentX + contentW, bodyBottom + 1, HubTheme.FAINT);
        }
    }

    private void roleButtons(GuiGraphicsExtractor g, int x, int y, UUID player, int current, boolean enabled,
            boolean revoke, int mx, int my) {
        String[] labels = {tr("access.v"), tr("access.p"), tr("access.a")};
        for (int r = 1; r <= 3; r++) {
            int role = r;
            button(g, x + (r - 1) * 20, y, 18, ROW - 3, labels[r - 1], enabled && current != role, current == role,
                    mx, my, tr("role." + ROLE_KEYS[role]),
                    () -> ChromaHubClient.send(HubAction.trust(network(), player, role)));
        }
        if (revoke) {
            button(g, x + 62, y, 14, ROW - 3, "x", enabled, false, mx, my, tr("hint.revoke"),
                    () -> ChromaHubClient.send(HubAction.trust(network(), player, 0)));
        }
    }

    // --- 3D view -------------------------------------------------------------------------------------------------

    private void drawView(GuiGraphicsExtractor g, int mx, int my) {
        int x = contentX;
        int y = bodyTop - 2;
        for (Tool t : Tool.values()) {
            String label = tr("tool." + t.name().toLowerCase(java.util.Locale.ROOT));
            int w = Math.max(28, font.width(label) + 8);
            if (x + w > contentX + contentW) {
                break;
            }
            button(g, x, y, w, 13, label, true, tool == t, mx, my, tr("tool." + t.name().toLowerCase(
                    java.util.Locale.ROOT) + ".hint"), () -> {
                        tool = t;
                        anchor = NO_BLOCK;
                        view.setMarks(Set.of());
                    });
            x += w + 2;
        }
        x = contentX;
        y = bodyTop + 13;
        String mode = zoneMode ? tr("view.mode_zone", fit(zoneName(selZone), 60)) : tr("view.mode_colour");
        int mw = font.width(mode) + 10;
        button(g, x, y, mw, 13, mode, true, zoneMode, mx, my, tr("view.mode.hint"), () -> zoneMode = !zoneMode);
        x += mw + 2;
        if (tool == Tool.FILL) {
            String m = tr("view.match." + match.name().toLowerCase(java.util.Locale.ROOT));
            int w = font.width(m) + 10;
            button(g, x, y, w, 13, m, true, false, mx, my, tr("view.match.hint"), () -> {
                Selections.FloodMatch[] all = Selections.FloodMatch.values();
                match = all[(match.ordinal() + 1) % all.length];
            });
            x += w + 2;
        }
        if (tool == Tool.AXIS) {
            String a = tr("view.axis", "XYZ".charAt(axis));
            int w = font.width(a) + 10;
            button(g, x, y, w, 13, a, true, false, mx, my, null, () -> axis = (axis + 1) % 3);
            x += w + 2;
        }
        button(g, contentX + contentW - 40, y, 40, 13, tr("view.frame"), true, false, mx, my, tr("view.frame.hint"),
                view::frame);

        int vy = bodyTop + 29;
        int vh = bodyBottom - vy - 14; // the controls hint sits below the viewport
        int vw = contentW - 14;
        view.layout(contentX, vy, vw, vh, pixelScale());
        if (drag == Drag.NONE || drag == Drag.BRUSH) {
            view.updateHover(mx, my);
        }
        g.fill(contentX - 1, vy - 1, contentX + vw + 1, vy + vh + 1, HubTheme.BUTTON_EDGE);
        view.draw(g);
        brackets(g, contentX + 2, vy + 2, vw - 4, vh - 4, 6, HubTheme.alpha(HubTheme.CYAN, 0xA0));
        text(g, tr("view.count", model.blockCount()), contentX + 6, vy + 5, HubTheme.SUBTLE);
        if (view.lod()) {
            String lod = tr("view.lod");
            g.fill(contentX + vw - font.width(lod) - 12, vy + 4, contentX + vw - 4, vy + 15,
                    HubTheme.alpha(HubTheme.VIOLET, 0x60));
            textRight(g, lod, contentX + vw - 8, vy + 6, HubTheme.TEXT);
        }
        String help = anchor != NO_BLOCK ? tr("view.second_point") : tr("view.controls");
        text(g, fit(help, vw), contentX, vy + vh + 3, HubTheme.FAINT);

        // Y slice slider.
        int sx = contentX + vw + 4;
        g.fill(sx, vy, sx + 8, vy + vh, HubTheme.WELL);
        int span = Math.max(1, view.maxY() - view.minY());
        int hy = vy + (vh - 6) * (view.maxY() - view.sliceY()) / span;
        g.fill(sx + 3, vy + 2, sx + 5, vy + vh - 2, HubTheme.LINE);
        g.fill(sx, hy, sx + 8, hy + 6, drag == Drag.SLICE ? HubTheme.CYAN : HubTheme.CYAN_DIM);
        region(sx, vy, 8, vh, true, mx, my, tr("view.slice", view.sliceY()), null);
    }

    private void sliceFromMouse(double my) {
        int vy = view.y();
        int vh = view.height();
        double t = (my - vy) / Math.max(1.0, vh - 6.0);
        view.setSlice(view.maxY() - (int) Math.round(t * (view.maxY() - view.minY())));
    }

    // --- picker --------------------------------------------------------------------------------------------------

    private void drawPicker(GuiGraphicsExtractor g, int mx, int my) {
        int px = pickerX;
        int py = bodyTop;
        g.fill(px - 6, bodyTop - 4, px - 5, bodyBottom, HubTheme.LINE);
        // Saturation (x) / value (y) square for the current hue.
        for (int col = 0; col < 96; col++) {
            ChromaColor top = ChromaColor.fromHsv(picker.hue(), col / 95.0, 1.0, 15);
            g.fillGradient(px + col, py, px + col + 1, py + 64, HubTheme.opaque(top.rgb()), 0xFF000000);
        }
        int cx = px + (int) Math.round(picker.saturation() * 95);
        int cy = py + (int) Math.round((1.0 - picker.value()) * 63);
        g.fill(cx - 2, cy - 2, cx + 3, cy + 3, 0xFFFFFFFF);
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, HubTheme.opaque(picker.primary().rgb()));
        // Hue bar.
        for (int row = 0; row < 64; row++) {
            g.fill(px + 100, py + row, px + 114, py + row + 1,
                    HubTheme.opaque(ChromaColor.fromHsv(row / 64.0, 1.0, 1.0, 15).rgb()));
        }
        int hy = py + (int) Math.round(picker.hue() * 63);
        g.fill(px + 98, hy - 1, px + 116, hy + 2, 0xFFFFFFFF);
        g.fill(px + 99, hy, px + 115, hy + 1, 0xFF05070F);
        region(px, py, 96, 64, true, mx, my, tr("picker.sv"), null);
        region(px + 98, py, 18, 64, true, mx, my, tr("picker.hue"), null);
        // Light level (0-15): the block's emitted light.
        int ly = py + 70;
        for (int i = 0; i < 16; i++) {
            int sx = px + i * 7;
            boolean lit = i <= picker.brightness();
            g.fill(sx, ly, sx + 6, ly + 8, lit ? HubTheme.alpha(HubTheme.CYAN, 0x60 + i * 9) : HubTheme.WELL);
        }
        region(px, ly, 112, 8, true, mx, my, tr("picker.light"), null);
        text(g, tr("picker.light_value", picker.brightness()), px, py + 81, HubTheme.SUBTLE);
        // Hex box and the two colours.
        well(g, px, py + 94, 58, 14, hexBox.isFocused());
        ChromaColor primary = picker.primary();
        g.fill(px + 62, py + 92, px + 88, py + 110, HubTheme.CYAN);
        g.fill(px + 63, py + 93, px + 87, py + 109, HubTheme.opaque(primary.rgb()));
        boolean hot = region(px + 90, py + 92, 24, 18, true, mx, my, tr("picker.swap"), () -> {
            picker.swap();
            syncHex();
        });
        g.fill(px + 90, py + 92, px + 114, py + 110, hot ? HubTheme.CYAN_DIM : HubTheme.BUTTON_EDGE);
        g.fill(px + 91, py + 93, px + 113, py + 109, HubTheme.opaque(picker.secondary().rgb()));
        text(g, "A", px + 65, py + 96, HubTheme.ink(primary.rgb()));
        text(g, "B", px + 93, py + 96, HubTheme.ink(picker.secondary().rgb()));
        // Context action.
        int ay = py + 116;
        boolean paint = detail.canPaint();
        if (tab == Tab.ZONES) {
            button(g, px, ay, PICKER_W, 16, tr("picker.paint_zone"), paint && !model.zoneBlocks(selZone).isEmpty(),
                    false, mx, my, tr("hint.paint_zone"), () -> {
                        model.begin();
                        int done = model.recolourZone(selZone, picker.primary());
                        after(done);
                    });
        } else if (tab == Tab.PALETTE) {
            button(g, px, ay, PICKER_W, 16, tr("picker.set_entry", Math.max(0, selEntry)), paint && selEntry >= 0,
                    false, mx, my, tr("hint.set_entry"), () -> {
                        model.begin();
                        model.setPaletteEntry(selEntry, picker.primary());
                        view.invalidate();
                    });
        } else if (tab == Tab.SCENES) {
            textWrapped(g, tr(composing ? "picker.compose_note" : "picker.scene_note"), px, ay + 4, PICKER_W,
                    HubTheme.FAINT, Math.max(2, (bodyBottom - ay - 4) / 10));
        } else if (tab == Tab.VIEW) {
            textWrapped(g, tr("picker.gradient_note"), px, ay + 4, PICKER_W, HubTheme.FAINT);
        }
    }

    private void pickerDrag(double mx, double my) {
        int px = pickerX;
        int py = bodyTop;
        switch (drag) {
            case SV -> picker.setSaturationValue((mx - px) / 95.0, 1.0 - (my - py) / 63.0);
            case HUE -> picker.setHue((my - py) / 64.0);
            case LIGHT -> picker.setBrightness((int) Math.floor((mx - px) / 7.0));
            default -> {
                return;
            }
        }
        syncHex();
    }

    private void setPrimary(ChromaColor colour) {
        if (colour != null) {
            picker.set(colour);
            syncHex();
        }
    }

    private void syncHex() {
        if (hexBox != null) {
            syncingHex = true;
            hexBox.setValue(picker.primary().toHex());
            syncingHex = false;
        }
    }

    private void hexTyped(String value) {
        if (syncingHex) {
            return;
        }
        Optional<ChromaColor> parsed = ChromaColor.parseHex(value);
        parsed.ifPresent(c -> picker.set(c.withBrightness(picker.brightness())));
    }

    // --- footer --------------------------------------------------------------------------------------------------

    private void drawFooter(GuiGraphicsExtractor g, int mx, int my) {
        int fy = y0 + ph - 18;
        g.fill(x0 + 6, fy - 2, x0 + pw - 6, fy - 1, HubTheme.LINE);
        int pending = model.pendingOps();
        boolean paint = detail.canPaint();
        int right = x0 + pw - 10;
        String applyLabel = pending > 0 ? tr("footer.apply_n", pending) : tr("footer.apply");
        int aw = font.width(applyLabel) + 14;
        button(g, right - aw, fy, aw, 14, applyLabel, paint && pending > 0, pending > 0, mx, my, tr("hint.apply"),
                this::apply);
        right -= aw + 3;
        button(g, right - 44, fy, 44, 14, tr("footer.revert"), pending > 0, false, mx, my, null, () -> {
            model.revert();
            view.invalidate();
        });
        right -= 47;
        button(g, right - 36, fy, 36, 14, tr("footer.redo"), model.canRedo(), false, mx, my, "Ctrl+Y", this::redo);
        right -= 39;
        button(g, right - 36, fy, 36, 14, tr("footer.undo"), model.canUndo(), false, mx, my, "Ctrl+Z", this::undo);
        right -= 42;
        String line;
        int colour;
        if (hoverHint != null) {
            line = hoverHint;
            colour = HubTheme.SUBTLE;
        } else if (!status.isEmpty()) {
            line = status;
            colour = statusColour;
        } else if (!paint) {
            line = tr("status.view_only");
            colour = HubTheme.FAINT;
        } else {
            line = "";
            colour = HubTheme.FAINT;
        }
        text(g, fit(line, right - x0 - 12), x0 + 12, fy + 3, colour);
    }

    private void apply() {
        List<ChromaOpBatch> batches = model.buildBatches();
        if (batches.isEmpty()) {
            return;
        }
        int ops = model.pendingOps();
        ChromaHubClient.sendOps(network(), batches);
        applied = true;
        say(batches.size() == 1 ? tr("status.sent", ops) : tr("status.sent_batches", ops, batches.size()),
                HubTheme.OK);
    }

    private void undo() {
        if (model.undo()) {
            view.invalidate();
        }
    }

    private void redo() {
        if (model.redo()) {
            view.invalidate();
        }
    }

    private void after(int result) {
        view.invalidate();
        if (result < 0) {
            say(tr("status.palette_full"), HubTheme.BAD);
        }
    }

    // =============================================================================================================
    // Input
    // =============================================================================================================

    @Override
    protected boolean onClick(double mx, double my, int button) {
        if (pickerShown() && button == LEFT) {
            if (inside(mx, my, pickerX, bodyTop, 96, 64)) {
                drag = Drag.SV;
            } else if (inside(mx, my, pickerX + 98, bodyTop, 18, 64)) {
                drag = Drag.HUE;
            } else if (inside(mx, my, pickerX, bodyTop + 70, 112, 8)) {
                drag = Drag.LIGHT;
            }
            if (drag != Drag.NONE) {
                pickerDrag(mx, my);
                return true;
            }
        }
        if (tab != Tab.VIEW) {
            return false;
        }
        int sx = view.x() + view.width() + 4;
        if (button == LEFT && inside(mx, my, sx, view.y(), 8, view.height())) {
            drag = Drag.SLICE;
            sliceFromMouse(my);
            return true;
        }
        if (!view.contains(mx, my)) {
            return false;
        }
        if (button == RIGHT) {
            drag = Drag.ORBIT;
            return true;
        }
        if (button == MIDDLE) {
            drag = Drag.PAN;
            return true;
        }
        if (button != LEFT) {
            return false;
        }
        if (tool == Tool.ORBIT) {
            drag = Drag.ORBIT;
            return true;
        }
        int hit = view.pick(mx, my);
        if (hit == NO_BLOCK) {
            return true;
        }
        useTool(hit);
        return true;
    }

    private void useTool(int hit) {
        if (tool == Tool.PICK) {
            setPrimary(model.colourAt(hit));
            return;
        }
        if (!detail.canPaint()) {
            say(tr("status.view_only"), HubTheme.WARN);
            return;
        }
        int slice = view.sliceY();
        switch (tool) {
            case BRUSH -> {
                model.begin();
                lastBrushed = NO_BLOCK;
                brush(hit);
                drag = Drag.BRUSH;
            }
            case BOX -> {
                if (anchor == NO_BLOCK) {
                    anchor = hit;
                    view.setMarks(Set.of(hit));
                } else {
                    List<Integer> box = Selections.box(model, RelativePos.x(anchor), RelativePos.y(anchor),
                            RelativePos.z(anchor), RelativePos.x(hit), RelativePos.y(hit), RelativePos.z(hit), slice);
                    applySelection(box);
                    clearAnchor();
                }
            }
            case FILL -> applySelection(Selections.flood(model, hit, match, slice, HubDetail.MAX_BLOCKS));
            case GRADIENT -> {
                if (anchor == NO_BLOCK) {
                    anchor = hit;
                    view.setMarks(Set.of(hit));
                } else {
                    List<Integer> blocks = sliceFilter(model.zoneBlocks(model.zoneAt(anchor)), slice);
                    model.begin();
                    after(model.paintEach(Selections.twoPointGradient(blocks, anchor, hit, gradient(),
                            GRADIENT_STEPS)));
                    clearAnchor();
                }
            }
            case AXIS -> {
                List<Integer> blocks = sliceFilter(model.zoneBlocks(model.zoneAt(hit)), slice);
                model.begin();
                after(model.paintEach(Selections.axisGradient(blocks, axis, gradient(), GRADIENT_STEPS)));
            }
            default -> {
            }
        }
    }

    private ChromaGradient gradient() {
        return ChromaGradient.even(List.of(picker.secondary(), picker.primary()));
    }

    private static List<Integer> sliceFilter(List<Integer> blocks, int slice) {
        List<Integer> out = new ArrayList<>();
        for (int rel : blocks) {
            if (RelativePos.y(rel) <= slice) {
                out.add(rel);
            }
        }
        return out;
    }

    private void clearAnchor() {
        anchor = NO_BLOCK;
        view.setMarks(Set.of());
    }

    private void applySelection(List<Integer> blocks) {
        if (blocks.isEmpty()) {
            return;
        }
        model.begin();
        if (zoneMode) {
            for (int rel : blocks) {
                model.setZone(rel, selZone);
            }
            view.invalidate();
        } else {
            after(model.paint(blocks, picker.primary()));
        }
        say(tr("status.selected", blocks.size()), HubTheme.SUBTLE);
    }

    private void brush(int hit) {
        if (hit == NO_BLOCK || hit == lastBrushed) {
            return;
        }
        lastBrushed = hit;
        if (zoneMode) {
            model.setZone(hit, selZone);
            view.invalidate();
        } else {
            after(model.paint(List.of(hit), picker.primary()));
        }
    }

    @Override
    protected boolean onDrag(double mx, double my, int button, double dx, double dy) {
        switch (drag) {
            case SV, HUE, LIGHT -> pickerDrag(mx, my);
            case SLICE -> sliceFromMouse(my);
            case ORBIT -> view.orbit(dx, dy);
            case PAN -> view.pan(dx, dy);
            case BRUSH -> brush(view.pick(mx, my));
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    protected boolean onRelease(double mx, double my, int button) {
        boolean had = drag != Drag.NONE;
        drag = Drag.NONE;
        lastBrushed = NO_BLOCK;
        return had;
    }

    @Override
    protected boolean onScroll(double mx, double my, double amount) {
        int step = amount > 0 ? -1 : 1;
        switch (tab) {
            case ZONES -> zoneScroll += step;
            case PALETTE -> paletteScroll += step;
            case SCENES -> {
                if (composing) {
                    keyScroll += step;
                } else {
                    slotScroll += step;
                }
            }
            case ACCESS -> {
                if (mx < contentX + contentW / 2) {
                    trustScroll += step;
                } else {
                    onlineScroll += step;
                }
            }
            case VIEW -> {
                if (view.contains(mx, my)) {
                    view.zoom(amount);
                } else {
                    view.setSlice(view.sliceY() - step);
                }
            }
        }
        return true;
    }

    @Override
    protected boolean onKey(int key, boolean ctrl, boolean shift) {
        if (ctrl && key == KEY_Z) {
            if (shift) {
                redo();
            } else {
                undo();
            }
            return true;
        }
        if (ctrl && key == KEY_Y) {
            redo();
            return true;
        }
        return false;
    }
}
