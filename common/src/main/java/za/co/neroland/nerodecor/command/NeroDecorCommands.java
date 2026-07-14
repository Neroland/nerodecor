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

import za.co.neroland.nerodecor.NeroDecorCommon;
import za.co.neroland.nerodecor.content.DecorColor;
import za.co.neroland.nerodecor.content.DecorProperties;
import za.co.neroland.nerodecor.registry.DecorBlocks;

/**
 * Creative-only showcase command: {@code /nerodecor gallery} places every registered decor
 * block in a floating grid on a platform, plus a "paint row" showing one block in each
 * {@link DecorColor} to demonstrate the paintable colour property. {@code /nerodecor gallery
 * clear} wipes the footprint so a rebuild starts clean. Registered per loader (Fabric
 * {@code CommandRegistrationCallback}, NeoForge/Forge {@code RegisterCommandsEvent}).
 */
public final class NeroDecorCommands {

    private static final int SPACING = 2;
    private static final int FLOAT_ABOVE = 2;
    private static final int GRID_OX = 3;   // offset east of the player
    private static final int GRID_OZ = 3;   // offset south of the player
    private static final int PAINT_ROW_DZ = -3;

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
        source.sendSuccess(() -> Component.literal("NeroDecor gallery built: " + blocks.size()
                + " blocks + " + colours.length + " paint swatches."), false);
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
        for (int gx = -2; gx <= Math.max(cols, DecorColor.values().length) * SPACING; gx++) {
            for (int gz = PAINT_ROW_DZ - 1; gz <= rows * SPACING + 1; gz++) {
                for (int gy = 0; gy <= FLOAT_ABOVE + 1; gy++) {
                    level.setBlockAndUpdate(new BlockPos(ox + gx, fy + gy, oz + gz), air);
                }
            }
        }
        source.sendSuccess(() -> Component.literal("NeroDecor gallery cleared."), false);
        return 1;
    }
}
