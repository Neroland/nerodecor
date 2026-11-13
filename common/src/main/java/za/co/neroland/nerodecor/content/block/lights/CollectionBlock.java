package za.co.neroland.nerodecor.content.block.lights;

import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
//? if >=1.21.2 {
import net.minecraft.world.level.ScheduledTickAccess;
//?} else {
/*import net.minecraft.world.level.LevelAccessor;
*///?}
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Base for the 0.8.0 collection blocks (lights, worlds, ancients, greebles): one place for the version-split
 * {@code updateShape} signature, a per-state outline cache, and the empty-hand "cycle the variant" interaction.
 * Subclasses override {@link #shapeOf}, {@link #refresh} and {@link #cycled} as needed. No block entity, no ticking.
 */
public class CollectionBlock extends Block {

    private volatile Map<BlockState, VoxelShape> shapes;

    public CollectionBlock(Properties properties) {
        super(properties);
    }

    /** The outline of {@code state}; full cube by default. Computed once per state. */
    protected VoxelShape shapeOf(BlockState state) {
        return Shapes.block();
    }

    /**
     * Recomputes connection properties after a neighbour changed. {@code level} reads the world around {@code pos};
     * {@code dir} points at the neighbour that changed.
     */
    protected BlockState refresh(BlockState state, LevelReader level, BlockPos pos, Direction dir, BlockState neighbour) {
        return state;
    }

    /** The state an empty-hand use switches to, or {@code null} when the block has nothing to cycle. */
    @Nullable
    protected BlockState cycled(BlockState state, Player player) {
        return null;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Map<BlockState, VoxelShape> map = shapes;
        if (map == null) {
            map = ModelShapes.cache(getStateDefinition().getPossibleStates(), this::shapeOf);
            shapes = map;
        }
        VoxelShape shape = map.get(state);
        return shape == null ? Shapes.block() : shape;
    }

    // 1.21.2 reordered updateShape and added the tick-access + random arguments.
    //? if >=1.21.2 {
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState,
                                     RandomSource random) {
    //?} else {
    /*@Override
    protected BlockState updateShape(BlockState state, Direction directionToNeighbour, BlockState neighbourState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
    *///?}
        return refresh(state, level, pos, directionToNeighbour, neighbourState);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
        BlockState next = cycled(state, player);
        if (next == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            level.setBlock(pos, next, Block.UPDATE_ALL);
        }
        return InteractionResult.SUCCESS;
    }
}
