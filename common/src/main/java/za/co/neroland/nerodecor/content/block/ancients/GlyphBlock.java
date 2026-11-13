package za.co.neroland.nerodecor.content.block.ancients;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

import za.co.neroland.nerodecor.content.block.ConnectedGlowBlock;
import za.co.neroland.nerodecor.content.fx.AmbientFx;

/**
 * Glyph block: carved precursor stone. The six {@link ConnectedGlowBlock} booleans drop the rim between joined
 * blocks so a wall reads as one cartouche; {@code glyph} (0..7) picks the carved, faintly glowing sign. A placed
 * block takes a glyph from its position (a wall of them varies with no effort); an empty-hand use carves the next
 * one (sneak: the previous).
 */
public class GlyphBlock extends ConnectedGlowBlock {

    public static final IntegerProperty GLYPH = IntegerProperty.create("glyph", 0, 7);

    public GlyphBlock(Properties properties) {
        super(properties, AmbientFx.NONE, false);
        registerDefaultState(defaultBlockState().setValue(GLYPH, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(GLYPH);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos p = context.getClickedPos();
        int seed = p.getX() * 73 + p.getY() * 31 + p.getZ() * 17;
        return super.getStateForPlacement(context).setValue(GLYPH, Math.floorMod(seed, 8));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            int step = player.isShiftKeyDown() ? 7 : 1;
            level.setBlock(pos, state.setValue(GLYPH, (state.getValue(GLYPH) + step) % 8), Block.UPDATE_ALL);
        }
        return InteractionResult.SUCCESS;
    }
}
