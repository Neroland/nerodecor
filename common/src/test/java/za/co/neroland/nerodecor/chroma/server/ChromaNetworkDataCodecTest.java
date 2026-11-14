package za.co.neroland.nerodecor.chroma.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.Test;

/** The chroma_networks.dat codec: format version field and lenient per-network decoding. */
class ChromaNetworkDataCodecTest {

    private static JsonObject network(long id) {
        JsonObject net = new JsonObject();
        net.addProperty("id", id);
        net.add("owner", uuid());
        JsonArray anchor = new JsonArray();
        anchor.add(10);
        anchor.add(64);
        anchor.add(-5);
        net.add("anchor", anchor);
        JsonArray size = new JsonArray();
        size.add(16);
        size.add(16);
        size.add(16);
        net.add("size", size);
        return net;
    }

    private static JsonArray uuid() {
        JsonArray a = new JsonArray();
        a.add(0);
        a.add(1);
        a.add(0);
        a.add(1);
        return a;
    }

    private static ChromaNetworkData decode(JsonObject root) {
        return ChromaNetworkData.CODEC.parse(JsonOps.INSTANCE, root).result().orElseThrow();
    }

    @Test
    void oneBadEntryDoesNotSinkTheOthers() {
        JsonArray list = new JsonArray();
        list.add(network(1L));
        JsonObject bad = new JsonObject();
        bad.addProperty("id", "not a number");
        bad.addProperty("anchor", "nope");
        list.add(bad);
        list.add(network(3L));
        JsonObject root = new JsonObject();
        root.addProperty("v", 1);
        root.add("networks", list);

        ChromaNetworkData data = decode(root);
        assertNotNull(data.store().get(1L));
        assertNotNull(data.store().get(3L));
        assertNull(data.store().get(2L));
        assertEquals(1, data.skippedOnLoad());
    }

    @Test
    void structurallyInvalidNetworkIsCountedToo() {
        JsonArray list = new JsonArray();
        JsonObject zeroId = network(0L); // parses, but id 0 is not a valid network
        list.add(zeroId);
        list.add(network(7L));
        JsonObject root = new JsonObject();
        root.add("networks", list);
        ChromaNetworkData data = decode(root);
        assertNotNull(data.store().get(7L));
        assertEquals(1, data.skippedOnLoad());
    }

    @Test
    void fileWithoutVersionLoadsAsV1() {
        JsonArray list = new JsonArray();
        list.add(network(5L));
        JsonObject root = new JsonObject();
        root.add("networks", list);
        ChromaNetworkData data = decode(root);
        assertEquals(1, data.loadedVersion());
        assertNotNull(data.store().get(5L));
        assertEquals(0, data.skippedOnLoad());
    }

    @Test
    void encodeWritesTheFormatVersion() {
        JsonArray list = new JsonArray();
        list.add(network(9L));
        JsonObject root = new JsonObject();
        root.add("networks", list);
        ChromaNetworkData data = decode(root);
        JsonObject out = ChromaNetworkData.CODEC.encodeStart(JsonOps.INSTANCE, data).result().orElseThrow()
                .getAsJsonObject();
        assertEquals(ChromaNetworkData.FORMAT_VERSION, out.get("v").getAsInt());
        assertEquals(1, out.getAsJsonArray("networks").size());
        assertNotNull(decode(out).store().get(9L));
    }
}
