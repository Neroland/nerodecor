package za.co.neroland.nerodecor.link;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongPredicate;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import za.co.neroland.nerolandcore.link.LinkActionHandler;
import za.co.neroland.nerolandcore.link.LinkActionResult;

import za.co.neroland.nerodecor.chroma.ChromaColor;
import za.co.neroland.nerodecor.chroma.ChromaOp;
import za.co.neroland.nerodecor.chroma.ChromaOpBatch;
import za.co.neroland.nerodecor.chroma.ChromaPalette;
import za.co.neroland.nerodecor.chroma.ChromaResult;
import za.co.neroland.nerodecor.chroma.SceneSlots;
import za.co.neroland.nerodecor.chroma.server.ChromaAction;
import za.co.neroland.nerodecor.chroma.server.ChromaScenes;
import za.co.neroland.nerodecor.chroma.server.ChromaVerdict;
import za.co.neroland.nerodecor.chroma.server.HubNetwork;

/**
 * The four Chroma remote actions of link schema 2. Each one is a thin adapter over the call the Chroma Hub GUI makes,
 * so the link can never do what the GUI would refuse:
 *
 * <ul>
 *   <li>{@code apply_scene {hub, slot}} - {@link ChromaScenes#activate}: painter role, the photosensitivity flash clamp,
 *       switches the hub to manual trigger (as the GUI's scene buttons do);</li>
 *   <li>{@code set_zone_colour {hub, zone, colour}} - recolours every block of one zone through
 *       {@code ChromaNetworkStore.applyOps}: painter role, every block linked, and {@code ChromaGuard} (game mode,
 *       spawn protection, world border, claim mods) for every block, checked for all of them before anything
 *       changes;</li>
 *   <li>{@code toggle_animation {hub}} - stops the playing scene (static colours) or resumes the last one played
 *       (else the first filled slot), through {@link ChromaScenes#activate};</li>
 *   <li>{@code set_brightness {hub, value}} - sets the brightness (0-15) of every palette entry through
 *       {@code applyOps}.</li>
 * </ul>
 *
 * <p><b>Limits.</b> One action per second per requester ({@link LinkRateLimiter}), charged before anything else so a
 * refused request still costs its token; then the shared Chroma edit limiter, exactly as the GUI is charged. Bodies
 * over {@value #MAX_BODY_BYTES} bytes are refused unread. The player must be online (the bridge enforces it, and the
 * world guard needs the player).
 *
 * <p><b>Refusals</b> carry a short token as their message ({@code rate_limited}, {@code not_owner}, {@code role},
 * {@code protected}, {@code flash_rejected}, ...), never server text, and never say whether a hub the requester
 * cannot see exists: an unknown id and somebody else's hub are both {@code not_owner}.
 *
 * <p>Nothing here logs.
 */
public final class ChromaLinkActions implements LinkActionHandler {

    public static final String APPLY_SCENE = "apply_scene";
    public static final String SET_ZONE_COLOUR = "set_zone_colour";
    public static final String TOGGLE_ANIMATION = "toggle_animation";
    public static final String SET_BRIGHTNESS = "set_brightness";

    public static final List<String> ACTIONS = List.of(APPLY_SCENE, SET_ZONE_COLOUR, TOGGLE_ANIMATION,
            SET_BRIGHTNESS);

    static final int MAX_BODY_BYTES = 1024;

    private final Supplier<ChromaLinkSource> source;
    private final LinkRateLimiter limiter;
    /** The slot each hub last played, so {@code toggle_animation} can resume it. In memory only; no player data. */
    private final Map<Long, Integer> lastPlayed = new ConcurrentHashMap<>();

    public ChromaLinkActions(Supplier<ChromaLinkSource> source) {
        this(source, System::nanoTime);
    }

    ChromaLinkActions(Supplier<ChromaLinkSource> source, LongSupplier clock) {
        this.source = source;
        this.limiter = new LinkRateLimiter(clock);
    }

    @Override
    public String moduleId() {
        return NeroDecorLinkModule.MODULE_ID;
    }

    @Override
    public List<String> actionIds() {
        return ACTIONS;
    }

    /** Forget every rate-limit bucket and remembered slot (server stop). */
    void clear() {
        limiter.clear();
        lastPlayed.clear();
    }

