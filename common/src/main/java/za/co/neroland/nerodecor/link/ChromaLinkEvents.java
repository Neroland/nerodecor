package za.co.neroland.nerodecor.link;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

import com.google.gson.JsonObject;

import za.co.neroland.nerolandcore.link.LinkEvent;

import za.co.neroland.nerodecor.chroma.Scene;
import za.co.neroland.nerodecor.chroma.SceneSlots;
import za.co.neroland.nerodecor.chroma.server.ChromaHooks;
import za.co.neroland.nerodecor.chroma.server.ChromaRole;
import za.co.neroland.nerodecor.chroma.server.HubNetwork;

/**
 * Chroma's live events for companion clients, all on topic {@value #TOPIC} (so the app's hub list, subscribed to
 * {@code nerodecor.chroma_hubs}, gets the section as its on-subscribe snapshot and these as deltas), discriminated by
 * {@code type}:
 *
 * <ul>
 *   <li>{@code scene_changed} - {@code hub}, {@code slot} (-1 = static colours), {@code scene} (name or null),
 *       {@code by_trigger};</li>
 *   <li>{@code trigger_fired} - {@code hub}, {@code trigger} ({@code redstone} / {@code daylight}), {@code slot};
 *       at most one per hub per second (a redstone clock must not flood the bus);</li>
 *   <li>{@code hub_broken} - {@code hub}.</li>
 * </ul>
 *
 * <p><b>Scoping.</b> Every event is {@link LinkEvent#forPlayer player-scoped}, one copy per member: the hub's owner and
 * each trusted player (viewer or higher). Never a broadcast. Payloads carry the hub id and scene data only: no
 * position, no dimension, no other player.
 */
final class ChromaLinkEvents implements ChromaHooks.Listener {

    static final String TOPIC = "chroma_hubs";
    static final String SCENE_CHANGED = "scene_changed";
    static final String TRIGGER_FIRED = "trigger_fired";
    static final String HUB_BROKEN = "hub_broken";

    static final long TRIGGER_COOLDOWN_NANOS = 1_000_000_000L;

    private final Consumer<LinkEvent> publisher;
    private final LongSupplier clock;
    private final Map<Long, Long> lastTrigger = new ConcurrentHashMap<>();

    ChromaLinkEvents(Consumer<LinkEvent> publisher, LongSupplier clock) {
        this.publisher = publisher;
        this.clock = clock;
    }

    void clear() {
        lastTrigger.clear();
    }

    /** Who hears about a hub: its owner and every trusted member. Never anyone else. */
    static List<UUID> recipients(HubNetwork network) {
        List<UUID> out = new ArrayList<>();
        if (network == null || network.orphaned()) {
            return out;
        }
        if (!HubNetwork.NO_OWNER.equals(network.owner())) {
            out.add(network.owner());
        }
        for (Map.Entry<UUID, ChromaRole> member : network.trusted().entrySet()) {
            if (member.getValue().atLeast(ChromaRole.VIEWER) && !out.contains(member.getKey())) {
                out.add(member.getKey());
            }
        }
        return out;
    }

    @Override
    public void sceneChanged(HubNetwork network, int slot, boolean byTrigger) {
        JsonObject payload = base(SCENE_CHANGED, network);
        Scene scene = network.scenes().scene(slot);
        payload.addProperty("slot", scene == null ? SceneSlots.STATIC : slot);
        payload.addProperty("scene", scene == null ? null : scene.name());
        payload.addProperty("by_trigger", byTrigger);
        publish(network, payload);
    }

    @Override
    public void triggerFired(HubNetwork network, SceneSlots.Trigger trigger, int slot) {
        long now = clock.getAsLong();
        Long last = lastTrigger.get(network.id());
        if (last != null && now - last < TRIGGER_COOLDOWN_NANOS) {
            return;
        }
        lastTrigger.put(network.id(), now);
        JsonObject payload = base(TRIGGER_FIRED, network);
        payload.addProperty("trigger", trigger == null ? null : trigger.id());
        payload.addProperty("slot", slot);
        publish(network, payload);
    }

    @Override
    public void hubBroken(HubNetwork network) {
        lastTrigger.remove(network.id());
        publish(network, base(HUB_BROKEN, network));
    }

    private static JsonObject base(String type, HubNetwork network) {
        JsonObject payload = new JsonObject();
        payload.addProperty("type", type);
        payload.addProperty("hub", ChromaLinkSections.hubId(network.id()));
        payload.addProperty("name", ChromaLinkSections.hubName(network.id()));
        return payload;
    }

    private void publish(HubNetwork network, JsonObject payload) {
        for (UUID member : recipients(network)) {
            publisher.accept(LinkEvent.forPlayer(NeroDecorLinkModule.MODULE_ID, TOPIC, member, payload.deepCopy()));
        }
    }
}
