package za.co.neroland.nerodecor.registry.collections;

import java.util.function.Function;
import java.util.function.UnaryOperator;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import za.co.neroland.nerolandcore.registry.BlockEntityTypeCompat;

import za.co.neroland.nerodecor.content.block.ConnectedGlowBlock;
import za.co.neroland.nerodecor.content.block.bulkhead.BhcShapes;
import za.co.neroland.nerodecor.content.block.bulkhead.BlastDoorBlock;
import za.co.neroland.nerodecor.content.block.bulkhead.CanopyGlassBlock;
import za.co.neroland.nerodecor.content.block.bulkhead.DoorAnimBlockEntity;
import za.co.neroland.nerodecor.content.block.bulkhead.IrisHatchBlock;
import za.co.neroland.nerodecor.content.block.bulkhead.MountedBlock;
import za.co.neroland.nerodecor.content.block.bulkhead.PortholeBlock;
import za.co.neroland.nerodecor.content.block.bulkhead.PressureTrapdoorBlock;
import za.co.neroland.nerodecor.content.block.bulkhead.ShapedBlock;
import za.co.neroland.nerodecor.content.block.bulkhead.ShutterBlock;
import za.co.neroland.nerodecor.content.block.bulkhead.SlidingDoorBlock;
import za.co.neroland.nerodecor.content.block.bulkhead.SmartGlassBlock;
import za.co.neroland.nerodecor.content.block.command.CommandConsoleBlock;
import za.co.neroland.nerodecor.content.block.command.FacingBlock;
import za.co.neroland.nerodecor.content.block.command.OperatorChairBlock;
import za.co.neroland.nerodecor.content.block.command.PatchPanelBlock;
import za.co.neroland.nerodecor.content.block.command.RackBlock;
import za.co.neroland.nerodecor.content.block.command.WallScreenBlock;
import za.co.neroland.nerodecor.content.block.command.WallScreenBlockEntity;
import za.co.neroland.nerodecor.content.block.holo.ArrowSignBlock;
import za.co.neroland.nerodecor.content.block.holo.HoloCurtainBlock;
import za.co.neroland.nerodecor.content.block.holo.HoloProjectorBlock;
import za.co.neroland.nerodecor.content.block.holo.HoloProjectorBlockEntity;
import za.co.neroland.nerodecor.content.block.holo.HoloSignBlock;
import za.co.neroland.nerodecor.content.block.holo.HoloSignBlockEntity;
import za.co.neroland.nerodecor.content.block.holo.HoloTableBlock;
import za.co.neroland.nerodecor.content.block.holo.HoloTableBlockEntity;
import za.co.neroland.nerodecor.content.block.holo.TickerStripBlock;
import za.co.neroland.nerodecor.content.block.holo.WorldGlobeBlock;
import za.co.neroland.nerodecor.content.block.holo.WorldGlobeBlockEntity;
import za.co.neroland.nerodecor.content.fx.AmbientFx;
import za.co.neroland.nerodecor.registry.RegistrationProvider.RegistryEntry;

/**
 * Work package: the bulkhead (12), holo (8) and command (8) collections — resources from {@code bulkhead.py},
 * {@code holo.py} and {@code command.py} under {@code tools/collections/}. Every registration for these collections
 * lives here; render layers and block-entity renderers are in
 * {@link za.co.neroland.nerodecor.client.collections.BulkheadHoloCommandClient}.
 *
 * <p>Block entities (none of them ticks): sliding door, iris hatch, blast door (centre tile only), pressure trapdoor,
 * shutter, holo projector, holo sign, world globe, holo table, wall screen.
 */
public final class BulkheadHoloCommandBlocks {

