package za.co.neroland.nerodecor.client.screen.chroma;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import za.co.neroland.nerodecor.chroma.RelativePos;
import za.co.neroland.nerodecor.chroma.editor.EditorModel;
import za.co.neroland.nerodecor.chroma.editor.HubDetail;
import za.co.neroland.nerodecor.chroma.editor.OrbitCamera;
import za.co.neroland.nerodecor.chroma.editor.RectList;
import za.co.neroland.nerodecor.chroma.editor.ViewportRaster;
import za.co.neroland.nerodecor.chroma.editor.VoxelPicker;

/**
 * The 3D view of a hub's volume inside the hub screen. The blocks are projected and scan-converted in pure Java
 * ({@link ViewportRaster}) and drawn as plain GUI fills in physical pixels, so it works the same on every Minecraft
 * version without touching the world renderer. Rasterised only when the camera, model, slice or hover change.
 */
final class HubViewport {

    final OrbitCamera camera = new OrbitCamera();
    private final RectList blocks = new RectList(ViewportRaster.MAX_RECTS);
    private final RectList grid = new RectList(40_000);
    private EditorModel model;
    private int minX;
    private int minY;
    private int minZ;
    private int maxX;
    private int maxY;
    private int maxZ;
    private int sliceY;
    private int x;
    private int y;
    private int w;
    private int h;
    private double scale = 1.0;
    private boolean dirty = true;
    private boolean lod;
    private int hover = Integer.MIN_VALUE;
    private final Set<Integer> marks = new HashSet<>();

    /** Binds a model; {@code reframe} also resets the slice and the camera. */
    void bind(EditorModel model, HubDetail detail, boolean reframe) {
        this.model = model;
        minX = -(detail.sizeX() / 2);
        maxX = detail.sizeX() - 1 - detail.sizeX() / 2;
        minY = -(detail.sizeY() / 2);
        maxY = detail.sizeY() - 1 - detail.sizeY() / 2;
        minZ = -(detail.sizeZ() / 2);
        maxZ = detail.sizeZ() - 1 - detail.sizeZ() / 2;
        if (reframe) {
            sliceY = maxY;
            frame();
        }
        sliceY = Math.max(minY, Math.min(maxY, sliceY));
        invalidate();
    }

    /** Frames the linked blocks (or the whole volume when there are none). */
    void frame() {
        if (model == null || model.blockCount() == 0) {
            camera.frame(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
        } else {
            int x0 = Integer.MAX_VALUE;
            int y0 = Integer.MAX_VALUE;
            int z0 = Integer.MAX_VALUE;
            int x1 = Integer.MIN_VALUE;
            int y1 = Integer.MIN_VALUE;
            int z1 = Integer.MIN_VALUE;
            for (int i = 0; i < model.blockCount(); i++) {
                int rel = model.offset(i);
                x0 = Math.min(x0, RelativePos.x(rel));
                y0 = Math.min(y0, RelativePos.y(rel));
                z0 = Math.min(z0, RelativePos.z(rel));
                x1 = Math.max(x1, RelativePos.x(rel));
                y1 = Math.max(y1, RelativePos.y(rel));
                z1 = Math.max(z1, RelativePos.z(rel));
            }
            camera.frame(x0, y0, z0, x1 + 1.0, y1 + 1.0, z1 + 1.0);
        }
        invalidate();
    }

    void layout(int x, int y, int w, int h, double scale) {
        boolean resized = w != this.w || h != this.h || scale != this.scale;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.scale = scale;
        if (resized) {
            camera.setViewport((int) Math.round(w * scale), (int) Math.round(h * scale));
            invalidate();
        }
    }

    void invalidate() {
        dirty = true;
    }

    boolean contains(double mx, double my) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    int x() {
        return x;
    }

    int y() {
        return y;
    }

    int width() {
        return w;
    }

    int height() {
        return h;
    }

    boolean lod() {
        return lod;
    }

    int sliceY() {
        return sliceY;
    }

    int minY() {
        return minY;
    }

    int maxY() {
        return maxY;
    }

    void setSlice(int y) {
        int next = Math.max(minY, Math.min(maxY, y));
        if (next != sliceY) {
            sliceY = next;
            invalidate();
        }
    }

    Set<Integer> marks() {
        return marks;
    }

    void setMarks(Set<Integer> next) {
        marks.clear();
        marks.addAll(next);
        invalidate();
    }

    int hover() {
        return hover;
    }

    /** Updates the hovered block from the mouse (GUI coordinates). */
    void updateHover(double mx, double my) {
        int next = contains(mx, my) ? pick(mx, my) : Integer.MIN_VALUE;
        if (next != hover) {
            hover = next;
            invalidate();
        }
    }

    /** The linked block under the mouse (at or below the slice), or {@code Integer.MIN_VALUE}. */
    int pick(double mx, double my) {
        if (model == null) {
            return Integer.MIN_VALUE;
        }
        OrbitCamera.Ray ray = camera.ray((mx - x) * scale, (my - y) * scale);
        VoxelPicker.Hit hit = VoxelPicker.pick(ray, minX, minY, minZ, maxX, Math.min(maxY, sliceY), maxZ,
                (cx, cy, cz) -> model.contains(cx, cy, cz));
        return hit == null ? Integer.MIN_VALUE : RelativePos.pack(hit.x(), hit.y(), hit.z());
    }

    void orbit(double dx, double dy) {
        camera.orbit(dx * scale, dy * scale);
        invalidate();
    }

    void pan(double dx, double dy) {
        camera.pan(dx * scale, dy * scale);
        invalidate();
    }

    void zoom(double notches) {
        camera.zoom(notches);
        invalidate();
    }

    void draw(GuiGraphicsExtractor g) {
        g.fillGradient(x, y, x + w, y + h, 0xFF0B1528, 0xFF04060D);
        if (model == null) {
            return;
        }
        if (dirty) {
            grid.clear();
            ViewportRaster.volume(camera, minX, minY, minZ, maxX, maxY, maxZ, grid);
            lod = ViewportRaster.render(model, camera, sliceY, marks, hover, blocks);
            dirty = false;
        }
        g.enableScissor(x, y, x + w, y + h);
        HubScreenBase.pushPixels(g, x, y, (float) (1.0 / scale));
        for (int i = 0; i < grid.size(); i++) {
            g.fill(grid.x0(i), grid.y0(i), grid.x1(i), grid.y1(i), grid.argb(i));
        }
        for (int i = 0; i < blocks.size(); i++) {
            g.fill(blocks.x0(i), blocks.y0(i), blocks.x1(i), blocks.y1(i), blocks.argb(i));
        }
        HubScreenBase.popPixels(g);
        g.disableScissor();
    }
}
