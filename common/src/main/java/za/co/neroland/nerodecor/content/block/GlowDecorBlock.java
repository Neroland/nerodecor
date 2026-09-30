package za.co.neroland.nerodecor.content.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import za.co.neroland.nerodecor.content.fx.AmbientFx;

/**
 * A Luminous-collection full block: a model-only cube whose glow comes from an emissive model
 * overlay ({@code "light_emission": 15}) and whose ambience comes from {@link AmbientFx}. No block
 * entity, no ticking. {@code glassy} blocks (aurora glass) behave like vanilla glass: they hide
 * the shared face between two of themselves, let skylight through and cast no ambient shadow.
 *
 * <p>These blocks carry their own signature finish, so unlike the hull/panel families they are
 * not paintable (no {@code COLOR} property).
 */
public class GlowDecorBlock extends Block {

    private final AmbientFx fx;
    private final boolean glassy;

    public GlowDecorBlock(Properties properties, AmbientFx fx, boolean glassy) {
        super(properties);
        this.fx = fx;
        this.glassy = glassy;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        fx.animate(state, level, pos, random);
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState neighborState, Direction direction) {
        return glassy && neighborState.is(this) || super.skipRendering(state, neighborState, direction);
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return glassy ? 1.0F : super.getShadeBrightness(state, level, pos);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return glassy || super.propagatesSkylightDown(state);
    }

    @Override
    protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return glassy ? Shapes.empty() : super.getVisualShape(state, level, pos, context);
    }
}
