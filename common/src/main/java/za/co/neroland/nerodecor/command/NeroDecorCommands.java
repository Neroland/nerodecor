package za.co.neroland.nerodecor.command;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import za.co.neroland.nerodecor.chroma.server.privacy.ChromaPrivacyCommands;
import za.co.neroland.nerodecor.command.showroom.BlockGrid;
import za.co.neroland.nerodecor.command.showroom.CameraPresets;
import za.co.neroland.nerodecor.command.showroom.Showroom;
import za.co.neroland.nerodecor.command.showroom.ShowroomScenes;

/**
 * Showcase commands. Every {@code /nerodecor gallery} subcommand needs operator permission (level 2): the showroom and
 * the grid replace a large box of blocks without drops and delete any Chroma hub network whose hub stood in it,
 * whoever owns it. {@code /nerodecor gallery} builds the walkable {@link Showroom} (eight staged bays) at a fixed
 * offset from the command position; {@code /nerodecor gallery clear} removes the last one built in this dimension.
 * {@code /nerodecor gallery grid} builds the {@link BlockGrid} catalogue (every visible block, one row per collection,
 * plus paint swatches and a Chroma demo strip) the same way, and {@code gallery grid clear} removes the last grid built
 * in this dimension. Both clears refuse when this dimension has no remembered build (they never fall back to the
 * current position).
 *
 * <p>{@code /nerodecor capture goto <preset>} (operator) moves the player to one of the {@link CameraPresets} at the
 * remembered showroom or grid; the client-side {@code /nerodecor capture} drives it. Registered per loader (Fabric
 * {@code CommandRegistrationCallback}, NeoForge/Forge {@code RegisterCommandsEvent}).
 */
public final class NeroDecorCommands {

    private NeroDecorCommands() {
    }

