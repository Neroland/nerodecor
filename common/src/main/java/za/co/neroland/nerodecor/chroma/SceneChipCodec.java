package za.co.neroland.nerodecor.chroma;

/**
 * What a Scene Chip stores: exactly the Stage 1 {@link SceneCodec} bytes of one scene (magic, version, CRC; at most
 * {@link #MAX_BYTES}), nothing else: no author, no player id, no coordinates. Also the share-code bridge the editor
 * uses to copy and import a chip's scene. Pure Java.
 */
public final class SceneChipCodec {

    public static final int MAX_BYTES = SceneCodec.MAX_ENCODED_BYTES;

    private SceneChipCodec() {
    }

    /** The chip bytes for a scene. */
    public static ChromaResult<byte[]> encode(Scene scene) {
        if (scene == null) {
            return ChromaResult.fail(ChromaError.OUT_OF_RANGE, "no scene");
        }
        return SceneCodec.encode(scene);
    }

    /** The scene on a chip; tampered bytes fail the CRC or the bounds checks. */
    public static ChromaResult<Scene> decode(byte[] data) {
        if (data == null || data.length == 0) {
            return ChromaResult.fail(ChromaError.TRUNCATED, "empty chip");
        }
        if (data.length > MAX_BYTES) {
            return ChromaResult.fail(ChromaError.OVERSIZED, "chip");
        }
        return SceneCodec.decode(data);
    }

    /** The share code ({@code ND1-...}) of a chip's scene. */
    public static ChromaResult<String> toShareCode(byte[] data) {
        ChromaResult<Scene> scene = decode(data);
        return scene.isOk() ? SceneCodec.toShareCode(scene.value()) : ChromaResult.fail(scene.error(), scene.detail());
    }

    /** Chip bytes from a share code (validated and re-encoded, so only canonical bytes reach a chip). */
    public static ChromaResult<byte[]> fromShareCode(String code) {
        ChromaResult<Scene> scene = SceneCodec.fromShareCode(code);
        return scene.isOk() ? encode(scene.value()) : ChromaResult.fail(scene.error(), scene.detail());
    }
}
