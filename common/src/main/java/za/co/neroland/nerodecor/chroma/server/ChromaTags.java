package za.co.neroland.nerodecor.chroma.server;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import za.co.neroland.nerodecor.NeroDecorCommon;

/**
 * {@code #nerodecor:chroma_capable}: the blocks a hub may control — every paintable NeroDecor block (hull, panel,
 * glass, neon) and the Luminous collection. Data-driven so packs can extend it.
 */
public final class ChromaTags {

    public static final TagKey<Block> CAPABLE = TagKey.create(Registries.BLOCK,
            Identifier.fromNamespaceAndPath(NeroDecorCommon.MOD_ID, "chroma_capable"));

    private ChromaTags() {
    }

    public static boolean isCapable(BlockState state) {
        return state.is(CAPABLE);
    }
}
