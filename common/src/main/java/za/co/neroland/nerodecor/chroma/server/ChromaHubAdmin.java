package za.co.neroland.nerodecor.chroma.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import za.co.neroland.nerodecor.chroma.ZoneAssignment;
import za.co.neroland.nerodecor.chroma.editor.HubDetail;

/**
 * Hub-screen management that the store has no single call for (zone rename, zone channel) and the builder of the
 * screen's {@link HubDetail}. Pure Java like the store: callers mark the dimension's saved data dirty when this
 * returns OK. Nothing here logs.
 */
public final class ChromaHubAdmin {

    private ChromaHubAdmin() {
    }

    /** Renames zone {@code zone}: MANAGE role and a name that survives {@link ChromaValidator#sanitiseZoneName}. */
    public static ChromaVerdict renameZone(HubNetwork network, UUID actor, int zone, String rawName) {
        ChromaVerdict role = ChromaValidator.checkAction(network, actor, ChromaAction.MANAGE);
        if (!role.isOk()) {
            return role;
        }
        if (zone < 0 || zone >= network.zones().size()) {
            return ChromaVerdict.BAD_ZONE;
        }
        Optional<String> name = ChromaValidator.sanitiseZoneName(rawName);
        if (name.isEmpty()) {
            return ChromaVerdict.BAD_NAME;
        }
        List<HubNetwork.Zone> zones = new ArrayList<>(network.zones());
        zones.set(zone, new HubNetwork.Zone(name.get(), zones.get(zone).channel()));
        network.restoreZones(zones);
        return ChromaVerdict.OK;
    }

    /** Moves zone {@code zone} to effect channel {@code channel} (0-15): MANAGE role. */
    public static ChromaVerdict setChannel(HubNetwork network, UUID actor, int zone, int channel) {
        ChromaVerdict role = ChromaValidator.checkAction(network, actor, ChromaAction.MANAGE);
        if (!role.isOk()) {
            return role;
        }
        if (zone < 0 || zone >= network.zones().size()) {
            return ChromaVerdict.BAD_ZONE;
        }
        if (channel < 0 || channel >= ZoneAssignment.MAX_CHANNELS) {
            return ChromaVerdict.BAD_CHANNEL;
        }
        List<HubNetwork.Zone> zones = new ArrayList<>(network.zones());
        zones.set(zone, new HubNetwork.Zone(zones.get(zone).name(), channel));
        network.restoreZones(zones);
        return ChromaVerdict.OK;
    }

    /**
     * What {@code viewer} may see of the network. The trusted list (ids and roles, never names) goes to the owner and
     * admins only; everyone else gets it empty.
     */
    public static HubDetail detail(HubNetwork network, UUID viewer, ChromaLimits limits, List<HubDetail.Slot> scenes,
            int activeScene) {
        ChromaRole role = network.roleOf(viewer);
        List<Integer> offsets = network.linkedOffsets();
        int n = Math.min(offsets.size(), HubDetail.MAX_BLOCKS);
        int[] rels = new int[n];
        int[] values = new int[n];
        for (int i = 0; i < n; i++) {
            int rel = offsets.get(i);
            rels[i] = rel;
            values[i] = (Math.max(0, network.paletteIndexAt(rel)) & 0xFF) | (Math.max(0, network.zoneAt(rel)) << 8);
        }
        List<HubDetail.Zone> zones = new ArrayList<>();
        for (HubNetwork.Zone z : network.zones()) {
            zones.add(new HubDetail.Zone(z.name(), z.channel()));
        }
        List<HubDetail.Trust> trusted = new ArrayList<>();
        if (role.atLeast(ChromaRole.ADMIN)) {
            for (Map.Entry<UUID, ChromaRole> e : network.trusted().entrySet()) {
                if (trusted.size() < HubDetail.MAX_TRUSTED) {
                    trusted.add(new HubDetail.Trust(e.getKey(), e.getValue().ordinal()));
                }
            }
        }
        List<HubDetail.Slot> slots = scenes.size() > HubDetail.MAX_SLOTS ? scenes.subList(0, HubDetail.MAX_SLOTS)
                : scenes;
        int active = activeScene >= 0 && activeScene < slots.size() ? activeScene : -1;
        return new HubDetail(network.id(), role.ordinal(), network.active(), network.autoAdopt(), network.sizeX(),
                network.sizeY(), network.sizeZ(), limits.maxBlocksPerHub(), limits.maxZones(),
                network.palette().toPacked(), zones, rels, values, trusted, slots, active, "");
    }
}
