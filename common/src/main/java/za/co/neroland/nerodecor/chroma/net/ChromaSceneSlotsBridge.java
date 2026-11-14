package za.co.neroland.nerodecor.chroma.net;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.server.level.ServerLevel;

import za.co.neroland.nerodecor.chroma.ChromaResult;
import za.co.neroland.nerodecor.chroma.Scene;
import za.co.neroland.nerodecor.chroma.SceneCodec;
import za.co.neroland.nerodecor.chroma.SceneSlots;
import za.co.neroland.nerodecor.chroma.editor.HubDetail;
import za.co.neroland.nerodecor.chroma.server.ChromaNetworks;
import za.co.neroland.nerodecor.chroma.server.ChromaScenes;
import za.co.neroland.nerodecor.chroma.server.ChromaVerdict;
import za.co.neroland.nerodecor.chroma.server.HubNetwork;

/**
 * Plugs Stage 4 scene slots into the hub editor's seam ({@link ChromaHubNet#setSceneSlots}). The editor has already
 * checked the PAINT role; everything else (slot bounds, codec size, the flash clamp before saving and before playing)
 * goes through {@link ChromaScenes}.
 */
public final class ChromaSceneSlotsBridge implements ChromaHubNet.SceneSlots {

    public static final ChromaSceneSlotsBridge INSTANCE = new ChromaSceneSlotsBridge();

    private ChromaSceneSlotsBridge() {
    }

    /** Registers with the editor seam. Called once from common init. */
    public static void register() {
        ChromaHubNet.setSceneSlots(INSTANCE);
    }

    @Override
    public List<HubDetail.Slot> slots(ServerLevel level, HubNetwork network) {
        List<HubDetail.Slot> out = new ArrayList<>(SceneSlots.MAX_SLOTS);
        for (int i = 0; i < SceneSlots.MAX_SLOTS; i++) {
            Scene scene = network.scenes().scene(i);
            if (scene == null) {
                out.add(new HubDetail.Slot("", new byte[0]));
            } else {
                ChromaResult<byte[]> bytes = SceneCodec.encode(scene);
                out.add(new HubDetail.Slot(scene.name(), bytes.isOk() ? bytes.value() : new byte[0]));
            }
        }
        return out;
    }

    @Override
    public int active(ServerLevel level, HubNetwork network) {
        return network.scenes().active();
    }

    @Override
    public ChromaVerdict activate(ServerLevel level, HubNetwork network, int slot) {
        return ChromaScenes.play(ChromaNetworks.store(level), network, slot, level.getGameTime());
    }

    @Override
    public ChromaVerdict rename(ServerLevel level, HubNetwork network, int slot, String name) {
        return ChromaScenes.rename(ChromaNetworks.store(level), network, slot, name);
    }

    @Override
    public ChromaVerdict store(ServerLevel level, HubNetwork network, int slot, Scene scene) {
        return ChromaScenes.store(ChromaNetworks.store(level), network, slot, scene, false, level.getGameTime());
    }
}
