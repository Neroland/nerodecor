package za.co.neroland.nerodecor.content.block.lights;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A stateless block with a fixed outline (the halo ring). */
public class ShapedBlock extends CollectionBlock {

    private final double[][] boxes;

    public ShapedBlock(Properties properties, double[]... boxes) {
        super(properties);
        this.boxes = boxes;
    }

    @Override
    protected VoxelShape shapeOf(BlockState state) {
        return ModelShapes.boxes(new ModelShapes.Rot(0, 0), boxes);
    }
}
