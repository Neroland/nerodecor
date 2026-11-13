package za.co.neroland.nerodecor.chroma.server;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import com.mojang.serialization.Codec;
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
import za.co.neroland.nerodecor.chroma.ZoneAssignment;

/**
 * Per-dimension SavedData holding that dimension's {@link ChromaNetworkStore}, always loaded through Core's
 * {@link SavedDataRecovery} guard (corrupt file: last-known-good backup, else a fresh store; never a crash loop).
 *
 * <p>Layout ({@code data/nerodecor/chroma_networks.dat}): {@code networks: [ {id, owner, trust:[{who, role}],
 * anchor:[x,y,z], size:[x,y,z], active, auto, since, zones:[{name, channel}], pos:int[], val:int[], palette:int[]} ]}.
 * {@code pos} holds {@link RelativePos}-packed offsets from the hub; {@code val} the matching
 * {@code paletteIndex | zone << 8}. Personal data: owner and trusted UUIDs only — erased through Core's
 * {@code PlayerDataErasure} with an immediate {@code backupNow}.
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

    record NetDto(long id, UUID owner, List<TrustDto> trust, int[] anchor, int[] size, boolean active, boolean auto,
            long since, List<ZoneDto> zones, int[] pos, int[] val, int[] palette) {
        static final Codec<NetDto> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("id").forGetter(NetDto::id),
                UUIDUtil.CODEC.fieldOf("owner").forGetter(NetDto::owner),
                TrustDto.CODEC.listOf().optionalFieldOf("trust", List.of()).forGetter(NetDto::trust),
                INTS.fieldOf("anchor").forGetter(NetDto::anchor),
                INTS.fieldOf("size").forGetter(NetDto::size),
                Codec.BOOL.optionalFieldOf("active", false).forGetter(NetDto::active),
                Codec.BOOL.optionalFieldOf("auto", false).forGetter(NetDto::auto),
                Codec.LONG.optionalFieldOf("since", 0L).forGetter(NetDto::since),
                ZoneDto.CODEC.listOf().optionalFieldOf("zones", List.of()).forGetter(NetDto::zones),
                INTS.optionalFieldOf("pos", new int[0]).forGetter(NetDto::pos),
                INTS.optionalFieldOf("val", new int[0]).forGetter(NetDto::val),
                INTS.optionalFieldOf("palette", new int[0]).forGetter(NetDto::palette)
        ).apply(i, NetDto::new));
    }

    public static final Codec<ChromaNetworkData> CODEC = RecordCodecBuilder.create(i -> i.group(
            NetDto.CODEC.listOf().optionalFieldOf("networks", List.of()).forGetter(ChromaNetworkData::toDtos)
    ).apply(i, ChromaNetworkData::fromDtos));

    public static final SavedDataType<ChromaNetworkData> TYPE = SavedDataCompat.type(
            Identifier.fromNamespaceAndPath(NeroDecorCommon.MOD_ID, "chroma_networks"), ChromaNetworkData::new,
            CODEC);

    private final ChromaNetworkStore store = new ChromaNetworkStore();
    private long lastSweep;

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

    /** Runs {@code task} at most once per {@code intervalMillis} (retention sweeps). */
    boolean dueForSweep(long now, long intervalMillis) {
        if (now - lastSweep < intervalMillis) {
            return false;
        }
        lastSweep = now;
        return true;
    }

    private static ChromaNetworkData fromDtos(List<NetDto> dtos) {
        ChromaNetworkData data = new ChromaNetworkData();
        for (NetDto dto : dtos) {
            HubNetwork network = fromDto(dto);
            if (network != null && data.store.get(network.id()) == null) {
                data.store.adopt(network);
            }
        }
        return data;
    }

    /** A network from its stored form, dropping anything malformed rather than failing the whole file. */
    static HubNetwork fromDto(NetDto dto) {
        if (dto.id() == 0L || dto.anchor().length != 3 || dto.size().length != 3) {
            return null;
        }
        HubNetwork network = new HubNetwork(dto.id(), dto.owner(), dto.size()[0], dto.size()[1], dto.size()[2]);
        network.setAnchor(dto.anchor()[0], dto.anchor()[1], dto.anchor()[2]);
        for (TrustDto trust : dto.trust()) {
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
        return new NetDto(network.id(), network.owner(), trust,
                new int[] {network.anchorX(), network.anchorY(), network.anchorZ()},
                new int[] {network.sizeX(), network.sizeY(), network.sizeZ()}, network.active(), network.autoAdopt(),
                network.inactiveSince(), zones, pos, val, network.palette().toPacked());
    }
}
