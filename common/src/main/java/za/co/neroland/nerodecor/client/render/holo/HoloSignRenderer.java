package za.co.neroland.nerodecor.client.render.holo;

import java.util.List;

import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import za.co.neroland.nerodecor.client.render.bhc.BhcRenderer;
import za.co.neroland.nerodecor.client.render.bhc.QuadBuffer;
import za.co.neroland.nerodecor.client.render.bhc.Sprites;
import za.co.neroland.nerodecor.client.render.bhc.Text;
import za.co.neroland.nerodecor.content.block.bulkhead.BhcProperties;
import za.co.neroland.nerodecor.content.block.holo.DisplayState;
import za.co.neroland.nerodecor.content.block.holo.HoloSignBlockEntity;
import za.co.neroland.nerodecor.content.block.holo.HoloText;

/**
 * Holo sign text: the two player lines, or a pushed {@code link.display} payload (icon + text + status colour) while
 * one is set. Authored with the front facing south on the panel plane z = 8 px (x 0..16, y 5..16).
 */
public class HoloSignRenderer extends BhcRenderer<HoloSignBlockEntity> {

    static final int TEXT = 0xBFF4FF;

    public HoloSignRenderer(BlockEntityRendererProvider.Context context) {
    }

    static float frontRotation(Direction facing) {
        return switch (facing) {
            case WEST -> 90.0F;
            case NORTH -> 180.0F;
            case EAST -> 270.0F;
            default -> 0.0F;
        };
    }

    static TextureAtlasSprite[] signPages() {
        return new TextureAtlasSprite[] {Sprites.get("holo", "holo_sign_glyphs_0"), Sprites.get("holo", "holo_sign_glyphs_1"),
                Sprites.get("holo", "holo_sign_glyphs_2")};
    }

    @Override
    protected void build(HoloSignBlockEntity be, float partialTick, QuadBuffer out, int light) {
        BlockState state = be.getBlockState();
        if (!state.hasProperty(BhcProperties.FACING)) {
            return;
        }
        DisplayState display = be.display();
        boolean payload = !display.isBlank();
        List<String> lines = payload ? HoloText.wrap(display.text(), HoloText.LINE_LENGTH, 2)
                : List.of(be.line(0), be.line(1));
        if (!payload && lines.get(0).isEmpty() && lines.get(1).isEmpty()) {
            return;
        }
        int colour = Text.opaque(payload && display.colour() >= 0 ? display.colour() : TEXT);
        TextureAtlasSprite[] pages = signPages();
        out.push().blockRotateY(frontRotation(state.getValue(BhcProperties.FACING)));
        float z = 8.03F / 16.0F;
        int tint = Text.opaque(TEXT) & 0x00FFFFFF;
        out.panelZ(QuadBuffer.Mode.TRANSLUCENT, Sprites.get("holo", "holo_sign_backing"), 0.0F, 1.0F, 5.0F / 16.0F,
                1.0F, 8.015F / 16.0F, 0, 1, 0, 11.0F / 16.0F, 0xB0000000 | tint, QuadBuffer.FULL_BRIGHT);
        float left = 1.0F / 16.0F;
        float width = 14.0F / 16.0F;
        if (payload && display.icon() >= 0) {
            int i = display.icon();
            float u0 = (i % 4) * 8 / 32.0F;
            float v0 = (i / 4) * 8 / 32.0F;
            float s = 7.0F / 16.0F;
            out.panelZ(QuadBuffer.Mode.TRANSLUCENT, Sprites.get("holo", "holo_sign_icons"), left, left + s,
                    7.0F / 16.0F, 7.0F / 16.0F + s, z, u0, u0 + 0.25F, v0, v0 + 0.25F, colour, QuadBuffer.FULL_BRIGHT);
            left += s + 1.0F / 16.0F;
            width -= s + 1.0F / 16.0F;
        }
        float[] tops = {14.5F / 16.0F, 10.0F / 16.0F};
        for (int l = 0; l < Math.min(2, lines.size()); l++) {
            String line = lines.get(l);
            if (line.isEmpty()) {
                continue;
            }
            float texel = Math.min(0.7F / 16.0F, width / (line.length() * 4));
            float x = left + (width - Text.width(line.length(), texel)) / 2.0F;
            Text.draw(out, QuadBuffer.Mode.TRANSLUCENT, pages, c -> HoloText.signGlyph((char) c), line, x,
                    tops[l], texel, 1, z, colour, QuadBuffer.FULL_BRIGHT);
        }
        out.pop();
    }
}
