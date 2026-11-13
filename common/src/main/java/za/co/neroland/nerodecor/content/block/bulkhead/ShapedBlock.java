package za.co.neroland.nerodecor.content.block.bulkhead;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A property-less decor block with a fixed outline (holo plinth and similar). */
public class ShapedBlock extends BhcBlock {

    private final double[][] boxes;

    public ShapedBlock(Properties properties, double[]... boxes) {
        super(properties);
        this.boxes = boxes;
    }

    @Override
    protected VoxelShape computeShape(BlockState state) {
        return BhcShapes.of(boxes);
    }
}