    /** Attach the command tree to a dispatcher. Called from each loader's command hook. */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("nerodecor")
                .requires(src -> src.getPlayer() != null)
                .then(Commands.literal("gallery")
                        .requires(operator())
                        .executes(ctx -> showroom(ctx.getSource()))
                        .then(Commands.literal("clear").executes(ctx -> clearShowroom(ctx.getSource())))
                        .then(Commands.literal("grid")
                                .executes(ctx -> grid(ctx.getSource()))
                                .then(Commands.literal("clear").executes(ctx -> clearGrid(ctx.getSource())))))
                .then(Commands.literal("capture")
                        .requires(operator())
                        .then(Commands.literal("goto")
                                .then(Commands.argument("preset", StringArgumentType.word())
                                        .executes(ctx -> captureGoto(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "preset"))))))
                .then(ChromaPrivacyCommands.privacyNode())
                .then(ChromaPrivacyCommands.chromaNode()));
    }

    /** Last showroom origin per dimension (this session), so {@code gallery clear} finds it again. */
    private static final Map<ResourceKey<Level>, BlockPos> SHOWROOMS = new HashMap<>();
    /** Last grid origin per dimension (this session), so {@code gallery grid clear} finds it again. */
    private static final Map<ResourceKey<Level>, BlockPos> GRIDS = new HashMap<>();

    private static Predicate<CommandSourceStack> operator() {
        //? if >=1.21.11 {
        return Commands.hasPermission(Commands.LEVEL_GAMEMASTERS);
        //?} else {
        /*return source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS);
        *///?}
    }

    private static boolean allowed(CommandSourceStack source) {
        return source.getPlayer() != null && operator().test(source);
    }

    private static int showroom(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Run this as a player."));
            return 0;
        }
        if (!allowed(source)) {
            source.sendFailure(Component.literal("The NeroDecor showroom needs operator rights."));
            return 0;
        }
        ServerLevel level = source.getLevel();
        BlockPos origin = Showroom.originFor(BlockPos.containing(source.getPosition()));
        if (!Showroom.fits(level, origin)) {
            source.sendFailure(Component.literal("The showroom does not fit inside the build height here."));
            return 0;
        }
        Showroom.Result result = Showroom.build(level, origin, player);
        SHOWROOMS.put(level.dimension(), origin);
        BlockPos lo = result.min();
        BlockPos hi = result.max();
        source.sendSuccess(() -> Component.literal("NeroDecor showroom built: " + ShowroomScenes.ALL.size()
                + " bays, " + result.placed() + " blocks, from " + lo.toShortString() + " to " + hi.toShortString()
                + ". Walk the corridor east; /nerodecor gallery clear removes it."), false);
        for (String note : result.notes()) {
            source.sendSystemMessage(Component.literal("Note: " + note));
        }
        for (String error : result.errors()) {
            source.sendFailure(Component.literal(error));
        }
        return result.errors().isEmpty() ? 1 : 0;
    }

    private static int clearShowroom(CommandSourceStack source) {
        if (!allowed(source)) {
            source.sendFailure(Component.literal("The NeroDecor showroom needs operator rights."));
            return 0;
        }
        ServerLevel level = source.getLevel();
        BlockPos origin = SHOWROOMS.remove(level.dimension());
        if (origin == null) {
            source.sendFailure(Component.literal("No NeroDecor showroom has been built in this dimension since the "
                    + "server started, so there is nothing to clear. Nothing was changed."));
            return 0;
        }
        Showroom.Cleared cleared = Showroom.clear(level, origin);
        BlockPos lo = Showroom.min(origin);
        BlockPos hi = Showroom.max(origin);
        source.sendSuccess(() -> Component.literal("NeroDecor showroom cleared (" + cleared.blocks() + " blocks, "
                + cleared.hubs() + " Chroma hub network(s), " + lo.toShortString() + " to " + hi.toShortString()
                + ")."), false);
        return 1;
    }

    private static int grid(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Run this as a player."));
            return 0;
        }
        if (!allowed(source)) {
            source.sendFailure(Component.literal("The NeroDecor block grid needs operator rights."));
            return 0;
        }
        ServerLevel level = source.getLevel();
        BlockPos origin = BlockGrid.originFor(BlockPos.containing(source.getPosition()));
        BlockGrid.Layout layout = BlockGrid.layout();
        if (!BlockGrid.fits(level, origin, layout)) {
            source.sendFailure(Component.literal("The block grid does not fit inside the build height here."));
            return 0;
        }
        // A grid built elsewhere in this dimension stays until its own clear; remember only the newest.
        BlockGrid.Result result = BlockGrid.build(level, origin, player);
        GRIDS.put(level.dimension(), origin);
        BlockPos lo = result.min();
        BlockPos hi = result.max();
        source.sendSuccess(() -> Component.literal("NeroDecor block grid built: " + result.shown().size()
                + " blocks in " + BlockGrid.COLLECTIONS.size() + " collections, plus paint swatches and a Chroma demo ("
                + result.placed() + " placed, " + lo.toShortString() + " to " + hi.toShortString()
                + "). /nerodecor gallery grid clear removes it."), false);
        for (String note : result.notes()) {
            source.sendSystemMessage(Component.literal("Note: " + note));
        }
        for (String error : result.errors()) {
            source.sendFailure(Component.literal(error));
        }
        return result.errors().isEmpty() ? 1 : 0;
    }

    private static int clearGrid(CommandSourceStack source) {
        if (!allowed(source)) {
            source.sendFailure(Component.literal("The NeroDecor block grid needs operator rights."));
            return 0;
        }
        ServerLevel level = source.getLevel();
        BlockPos origin = GRIDS.remove(level.dimension());
        if (origin == null) {
            source.sendFailure(Component.literal("No NeroDecor block grid has been built in this dimension since the "
                    + "server started, so there is nothing to clear. Nothing was changed."));
            return 0;
        }
        Showroom.Cleared cleared = BlockGrid.clear(level, origin);
        source.sendSuccess(() -> Component.literal("NeroDecor block grid cleared (" + cleared.blocks() + " blocks, "
                + cleared.hubs() + " Chroma hub network(s))."), false);
        return 1;
    }

    /** Teleports the player to a camera preset at the remembered showroom or grid in this dimension. */
    private static int captureGoto(CommandSourceStack source, String id) {
        ServerPlayer player = source.getPlayer();
        if (player == null || !allowed(source)) {
            source.sendFailure(Component.literal("Capture viewpoints need operator rights."));
            return 0;
        }
        Optional<CameraPresets.Preset> found = CameraPresets.byId(id);
        if (found.isEmpty()) {
            source.sendFailure(Component.literal("No capture viewpoint named " + id + "."));
            return 0;
        }
        CameraPresets.Preset preset = found.get();
        ServerLevel level = source.getLevel();
        boolean grid = CameraPresets.GRID.equals(preset.target());
        BlockPos origin = (grid ? GRIDS : SHOWROOMS).get(level.dimension());
        if (origin == null) {
            source.sendFailure(Component.literal("No NeroDecor " + (grid ? "block grid" : "showroom")
                    + " has been built in this dimension since the server started; run /nerodecor gallery"
                    + (grid ? " grid" : "") + " first."));
            return 0;
        }
        String tp = String.format(Locale.ROOT, "tp @s %.2f %.2f %.2f %.1f %.1f", origin.getX() + preset.dx(),
                origin.getY() + preset.dy(), origin.getZ() + preset.dz(), preset.yaw(), preset.pitch());
        source.getServer().getCommands().performPrefixedCommand(source.withSuppressedOutput(), tp);
        return 1;
    }
}