    @Override
    public LinkActionResult execute(UUID playerId, String actionId, JsonObject params) {
        if (!ACTIONS.contains(actionId)) {
            return invalid("unknown_action");
        }
        if (playerId == null) {
            return notOwner();
        }
        if (!limiter.tryAcquire(playerId)) {
            return invalid("rate_limited");
        }
        JsonObject body = params == null ? new JsonObject() : params;
        if (body.toString().getBytes(StandardCharsets.UTF_8).length > MAX_BODY_BYTES) {
            return invalid("too_large");
        }
        ChromaLinkSource world = source.get();
        if (world == null) {
            return LinkActionResult.error(LinkActionResult.Error.INTERNAL, "not_running");
        }
        if (!world.enabled()) {
            return LinkActionResult.error(LinkActionResult.Error.ACTION_DISABLED, "chroma_disabled");
        }
        Optional<ChromaLinkSource.Hub> found = string(body, "hub").flatMap(ChromaLinkSections::parseHubId)
                .flatMap(world::hub);
        if (found.isEmpty() || !ChromaLinkSections.visible(found.get().network(), playerId)) {
            return notOwner(); // unknown and not-yours look the same
        }
        ChromaLinkSource.Hub hub = found.get();
        HubNetwork network = hub.network();
        if (!network.roleOf(playerId).atLeast(ChromaAction.PAINT.minimum())) {
            return LinkActionResult.error(LinkActionResult.Error.NOT_OWNER, "role");
        }
        LongPredicate editor = world.editor(playerId, hub);
        if (editor == null) {
            return LinkActionResult.error(LinkActionResult.Error.PLAYER_OFFLINE_REQUIRED, "offline");
        }
        ChromaVerdict verdict = switch (actionId) {
            case APPLY_SCENE -> applyScene(world, hub, playerId, body);
            case SET_ZONE_COLOUR -> setZoneColour(world, hub, playerId, body, editor);
            case TOGGLE_ANIMATION -> toggleAnimation(world, hub, playerId);
            case SET_BRIGHTNESS -> setBrightness(world, hub, playerId, body, editor);
            default -> ChromaVerdict.BAD_OP;
        };
        if (verdict == null) {
            return invalid("bad_params");
        }
        if (!verdict.isOk()) {
            return refused(verdict);
        }
        int active = network.scenes().active();
        if (SceneSlots.isSlot(active)) {
            lastPlayed.put(network.id(), active);
        }
        JsonObject state = new JsonObject();
        state.add("hub", ChromaLinkSections.hubRow(network, playerId));
        return LinkActionResult.ok(state);
    }

    // --- actions ----------------------------------------------------------------------------------------------------

    private ChromaVerdict applyScene(ChromaLinkSource world, ChromaLinkSource.Hub hub, UUID player, JsonObject body) {
        OptionalInt slot = integer(body, "slot");
        if (slot.isEmpty()) {
            return null;
        }
        if (!SceneSlots.isSlot(slot.getAsInt())) {
            return ChromaVerdict.BAD_SLOT;
        }
        ChromaVerdict rate = world.charge(player, 1);
        if (!rate.isOk()) {
            return rate;
        }
        return ChromaScenes.activate(hub.store(), hub.network().id(), player, slot.getAsInt(), hub.gameTime());
    }

    private ChromaVerdict toggleAnimation(ChromaLinkSource world, ChromaLinkSource.Hub hub, UUID player) {
        HubNetwork network = hub.network();
        SceneSlots slots = network.scenes();
        int target;
        if (slots.activeScene() != null) {
            lastPlayed.put(network.id(), slots.active());
            target = SceneSlots.STATIC;
        } else {
            Integer last = lastPlayed.get(network.id());
            target = last != null && slots.scene(last) != null ? last : firstFilled(slots);
            if (target == SceneSlots.STATIC) {
                return ChromaVerdict.BAD_SLOT; // nothing to play
            }
        }
        ChromaVerdict rate = world.charge(player, 1);
        if (!rate.isOk()) {
            return rate;
        }
        return ChromaScenes.activate(hub.store(), network.id(), player, target, hub.gameTime());
    }

    private ChromaVerdict setZoneColour(ChromaLinkSource world, ChromaLinkSource.Hub hub, UUID player,
            JsonObject body, LongPredicate editor) {
        OptionalInt zone = integer(body, "zone");
        Optional<ChromaColor> parsed = string(body, "colour").flatMap(ChromaColor::parseHex);
        if (zone.isEmpty() || parsed.isEmpty()) {
            return null;
        }
        HubNetwork network = hub.network();
        if (zone.getAsInt() < 0 || zone.getAsInt() >= network.zones().size()) {
            return ChromaVerdict.BAD_ZONE;
        }
        // The zone keeps the hub's current brightness: colour and brightness are separate controls.
        int brightness = ChromaLinkSections.brightness(network).orElse(ChromaColor.MAX_BRIGHTNESS);
        ChromaColor colour = parsed.get().withBrightness(brightness);
        ChromaPalette palette = network.palette();
        int index = palette.indexOf(colour);
        List<ChromaOp> ops = new ArrayList<>();
        if (index < 0) {
            if (palette.isFull()) {
                return ChromaVerdict.BAD_OP;
            }
            index = palette.size();
            ops.add(ChromaOp.setPaletteEntry(index, colour));
        }
        List<Integer> targets = new ArrayList<>();
        for (int rel : network.linkedOffsets()) {
            if (network.zoneAt(rel) == zone.getAsInt()) {
                targets.add(rel);
                if (network.paletteIndexAt(rel) != index) {
                    ops.add(ChromaOp.setIndex(rel, index));
                }
            }
        }
        if (targets.isEmpty()) {
            return ChromaVerdict.NOT_LINKED;
        }
        // All-or-nothing: every block of the zone must pass the world guard before the first batch is applied.
        for (int rel : targets) {
            if (!editor.test(network.absolute(rel))) {
                return ChromaVerdict.PROTECTED;
            }
        }
        return apply(world, hub, player, ops, editor);
    }

