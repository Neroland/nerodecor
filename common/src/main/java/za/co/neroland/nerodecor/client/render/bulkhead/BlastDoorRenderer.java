package za.co.neroland.nerodecor.client.render.bulkhead;

import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.level.block.state.BlockState;

import za.co.neroland.nerodecor.client.render.bhc.BhcRenderer;
import za.co.neroland.nerodecor.client.render.bhc.QuadBuffer;
import za.co.neroland.nerodecor.client.render.bhc.Sprites;
import za.co.neroland.nerodecor.content.block.bulkhead.BhcProperties;
import za.co.neroland.nerodecor.content.block.bulkhead.BlastDoorBlock;
import za.co.neroland.nerodecor.content.block.bulkhead.DoorAnimBlockEntity;

/**
 * The two blast-door panels, drawn by the centre tile in its own block space (authored facing north, the 48 px design
 * spanning -16..32 px). Each panel slides sideways into the frame; the part that has entered the frame is clipped, so
 * the panel never shows outside the door.
 */
public class BlastDoorRenderer extends BhcRenderer<DoorAnimBlockEntity> {

    /** Panel travel in px: a panel is 21 px wide. */
    private static final float TRAVEL = 21.0F;
    private static final int[][] ROWS = {{3, 16, 0}, {16, 32, 1}, {32, 45, 2}};
    private static final int[][] LEFT = {{3, 16, 0}, {16, 24, 1}};
    private static final int[][] RIGHT = {{24, 32, 1}, {32, 45, 2}};

    public BlastDoorRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected double reach() {
        return 1.0;
    }

    @Override
    protected void build(DoorAnimBlockEntity be, float partialTick, QuadBuffer out, int light) {
        float o = DoorRenders.openness(be, partialTick, BlastDoorBlock.DURATION);
        BlockState state = be.getBlockState();
        if (o < 0.0F || !state.hasProperty(BhcProperties.PART)) {
            return;
        }
        TextureAtlasSprite[] tiles = new TextureAtlasSprite[9];
        TextureAtlasSprite[] glows = new TextureAtlasSprite[9];
        for (int n = 0; n < 9; n++) {
            tiles[n] = Sprites.get("bulkhead", "blast_door_" + n);
            if (n >= 3 && n <= 5) {
                glows[n] = Sprites.get("bulkhead", "blast_door_" + n + "_glow");
            }
        }
        TextureAtlasSprite edge = Sprites.get("bulkhead", "blast_door_edge");
        float shift = TRAVEL * o;
        out.push().blockRotateY(DoorRenders.yRot(state.getValue(BhcProperties.FACING)));
        // left panel (viewer's left = +x) slides towards +x and is clipped at x = 29; right towards -x, clipped at -13
        panel(out, LEFT, shift, -1000.0F, 29.0F, tiles, glows, edge, light);
        panel(out, RIGHT, -shift, -13.0F, 1000.0F, tiles, glows, edge, light);
        out.pop();
    }

    private static void panel(QuadBuffer out, int[][] cols, float shift, float clipLo, float clipHi,
                              TextureAtlasSprite[] tiles, TextureAtlasSprite[] glows, TextureAtlasSprite edge,
                              int light) {
        for (int[] col : cols) {
            int d0 = col[0];
            int d1 = col[1];
            int c = col[2];
            int cb = 2 - c;
            // panel-local x range, then slid and clipped
            float xa = 32 - d1 + shift;
            float xb = 32 - d0 + shift;
            float lo = Math.max(xa, clipLo);
            float hi = Math.min(xb, clipHi);
            if (hi - lo < 0.01F) {
                continue;
            }
            for (int[] row : ROWS) {
                int e0 = row[0];
                int e1 = row[1];
                int r = row[2];
                float yb = 32 - e1;
                float yt = 32 - e0;
                float vT = (e0 - 16 * r) / 16.0F;
                float vB = (e1 - 16 * r) / 16.0F;
                // north face (tile r*3+c): u = (32 - x) - 16c, x taken before the slide
                float nuHi = (32 - (hi - shift) - 16 * c) / 16.0F;
                float nuLo = (32 - (lo - shift) - 16 * c) / 16.0F;
                // south face (tile r*3+cb): u = x + 16 - 16cb
                float suLo = ((lo - shift) + 16 - 16 * cb) / 16.0F;
                float suHi = ((hi - shift) + 16 - 16 * cb) / 16.0F;
                int fr = r * 3 + c;
                int bk = r * 3 + cb;
                float x0 = lo / 16.0F;
                float x1 = hi / 16.0F;
                float y0 = yb / 16.0F;
                float y1 = yt / 16.0F;
                float zn = 5 / 16.0F;
                float zs = 11 / 16.0F;
                out.panelZ(QuadBuffer.Mode.CUTOUT, tiles[fr], x1, x0, y0, y1, zn, nuHi, nuLo, vT, vB, -1, light);
                out.panelZ(QuadBuffer.Mode.CUTOUT, tiles[bk], x0, x1, y0, y1, zs, suLo, suHi, vT, vB, -1, light);
                if (r == 1) {
                    out.panelZ(QuadBuffer.Mode.CUTOUT, glows[fr], x1, x0, y0, y1, zn - 0.001F, nuHi, nuLo, vT, vB, -1,
                            QuadBuffer.FULL_BRIGHT);
                    out.panelZ(QuadBuffer.Mode.CUTOUT, glows[bk], x0, x1, y0, y1, zs + 0.001F, suLo, suHi, vT, vB, -1,
                            QuadBuffer.FULL_BRIGHT);
                }
                // the leading / trailing edges and the top / bottom of the slab
                out.panelX(QuadBuffer.Mode.CUTOUT, edge, zn, zs, y0, y1, x0, 0.25F, 0.75F, 0.25F, 0.75F, -1, light);
                out.panelX(QuadBuffer.Mode.CUTOUT, edge, zs, zn, y0, y1, x1, 0.25F, 0.75F, 0.25F, 0.75F, -1, light);
                out.rectY(QuadBuffer.Mode.CUTOUT, edge, x0, zn, x1, zs, y1, 0.25F, 0.25F, 0.75F, 0.75F, -1, light, true);
                out.rectY(QuadBuffer.Mode.CUTOUT, edge, x0, zn, x1, zs, y0, 0.25F, 0.25F, 0.75F, 0.75F, -1, light, true);
            }
        }
    }
}
