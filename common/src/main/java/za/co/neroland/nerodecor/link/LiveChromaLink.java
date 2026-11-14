package za.co.neroland.nerodecor.link;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.LongPredicate;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.nerodecor.chroma.server.ChromaConfig;
import za.co.neroland.nerodecor.chroma.server.ChromaGuard;
import za.co.neroland.nerodecor.chroma.server.ChromaNetworks;
import za.co.neroland.nerodecor.chroma.server.ChromaVerdict;
import za.co.neroland.nerodecor.chroma.server.HubNetwork;

/**
 * {@link ChromaLinkSource} over the running server: every dimension's Chroma store, the shared Chroma limiter, and
 * {@link ChromaGuard} for the online player. The player resolver is the server's player list in production; GameTests
 * pass their mock players.
 */
public final class LiveChromaLink implements ChromaLinkSource {

    private final MinecraftServer server;
    private final Function<UUID, ServerPlayer> players;

    public LiveChromaLink(MinecraftServer server) {
        this(server, id -> server.getPlayerList().getPlayer(id));
    }

    public LiveChromaLink(MinecraftServer server, Function<UUID, ServerPlayer> players) {
        this.server = server;
        this.players = players;
    }

    @Override
    public boolean enabled() {
        return ChromaConfig.ENABLED.get();
    }

    @Override
    public List<HubNetwork> networks() {
        List<HubNetwork> out = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            out.addAll(ChromaNetworks.store(level).all());
        }
        return out;
    }

    @Override
    public Optional<Hub> hub(long id) {
        for (ServerLevel level : server.getAllLevels()) {
            var store = ChromaNetworks.store(level);
            HubNetwork network = store.get(id);
            if (network != null) {
                return Optional.of(new Hub(network, store, level.getGameTime()));
            }
        }
        return Optional.empty();
    }

    @Override
    public LongPredicate editor(UUID player, Hub hub) {
        ServerPlayer online = player == null ? null : players.apply(player);
        if (online == null) {
            return null;
        }
        ServerLevel level = levelOf(hub.network().id());
        if (level == null) {
            return null;
        }
        return packed -> {
            BlockPos pos = BlockPos.of(packed);
            return level.isLoaded(pos) && ChromaGuard.mayEdit(online, level, pos, level.getBlockState(pos));
        };
    }

    @Override
    public ChromaVerdict charge(UUID player, int ops) {
        return ChromaNetworks.limiter().tryAcquire(player, ops, System.nanoTime());
    }

    private ServerLevel levelOf(long id) {
        for (ServerLevel level : server.getAllLevels()) {
            if (ChromaNetworks.store(level).get(id) != null) {
                return level;
            }
        }
        return null;
    }
}
