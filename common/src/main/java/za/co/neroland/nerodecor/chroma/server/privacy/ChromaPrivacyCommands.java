package za.co.neroland.nerodecor.chroma.server.privacy;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import java.util.function.Predicate;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.nerodecor.NeroDecorCommon;
import za.co.neroland.nerodecor.chroma.server.ChromaConfig;
import za.co.neroland.nerodecor.chroma.server.ChromaVerdict;

/**
 * Chroma privacy commands, attached under {@code /nerodecor}:
 *
 * <pre>
 * /nerodecor privacy export   (any player) writes everything Chroma holds about you to one JSON file
 * /nerodecor chroma claim     (operators) takes over the orphaned hub network whose volume you stand in
 * </pre>
 *
 * Replies name no other player. Nothing is logged.
 */
public final class ChromaPrivacyCommands {

    private ChromaPrivacyCommands() {
    }

    /** {@code privacy export}. */
    public static LiteralArgumentBuilder<CommandSourceStack> privacyNode() {
        return Commands.literal("privacy")
                .then(Commands.literal("export").executes(ctx -> export(ctx.getSource())));
    }

    /** {@code chroma claim} (operators only). */
    public static LiteralArgumentBuilder<CommandSourceStack> chromaNode() {
        return Commands.literal("chroma")
                .then(Commands.literal("claim").requires(operator()).executes(ctx -> claim(ctx.getSource())));
    }

    private static int export(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        UUID subject = player.getUUID();
        long now = ChromaPrivacy.now();
        long wait = ChromaExport.COOLDOWN.tryAcquire(subject, now);
        if (wait > 0L) {
            source.sendFailure(Component.translatable("message." + NeroDecorCommon.MOD_ID + ".privacy.export.cooldown",
                    Long.toString((wait + 999L) / 1000L)));
            return 0;
        }
        Path directory = ChromaExport.directory(source.getServer());
        ChromaExport.purgeExpired(directory, now);
        try {
            String relative = ChromaExport.write(directory, subject, ChromaExport.build(subject,
                    ChromaExport.collect(source.getServer()), now, ChromaConfig.ORPHAN_RETENTION_DAYS.get(),
                    ChromaConfig.INACTIVE_RETENTION_DAYS.get()));
            source.sendSuccess(() -> Component.translatable("message." + NeroDecorCommon.MOD_ID
                    + ".privacy.export.done", relative, Integer.toString(ChromaExport.RETENTION_DAYS)), false);
            return 1;
        } catch (IOException | RuntimeException e) {
            NeroDecorCommon.LOGGER.warn("[NeroDecor] A Chroma export could not be written ({}).",
                    e.getClass().getName());
            source.sendFailure(Component.translatable("message." + NeroDecorCommon.MOD_ID
                    + ".privacy.export.failed"));
            return 0;
        }
    }

    private static int claim(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        BlockPos pos = BlockPos.containing(source.getPosition());
        ChromaVerdict verdict = ChromaPrivacy.claimAt(source.getLevel(), pos.getX(), pos.getY(), pos.getZ(),
                player.getUUID());
        if (verdict.isOk()) {
            source.sendSuccess(() -> Component.translatable("message." + NeroDecorCommon.MOD_ID
                    + ".chroma.claimed"), false);
            return 1;
        }
        source.sendFailure(Component.translatable("message." + NeroDecorCommon.MOD_ID + ".chroma.no_orphan"));
        return 0;
    }

    static Predicate<CommandSourceStack> operator() {
        //? if >=1.21.11 {
        return Commands.hasPermission(Commands.LEVEL_GAMEMASTERS);
        //?} else {
        /*return source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS);
        *///?}
    }
}
