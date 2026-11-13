package za.co.neroland.nerodecor.client.collections;

import net.minecraft.client.Minecraft;

import za.co.neroland.nerodecor.client.ClientRenderRegistry;
import za.co.neroland.nerodecor.client.ClientRenderRegistry.Layer;
import za.co.neroland.nerodecor.client.render.bulkhead.BlastDoorRenderer;
import za.co.neroland.nerodecor.client.render.bulkhead.IrisHatchRenderer;
import za.co.neroland.nerodecor.client.render.bulkhead.ShutterRenderer;
import za.co.neroland.nerodecor.client.render.bulkhead.SlidingDoorRenderer;
import za.co.neroland.nerodecor.client.render.bulkhead.TrapdoorRenderer;
import za.co.neroland.nerodecor.client.render.command.WallScreenRenderer;
import za.co.neroland.nerodecor.client.render.holo.HoloProjectorRenderer;
import za.co.neroland.nerodecor.client.render.holo.HoloSignEditScreen;
import za.co.neroland.nerodecor.client.render.holo.HoloSignRenderer;
import za.co.neroland.nerodecor.client.render.holo.HoloTableRenderer;
import za.co.neroland.nerodecor.client.render.holo.WorldGlobeRenderer;
import za.co.neroland.nerodecor.content.block.holo.HoloSignBlock;

import static za.co.neroland.nerodecor.registry.collections.BulkheadHoloCommandBlocks.*;

/**
 * Client half of {@link za.co.neroland.nerodecor.registry.collections.BulkheadHoloCommandBlocks}: chunk layers for
 * every non-solid block, the ten block-entity renderers, and the holo sign editor hook. Client only.
 */
public final class BulkheadHoloCommandClient {

    private BulkheadHoloCommandClient() {
    }

    /** Called once from {@link za.co.neroland.nerodecor.client.ClientRenderRegistry#bootstrap()}. */
    public static void register() {
        // bulkhead
        ClientRenderRegistry.blockLayer(SLIDING_DOOR, Layer.CUTOUT);
        ClientRenderRegistry.blockLayer(IRIS_HATCH, Layer.CUTOUT);
        ClientRenderRegistry.blockLayer(BLAST_DOOR, Layer.CUTOUT);
        ClientRenderRegistry.blockLayer(PRESSURE_TRAPDOOR, Layer.CUTOUT);
        ClientRenderRegistry.blockLayer(AIRLOCK_FRAME, Layer.CUTOUT);
        ClientRenderRegistry.blockLayer(DOOR_STATUS_LIGHT, Layer.CUTOUT);
        ClientRenderRegistry.blockLayer(ACCESS_PAD, Layer.CUTOUT);
        ClientRenderRegistry.blockLayer(VIEWPORT_FRAME, Layer.TRANSLUCENT);
        ClientRenderRegistry.blockLayer(PORTHOLE, Layer.TRANSLUCENT);
        ClientRenderRegistry.blockLayer(CANOPY_GLASS, Layer.TRANSLUCENT);
        ClientRenderRegistry.blockLayer(SHUTTER, Layer.CUTOUT);
        ClientRenderRegistry.blockLayer(SMART_GLASS, Layer.TRANSLUCENT);
        // holo
        ClientRenderRegistry.blockLayer(HOLO_PROJECTOR, Layer.CUTOUT);
        ClientRenderRegistry.blockLayer(HOLO_SIGN, Layer.CUTOUT);
        ClientRenderRegistry.blockLayer(ARROW_SIGN, Layer.CUTOUT);
        ClientRenderRegistry.blockLayer(WORLD_GLOBE, Layer.CUTOUT);
        ClientRenderRegistry.blockLayer(HOLO_TABLE, Layer.CUTOUT);
        ClientRenderRegistry.blockLayer(HOLO_CURTAIN, Layer.TRANSLUCENT);
        ClientRenderRegistry.blockLayer(HOLO_PLINTH, Layer.CUTOUT);
        ClientRenderRegistry.blockLayer(TICKER_STRIP, Layer.CUTOUT);
        // command
        ClientRenderRegistry.blockLayer(COMMAND_CONSOLE, Layer.CUTOUT);
        ClientRenderRegistry.blockLayer(WALL_SCREEN, Layer.CUTOUT);
        ClientRenderRegistry.blockLayer(KEYBOARD_SHELF, Layer.CUTOUT);
        ClientRenderRegistry.blockLayer(OPERATOR_CHAIR, Layer.CUTOUT);
        ClientRenderRegistry.blockLayer(SERVER_RACK, Layer.CUTOUT);
        ClientRenderRegistry.blockLayer(DRIVE_RACK, Layer.CUTOUT);
        ClientRenderRegistry.blockLayer(PATCH_PANEL, Layer.CUTOUT);
        ClientRenderRegistry.blockLayer(BLINKENLIGHT_PANEL, Layer.CUTOUT);

        ClientRenderRegistry.blockEntityRenderer(() -> SLIDING_DOOR_BE.get(), SlidingDoorRenderer::new);
        ClientRenderRegistry.blockEntityRenderer(() -> IRIS_HATCH_BE.get(), IrisHatchRenderer::new);
        ClientRenderRegistry.blockEntityRenderer(() -> BLAST_DOOR_BE.get(), BlastDoorRenderer::new);
        ClientRenderRegistry.blockEntityRenderer(() -> PRESSURE_TRAPDOOR_BE.get(), TrapdoorRenderer::new);
        ClientRenderRegistry.blockEntityRenderer(() -> SHUTTER_BE.get(), ShutterRenderer::new);
        ClientRenderRegistry.blockEntityRenderer(() -> HOLO_PROJECTOR_BE.get(), HoloProjectorRenderer::new);
        ClientRenderRegistry.blockEntityRenderer(() -> HOLO_SIGN_BE.get(), HoloSignRenderer::new);
        ClientRenderRegistry.blockEntityRenderer(() -> WORLD_GLOBE_BE.get(), WorldGlobeRenderer::new);
        ClientRenderRegistry.blockEntityRenderer(() -> HOLO_TABLE_BE.get(), HoloTableRenderer::new);
        ClientRenderRegistry.blockEntityRenderer(() -> WALL_SCREEN_BE.get(), WallScreenRenderer::new);

        //? if >=26.2 {
        HoloSignBlock.editor = sign -> Minecraft.getInstance().gui.setScreen(new HoloSignEditScreen(sign));
        //?} else {
        /*HoloSignBlock.editor = sign -> Minecraft.getInstance().setScreen(new HoloSignEditScreen(sign));
        *///?}
    }
}