    private ChromaVerdict setBrightness(ChromaLinkSource world, ChromaLinkSource.Hub hub, UUID player,
            JsonObject body, LongPredicate editor) {
        OptionalInt value = integer(body, "value");
        if (value.isEmpty()) {
            return null;
        }
        if (value.getAsInt() < 0 || value.getAsInt() > ChromaColor.MAX_BRIGHTNESS) {
            return ChromaVerdict.BAD_OP;
        }
        ChromaPalette palette = hub.network().palette();
        if (palette.size() == 0) {
            return ChromaVerdict.NOT_LINKED;
        }
        List<ChromaOp> ops = new ArrayList<>();
        for (int i = 0; i < palette.size(); i++) {
            ops.add(ChromaOp.setPaletteEntry(i, palette.get(i).withBrightness(value.getAsInt())));
        }
        return apply(world, hub, player, ops, editor);
    }

    /** Charges the Chroma limiter for every op, then applies them through the store in batches of the GUI's size. */
    private static ChromaVerdict apply(ChromaLinkSource world, ChromaLinkSource.Hub hub, UUID player,
            List<ChromaOp> ops, LongPredicate editor) {
        ChromaVerdict rate = world.charge(player, ops.size());
        if (!rate.isOk()) {
            return rate;
        }
        for (int from = 0; from < ops.size(); from += ChromaOpBatch.MAX_OPS) {
            ChromaResult<ChromaOpBatch> batch = ChromaOpBatch.of(
                    ops.subList(from, Math.min(ops.size(), from + ChromaOpBatch.MAX_OPS)));
            if (!batch.isOk()) {
                return ChromaVerdict.BAD_OP;
            }
            ChromaVerdict verdict = hub.store().applyOps(hub.network().id(), player, batch.value(), editor);
            if (!verdict.isOk()) {
                return verdict;
            }
        }
        return ChromaVerdict.OK;
    }

    // --- helpers ----------------------------------------------------------------------------------------------------

    private static int firstFilled(SceneSlots slots) {
        for (int slot = 0; slot < SceneSlots.MAX_SLOTS; slot++) {
            if (slots.scene(slot) != null) {
                return slot;
            }
        }
        return SceneSlots.STATIC;
    }

    /** A refused verdict as the link error the bridge maps to a status code. */
    static LinkActionResult refused(ChromaVerdict verdict) {
        return switch (verdict) {
            case NO_PERMISSION -> LinkActionResult.error(LinkActionResult.Error.NOT_OWNER, "role");
            case NO_NETWORK -> notOwner();
            case PROTECTED -> LinkActionResult.error(LinkActionResult.Error.NOT_OWNER, "protected");
            case DISABLED -> LinkActionResult.error(LinkActionResult.Error.ACTION_DISABLED, "chroma_disabled");
            default -> invalid(verdict.id());
        };
    }

    private static LinkActionResult notOwner() {
        return LinkActionResult.error(LinkActionResult.Error.NOT_OWNER, "not_owner");
    }

    private static LinkActionResult invalid(String token) {
        return LinkActionResult.error(LinkActionResult.Error.VALIDATION, token);
    }

    private static Optional<String> string(JsonObject body, String key) {
        JsonElement value = body.get(key);
        if (value instanceof JsonPrimitive primitive && primitive.isString()) {
            return Optional.of(primitive.getAsString());
        }
        return Optional.empty();
    }

    private static OptionalInt integer(JsonObject body, String key) {
        JsonElement value = body.get(key);
        if (value instanceof JsonPrimitive primitive && primitive.isNumber()) {
            double d = primitive.getAsDouble();
            if (d == Math.rint(d) && d >= Integer.MIN_VALUE && d <= Integer.MAX_VALUE) {
                return OptionalInt.of((int) d);
            }
        }
        return OptionalInt.empty();
    }
}
