package za.co.neroland.nerodecor.chroma.net;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.nerodecor.NeroDecorCommon;
import za.co.neroland.nerodecor.chroma.ChromaResult;
import za.co.neroland.nerodecor.chroma.Scene;
import za.co.neroland.nerodecor.chroma.SceneCodec;
import za.co.neroland.nerodecor.chroma.editor.HubAction;
import za.co.neroland.nerodecor.chroma.editor.HubDetail;
import za.co.neroland.nerodecor.chroma.server.ChromaAction;
import za.co.neroland.nerodecor.chroma.server.ChromaConfig;
import za.co.neroland.nerodecor.chroma.server.ChromaHubAdmin;
import za.co.neroland.nerodecor.chroma.server.ChromaLimits;
import za.co.neroland.nerodecor.chroma.server.ChromaNetworkData;
import za.co.neroland.nerodecor.chroma.server.ChromaNetworkStore;
import za.co.neroland.nerodecor.chroma.server.ChromaNetworks;
import za.co.neroland.nerodecor.chroma.server.ChromaReach;
import za.co.neroland.nerodecor.chroma.server.ChromaRole;
import za.co.neroland.nerodecor.chroma.server.ChromaValidator;
import za.co.neroland.nerodecor.chroma.server.ChromaVerdict;
import za.co.neroland.nerodecor.chroma.server.HubNetwork;

/**
 * The Chroma Hub screen's packets: {@link Detail} (server to client: opens or refreshes the screen) and {@link Action}
 * (client to server: zone, trust and scene-slot management). Colour edits use {@link ChromaPayloads.Ops}. Every action
 * is rate limited and re-checks the sender's role on the server, and the sender must be at the hub
 * ({@link ChromaReach}: its volume plus a margin, and {@code ChromaGuard} for changes); the answer is a fresh detail
 * carrying the verdict, which the screen shows. Trust is granted only to players the server knows, up to
 * {@code ChromaLimits.MAX_TRUSTED} per hub. Nothing is logged (POPIA/GDPR).
 *
 * <p>Scene slots belong to the scene system: it plugs in through {@link #setSceneSlots}; until it does, hubs show no
 * slots and slot actions answer {@link ChromaVerdict#DISABLED}.
 */
public final class ChromaHubNet {

    /** Server to client: a {@link HubDetail}. */
    public record Detail(byte[] data) implements CustomPacketPayload {
        public static final Type<Detail> TYPE = new Type<>(id("chroma_hub_detail"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Detail> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> buf.writeByteArray(p.data()), buf -> new Detail(buf.readByteArray(HubDetail.MAX_BYTES)));

        @Override
        public Type<Detail> type() {
            return TYPE;
        }
    }