    // --- bulkhead ---------------------------------------------------------------------------
    public static RegistryEntry<Block> SLIDING_DOOR;
    public static RegistryEntry<Block> IRIS_HATCH;
    public static RegistryEntry<Block> BLAST_DOOR;
    public static RegistryEntry<Block> PRESSURE_TRAPDOOR;
    public static RegistryEntry<Block> AIRLOCK_FRAME;
    public static RegistryEntry<Block> DOOR_STATUS_LIGHT;
    public static RegistryEntry<Block> ACCESS_PAD;
    public static RegistryEntry<Block> VIEWPORT_FRAME;
    public static RegistryEntry<Block> PORTHOLE;
    public static RegistryEntry<Block> CANOPY_GLASS;
    public static RegistryEntry<Block> SHUTTER;
    public static RegistryEntry<Block> SMART_GLASS;
    // --- holo -------------------------------------------------------------------------------
    public static RegistryEntry<Block> HOLO_PROJECTOR;
    public static RegistryEntry<Block> HOLO_SIGN;
    public static RegistryEntry<Block> ARROW_SIGN;
    public static RegistryEntry<Block> WORLD_GLOBE;
    public static RegistryEntry<Block> HOLO_TABLE;
    public static RegistryEntry<Block> HOLO_CURTAIN;
    public static RegistryEntry<Block> HOLO_PLINTH;
    public static RegistryEntry<Block> TICKER_STRIP;
    // --- command ----------------------------------------------------------------------------
    public static RegistryEntry<Block> COMMAND_CONSOLE;
    public static RegistryEntry<Block> WALL_SCREEN;
    public static RegistryEntry<Block> KEYBOARD_SHELF;
    public static RegistryEntry<Block> OPERATOR_CHAIR;
    public static RegistryEntry<Block> SERVER_RACK;
    public static RegistryEntry<Block> DRIVE_RACK;
    public static RegistryEntry<Block> PATCH_PANEL;
    public static RegistryEntry<Block> BLINKENLIGHT_PANEL;

    // --- block entities ---------------------------------------------------------------------
    public static RegistryEntry<BlockEntityType<DoorAnimBlockEntity>> SLIDING_DOOR_BE;
    public static RegistryEntry<BlockEntityType<DoorAnimBlockEntity>> IRIS_HATCH_BE;
    public static RegistryEntry<BlockEntityType<DoorAnimBlockEntity>> BLAST_DOOR_BE;
    public static RegistryEntry<BlockEntityType<DoorAnimBlockEntity>> PRESSURE_TRAPDOOR_BE;
    public static RegistryEntry<BlockEntityType<DoorAnimBlockEntity>> SHUTTER_BE;
    public static RegistryEntry<BlockEntityType<HoloProjectorBlockEntity>> HOLO_PROJECTOR_BE;
    public static RegistryEntry<BlockEntityType<HoloSignBlockEntity>> HOLO_SIGN_BE;
    public static RegistryEntry<BlockEntityType<WorldGlobeBlockEntity>> WORLD_GLOBE_BE;
    public static RegistryEntry<BlockEntityType<HoloTableBlockEntity>> HOLO_TABLE_BE;
    public static RegistryEntry<BlockEntityType<WallScreenBlockEntity>> WALL_SCREEN_BE;

    private BulkheadHoloCommandBlocks() {
    }

    /** Called once from {@link CollectionRegistries#init()}. */
    public static void init() {
        registerBulkhead();
        registerHolo();
        registerCommand();
        registerBlockEntities();
    }

