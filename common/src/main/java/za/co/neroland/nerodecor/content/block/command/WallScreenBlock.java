package za.co.neroland.nerodecor.content.block.command;

import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

import za.co.neroland.nerolandcore.link.display.DisplaySurfaces;

import za.co.neroland.nerodecor.content.block.bulkhead.BhcProperties;
import za.co.neroland.nerodecor.content.block.bulkhead.BhcShapes;
import za.co.neroland.nerodecor.content.block.holo.SurfaceBlockEntity;

/**
 * Wall screen: tiles with the same facing join into one N x M display ({@code up} / {@code down} / {@code left} /
 * {@code right} = same screen continues there, left / right as the viewer sees them). {@code idle} shows the animated
 * idle layer; it is switched off across the group while a {@code link.display} payload is shown.
 */
public class WallScreenBlock extends FacingBlock implements EntityBlock {

    /** Largest group side, in tiles, that the anchor search and the renderer walk. */
    public static final int MAX_SIDE = 16;

    private final Supplier<? extends BlockEntityType<WallScreenBlockEntity>> type;

    public WallScreenBlock(Properties properties, Supplier<? extends BlockEntityType<WallScreenBlockEntity>> type) {
        super(properties, BhcShapes.box(0, 0, 14, 16, 16, 16));
        this.type = type;
        registerDefaultState(defaultBlockState().setValue(BhcProperties.UP, false).setValue(BhcProperties.DOWN, false)
                .setValue(BhcProperties.LEFT, false).setValue(BhcProperties.RIGHT, false)
                .setValue(BhcProperties.IDLE, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(BhcProperties.UP, BhcProperties.DOWN, BhcProperties.LEFT, BhcProperties.RIGHT, BhcProperties.IDLE);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WallScreenBlockEntity(type.get(), pos, state);
    }

    private static boolean joins(BlockState state, BlockState other) {
        return other.getBlock() == state.getBlock()
                && other.getValue(BhcProperties.FACING) == state.getValue(BhcProperties.FACING);
    }

    /** The in-plane horizontal direction towards lower x / z. */
    public static Direction lateralNegative(Direction facing) {
        return facing.getAxis() == Direction.Axis.Z ? Direction.WEST : Direction.NORTH;
    }

    /** The group's anchor (lowest x, y, z) for the tile at {@code pos}. */
    public static BlockPos anchor(BlockGetter level, BlockPos pos, BlockState state) {
        Direction neg = lateralNegative(state.getValue(BhcProperties.FACING));
        BlockPos p = pos;
        for (int i = 0; i < MAX_SIDE * 2; i++) {
            if (joins(state, level.getBlockState(p.relative(neg)))) {
                p = p.relative(neg);
            } else if (joins(state, level.getBlockState(p.below()))) {
                p = p.below();
            } else {
                break;
            }
        }
        return p;
    }

    /** Whether the tile at {@code pos} is its group's anchor. */
    public static boolean isAnchor(BlockGetter level, BlockPos pos, BlockState state) {
        Direction neg = lateralNegative(state.getValue(BhcProperties.FACING));
        return !joins(state, level.getBlockState(pos.relative(neg))) && !joins(state, level.getBlockState(pos.below()));
    }

    /** Group size from the anchor: {@code {width, height}} in tiles. */
    public static int[] size(BlockGetter level, BlockPos anchor, BlockState state) {
        Direction pos = lateralNegative(state.getValue(BhcProperties.FACING)).getOpposite();
        int w = 1;
        while (w < MAX_SIDE && joins(state, level.getBlockState(anchor.relative(pos, w)))) {
            w++;
        }
        int h = 1;
        while (h < MAX_SIDE && joins(state, level.getBlockState(anchor.above(h)))) {
            h++;
        }
        return new int[] {w, h};
    }

    /** Server: set {@code idle} on every tile of the group containing {@code pos}. */
    public static void setGroupIdle(Level level, BlockPos pos, BlockState state, boolean idle) {
        BlockPos anchor = anchor(level, pos, state);
        int[] wh = size(level, anchor, state);
        Direction lateral = lateralNegative(state.getValue(BhcProperties.FACING)).getOpposite();
        for (int x = 0; x < wh[0]; x++) {
            for (int y = 0; y < wh[1]; y++) {
                BlockPos p = anchor.relative(lateral, x).above(y);
                BlockState s = level.getBlockState(p);
                if (joins(state, s) && s.getValue(BhcProperties.IDLE) != idle) {
                    level.setBlock(p, s.setValue(BhcProperties.IDLE, idle), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    private BlockState links(BlockState state, BlockGetter level, BlockPos pos) {
        Direction f = state.getValue(BhcProperties.FACING);
        return state.setValue(BhcProperties.UP, joins(state, level.getBlockState(pos.above())))
                .setValue(BhcProperties.DOWN, joins(state, level.getBlockState(pos.below())))
                .setValue(BhcProperties.LEFT, joins(state, level.getBlockState(pos.relative(BhcProperties.leftOf(f)))))
                .setValue(BhcProperties.RIGHT, joins(state, level.getBlockState(pos.relative(BhcProperties.rightOf(f)))));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clicked = context.getClickedFace();
        BlockState state = clicked.getAxis().isHorizontal()
                ? defaultBlockState().setValue(BhcProperties.FACING, clicked) : super.getStateForPlacement(context);
        return links(state, context.getLevel(), context.getClickedPos());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        super.setPlacedBy(level, pos, state, by, stack);
        if (!level.isClientSide()) {
            BlockPos anchor = anchor(level, pos, state);
            if (level.getBlockEntity(anchor) instanceof WallScreenBlockEntity be && !be.display().isBlank()) {
                setGroupIdle(level, pos, state, false);
            }
        }
    }

    @Override
    protected BlockState updateLinks(BlockState state, LevelReader level, BlockPos pos, Direction direction,
                                     BlockPos neighbourPos, BlockState neighbourState) {
        return links(state, level, pos);
    }

    @Override
    protected void onRemovedFromWorld(BlockState state, Level level, BlockPos pos) {
        DisplaySurfaces.remove(SurfaceBlockEntity.address(level, pos));
    }
}