    /** Client to server: a {@link HubAction}. */
    public record Action(byte[] data) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(id("chroma_hub_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> buf.writeByteArray(p.data()), buf -> new Action(buf.readByteArray(HubAction.MAX_BYTES)));

        @Override
        public Type<Action> type() {
            return TYPE;
        }
    }

    /** The scene system's view of a hub's slots (server side). */
    public interface SceneSlots {
        /** Slots in order: name and the stored scene's {@link SceneCodec} bytes (empty when unset). */
        List<HubDetail.Slot> slots(ServerLevel level, HubNetwork network);

        /** The active slot, or -1. */
        int active(ServerLevel level, HubNetwork network);

        /** Activates slot {@code slot} (-1 = back to static colours). Role is already checked (PAINT). */
        ChromaVerdict activate(ServerLevel level, HubNetwork network, int slot);

        /** Renames slot {@code slot}. Role is already checked (PAINT). */
        ChromaVerdict rename(ServerLevel level, HubNetwork network, int slot, String name);

        /** Stores a decoded, validated scene in slot {@code slot}. Role is already checked (PAINT). */
        ChromaVerdict store(ServerLevel level, HubNetwork network, int slot, Scene scene);

        SceneSlots NONE = new SceneSlots() {
            @Override
            public List<HubDetail.Slot> slots(ServerLevel level, HubNetwork network) {
                return List.of();
            }

            @Override
            public int active(ServerLevel level, HubNetwork network) {
                return -1;
            }

            @Override
            public ChromaVerdict activate(ServerLevel level, HubNetwork network, int slot) {
                return ChromaVerdict.DISABLED;
            }

            @Override
            public ChromaVerdict rename(ServerLevel level, HubNetwork network, int slot, String name) {
                return ChromaVerdict.DISABLED;
            }

            @Override
            public ChromaVerdict store(ServerLevel level, HubNetwork network, int slot, Scene scene) {
                return ChromaVerdict.DISABLED;
            }
        };
    }

    private static volatile SceneSlots scenes = SceneSlots.NONE;
    /** Client seam: set by the client bootstrap; the server never loads a screen class through this file. */
    private static volatile java.util.function.Consumer<byte[]> clientDetail = data -> { };

    private ChromaHubNet() {
    }

    /** Plugs the scene system in (call once during common init). */
    public static void setSceneSlots(SceneSlots slots) {
        scenes = slots == null ? SceneSlots.NONE : slots;
    }

    /** Client bootstrap: where incoming {@link Detail} payloads go (opens or refreshes the screen). */
    public static void setClientDetailHandler(java.util.function.Consumer<byte[]> handler) {
        clientDetail = handler == null ? data -> { } : handler;
    }

    /** Client main thread: one {@link Detail} arrived. */
    public static void onClientDetail(byte[] data) {
        clientDetail.accept(data);
    }

    /** Opens the hub screen for {@code player}, if they may view the network. */
    public static void open(ServerPlayer player, ServerLevel level, HubNetwork network) {
        if (!ChromaValidator.checkAction(network, player.getUUID(), ChromaAction.VIEW).isOk()) {
            ChromaNetworks.tell(player, ChromaVerdict.NO_PERMISSION);
            return;
        }
        send(player, level, network, ChromaVerdict.OK);
    }

    private static void send(ServerPlayer player, ServerLevel level, HubNetwork network, ChromaVerdict verdict) {
        SceneSlots slots = scenes;
        HubDetail detail = ChromaHubAdmin.detail(network, player.getUUID(), ChromaLimits.fromConfig(),
                slots.slots(level, network), slots.active(level, network))
                .withNotice(verdict.isOk() ? "" : verdict.id());
        byte[] bytes = detail.encode();
        if (bytes.length <= HubDetail.MAX_BYTES) {
            ChromaNet.sendToPlayer(player, new Detail(bytes));
        }
    }

    /** Server handler for {@link Action}. */
    public static void handle(ServerPlayer player, byte[] data) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (!ChromaConfig.ENABLED.get()) {
            ChromaNetworks.tell(player, ChromaVerdict.DISABLED);
            return;
        }
        Optional<HubAction> decoded = HubAction.decode(data);
        if (decoded.isEmpty()) {
            return;
        }
        HubAction action = decoded.get();
        ChromaNetworkStore store = ChromaNetworks.store(level);
        HubNetwork network = store.get(action.network());
        if (network == null) {
            ChromaNetworks.tell(player, ChromaVerdict.NO_NETWORK);
            return;
        }
        UUID actor = player.getUUID();
        if (!ChromaValidator.checkAction(network, actor, ChromaAction.VIEW).isOk()) {
            ChromaNetworks.tell(player, ChromaVerdict.NO_PERMISSION);
            return;
        }
        boolean refresh = action.kind() == HubAction.Kind.REFRESH;
        ChromaVerdict near = refresh ? ChromaReach.reach(player, network)
                : ChromaReach.mayEditFromScreen(player, level, network, required(action.kind()));
        if (!near.isOk()) {
            if (!refresh) {
                ChromaNetworks.tell(player, near);
            }
            return; // a refresh from afar is silent; the screen keeps what it has
        }
        ChromaVerdict verdict = ChromaNetworks.limiter().tryAcquire(actor, 0, System.nanoTime());
        if (!verdict.isOk() && refresh) {
            return; // a refused refresh is silent; the screen keeps what it has
        }
        if (verdict.isOk()) {
            verdict = apply(level, store, network, actor, action);
        }
        boolean visible = store.get(action.network()) != null
                && ChromaValidator.checkAction(network, actor, ChromaAction.VIEW).isOk();
        if (visible) {
            send(player, level, network, verdict); // the screen shows the verdict
        } else if (!verdict.isOk()) {
            ChromaNetworks.tell(player, verdict);
        }
    }

