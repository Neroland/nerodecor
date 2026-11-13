package za.co.neroland.nerodecor.chroma.server;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongPredicate;

import za.co.neroland.nerodecor.chroma.RelativePos;

/**
 * Every hub network of one dimension plus the claim index (absolute position to the id of the <em>active</em> network
 * that controls it), pure Java. All mutation goes through here so the index can never disagree with the networks:
 * a block belongs to at most one active hub. The game layer persists this through {@code ChromaNetworkData}.
 */
public final class ChromaNetworkStore {

    private final Map<Long, HubNetwork> networks = new LinkedHashMap<>();
    private final Map<Long, Long> claims = new HashMap<>();
    private Runnable onChange = () -> { };

    /** Called after every change (the SavedData marks itself dirty). */
    public void onChange(Runnable listener) {
        this.onChange = listener == null ? () -> { } : listener;
    }

    private void changed() {
        onChange.run();
    }

    public HubNetwork get(long id) {
        return networks.get(id);
    }

    public Collection<HubNetwork> all() {
        return Collections.unmodifiableCollection(networks.values());
    }

    public int count() {
        return networks.size();
    }

    /** Id of the active network that controls the absolute position, or 0. */
    public long claimant(int x, int y, int z) {
        Long id = claims.get(ChromaPos.pack(x, y, z));
        return id == null ? 0L : id;
    }

    /** Networks owned by {@code player} here, placed or not. */
    public int ownedBy(UUID player) {
        int n = 0;
        for (HubNetwork network : networks.values()) {
            if (network.owner().equals(player)) {
                n++;
            }
        }
        return n;
    }

    /** A new, active network anchored at the hub position with the given volume. */
    public HubNetwork create(long id, UUID owner, int x, int y, int z, int sizeX, int sizeY, int sizeZ) {
        if (networks.containsKey(id)) {
            throw new IllegalArgumentException("network id in use");
        }
        HubNetwork network = new HubNetwork(id, owner, sizeX, sizeY, sizeZ);
        network.setAnchor(x, y, z);
        network.setActive(true, 0L);
        networks.put(id, network);
        changed();
        return network;
    }

    /** Adds a network restored from disk or moved from another dimension; claims it if it is active. */
    public void adopt(HubNetwork network) {
        networks.put(network.id(), network);
        if (network.active()) {
            claimAll(network, pos -> true);
        }
        changed();
    }

    /** Removes a network (deleted, or moving to another dimension) and releases its claims. */
    public HubNetwork remove(long id) {
        HubNetwork network = networks.remove(id);
        if (network != null) {
            release(network);
            changed();
        }
        return network;
    }

    /** Links one block after {@link ChromaValidator#checkLink}; the block joins the default zone, palette 0. */
    public ChromaVerdict link(long id, UUID player, int x, int y, int z, boolean capable, ChromaLimits limits) {
        HubNetwork network = networks.get(id);
        ChromaVerdict verdict = ChromaValidator.checkLink(network, player, x, y, z, capable, claimant(x, y, z),
                limits);
        if (verdict == ChromaVerdict.OK) {
            network.put(RelativePos.pack(x - network.anchorX(), y - network.anchorY(), z - network.anchorZ()), 0, 0);
            claims.put(ChromaPos.pack(x, y, z), id);
            changed();
        }
        return verdict;
    }

    /** Unlinks one block after {@link ChromaValidator#checkUnlink}. */
    public ChromaVerdict unlink(long id, UUID player, int x, int y, int z) {
        HubNetwork network = networks.get(id);
        ChromaVerdict verdict = ChromaValidator.checkUnlink(network, player, x, y, z);
        if (verdict == ChromaVerdict.OK) {
            network.remove(RelativePos.pack(x - network.anchorX(), y - network.anchorY(), z - network.anchorZ()));
            long key = ChromaPos.pack(x, y, z);
            if (claims.getOrDefault(key, 0L) == id) {
                claims.remove(key);
            }
            changed();
        }
        return verdict;
    }

    /** Drops a block from whichever active network controls it (block destroyed or no longer capable). */
    public boolean forgetBlock(int x, int y, int z) {
        long key = ChromaPos.pack(x, y, z);
        Long id = claims.remove(key);
        if (id == null) {
            return false;
        }
        HubNetwork network = networks.get(id);
        if (network != null) {
            network.remove(RelativePos.pack(x - network.anchorX(), y - network.anchorY(), z - network.anchorZ()));
        }
        changed();
        return true;
    }

    /**
     * The hub was (re-)placed at {@code x,y,z}: the network becomes active there and re-claims its blocks. A block
     * that another active network now controls, or that {@code keep} rejects (not capable any more), is dropped.
     *
     * @return how many stored blocks were dropped
     */
    public int activate(long id, int x, int y, int z, LongPredicate keep) {
        HubNetwork network = networks.get(id);
        if (network == null) {
            return 0;
        }
        if (network.active()) {
            release(network);
        }
        network.setAnchor(x, y, z);
        network.setActive(true, 0L);
        int dropped = claimAll(network, keep);
        changed();
        return dropped;
    }

