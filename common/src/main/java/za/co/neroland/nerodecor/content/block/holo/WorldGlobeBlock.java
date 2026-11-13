package za.co.neroland.nerodecor.content.block.holo;

import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

import za.co.neroland.nerodecor.content.block.bulkhead.BhcBlock;
import za.co.neroland.nerodecor.content.block.bulkhead.BhcShapes;

/** World globe: a cradle with a slowly turning globe of one Nerospace world; use cycles the world. */
public class WorldGlobeBlock extends BhcBlock implements EntityBlock {

    public static final EnumProperty<GlobeWorld> WORLD = EnumProperty.create("world", GlobeWorld.class);

    private final Supplier<? extends BlockEntityType<WorldGlobeBlockEntity>> type;

    public WorldGlobeBlock(Properties properties, Supplier<? extends BlockEntityType<WorldGlobeBlockEntity>> type) {
        super(properties);
        this.type = type;
        registerDefaultState(stateDefinition.any().setValue(WORLD, GlobeWorld.STATION));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WORLD);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WorldGlobeBlockEntity(type.get(), pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!level.isClientSide()) {
            level.setBlock(pos, state.setValue(WORLD, state.getValue(WORLD).next()), Block.UPDATE_ALL);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected VoxelShape computeShape(BlockState state) {
        return BhcShapes.of(BhcShapes.box(3, 0, 3, 13, 7, 13), BhcShapes.box(3.5, 7, 3.5, 12.5, 16, 12.5));
    }
}
