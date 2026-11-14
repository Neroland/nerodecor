package za.co.neroland.nerodecor.link;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import za.co.neroland.nerodecor.chroma.ChromaColor;
import za.co.neroland.nerodecor.chroma.ChromaPalette;
import za.co.neroland.nerodecor.chroma.Scene;
import za.co.neroland.nerodecor.chroma.SceneSlots;
import za.co.neroland.nerodecor.chroma.server.ChromaAction;
import za.co.neroland.nerodecor.chroma.server.ChromaRole;
import za.co.neroland.nerodecor.chroma.server.HubNetwork;

/**
 * The three Chroma sections of link schema 2: {@code chroma_hubs}, {@code chroma_zones} and {@code chroma_scenes}.
 * Pure: built from {@link HubNetwork}s handed over by a {@link ChromaLinkSource}, so JUnit can assert on the exact
 * payload.
 *
 * <h2>Scoping (POPIA/GDPR)</h2>
 *
 * <ul>
 *   <li>A requester sees a hub only when {@link HubNetwork#roleOf} gives them {@link ChromaRole#VIEWER} or higher:
 *       hubs they own or are trusted on. Orphaned hubs (owner erased) answer {@code NONE} for everybody.</li>
 *   <li>No coordinates: not the hub's anchor, not its volume, not a block position, not the dimension. A hub is
 *       named by its random network id.</li>
 *   <li>No roster: no other player's UUID or name. The {@code members} object is three counts (admins, painters,
 *       viewers). The requester's own role is the only role named.</li>
 *   <li>A hub that does not exist and a hub the requester may not see produce the same empty answer, so the
 *       sections cannot be used to probe ids.</li>
 * </ul>
 */
public final class ChromaLinkSections {

    /** Cap on hubs listed for one requester (the hub cap per owner is far lower; trusted hubs add to it). */
    static final int MAX_HUBS = 64;

    private ChromaLinkSections() {
    }

    // --- ids and labels -------------------------------------------------------------------------------------------

    /** A network id as the link API spells it: 16 lower-case hex digits. */
    public static String hubId(long id) {
        return String.format(Locale.ROOT, "%016x", id);
    }

    /** Parses {@link #hubId}; empty for anything else (including id 0, which is reserved). */
    public static Optional<Long> parseHubId(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String s = raw.trim();
        if (s.isEmpty() || s.length() > 16) {
            return Optional.empty();
        }
        for (int i = 0; i < s.length(); i++) {
            if (Character.digit(s.charAt(i), 16) < 0) {
                return Optional.empty();
            }
        }
        long id = Long.parseUnsignedLong(s, 16);
        return id == 0L ? Optional.empty() : Optional.of(id);
    }

    /**
     * The hub's display name. Hubs carry no player-set name in 0.8.0, so the link names one after the last four hex
     * digits of its id: stable, unique enough to tell a player's hubs apart, and not a position.
     */
    public static String hubName(long id) {
        String hex = hubId(id);
        return "Chroma Hub " + hex.substring(hex.length() - 4).toUpperCase(Locale.ROOT);
    }

    /** The role a requester has on a hub, or {@link ChromaRole#NONE}. */
    static ChromaRole roleOf(HubNetwork network, UUID requester) {
        return network == null || requester == null ? ChromaRole.NONE : network.roleOf(requester);
    }

    /** Whether the requester may see this hub through the link at all (viewer or higher). */
    public static boolean visible(HubNetwork network, UUID requester) {
        return roleOf(network, requester).atLeast(ChromaAction.VIEW.minimum());
    }

    /**
     * The hub's static brightness (0-15): the brightest palette entry, which is what every linked block shows at
     * most. Empty when the palette is.
     */
    public static OptionalInt brightness(HubNetwork network) {
        ChromaPalette palette = network.palette();
        int max = -1;
        for (int i = 0; i < palette.size(); i++) {
            max = Math.max(max, palette.get(i).brightness());
        }
        return max < 0 ? OptionalInt.empty() : OptionalInt.of(max);
    }