    private static void registerBulkhead() {
        SLIDING_DOOR = block("sliding_door", key -> new SlidingDoorBlock(door(key), () -> SLIDING_DOOR_BE.get()));
        IRIS_HATCH = block("iris_hatch", key -> new IrisHatchBlock(door(key), () -> IRIS_HATCH_BE.get()));
        BLAST_DOOR = block("blast_door", key -> new BlastDoorBlock(
                door(key).strength(8.0F, 12.0F), () -> BLAST_DOOR_BE.get()));
        PRESSURE_TRAPDOOR = block("pressure_trapdoor", key -> new PressureTrapdoorBlock(door(key),
                () -> PRESSURE_TRAPDOOR_BE.get()));
        AIRLOCK_FRAME = block("airlock_frame", key -> new ConnectedGlowBlock(metal(key), AmbientFx.NONE, false));
        DOOR_STATUS_LIGHT = block("door_status_light", key -> new MountedBlock(
                fitting(key).lightLevel(s -> 7), BhcShapes.box(4, 0, 6, 12, 2, 10)));
        ACCESS_PAD = block("access_pad", key -> new MountedBlock(fitting(key), BhcShapes.box(4, 0, 3, 12, 1, 13)));
        VIEWPORT_FRAME = block("viewport_frame", key -> new ConnectedGlowBlock(glass(key), AmbientFx.NONE, true));
        PORTHOLE = block("porthole", key -> new PortholeBlock(glass(key).strength(2.0F, 6.0F).sound(SoundType.METAL)));
        CANOPY_GLASS = block("canopy_glass", key -> new CanopyGlassBlock(glass(key)));
        SHUTTER = block("shutter", key -> new ShutterBlock(door(key), () -> SHUTTER_BE.get()));
        SMART_GLASS = block("smart_glass", key -> new SmartGlassBlock(glass(key)));
    }

    private static void registerHolo() {
        HOLO_PROJECTOR = block("holo_projector", key -> new HoloProjectorBlock(fitting(key),
                () -> HOLO_PROJECTOR_BE.get()));
        HOLO_SIGN = block("holo_sign", key -> new HoloSignBlock(fitting(key), () -> HOLO_SIGN_BE.get()));
        ARROW_SIGN = block("arrow_sign", key -> new ArrowSignBlock(fitting(key).lightLevel(s -> 7)));
        WORLD_GLOBE = block("world_globe", key -> new WorldGlobeBlock(fitting(key), () -> WORLD_GLOBE_BE.get()));
        HOLO_TABLE = block("holo_table", key -> new HoloTableBlock(metal(key).noOcclusion(), () -> HOLO_TABLE_BE.get()));
        HOLO_CURTAIN = block("holo_curtain", key -> new HoloCurtainBlock(props(key, p -> p
                .mapColor(MapColor.COLOR_LIGHT_BLUE).strength(0.3F).sound(SoundType.GLASS).noOcclusion()
                .lightLevel(s -> 8))));
        HOLO_PLINTH = block("holo_plinth", key -> new ShapedBlock(metal(key).noOcclusion(),
                BhcShapes.box(2, 0, 2, 14, 16, 14)));
        TICKER_STRIP = block("ticker_strip", key -> new TickerStripBlock(fitting(key)));
    }

    private static void registerCommand() {
        COMMAND_CONSOLE = block("command_console", key -> new CommandConsoleBlock(metal(key).noOcclusion()));
        WALL_SCREEN = block("wall_screen", key -> new WallScreenBlock(fitting(key).lightLevel(s -> 6),
                () -> WALL_SCREEN_BE.get()));
        KEYBOARD_SHELF = block("keyboard_shelf", key -> new FacingBlock(fitting(key),
                BhcShapes.box(1, 3, 5, 15, 8.75, 16)));
        OPERATOR_CHAIR = block("operator_chair", key -> new OperatorChairBlock(metal(key).noOcclusion()));
        SERVER_RACK = block("server_rack", key -> new RackBlock(metal(key).noOcclusion()));
        DRIVE_RACK = block("drive_rack", key -> new RackBlock(metal(key).noOcclusion()));
        PATCH_PANEL = block("patch_panel", key -> new PatchPanelBlock(fitting(key)));
        BLINKENLIGHT_PANEL = block("blinkenlight_panel", key -> new Block(metal(key)));
    }

