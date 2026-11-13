package za.co.neroland.nerodecor.content.block.lights;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import za.co.neroland.nerodecor.content.block.DecorPillarBlock;
import za.co.neroland.nerodecor.content.fx.AmbientFx;

/**
 * A {@link DecorPillarBlock} thinner than a full block: a square column {@code inset} pixels in from each side,
 * running the full length of its axis (light pillar, cryo fin).
 */
public class ShapedPillarBlock extends DecorPillarBlock {

    private final VoxelShape x;
    private final VoxelShape y;
    private final VoxelShape z;

    public ShapedPillarBlock(Properties properties, AmbientFx fx, double inset) {
        super(properties, fx);
        double hi = 16 - inset;
        this.x = Block.box(0, inset, inset, 16, hi, hi);
        this.y = Block.box(inset, 0, inset, hi, 16, hi);
        this.z = Block.box(inset, inset, 0, hi, hi, 16);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction.Axis axis = state.getValue(AXIS);
        return axis == Direction.Axis.X ? x : axis == Direction.Axis.Y ? y : z;
    }
}
