package za.co.neroland.nerodecor.client.render.command;

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
import za.co.neroland.nerodecor.content.block.command.WallScreenBlock;
import za.co.neroland.nerodecor.content.block.command.WallScreenBlockEntity;
import za.co.neroland.nerodecor.content.block.holo.DisplayState;
import za.co.neroland.nerodecor.content.block.holo.HoloText;

/**
 * Wall screen payload, drawn only by the group's anchor tile across the whole N x M display, 0.01 px in front of the
 * z = 14 face and inset 1.5 px from every bezel edge. Authored facing north (front at z = 14 px, viewer's left = +x).
 * Text is word-wrapped at the largest font size that fits.
 */
public class WallScreenRenderer extends BhcRenderer<WallScreenBlockEntity> {

    private static final int TEXT = 0x9FE8FF;
    private static final float[] TEXELS = {2.0F, 1.5F, 1.0F, 0.75F, 0.5F, 0.375F, 0.25F};

    public WallScreenRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected int viewDistance() {
        return 64;
    }

    @Override
    protected double reach() {
        return WallScreenBlock.MAX_SIDE;
    }

    @Override
    protected void build(WallScreenBlockEntity be, float partialTick, QuadBuffer out, int light) {
        DisplayState display = be.display();
        BlockState state = be.getBlockState();
        if (display.isBlank() || be.getLevel() == null || !state.hasProperty(BhcProperties.FACING)
                || !WallScreenBlock.isAnchor(be.getLevel(), be.getBlockPos(), state)) {
            return;
        }
        Direction facing = state.getValue(BhcProperties.FACING);
        int[] wh = WallScreenBlock.size(be.getLevel(), be.getBlockPos(), state);
        boolean positive = facing == Direction.NORTH || facing == Direction.EAST;
        float xMin = positive ? 0.0F : 1.0F - wh[0];
        float xMax = xMin + wh[0];
        float inset = 1.5F / 16.0F;
        float left = xMax - inset;               // viewer's left edge (+x in the authored frame)
        float areaW = wh[0] - 2 * inset;
        float top = wh[1] - inset;
        float areaH = wh[1] - 2 * inset;
        float z = (14.0F - 0.01F) / 16.0F;
        int argb = Text.opaque(display.colour() >= 0 ? display.colour() : TEXT);
        TextureAtlasSprite[] pages = {Sprites.get("command", "wall_screen_font_0"),
                Sprites.get("command", "wall_screen_font_1")};

        out.push().blockRotateY(switch (facing) {
            case EAST -> 90.0F;
            case SOUTH -> 180.0F;
            case WEST -> 270.0F;
            default -> 0.0F;
        });
        float textW = areaW;
        float texel = 0;
        List<String> lines = List.of();
        for (float candidate : TEXELS) {
            float t = candidate / 16.0F;
            float icon = display.icon() >= 0 ? 7 * t : 0;
            int cols = (int) ((areaW - icon + t) / (4 * t));
            int rows = (int) ((areaH + t) / (6 * t));
            if (cols < 1 || rows < 1) {
                continue;
            }
            List<String> wrapped = HoloText.wrap(display.text(), cols, rows);
            if (String.join(" ", wrapped).length() >= display.text().length()) {
                texel = t;
                lines = wrapped;
                textW = areaW - icon;
                break;
            }
        }
        if (texel == 0) {
            texel = TEXELS[TEXELS.length - 1] / 16.0F;
            int cols = Math.max(1, (int) ((areaW + texel) / (4 * texel)));
            lines = HoloText.wrap(display.text(), cols, Math.max(1, (int) ((areaH + texel) / (6 * texel))));
        }
        float x = left;
        if (display.icon() >= 0) {
            int i = display.icon();
            float u0 = (i % 4) * 8 / 32.0F;
            float v0 = (i / 4) * 8 / 32.0F;
            float s = 6 * texel;
            out.panelZ(QuadBuffer.Mode.TRANSLUCENT, Sprites.get("holo", "holo_sign_icons"), x, x - s, top - s, top, z,
                    u0, u0 + 0.25F, v0, v0 + 0.25F, argb, QuadBuffer.FULL_BRIGHT);
            x -= 7 * texel;
        }
        float blockH = lines.size() * 6 * texel - texel;
        float y = top - Math.max(0.0F, (areaH - blockH) / 2.0F);
        for (String line : lines) {
            float pad = Math.max(0.0F, (textW - Text.width(line.length(), texel)) / 2.0F);
            Text.draw(out, QuadBuffer.Mode.TRANSLUCENT, pages, c -> HoloText.screenGlyph((char) c), line, x - pad, y,
                    texel, -1, z, argb, QuadBuffer.FULL_BRIGHT);
            y -= 6 * texel;
        }
        out.pop();
    }
}
