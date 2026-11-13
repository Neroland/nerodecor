package za.co.neroland.nerodecor.client.render.bhc;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;

import za.co.neroland.nerodecor.NeroDecorCommon;

/**
 * Block-atlas sprite lookup for the collection renderers. Every texture under {@code textures/block/} is stitched into
 * the block atlas, block-entity-only parts included, so animated part textures animate for free.
 */
public final class Sprites {

    private Sprites() {
    }

    /** {@code nerodecor:block/<collection>/<id>} from the block atlas (the missing sprite if absent). */
    public static TextureAtlasSprite get(String collection, String id) {
        AbstractTexture atlas = Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
        Identifier sprite = Identifier.fromNamespaceAndPath(NeroDecorCommon.MOD_ID, "block/" + collection + "/" + id);
        return ((TextureAtlas) atlas).getSprite(sprite);
    }
}