    private static void registerBlockEntities() {
        SLIDING_DOOR_BE = CollectionRegistries.BLOCK_ENTITIES.register("sliding_door", key -> BlockEntityTypeCompat.create(
                (pos, state) -> new DoorAnimBlockEntity(SLIDING_DOOR_BE.get(), pos, state), SLIDING_DOOR.get()));
        IRIS_HATCH_BE = CollectionRegistries.BLOCK_ENTITIES.register("iris_hatch", key -> BlockEntityTypeCompat.create(
                (pos, state) -> new DoorAnimBlockEntity(IRIS_HATCH_BE.get(), pos, state), IRIS_HATCH.get()));
        BLAST_DOOR_BE = CollectionRegistries.BLOCK_ENTITIES.register("blast_door", key -> BlockEntityTypeCompat.create(
                (pos, state) -> new DoorAnimBlockEntity(BLAST_DOOR_BE.get(), pos, state), BLAST_DOOR.get()));
        PRESSURE_TRAPDOOR_BE = CollectionRegistries.BLOCK_ENTITIES.register("pressure_trapdoor",
                key -> BlockEntityTypeCompat.create((pos, state) -> new DoorAnimBlockEntity(PRESSURE_TRAPDOOR_BE.get(),
                        pos, state), PRESSURE_TRAPDOOR.get()));
        SHUTTER_BE = CollectionRegistries.BLOCK_ENTITIES.register("shutter", key -> BlockEntityTypeCompat.create(
                (pos, state) -> new DoorAnimBlockEntity(SHUTTER_BE.get(), pos, state), SHUTTER.get()));
        HOLO_PROJECTOR_BE = CollectionRegistries.BLOCK_ENTITIES.register("holo_projector",
                key -> BlockEntityTypeCompat.create((pos, state) -> new HoloProjectorBlockEntity(HOLO_PROJECTOR_BE.get(),
                        pos, state), HOLO_PROJECTOR.get()));
        HOLO_SIGN_BE = CollectionRegistries.BLOCK_ENTITIES.register("holo_sign", key -> BlockEntityTypeCompat.create(
                (pos, state) -> new HoloSignBlockEntity(HOLO_SIGN_BE.get(), pos, state), HOLO_SIGN.get()));
        WORLD_GLOBE_BE = CollectionRegistries.BLOCK_ENTITIES.register("world_globe", key -> BlockEntityTypeCompat.create(
                (pos, state) -> new WorldGlobeBlockEntity(WORLD_GLOBE_BE.get(), pos, state), WORLD_GLOBE.get()));
        HOLO_TABLE_BE = CollectionRegistries.BLOCK_ENTITIES.register("holo_table", key -> BlockEntityTypeCompat.create(
                (pos, state) -> new HoloTableBlockEntity(HOLO_TABLE_BE.get(), pos, state), HOLO_TABLE.get()));
        WALL_SCREEN_BE = CollectionRegistries.BLOCK_ENTITIES.register("wall_screen", key -> BlockEntityTypeCompat.create(
                (pos, state) -> new WallScreenBlockEntity(WALL_SCREEN_BE.get(), pos, state), WALL_SCREEN.get()));
    }

    // --- property presets -------------------------------------------------------------------
    private static BlockBehaviour.Properties props(ResourceKey<Block> key, UnaryOperator<BlockBehaviour.Properties> f) {
        return f.apply(BlockBehaviour.Properties.of().setId(key));
    }

    private static BlockBehaviour.Properties metal(ResourceKey<Block> key) {
        return props(key, p -> p.mapColor(MapColor.METAL).strength(3.0F, 6.0F).requiresCorrectToolForDrops()
                .sound(SoundType.METAL));
    }

    private static BlockBehaviour.Properties door(ResourceKey<Block> key) {
        return metal(key).noOcclusion();
    }

    private static BlockBehaviour.Properties fitting(ResourceKey<Block> key) {
        return props(key, p -> p.mapColor(MapColor.METAL).strength(1.0F, 3.0F).sound(SoundType.METAL).noOcclusion());
    }

    /** Vanilla glass behaviour (copied from {@link Blocks#GLASS}, so its predicates stay right on every version). */
    private static BlockBehaviour.Properties glass(ResourceKey<Block> key) {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS).setId(key)
                .mapColor(MapColor.NONE).strength(0.8F).sound(SoundType.GLASS);
    }

    private static RegistryEntry<Block> block(String name, Function<ResourceKey<Block>, Block> factory) {
        return CollectionRegistries.block(name, factory);
    }
}