    private static ChromaVerdict apply(ServerLevel level, ChromaNetworkStore store, HubNetwork network, UUID actor,
            HubAction action) {
        long id = network.id();
        return switch (action.kind()) {
            case REFRESH -> ChromaVerdict.OK;
            case RENAME_ZONE -> dirty(level, ChromaHubAdmin.renameZone(network, actor, action.a(), action.text()));
            case SET_CHANNEL -> dirty(level, ChromaHubAdmin.setChannel(network, actor, action.a(), action.b()));
            case ADD_ZONE -> store.addZone(id, actor, action.text(), action.b(), ChromaLimits.fromConfig());
            case SET_TRUST -> {
                ChromaRole role = switch (action.a()) {
                    case 0 -> ChromaRole.NONE;
                    case 1 -> ChromaRole.VIEWER;
                    case 2 -> ChromaRole.PAINTER;
                    case 3 -> ChromaRole.ADMIN;
                    default -> null;
                };
                if (role == null || action.player() == null) {
                    yield ChromaVerdict.NO_PERMISSION;
                }
                // Granting needs a real player; revoking always works, so a stale entry can always be removed.
                if (role != ChromaRole.NONE && network.roleOf(action.player()) == ChromaRole.NONE
                        && !ChromaReach.isKnownPlayer(level.getServer(), action.player())) {
                    yield ChromaVerdict.UNKNOWN_PLAYER;
                }
                yield store.setTrust(id, actor, action.player(), role);
            }
            case SET_AUTO_ADOPT -> network.autoAdopt() == (action.b() != 0) ? ChromaVerdict.OK
                    : store.setAutoAdopt(id, actor, action.b() != 0);
            case SCENE_ACTIVATE, SCENE_RENAME, SCENE_STORE -> scene(level, network, actor, action);
        };
    }

    /** The role an action needs (checked before reach, so refusals name the real reason). */
    private static ChromaAction required(HubAction.Kind kind) {
        return switch (kind) {
            case REFRESH -> ChromaAction.VIEW;
            case SET_TRUST -> ChromaAction.TRUST;
            case SCENE_ACTIVATE, SCENE_RENAME, SCENE_STORE -> ChromaAction.PAINT;
            default -> ChromaAction.MANAGE;
        };
    }

    private static ChromaVerdict scene(ServerLevel level, HubNetwork network, UUID actor, HubAction action) {
        ChromaVerdict role = ChromaValidator.checkAction(network, actor, ChromaAction.PAINT);
        if (!role.isOk()) {
            return role;
        }
        SceneSlots slots = scenes;
        return switch (action.kind()) {
            case SCENE_ACTIVATE -> slots.activate(level, network, action.a());
            case SCENE_RENAME -> slots.rename(level, network, action.a(), Scene.sanitiseName(action.text()));
            default -> {
                ChromaResult<Scene> scene = SceneCodec.decode(action.blob());
                yield scene.isOk() ? slots.store(level, network, action.a(), scene.value()) : ChromaVerdict.BAD_OP;
            }
        };
    }

    private static ChromaVerdict dirty(ServerLevel level, ChromaVerdict verdict) {
        if (verdict.isOk()) {
            ChromaNetworkData.get(level).setDirty();
        }
        return verdict;
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(NeroDecorCommon.MOD_ID, path);
    }
}
