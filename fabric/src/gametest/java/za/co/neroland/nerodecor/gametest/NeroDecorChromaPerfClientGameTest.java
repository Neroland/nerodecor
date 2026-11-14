package za.co.neroland.nerodecor.gametest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import za.co.neroland.nerodecor.chroma.EffectParams;
import za.co.neroland.nerodecor.chroma.EffectProgram;
import za.co.neroland.nerodecor.chroma.EffectType;
import za.co.neroland.nerodecor.chroma.Scene;
import za.co.neroland.nerodecor.chroma.ZoneAssignment;
import za.co.neroland.nerodecor.chroma.client.ChromaAnimationClient;
import za.co.neroland.nerodecor.chroma.client.ChromaLayerBudget;
import za.co.neroland.nerodecor.chroma.client.ChromaLayerProfile;
import za.co.neroland.nerodecor.chroma.server.ChromaNetworkStore;
import za.co.neroland.nerodecor.chroma.server.ChromaNetworks;
import za.co.neroland.nerodecor.chroma.server.ChromaScenes;
import za.co.neroland.nerodecor.content.block.ChromaHubBlockEntity;
import za.co.neroland.nerodecor.registry.ChromaContent;

/**
 * Performance pass for the Chroma animation layer: one hub with its volume set to 64 x 64 x 64 and 4,096 linked
 * Chroma cubes (a 64 x 64 floor) playing an animated rainbow scene, watched from above for about ten seconds. The
 * layer's per-frame draw cost and per-tick colour-update cost are recorded through {@link ChromaLayerProfile}, with
 * whether the {@link ChromaLayerBudget} planner degraded the hub, and written to {@code chroma-perf.txt} beside the
 * screenshots. The numbers are reported as measured; a budget overrun is recorded, not hidden. Test-only; no player
 * data is written (timings and counts only).
 */
@SuppressWarnings("UnstableApiUsage")
public class NeroDecorChromaPerfClientGameTest implements FabricClientGameTest {

    private static final Logger LOG = LoggerFactory.getLogger("NeroDecorChromaPerf");
    private static final int SIDE = 64;
    private static final int RUN_TICKS = 200; // ~10 s at 20 TPS

    private final List<String> report = new ArrayList<>();

    @Override
    public void runTest(ClientGameTestContext ctx) {
        ctx.getInput().resizeWindow(1600, 900);
        try (TestSingleplayerContext sp = ctx.worldBuilder().adjustSettings(s -> {
            s.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
            s.setAllowCommands(true);
        }).create()) {
            sp.getConnection().waitForChunksRender();
            TestServerContext server = sp.getServer();
            BlockPos spawn = server.computeOnServer(s -> player(s).blockPosition());
            BlockPos centre = spawn.offset(0, 0, 90);
            String linked = server.computeOnServer(s -> build(s, centre));
            report.add("setup   " + linked);
            server.runCommand(String.format(Locale.ROOT, "tp @a %d %d %d 0 70", centre.getX(), centre.getY() + 30,
                    centre.getZ() - 20));
            ctx.runOnClient(mc -> {
                mc.player.getAbilities().flying = true;
                NeroDecorShowcaseClientGameTest.setHudHidden(mc, true);
            });
            ctx.waitTicks(60); // scene + zone sync, first cache rebuild
            sp.getConnection().waitForChunksRender();
            ctx.waitTicks(40);
            ctx.runOnClient(mc -> ChromaAnimationClient.startProfile());
            long t0 = System.nanoTime();
            ctx.waitTicks(RUN_TICKS);
            long wallMs = (System.nanoTime() - t0) / 1_000_000L;
            ChromaLayerProfile p = ctx.computeOnClient(mc -> ChromaAnimationClient.stopProfile());
            ctx.takeScreenshot("chroma_perf_4096");
            double budgetMs = ChromaLayerBudget.FRAME_BUDGET_NANOS / 1_000_000.0;
            if (p == null) {
                report.add("FAIL    no profile window was open");
            } else {
                report.add(String.format(Locale.ROOT, "blocks  animated=%d quads=%d", p.maxBlocks(), p.maxQuads()));
                report.add(String.format(Locale.ROOT, "frames  n=%d avg=%.3f ms p99=%.3f ms (budget %.3f ms, wall %d ms)",
                        p.frames(), p.frameAvgMs(), p.frameP99Ms(), budgetMs, wallMs));
                report.add(String.format(Locale.ROOT, "updates n=%d avg=%.3f ms p99=%.3f ms", p.updates(),
                        p.updateAvgMs(), p.updateP99Ms()));
                boolean over = p.frameAvgMs() + p.updateAvgMs() > budgetMs;
                report.add("budget  degraded=" + p.degraded() + " avg draw+update over budget=" + over);
            }
        } finally {
            write();
        }
    }

    private static String build(MinecraftServer server, BlockPos centre) {
        ServerLevel level = server.overworld();
        ServerPlayer owner = player(server);
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "gamerule advance_time false");
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                "gamerule doDaylightCycle false");
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set midnight");
        Block cube = ChromaContent.CUBE.get();
        int y = centre.getY() - 1;
        int x0 = centre.getX() - SIDE / 2;
        int z0 = centre.getZ() - SIDE / 2;
        for (int dx = 0; dx < SIDE; dx++) {
            for (int dz = 0; dz < SIDE; dz++) {
                level.setBlock(new BlockPos(x0 + dx, y, z0 + dz), cube.defaultBlockState(), Block.UPDATE_CLIENTS);
                level.setBlock(new BlockPos(x0 + dx, y - 1, z0 + dz), Blocks.DEEPSLATE_TILES.defaultBlockState(),
                        Block.UPDATE_CLIENTS);
            }
        }
        BlockPos hubPos = centre.below(2).below(); // under the floor, in the middle of the 64-wide volume
        level.setBlock(hubPos, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        BlockState hubState = ChromaContent.HUB.get().defaultBlockState();
        level.setBlock(hubPos, hubState, Block.UPDATE_ALL);
        hubState.getBlock().setPlacedBy(level, hubPos, hubState, owner, new ItemStack(ChromaContent.HUB_ITEM.get()));
        if (!(level.getBlockEntity(hubPos) instanceof ChromaHubBlockEntity hub) || hub.networkId() == 0L) {
            return "FAIL hub did not start a network";
        }
        long id = hub.networkId();
        ChromaNetworkStore store = ChromaNetworks.store(level);
        var volume = store.setVolume(id, owner.getUUID(), SIDE, SIDE, SIDE);
        var selection = ChromaNetworks.linkBox(level, owner, id, new BlockPos(x0, y, z0),
                new BlockPos(x0 + SIDE - 1, y, z0 + SIDE - 1));
        Scene scene = new Scene("Perf", List.of(new ZoneAssignment(0, 0, new EffectProgram(EffectType.RAINBOW,
                EffectParams.builder().periodTicks(100).wavelength(32).build()))));
        var saved = ChromaScenes.save(store, id, owner.getUUID(), 0, scene, true, level.getGameTime());
        return "volume=" + volume + " linked=" + selection.linked() + " refused=" + selection.refused()
                + " link=" + selection.verdict() + " scene=" + saved;
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().get(0);
    }

    private void write() {
        for (String line : report) {
            LOG.info("[chroma-perf] {}", line);
        }
        Path dir = FabricLoader.getInstance().getGameDir().resolve("screenshots");
        try {
            Files.createDirectories(dir);
            Files.write(dir.resolve("chroma-perf.txt"), report, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.error("[chroma-perf] could not write the report", e);
        }
    }
}
