package za.co.neroland.nerodecor.client.render.holo;

import java.util.List;

import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

import za.co.neroland.nerodecor.client.render.bhc.BhcRenderer;
import za.co.neroland.nerodecor.client.render.bhc.QuadBuffer;
import za.co.neroland.nerodecor.client.render.bhc.Sprites;
import za.co.neroland.nerodecor.client.render.bhc.Text;
import za.co.neroland.nerodecor.content.block.holo.DisplayState;
import za.co.neroland.nerodecor.content.block.holo.HoloTableBlockEntity;
import za.co.neroland.nerodecor.content.block.holo.HoloText;

/**
 * The table's terrain hologram: a wireframe grid over the well with marker blips and a light column, plus any pushed
 * {@code link.display} readout floating above it on all four sides.
 */
public class HoloTableRenderer extends BhcRenderer<HoloTableBlockEntity> {

    private static final int TINT = 0x7FE6FF;

    public HoloTableRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected double reach() {
        return 1.0;
    }

    @Override
    protected void build(HoloTableBlockEntity be, float partialTick, QuadBuffer out, int light) {
        DisplayState display = be.display();
        int tint = display.colour() >= 0 ? display.colour() : TINT;
        TextureAtlasSprite grid = Sprites.get("holo", "holo_table_grid");
        TextureAtlasSprite blips = Sprites.get("holo", "holo_table_blips");
        TextureAtlasSprite beam = Sprites.get("holo", "holo_table_beam");
        float lo = 2 / 16.0F;
        float hi = 14 / 16.0F;
        out.rectY(QuadBuffer.Mode.TRANSLUCENT, grid, lo, lo, hi, hi, 13.6F / 16.0F, 0, 0, 1, 1, 0xCC000000 | tint,
                QuadBuffer.FULL_BRIGHT, true);
        out.rectY(QuadBuffer.Mode.TRANSLUCENT, grid, lo, lo, hi, hi, 16.5F / 16.0F, 0.25F, 0.25F, 0.75F, 0.75F,
                0x55000000 | tint, QuadBuffer.FULL_BRIGHT, true);
        out.rectY(QuadBuffer.Mode.TRANSLUCENT, blips, lo, lo, hi, hi, 13.8F / 16.0F, 0, 0, 1, 1, 0xFF000000 | tint,
                QuadBuffer.FULL_BRIGHT, true);
        float c0 = 7 / 16.0F;
        float c1 = 9 / 16.0F;
        float yb = 13.6F / 16.0F;
        float yt = 26.0F / 16.0F;
        int col = 0x80000000 | tint;
        out.panelZ(QuadBuffer.Mode.TRANSLUCENT, beam, c0, c1, yb, yt, c1, 0, 1, 0, 1, col, QuadBuffer.FULL_BRIGHT);
        out.panelZ(QuadBuffer.Mode.TRANSLUCENT, beam, c1, c0, yb, yt, c0, 0, 1, 0, 1, col, QuadBuffer.FULL_BRIGHT);
        out.panelX(QuadBuffer.Mode.TRANSLUCENT, beam, c1, c0, yb, yt, c1, 0, 1, 0, 1, col, QuadBuffer.FULL_BRIGHT);
        out.panelX(QuadBuffer.Mode.TRANSLUCENT, beam, c0, c1, yb, yt, c0, 0, 1, 0, 1, col, QuadBuffer.FULL_BRIGHT);
        if (display.isBlank()) {
            return;
        }
        List<String> lines = HoloText.wrap(display.text(), 16, 2);
        TextureAtlasSprite[] pages = HoloSignRenderer.signPages();
        int argb = Text.opaque(tint);
        float texel = 0.6F / 16.0F;
        for (int side = 0; side < 4; side++) {
            out.push().blockRotateY(side * 90.0F);
            float z = 0.5F + 2.5F / 16.0F;
            float top = 25.0F / 16.0F;
            if (display.icon() >= 0) {
                int i = display.icon();
                float u0 = (i % 4) * 8 / 32.0F;
                float v0 = (i / 4) * 8 / 32.0F;
                out.panelZ(QuadBuffer.Mode.TRANSLUCENT, Sprites.get("holo", "holo_sign_icons"), 0.5F - 2.5F / 16.0F,
                        0.5F + 2.5F / 16.0F, top - 0.5F / 16.0F, top + 4.5F / 16.0F, z, u0, u0 + 0.25F, v0, v0 + 0.25F,
                        argb, QuadBuffer.FULL_BRIGHT);
            }
            for (int l = 0; l < lines.size(); l++) {
                String line = lines.get(l);
                float w = Text.width(line.length(), texel);
                Text.draw(out, QuadBuffer.Mode.TRANSLUCENT, pages, c -> HoloText.signGlyph((char) c), line, 0.5F - w / 2,
                        top - 1.0F / 16.0F - l * 4.0F / 16.0F, texel, 1, z, argb, QuadBuffer.FULL_BRIGHT);
            }
            out.pop();
        }
    }
}
