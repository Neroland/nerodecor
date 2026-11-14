package za.co.neroland.nerodecor.chroma.server;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import za.co.neroland.nerolandcore.data.SavedDataCompat;
import za.co.neroland.nerolandcore.data.SavedDataRecovery;

import za.co.neroland.nerodecor.NeroDecorCommon;
import za.co.neroland.nerodecor.chroma.ChromaPalette;
import za.co.neroland.nerodecor.chroma.RelativePos;
import za.co.neroland.nerodecor.chroma.SceneSlots;
import za.co.neroland.nerodecor.chroma.ZoneAssignment;

/**
 * Per-dimension SavedData holding that dimension's {@link ChromaNetworkStore}, always loaded through Core's
 * {@link SavedDataRecovery} guard (corrupt file: last-known-good backup, else a fresh store; never a crash loop).
 *
 * <p>Layout ({@code data/nerodecor/chroma_networks.dat}): {@code v} (format version, {@link #FORMAT_VERSION}; a file
 * without it is version 1), {@code networks: [ {id, owner?, trust:[{who, role}],
 * anchor:[x,y,z], size:[x,y,z], active, auto, since, zones:[{name, channel}], pos:int[], val:int[], palette:int[],
 * orphan} ]}.
 * {@code pos} holds {@link RelativePos}-packed offsets from the hub; {@code val} the matching
 * {@code paletteIndex | zone << 8}. Personal data: owner and trusted UUIDs and the names players typed, erased
 * through Core's {@code PlayerDataErasure} followed by {@link #persistNow} (primary file and backup written at once).
 * Owner erasure orphans the network: {@code owner} omitted, {@code orphan} = erasure time, trust empty, names neutral.
 *
 * <p>The network list decodes leniently: an entry that does not parse is skipped and counted (one count is logged,
 * with no ids, owners or positions), so one bad network never sends the whole file into recovery.
 */
public final class ChromaNetworkData extends SavedData {

    public static final String RECOVERY_NAME = "nerodecor:chroma_networks";

    private static final Codec<int[]> INTS = Codec.INT_STREAM.xmap(IntStream::toArray, Arrays::stream);

