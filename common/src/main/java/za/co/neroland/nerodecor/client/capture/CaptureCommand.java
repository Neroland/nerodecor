package za.co.neroland.nerodecor.client.capture;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;

/**
 * The client-side {@code /nerodecor capture [showroom|grid|all|here] [count]} command, built once for every loader's
 * client command dispatcher (Fabric {@code ClientCommandRegistrationCallback}, NeoForge and Forge
 * {@code RegisterClientCommandsEvent}); the source type is whatever that loader uses, and it is never read.
 *
 * <p>The server also has a {@code /nerodecor} command ({@code gallery}, {@code privacy}, {@code chroma}, and the
 * operator-only {@code capture goto} this command drives). So that a client {@code nerodecor} root never shadows those,
 * anything after {@code /nerodecor} that is not {@code capture} is sent on to the server unchanged.
 */
public final class CaptureCommand {

    private CaptureCommand() {
    }

    public static <S> LiteralArgumentBuilder<S> node() {
        LiteralArgumentBuilder<S> capture = LiteralArgumentBuilder.<S>literal("capture")
                .executes(ctx -> CaptureSession.start(CaptureSession.ALL, 0));
        for (String target : new String[] {CaptureSession.SHOWROOM, CaptureSession.GRID, CaptureSession.ALL,
                CaptureSession.HERE}) {
            capture.then(LiteralArgumentBuilder.<S>literal(target)
                    .executes(ctx -> CaptureSession.start(target, 0))
                    .then(RequiredArgumentBuilder.<S, Integer>argument("count", IntegerArgumentType.integer(1, 64))
                            .executes(ctx -> CaptureSession.start(target,
                                    IntegerArgumentType.getInteger(ctx, "count")))));
        }
        return LiteralArgumentBuilder.<S>literal("nerodecor")
                .then(capture)
                .then(RequiredArgumentBuilder.<S, String>argument("server", StringArgumentType.greedyString())
                        .executes(ctx -> forward("nerodecor " + StringArgumentType.getString(ctx, "server"))));
    }

    /** Sends a {@code /nerodecor ...} command the client does not handle to the server. */
    private static int forward(String command) {
        return toServer(Minecraft.getInstance(), command) ? 1 : 0;
    }

    /**
     * Sends a command straight to the server as an unsigned command packet. {@code ClientPacketListener.sendCommand}
     * would offer it to the client command dispatcher first (every loader hooks it there), which would loop back into
     * this command; none of the commands sent this way has an argument that needs signing.
     */
    static boolean toServer(Minecraft mc, String command) {
        if (mc.getConnection() == null) {
            return false;
        }
        mc.getConnection().send(new ServerboundChatCommandPacket(command));
        return true;
    }
}
