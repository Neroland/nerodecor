package za.co.neroland.nerodecor.client.render.bhc;

import java.util.function.IntUnaryOperator;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * Draws text from the shipped glyph pages (32x32, 4x6 texel cells, 8 x 5 cells = 40 glyphs a page, the glyph is the
 * 3x5 block at the cell's top-left) as quads on a vertical panel of constant z.
 */
public final class Text {

    private Text() {
    }

    /**
     * Draw {@code text} starting at {@code xLeft} (as the viewer sees it), top at {@code yTop}, one font texel =
     * {@code texel} blocks, advancing towards {@code dir} (+1 = +x, -1 = -x). {@code glyph} maps a char to a glyph
     * index (or -1 to skip); {@code pages} are the page sprites.
     */
    public static void draw(QuadBuffer out, QuadBuffer.Mode mode, TextureAtlasSprite[] pages, IntUnaryOperator glyph,
                            String text, float xLeft, float yTop, float texel, int dir, float z, int argb, int light) {
        float x = xLeft;
        for (int i = 0; i < text.length(); i++) {
            int g = glyph.applyAsInt(text.charAt(i));
            if (g >= 0 && g / 40 < pages.length && text.charAt(i) != ' ') {
                int slot = g % 40;
                float u0 = (slot % 8) * 4 / 32.0F;
                float v0 = (slot / 8) * 6 / 32.0F;
                out.panelZ(mode, pages[g / 40], x, x + dir * 3 * texel, yTop - 5 * texel, yTop, z,
                        u0, u0 + 3 / 32.0F, v0, v0 + 5 / 32.0F, argb, light);
            }
            x += dir * 4 * texel;
        }
    }

    /** Width of {@code chars} characters at {@code texel} (last advance trimmed to the glyph). */
    public static float width(int chars, float texel) {
        return chars <= 0 ? 0 : (chars * 4 - 1) * texel;
    }

    /** ARGB from 0xRRGGBB with full alpha. */
    public static int opaque(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }
}
