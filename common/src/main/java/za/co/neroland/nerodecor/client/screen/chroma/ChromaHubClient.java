package za.co.neroland.nerodecor.client.screen.chroma;

import java.util.List;
import java.util.Optional;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import za.co.neroland.nerodecor.chroma.ChromaOpBatch;
import za.co.neroland.nerodecor.chroma.ChromaSyncCodec;
import za.co.neroland.nerodecor.chroma.editor.HubAction;
import za.co.neroland.nerodecor.chroma.editor.HubDetail;
import za.co.neroland.nerodecor.chroma.net.ChromaHubNet;
import za.co.neroland.nerodecor.chroma.net.ChromaNet;
import za.co.neroland.nerodecor.chroma.net.ChromaPayloads;

/**
 * Client side of the Chroma Hub screen: a hub detail opens the screen, or refreshes it when it already shows that
 * network; the screen sends its requests and colour batches through here. Client main thread only; registered as the
 * {@link ChromaHubNet} client seam by the client bootstrap.
 */
public final class ChromaHubClient {

    private static ChromaHubScreen open;

    private ChromaHubClient() {
    }

    public static void onDetail(byte[] data) {
        Optional<HubDetail> decoded = HubDetail.decode(data);
        if (decoded.isEmpty()) {
            return;
        }
        HubDetail detail = decoded.get();
        if (open != null && open.network() == detail.network()) {
            open.refresh(detail);
            return;
        }
        show(new ChromaHubScreen(detail));
    }

    static void opened(ChromaHubScreen screen) {
        open = screen;
    }

    static void closed(ChromaHubScreen screen) {
        if (open == screen) {
            open = null;
        }
    }

    static void show(Screen screen) {
        //? if >=26.2 {
        Minecraft.getInstance().gui.setScreen(screen);
        //?} else {
        /*Minecraft.getInstance().setScreen(screen);
        *///?}
    }

    static void send(HubAction action) {
        ChromaNet.sendToServer(new ChromaHubNet.Action(action.encode()));
    }

    /** Sends the batches in order, then asks for a fresh detail. */
    static void sendOps(long network, List<ChromaOpBatch> batches) {
        for (ChromaOpBatch batch : batches) {
            ChromaNet.sendToServer(new ChromaPayloads.Ops(ChromaSyncCodec.encodeOps(network, batch)));
        }
        send(HubAction.simple(network, HubAction.Kind.REFRESH, 0, 0));
    }
}