    /** The hub was removed: the network stops controlling its blocks but keeps its data for re-placement. */
    public void deactivate(long id, long now) {
        HubNetwork network = networks.get(id);
        if (network == null || !network.active()) {
            return;
        }
        release(network);
        network.setActive(false, now);
        changed();
    }

    public ChromaVerdict setTrust(long id, UUID actor, UUID target, ChromaRole role) {
        HubNetwork network = networks.get(id);
        ChromaVerdict verdict = network == null ? ChromaVerdict.NO_NETWORK
                : ChromaValidator.checkTrust(network, actor, target, role);
        if (verdict.isOk() && network != null) {
            network.setTrust(target, role);
            changed();
        }
        return verdict;
    }

    public ChromaVerdict addZone(long id, UUID actor, String rawName, int channel, ChromaLimits limits) {
        HubNetwork network = networks.get(id);
        if (network == null) {
            return ChromaVerdict.NO_NETWORK;
        }
        ChromaVerdict verdict = ChromaValidator.checkAddZone(network, actor, rawName, channel, limits);
        if (verdict.isOk()) {
            network.addZone(new HubNetwork.Zone(ChromaValidator.sanitiseZoneName(rawName).orElseThrow(), channel));
            changed();
        }
        return verdict;
    }

    public ChromaVerdict setAutoAdopt(long id, UUID actor, boolean on) {
        HubNetwork network = networks.get(id);
        ChromaVerdict verdict = ChromaValidator.checkAction(network, actor, ChromaAction.MANAGE);
        if (verdict.isOk()) {
            network.setAutoAdopt(on);
            changed();
        }
        return verdict;
    }

    /** Resizes a network's volume; blocks that fall outside are unlinked. */
    public ChromaVerdict setVolume(long id, UUID actor, int sizeX, int sizeY, int sizeZ) {
        HubNetwork network = networks.get(id);
        ChromaVerdict verdict = ChromaValidator.checkAction(network, actor, ChromaAction.MANAGE);
        if (!verdict.isOk()) {
            return verdict;
        }
        verdict = ChromaValidator.checkVolumeSize(sizeX, sizeY, sizeZ);
        if (!verdict.isOk()) {
            return verdict;
        }
        boolean wasActive = network.active();
        if (wasActive) {
            release(network);
        }
        network.setSize(sizeX, sizeY, sizeZ);
        for (int rel : network.linkedOffsets()) {
            if (!network.containsOffset(RelativePos.x(rel), RelativePos.y(rel), RelativePos.z(rel))) {
                network.remove(rel);
            }
        }
        if (wasActive) {
            claimAll(network, pos -> true);
        }
        changed();
        return ChromaVerdict.OK;
    }

    /**
     * Erasure (POPIA/GDPR): every network {@code player} owns is deleted and they are removed from every trusted list.
     *
     * @return whether anything changed
     */
    public boolean eraseFor(UUID player) {
        boolean any = false;
        Iterator<HubNetwork> it = networks.values().iterator();
        while (it.hasNext()) {
            HubNetwork network = it.next();
            if (network.owner().equals(player)) {
                release(network);
                it.remove();
                any = true;
            } else if (network.forget(player)) {
                any = true;
            }
        }
        if (any) {
            changed();
        }
        return any;
    }

    /** Deletes networks whose hub has been gone longer than {@code retentionMillis}. */
    public int purgeInactive(long now, long retentionMillis) {
        List<Long> stale = new ArrayList<>();
        for (HubNetwork network : networks.values()) {
            if (!network.active() && network.inactiveSince() > 0L && now - network.inactiveSince() > retentionMillis) {
                stale.add(network.id());
            }
        }
        stale.forEach(networks::remove);
        if (!stale.isEmpty()) {
            changed();
        }
        return stale.size();
    }

    private void release(HubNetwork network) {
        for (int rel : network.linkedOffsets()) {
            long key = network.absolute(rel);
            if (claims.getOrDefault(key, 0L) == network.id()) {
                claims.remove(key);
            }
        }
    }

    private int claimAll(HubNetwork network, LongPredicate keep) {
        int dropped = 0;
        for (int rel : network.linkedOffsets()) {
            long key = network.absolute(rel);
            Long other = claims.get(key);
            boolean outside = !network.containsOffset(RelativePos.x(rel), RelativePos.y(rel), RelativePos.z(rel));
            if (outside || (other != null && other != network.id()) || !keep.test(key)) {
                network.remove(rel);
                dropped++;
            } else {
                claims.put(key, network.id());
            }
        }
        return dropped;
    }
}