    /** The colour most of a zone's blocks show (lowest palette index on a tie), or null for an empty zone. */
    public static ChromaColor zoneColour(HubNetwork network, int zone) {
        int[] counts = new int[ChromaPalette.MAX_ENTRIES];
        for (int rel : network.linkedOffsets()) {
            if (network.zoneAt(rel) == zone) {
                int index = network.paletteIndexAt(rel);
                if (index >= 0 && index < counts.length) {
                    counts[index]++;
                }
            }
        }
        int best = -1;
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] > 0 && (best < 0 || counts[i] > counts[best])) {
                best = i;
            }
        }
        return best >= 0 && network.palette().isValidIndex(best) ? network.palette().get(best) : null;
    }

    /** How many linked blocks sit in a zone. */
    public static int zoneBlocks(HubNetwork network, int zone) {
        int n = 0;
        for (int rel : network.linkedOffsets()) {
            if (network.zoneAt(rel) == zone) {
                n++;
            }
        }
        return n;
    }

    // --- sections -------------------------------------------------------------------------------------------------

    /** {@code chroma_hubs}: every hub the requester owns or is trusted on. */
    public static JsonObject hubs(ChromaLinkSource source, UUID requester) {
        JsonObject root = envelope(source);
        JsonArray rows = new JsonArray();
        List<HubNetwork> visible = new ArrayList<>();
        if (source != null && source.enabled()) {
            for (HubNetwork network : source.networks()) {
                if (visible(network, requester)) {
                    visible.add(network);
                }
            }
        }
        // Owned hubs first, then by id, so the list is stable between polls.
        visible.sort(Comparator.comparing((HubNetwork n) -> n.roleOf(requester) != ChromaRole.OWNER)
                .thenComparing(n -> hubId(n.id())));
        for (HubNetwork network : visible) {
            if (rows.size() >= MAX_HUBS) {
                break;
            }
            rows.add(hubRow(network, requester));
        }
        root.add("hubs", rows);
        root.addProperty("hubs_visible", visible.size());
        root.addProperty("truncated", visible.size() > rows.size());
        return root;
    }

    /** One hub's row: the {@code chroma_hubs} entry, and the state every action answers with. */
    public static JsonObject hubRow(HubNetwork network, UUID requester) {
        ChromaRole role = roleOf(network, requester);
        SceneSlots scenes = network.scenes();
        JsonObject row = new JsonObject();
        row.addProperty("hub", hubId(network.id()));
        row.addProperty("name", hubName(network.id()));
        row.addProperty("role", role.id());
        row.addProperty("can_control", role.atLeast(ChromaAction.PAINT.minimum()));
        row.addProperty("active", network.active());
        row.addProperty("blocks", network.size());
        row.addProperty("zones", network.zones().size());
        row.addProperty("scenes", scenes.filled());
        int active = scenes.active();
        row.addProperty("active_scene", active);
        Scene playing = scenes.activeScene();
        row.addProperty("active_scene_name", playing == null ? null : playing.name());
        row.addProperty("animating", playing != null);
        row.addProperty("trigger", scenes.trigger().id());
        OptionalInt brightness = brightness(network);
        if (brightness.isPresent()) {
            row.addProperty("brightness", brightness.getAsInt());
        } else {
            row.add("brightness", com.google.gson.JsonNull.INSTANCE);
        }
        // Counts per role only: never who.
        Map<ChromaRole, Integer> counts = new EnumMap<>(ChromaRole.class);
        for (ChromaRole granted : network.trusted().values()) {
            counts.merge(granted, 1, Integer::sum);
        }
        JsonObject members = new JsonObject();
        members.addProperty("admin", counts.getOrDefault(ChromaRole.ADMIN, 0));
        members.addProperty("painter", counts.getOrDefault(ChromaRole.PAINTER, 0));
        members.addProperty("viewer", counts.getOrDefault(ChromaRole.VIEWER, 0));
        row.add("members", members);
        return row;
    }

    /** {@code chroma_zones?hub=<id>}: the hub's zones with their channel, colour and block count. */
    public static JsonObject zones(ChromaLinkSource source, UUID requester, String hubParam) {
        JsonObject root = envelope(source);
        JsonArray rows = new JsonArray();
        Optional<HubNetwork> found = find(source, requester, hubParam);
        found.ifPresent(network -> {
            root.addProperty("hub", hubId(network.id()));
            root.addProperty("name", hubName(network.id()));
            root.addProperty("role", roleOf(network, requester).id());
            List<HubNetwork.Zone> zones = network.zones();
            for (int i = 0; i < zones.size(); i++) {
                HubNetwork.Zone zone = zones.get(i);
                JsonObject row = new JsonObject();
                row.addProperty("zone", i);
                row.addProperty("name", zone.name());
                row.addProperty("channel", zone.channel());
                ChromaColor colour = zoneColour(network, i);
                row.addProperty("colour", colour == null ? null : colour.toHex());
                if (colour == null) {
                    row.add("brightness", com.google.gson.JsonNull.INSTANCE);
                } else {
                    row.addProperty("brightness", colour.brightness());
                }
                row.addProperty("blocks", zoneBlocks(network, i));
                rows.add(row);
            }
        });
        root.add("zones", rows);
        return root;
    }

    /** {@code chroma_scenes?hub=<id>}: the eight scene slots, which are filled, and which one plays. */
    public static JsonObject scenes(ChromaLinkSource source, UUID requester, String hubParam) {
        JsonObject root = envelope(source);
        JsonArray rows = new JsonArray();
        Optional<HubNetwork> found = find(source, requester, hubParam);
        found.ifPresent(network -> {
            SceneSlots slots = network.scenes();
            root.addProperty("hub", hubId(network.id()));
            root.addProperty("name", hubName(network.id()));
            root.addProperty("role", roleOf(network, requester).id());
            root.addProperty("active_scene", slots.active());
            root.addProperty("animating", slots.activeScene() != null);
            root.addProperty("trigger", slots.trigger().id());
            for (int slot = 0; slot < SceneSlots.MAX_SLOTS; slot++) {
                Scene scene = slots.scene(slot);
                JsonObject row = new JsonObject();
                row.addProperty("slot", slot);
                row.addProperty("name", scene == null ? null : scene.name());
                row.addProperty("filled", scene != null);
                row.addProperty("active", scene != null && slots.active() == slot);
                rows.add(row);
            }
        });
        root.add("slots", rows);
        return root;
    }

    // --- helpers --------------------------------------------------------------------------------------------------

    /** The visible hub named by {@code hubParam}, or empty (missing, unknown and not-yours all look the same). */
    static Optional<HubNetwork> find(ChromaLinkSource source, UUID requester, String hubParam) {
        if (source == null || !source.enabled()) {
            return Optional.empty();
        }
        return parseHubId(hubParam)
                .flatMap(source::hub)
                .map(ChromaLinkSource.Hub::network)
                .filter(network -> visible(network, requester));
    }

    private static JsonObject envelope(ChromaLinkSource source) {
        JsonObject root = new JsonObject();
        root.addProperty("schema_version", NeroDecorLinkModule.SCHEMA_VERSION);
        root.addProperty("chroma_enabled", source != null && source.enabled());
        return root;
    }
}
