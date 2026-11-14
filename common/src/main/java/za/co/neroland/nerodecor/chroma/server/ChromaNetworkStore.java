package za.co.neroland.nerodecor.chroma.server;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongPredicate;

import za.co.neroland.nerodecor.chroma.ChromaColor;
import za.co.neroland.nerodecor.chroma.ChromaOp;
import za.co.neroland.nerodecor.chroma.ChromaOpBatch;
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
    /** Ids of networks whose colouring may have changed since the last {@link #drainTouched()} (sync layer). */
    private final Set<Long> touched = new LinkedHashSet<>();

    /** Called after every change (the SavedData marks itself dirty). */
    public void onChange(Runnable listener) {
        this.onChange = listener == null ? () -> { } : listener;
    }

    private void changed() {
        onChange.run();
    }

    private void touch(long id) {
        touched.add(id);
    }

    /** Stage 4: a network's scene slots changed (saved data must be written; scene sync follows the revision). */
    void scenesChanged(long id) {
        if (networks.containsKey(id)) {
            changed();
        }
    }

    /** Networks whose linked blocks, palette, anchor or activity changed since the last call; clears the set. */
    public Set<Long> drainTouched() {
        if (touched.isEmpty()) {
            return Set.of();
        }
        Set<Long> out = new LinkedHashSet<>(touched);
        touched.clear();
        return out;
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
        touch(id);
        changed();
        return network;
    }

    /** Adds a network restored from disk or moved from another dimension; claims it if it is active. */
    public void adopt(HubNetwork network) {
        networks.put(network.id(), network);
        if (network.active()) {
            claimAll(network, pos -> true);
        }
        touch(network.id());
        changed();
    }

    /** Removes a network (deleted, or moving to another dimension) and releases its claims. */
    public HubNetwork remove(long id) {
        HubNetwork network = networks.remove(id);
        if (network != null) {
            release(network);
            touch(id);
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
            touch(id);
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
            touch(id);
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
        touch(id);
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
        touch(id);
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
        touch(id);
        changed();
    }

    /**
     * Applies a validated edit batch (palette entries, block palette indices, zone assignments) atomically: every
     * op is checked first and nothing changes unless all pass. Block ops must target blocks this network already
     * controls, and {@code mayEdit} (absolute {@link ChromaPos}-packed position) must allow each of them.
     */
    public ChromaVerdict applyOps(long id, UUID actor, ChromaOpBatch batch, LongPredicate mayEdit) {
        HubNetwork network = networks.get(id);
        if (network == null) {
            return ChromaVerdict.NO_NETWORK;
        }
        if (!network.active()) {
            return ChromaVerdict.INACTIVE;
        }
        ChromaVerdict role = ChromaValidator.checkAction(network, actor, ChromaAction.PAINT);
        if (!role.isOk()) {
            return role;
        }
        if (batch.firstUnresolvedIndex(network.palette().size()) >= 0) {
            return ChromaVerdict.BAD_OP;
        }
        for (ChromaOp op : batch.ops()) {
            if (op.kind() == ChromaOp.Kind.SET_PALETTE_ENTRY) {
                continue;
            }
            if (!network.isLinkedOffset(op.target())) {
                return ChromaVerdict.NOT_LINKED;
            }
            if (op.kind() == ChromaOp.Kind.ASSIGN_ZONE && op.value() >= network.zones().size()) {
                return ChromaVerdict.BAD_ZONE;
            }
            if (!mayEdit.test(network.absolute(op.target()))) {
                return ChromaVerdict.PROTECTED;
            }
        }
        for (ChromaOp op : batch.ops()) {
            switch (op.kind()) {
                case SET_PALETTE_ENTRY -> network.palette().set(op.target(), ChromaColor.fromPacked(op.value()));
                case SET_INDEX -> network.put(op.target(), op.value(), network.zoneAt(op.target()));
                case ASSIGN_ZONE -> network.put(op.target(), network.paletteIndexAt(op.target()), op.value());
                default -> {
                }
            }
        }
        if (batch.size() > 0) {
            touch(id);
            changed();
        }
        return ChromaVerdict.OK;
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
        touch(id);
        changed();
        return ChromaVerdict.OK;
    }

    /**
     * Erasure (POPIA/GDPR): {@code player} leaves every trusted list, and every network they own is orphaned (owner
     * cleared, trusted list emptied, names blanked, orphan clock started at {@code now}). An orphaned network keeps
     * colouring its blocks but nobody may edit it until an operator {@linkplain #claimOrphan claims} it; it is deleted
     * by {@link #purgeOrphaned} once its retention period runs out.
     *
     * @return whether anything changed
     */
    public boolean eraseFor(UUID player, long now) {
        if (player == null || HubNetwork.NO_OWNER.equals(player)) {
            return false;
        }
        boolean any = false;
        for (HubNetwork network : networks.values()) {
            if (!network.orphaned() && network.owner().equals(player)) {
                network.orphan(now);
                touch(network.id());
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

    /** Deletes orphaned networks older than {@code retentionMillis}; returns how many. */
    public int purgeOrphaned(long now, long retentionMillis) {
        List<Long> stale = new ArrayList<>();
        for (HubNetwork network : networks.values()) {
            if (network.orphaned() && now - network.orphanedSince() > retentionMillis) {
                stale.add(network.id());
            }
        }
        for (long id : stale) {
            HubNetwork network = networks.remove(id);
            if (network != null) {
                release(network);
                touch(id);
            }
        }
        if (!stale.isEmpty()) {
            changed();
        }
        return stale.size();
    }

    /**
     * An operator takes over an orphaned network for {@code newOwner} (the caller checks the operator permission).
     * The orphan clock stops.
     */
    public ChromaVerdict claimOrphan(long id, UUID newOwner) {
        HubNetwork network = networks.get(id);
        if (network == null) {
            return ChromaVerdict.NO_NETWORK;
        }
        if (!network.orphaned() || newOwner == null || HubNetwork.NO_OWNER.equals(newOwner)) {
            return ChromaVerdict.NO_PERMISSION;
        }
        network.claimOrphan(newOwner);
        touch(id);
        changed();
        return ChromaVerdict.OK;
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
