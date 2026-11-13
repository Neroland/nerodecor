package za.co.neroland.nerodecor.content.block.lights;

import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import za.co.neroland.nerodecor.content.block.ConnectedGlowBlock;
import za.co.neroland.nerodecor.content.fx.AmbientFx;

/**
 * Light pane: a 2px pane-style light wall. The six {@link ConnectedGlowBlock} booleans say where the same pane
 * continues; the horizontal ones grow pane arms, up/down join the frame vertically. Only the outside of a group is
 * framed.
 */
public class LightPaneBlock extends ConnectedGlowBlock {

    private final Map<BlockState, VoxelShape> shapes = new IdentityHashMap<>();

    public LightPaneBlock(Properties properties) {
        super(properties, AmbientFx.NONE, false);
        for (BlockState s : getStateDefinition().getPossibleStates()) {
            VoxelShape out = Block.box(7, 0, 7, 9, 16, 9);
            if (s.getValue(NORTH)) {
                out = Shapes.or(out, Block.box(7, 0, 0, 9, 16, 9));
            }
            if (s.getValue(SOUTH)) {
                out = Shapes.or(out, Block.box(7, 0, 7, 9, 16, 16));
            }
            if (s.getValue(WEST)) {
                out = Shapes.or(out, Block.box(0, 0, 7, 9, 16, 9));
            }
            if (s.getValue(EAST)) {
                out = Shapes.or(out, Block.box(7, 0, 7, 16, 16, 9));
            }
            shapes.put(s, out);
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape s = shapes.get(state);
        return s == null ? Shapes.block() : s;
    }
}