    record TrustDto(UUID who, String role) {
        static final Codec<TrustDto> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("who").forGetter(TrustDto::who),
                Codec.STRING.fieldOf("role").forGetter(TrustDto::role)
        ).apply(i, TrustDto::new));
    }

    record ZoneDto(String name, int channel) {
        static final Codec<ZoneDto> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("name").forGetter(ZoneDto::name),
                Codec.INT.optionalFieldOf("channel", 0).forGetter(ZoneDto::channel)
        ).apply(i, ZoneDto::new));
    }

    record NetDto(long id, Optional<UUID> owner, List<TrustDto> trust, int[] anchor, int[] size, boolean active,
            boolean auto, long since, List<ZoneDto> zones, int[] pos, int[] val, int[] palette, long orphan,
            Optional<SceneDto> scenes) {
        /** Without scene slots (pre-Stage 4 shape). */
        NetDto(long id, Optional<UUID> owner, List<TrustDto> trust, int[] anchor, int[] size, boolean active,
                boolean auto, long since, List<ZoneDto> zones, int[] pos, int[] val, int[] palette, long orphan) {
            this(id, owner, trust, anchor, size, active, auto, since, zones, pos, val, palette, orphan,
                    Optional.empty());
        }

        static final Codec<NetDto> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("id").forGetter(NetDto::id),
                UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(NetDto::owner),
                TrustDto.CODEC.listOf().optionalFieldOf("trust", List.of()).forGetter(NetDto::trust),
                INTS.fieldOf("anchor").forGetter(NetDto::anchor),
                INTS.fieldOf("size").forGetter(NetDto::size),
                Codec.BOOL.optionalFieldOf("active", false).forGetter(NetDto::active),
                Codec.BOOL.optionalFieldOf("auto", false).forGetter(NetDto::auto),
                Codec.LONG.optionalFieldOf("since", 0L).forGetter(NetDto::since),
                ZoneDto.CODEC.listOf().optionalFieldOf("zones", List.of()).forGetter(NetDto::zones),
                INTS.optionalFieldOf("pos", new int[0]).forGetter(NetDto::pos),
                INTS.optionalFieldOf("val", new int[0]).forGetter(NetDto::val),
                INTS.optionalFieldOf("palette", new int[0]).forGetter(NetDto::palette),
                Codec.LONG.optionalFieldOf("orphan", 0L).forGetter(NetDto::orphan),
                SceneDto.CODEC.optionalFieldOf("scenes").forGetter(NetDto::scenes)
        ).apply(i, NetDto::new));
    }

    /**
     * Stage 4 scene slots: {@code slots} are share codes ({@code ""} = empty; no author, no player id),
     * {@code active} the playing slot (-1 static) from game tick {@code start}, then the trigger and its maps.
     */
    record SceneDto(List<String> slots, int active, long start, String trigger, int[] redstone, int[] daylight) {
        static final Codec<SceneDto> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.listOf().optionalFieldOf("slots", List.of()).forGetter(SceneDto::slots),
                Codec.INT.optionalFieldOf("active", SceneSlots.STATIC).forGetter(SceneDto::active),
                Codec.LONG.optionalFieldOf("start", 0L).forGetter(SceneDto::start),
                Codec.STRING.optionalFieldOf("trigger", "manual").forGetter(SceneDto::trigger),
                INTS.optionalFieldOf("redstone", new int[0]).forGetter(SceneDto::redstone),
                INTS.optionalFieldOf("daylight", new int[0]).forGetter(SceneDto::daylight)
        ).apply(i, SceneDto::new));

        static SceneDto of(SceneSlots s) {
            return new SceneDto(s.slotCodes(), s.active(), s.startTick(), s.trigger().id(), s.redstoneMap(),
                    s.daylightMap());
        }

        SceneSlots restore() {
            return SceneSlots.restore(slots, active, start, trigger, redstone, daylight);
        }
    }

    /** The save format this build writes. Files written before the field existed are read as version 1. */
    public static final int FORMAT_VERSION = 1;

    /** The decoded network list: the entries that parsed, and how many did not. */
    record Decoded(List<NetDto> networks, int skipped) {
    }

    /** A list codec that skips (and counts) entries that fail to parse instead of failing the whole list. */
    static final Codec<Decoded> LENIENT_NETWORKS = new Codec<>() {
        @Override
        public <T> DataResult<Pair<Decoded, T>> decode(DynamicOps<T> ops, T input) {
            return ops.getStream(input).map(stream -> {
                List<NetDto> ok = new ArrayList<>();
                int[] skipped = {0};
                stream.forEach(entry -> {
                    Optional<NetDto> parsed = NetDto.CODEC.parse(ops, entry).result();
                    if (parsed.isPresent()) {
                        ok.add(parsed.get());
                    } else {
                        skipped[0]++;
                    }
                });
                return Pair.of(new Decoded(ok, skipped[0]), input);
            });
        }

        @Override
        public <T> DataResult<T> encode(Decoded input, DynamicOps<T> ops, T prefix) {
            return NetDto.CODEC.listOf().encode(input.networks(), ops, prefix);
        }
    };

    public static final Codec<ChromaNetworkData> CODEC = RecordCodecBuilder.create(i -> i.group(
            // Optional<Integer> rather than a default, so the field is always written (a default would be omitted).
            Codec.INT.optionalFieldOf("v").forGetter(d -> Optional.of(FORMAT_VERSION)),
            LENIENT_NETWORKS.optionalFieldOf("networks", new Decoded(List.of(), 0)).forGetter(d -> new Decoded(d.toDtos(), 0))
    ).apply(i, ChromaNetworkData::fromDtos));

    public static final SavedDataType<ChromaNetworkData> TYPE = SavedDataCompat.type(
            Identifier.fromNamespaceAndPath(NeroDecorCommon.MOD_ID, "chroma_networks"), ChromaNetworkData::new,
            CODEC);

    private final ChromaNetworkStore store = new ChromaNetworkStore();
    private long lastSweep;
    /** Format version of the file this was loaded from ({@link #FORMAT_VERSION} for a fresh store). */
    private int loadedVersion = FORMAT_VERSION;
    /** Network entries skipped as malformed when this was loaded. */
    private int skippedOnLoad;

    public ChromaNetworkData() {
        this.store.onChange(this::setDirty);
    }

    /** This dimension's networks, through Core's recovery guard. */
    public static ChromaNetworkData get(ServerLevel level) {
        return SavedDataRecovery.get(level, TYPE, ChromaNetworkData::new, RECOVERY_NAME);
    }

    /**
     * A store type with the same codec under another id: lets verification code exercise the recovery guard on a
     * file of this format without touching the live store's cache.
     */
    public static SavedDataType<ChromaNetworkData> typeFor(Identifier id) {
        return SavedDataCompat.type(id, ChromaNetworkData::new, CODEC);
    }

    /** The file stem (under the dimension's {@code data/} folder, without {@code .dat}) of a store type. */
    public static String fileStem(SavedDataType<ChromaNetworkData> type) {
        return SavedDataCompat.fileStem(type);
    }

    /** Loads a store of {@code type} through Core's recovery guard under the given recovery label. */
    public static ChromaNetworkData load(ServerLevel level, SavedDataType<ChromaNetworkData> type, String name) {
        return SavedDataRecovery.get(level, type, ChromaNetworkData::new, name);
    }

    /** Forces Core's last-known-good backup of {@code data} under the given recovery label. */
    public static void backupNow(ServerLevel level, SavedDataType<ChromaNetworkData> type, ChromaNetworkData data,
            String name) {
        SavedDataRecovery.backupNow(level, type, data, name);
    }

    public ChromaNetworkStore store() {
        return store;
    }

    /** Format version of the file this store was loaded from. */
    public int loadedVersion() {
        return loadedVersion;
    }

    /** How many network entries were skipped as malformed on load. */
    public int skippedOnLoad() {
        return skippedOnLoad;
    }

    /**
     * Makes the current state durable now: marks the store dirty, has the dimension's data storage write every dirty
     * file synchronously (so the primary {@code chroma_networks.dat} on disk no longer holds anything an erasure or a
     * retention sweep removed), then refreshes Core's last-known-good backup. Server thread.
     *
     * @throws RuntimeException if either write fails (the caller reports it)
     */
    public static void persistNow(ServerLevel level, ChromaNetworkData data) {
        data.setDirty();
        //? if >=1.21.2 {
        level.getDataStorage().saveAndJoin();
        //?} else {
        /*level.getDataStorage().save();
        *///?}
        SavedDataRecovery.backupNow(level, TYPE, data, RECOVERY_NAME);
    }

    /** Runs {@code task} at most once per {@code intervalMillis} (retention sweeps). */
    public boolean dueForSweep(long now, long intervalMillis) {
        if (now - lastSweep < intervalMillis) {
            return false;
        }
        lastSweep = now;
        return true;
    }

    private static ChromaNetworkData fromDtos(Optional<Integer> storedVersion, Decoded decoded) {
        int version = storedVersion.orElse(1);
        ChromaNetworkData data = new ChromaNetworkData();
        data.loadedVersion = version;
        int skipped = decoded.skipped();
        for (NetDto dto : decoded.networks()) {
            HubNetwork network = fromDto(dto);
            if (network == null) {
                skipped++;
            } else if (data.store.get(network.id()) == null) {
                data.store.adopt(network);
            }
        }
        data.skippedOnLoad = skipped;
        if (skipped > 0) {
            // A count only: no network ids, owners or positions.
            NeroDecorCommon.LOGGER.warn("[NeroDecor] Skipped {} malformed Chroma network entr{} while loading; the"
                    + " others loaded normally.", skipped, skipped == 1 ? "y" : "ies");
        }
        if (version > FORMAT_VERSION) {
            NeroDecorCommon.LOGGER.warn("[NeroDecor] Chroma networks file is format v{}, newer than this build"
                    + " (v{}); unknown fields are ignored.", version, FORMAT_VERSION);
        }
        return data;
    }

    /** A network from its stored form, dropping anything malformed rather than failing the whole file. */
    static HubNetwork fromDto(NetDto dto) {
        if (dto.id() == 0L || dto.anchor().length != 3 || dto.size().length != 3) {
            return null;
        }
        // An orphaned network is saved without an owner. A network with no owner and no orphan time is malformed: it
        // is treated as orphaned long ago, so the next retention sweep deletes it.
        long orphan = dto.owner().isEmpty() ? Math.max(1L, dto.orphan()) : Math.max(0L, dto.orphan());
        HubNetwork network = new HubNetwork(dto.id(), dto.owner().orElse(HubNetwork.NO_OWNER), dto.size()[0],
                dto.size()[1], dto.size()[2]);
        network.setAnchor(dto.anchor()[0], dto.anchor()[1], dto.anchor()[2]);
        for (TrustDto trust : dto.trust()) {
            if (network.trusted().size() >= ChromaLimits.MAX_TRUSTED) {
                break; // an over-long list (older build or edited file) is cut to what the screen can show
            }
            ChromaRole.grantableById(trust.role()).ifPresent(role -> network.setTrust(trust.who(), role));
        }
        List<HubNetwork.Zone> zones = new ArrayList<>();
        for (ZoneDto zone : dto.zones()) {
            if (zones.size() >= ZoneAssignment.MAX_ZONES) {
                break;
            }
            String name = ChromaValidator.sanitiseZoneName(zone.name()).orElse(HubNetwork.DEFAULT_ZONE);
            int channel = Math.max(0, Math.min(ZoneAssignment.MAX_CHANNELS - 1, zone.channel()));
            zones.add(new HubNetwork.Zone(name, channel));
        }
        network.restoreZones(zones);
        ChromaPalette palette = ChromaPalette.fromPacked(dto.palette());
        network.restorePalette(palette == null ? new ChromaPalette() : palette);
        int n = Math.min(Math.min(dto.pos().length, dto.val().length), ChromaLimits.HARD_BLOCK_CAP);
        for (int k = 0; k < n; k++) {
            int rel = dto.pos()[k];
            if (!RelativePos.isValid(rel)) {
                continue;
            }
            int value = dto.val()[k];
            int zone = value >>> 8;
            network.put(rel, value & 0xFF, zone < network.zones().size() ? zone : 0);
        }
        network.setActive(dto.active(), 0L);
        network.restoreInactiveSince(dto.active() ? 0L : dto.since());
        network.setAutoAdopt(dto.auto());
        dto.scenes().ifPresent(scenes -> network.replaceScenes(scenes.restore()));
        if (orphan > 0L) {
            network.orphan(orphan); // re-applies the erasure invariants: no owner, no trust, neutral names
        }
        return network;
    }

    private List<NetDto> toDtos() {
        List<NetDto> out = new ArrayList<>();
        for (HubNetwork network : store.all()) {
            out.add(toDto(network));
        }
        return out;
    }

    static NetDto toDto(HubNetwork network) {
        List<TrustDto> trust = new ArrayList<>();
        for (Map.Entry<UUID, ChromaRole> e : network.trusted().entrySet()) {
            trust.add(new TrustDto(e.getKey(), e.getValue().id()));
        }
        List<ZoneDto> zones = new ArrayList<>();
        for (HubNetwork.Zone zone : network.zones()) {
            zones.add(new ZoneDto(zone.name(), zone.channel()));
        }
        List<Integer> offsets = network.linkedOffsets();
        int[] pos = new int[offsets.size()];
        int[] val = new int[offsets.size()];
        for (int k = 0; k < pos.length; k++) {
            pos[k] = offsets.get(k);
            val[k] = network.rawAt(pos[k]);
        }
        if (network.orphaned()) {
            trust.clear();
        }
        return new NetDto(network.id(), network.orphaned() ? Optional.empty() : Optional.of(network.owner()), trust,
                new int[] {network.anchorX(), network.anchorY(), network.anchorZ()},
                new int[] {network.sizeX(), network.sizeY(), network.sizeZ()}, network.active(), network.autoAdopt(),
                network.inactiveSince(), zones, pos, val, network.palette().toPacked(), network.orphanedSince(),
                network.scenes().isDefault() ? Optional.empty() : Optional.of(SceneDto.of(network.scenes())));
    }
}
