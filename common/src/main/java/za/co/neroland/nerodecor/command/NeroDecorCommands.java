package za.co.neroland.nerodecor.command;

import java.util.ArrayList;
import java.util.List;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import za.co.neroland.nerodecor.NeroDecorCommon;
import za.co.neroland.nerodecor.content.DecorColor;
import za.co.neroland.nerodecor.content.DecorProperties;
import za.co.neroland.nerodecor.content.block.ConnectedGlowBlock;
import za.co.neroland.nerodecor.content.block.DecorPillarBlock;
import za.co.neroland.nerodecor.content.block.FusionLampBlock;
import za.co.neroland.nerodecor.registry.DecorBlocks;

/**
 * Creative-only showcase command: {@code /nerodecor gallery} places every registered decor
 * block in a floating grid on a platform, plus a "paint row" showing one block in each
 * {@link DecorColor} to demonstrate the paintable colour property, plus a "Luminous row" that
 * shows the Luminous collection the way it is meant to be built: connected surfaces as 3x3
 * walls (so the seamless bezel is visible), pillars as linked 3-high columns, the Fusion Lamp
 * lit on a redstone block, and the remaining Luminous blocks and slabs on plinths.
 * {@code /nerodecor gallery clear} wipes the footprint so a rebuild starts clean. Registered per loader (Fabric
 * {@code CommandRegistrationCallback}, NeoForge/Forge {@code RegisterCommandsEvent}).
 */
public final class NeroDecorCommands {

    private static final int SPACING = 2;
    private static final int FLOAT_ABOVE = 2;
    private static final int GRID_OX = 3;   // offset east of the player
    private static final int GRID_OZ = 3;   // offset south of the player
    private static final int PAINT_ROW_DZ = -3;
    private static final int LUMINOUS_ROW_DZ = -7;  // north of the paint row
    private static final int EXHIBIT_WIDTH = 4;     // 3 blocks + 1 gap
    private static final int EXHIBIT_HEIGHT = 3;

    private NeroDecorCommands() {
    }

    /** Attach the command tree to a dispatcher. Called from each loader's command hook. */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("nerodecor")
                .requires(src -> src.getPlayer() != null)
                .then(Commands.literal("gallery")
                        .executes(ctx -> build(ctx.getSource()))
                        .then(Commands.literal("clear").executes(ctx -> clear(ctx.getSource())))));
    }

    private static List<Block> decorBlocks() {
        List<Block> blocks = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            Identifier id = BuiltInRegistries.BLOCK.getKey(block);
            if (NeroDecorCommon.MOD_ID.equals(id.getNamespace())) {
                blocks.add(block);
            }
        }
        return blocks;
    }

    private static int build(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Run this as a player."));
            return 0;
        }
        if (!player.getAbilities().instabuild) {
            source.sendFailure(Component.literal("The NeroDecor gallery is creative-only."));
            return 0;
        }
        ServerLevel level = player.level();
        BlockPos origin = player.blockPosition();
        List<Block> blocks = decorBlocks();
        int cols = Math.max(1, (int) Math.ceil(Math.sqrt(blocks.size())));
        int rows = (int) Math.ceil(blocks.size() / (double) cols);

        int ox = origin.getX() + GRID_OX;
        int oz = origin.getZ() + GRID_OZ;
        int fy = origin.getY();
        BlockState floor = Blocks.POLISHED_ANDESITE.defaultBlockState();

        // platform under the grid (+1 margin)
        for (int gx = -1; gx <= cols * SPACING; gx++) {
            for (int gz = -1; gz <= rows * SPACING; gz++) {
                level.setBlockAndUpdate(new BlockPos(ox + gx, fy, oz + gz), floor);
            }
        }
        // floating block displays
        for (int i = 0; i < blocks.size(); i++) {
            BlockPos pos = new BlockPos(ox + (i % cols) * SPACING, fy + FLOAT_ABOVE, oz + (i / cols) * SPACING);
            level.setBlockAndUpdate(pos, blocks.get(i).defaultBlockState());
        }
        // paint row — one hull block per DecorColor (functional now; tints once E5 rendering lands)
        Block hull = DecorBlocks.HULL_NERO_ALLOY.get();
        DecorColor[] colours = DecorColor.values();
        for (int k = 0; k < colours.length; k++) {
            level.setBlockAndUpdate(new BlockPos(ox + k, fy, oz + PAINT_ROW_DZ), floor);
            level.setBlockAndUpdate(new BlockPos(ox + k, fy + 1, oz + PAINT_ROW_DZ),
                    hull.defaultBlockState().setValue(DecorProperties.COLOR, colours[k]));
        }
        int exhibits = buildLuminousRow(level, ox, fy, oz + LUMINOUS_ROW_DZ, floor, blocks);
        source.sendSuccess(() -> Component.literal("NeroDecor gallery built: " + blocks.size()
                + " blocks + " + colours.length + " paint swatches + " + exhibits + " Luminous exhibits."), false);
        return blocks.size();
    }

    private static int clear(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Run this as a player."));
            return 0;
        }
        ServerLevel level = player.level();
        BlockPos origin = player.blockPosition();
        List<Block> blocks = decorBlocks();
        int cols = Math.max(1, (int) Math.ceil(Math.sqrt(blocks.size())));
        int rows = (int) Math.ceil(blocks.size() / (double) cols);
        int ox = origin.getX() + GRID_OX;
        int oz = origin.getZ() + GRID_OZ;
        int fy = origin.getY();
        BlockState air = Blocks.AIR.defaultBlockState();
        int width = Math.max(Math.max(cols, DecorColor.values().length) * SPACING,
                luminousBlocks(blocks).size() * EXHIBIT_WIDTH);
        for (int gx = -2; gx <= width; gx++) {
            for (int gz = LUMINOUS_ROW_DZ - 1; gz <= rows * SPACING + 1; gz++) {
                for (int gy = 0; gy <= Math.max(FLOAT_ABOVE, EXHIBIT_HEIGHT) + 1; gy++) {
                    level.setBlockAndUpdate(new BlockPos(ox + gx, fy + gy, oz + gz), air);
                }
            }
        }
        source.sendSuccess(() -> Component.literal("NeroDecor gallery cleared."), false);
        return 1;
    }

    /** The Luminous collection: every NeroDecor block without the paintable {@code COLOR} property. */
    private static List<Block> luminousBlocks(List<Block> all) {
        List<Block> out = new ArrayList<>();
        for (Block block : all) {
            if (!block.defaultBlockState().hasProperty(DecorProperties.COLOR)) {
                out.add(block);
            }
        }
        return out;
    }

    /**
     * One exhibit per Luminous block along +x, on a floor strip at {@code (fy, z)}. Connected
     * surfaces get a 3x3 wall and pillars a 3-high column; after placing, every exhibit block is
     * re-resolved against its finished neighbours so the connection states are exact.
     */
    private static int buildLuminousRow(ServerLevel level, int ox, int fy, int z, BlockState floor, List<Block> all) {
        List<Block> luminous = luminousBlocks(all);
        List<BlockPos> placed = new ArrayList<>();
        for (int i = 0; i < luminous.size(); i++) {
            Block block = luminous.get(i);
            int x0 = ox + i * EXHIBIT_WIDTH;
            for (int dx = -1; dx < EXHIBIT_WIDTH; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    level.setBlockAndUpdate(new BlockPos(x0 + dx, fy, z + dz), floor);
                }
            }
            if (block instanceof ConnectedGlowBlock) {
                for (int dx = 0; dx < 3; dx++) {
                    for (int dy = 1; dy <= EXHIBIT_HEIGHT; dy++) {
                        placed.add(place(level, new BlockPos(x0 + dx, fy + dy, z), block.defaultBlockState()));
                    }
                }
            } else if (block instanceof DecorPillarBlock) {
                for (int dy = 1; dy <= EXHIBIT_HEIGHT; dy++) {
                    placed.add(place(level, new BlockPos(x0 + 1, fy + dy, z), block.defaultBlockState()));
                }
            } else if (block instanceof FusionLampBlock) {
                level.setBlockAndUpdate(new BlockPos(x0 + 1, fy + 1, z), Blocks.REDSTONE_BLOCK.defaultBlockState());
                place(level, new BlockPos(x0 + 1, fy + 2, z),
                        block.defaultBlockState().setValue(FusionLampBlock.LIT, true));
            } else if (block.defaultBlockState().hasProperty(BlockStateProperties.SLAB_TYPE)) {
                place(level, new BlockPos(x0 + 1, fy + 1, z), block.defaultBlockState());
            } else {
                place(level, new BlockPos(x0 + 1, fy + 2, z), block.defaultBlockState());
            }
        }
        for (BlockPos pos : placed) {
            BlockState state = level.getBlockState(pos);
            BlockState resolved = Block.updateFromNeighbourShapes(state, level, pos);
            if (resolved != state) {
                level.setBlockAndUpdate(pos, resolved);
            }
        }
        return luminous.size();
    }

    private static BlockPos place(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlockAndUpdate(pos, state);
        return pos;
    }
}
