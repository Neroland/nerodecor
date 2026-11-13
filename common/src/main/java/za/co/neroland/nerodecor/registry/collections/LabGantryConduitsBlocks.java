package za.co.neroland.nerodecor.registry.collections;

import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import za.co.neroland.nerodecor.content.block.conduits.CableTrayBlock;
import za.co.neroland.nerodecor.content.block.conduits.ConduitPipeBlock;
import za.co.neroland.nerodecor.content.block.conduits.FacingDecorBlock;
import za.co.neroland.nerodecor.content.block.conduits.HangingCablesBlock;
import za.co.neroland.nerodecor.content.block.conduits.InlineFittingBlock;
import za.co.neroland.nerodecor.content.block.gantry.AxisLinkBlock;
import za.co.neroland.nerodecor.content.block.gantry.CatwalkBlock;
import za.co.neroland.nerodecor.content.block.gantry.CatwalkStairsBlock;
import za.co.neroland.nerodecor.content.block.gantry.FloorGrateBlock;
import za.co.neroland.nerodecor.content.block.gantry.FloorGrateSlabBlock;
import za.co.neroland.nerodecor.content.block.gantry.GantryLadderBlock;
import za.co.neroland.nerodecor.content.block.gantry.ModelShapes;
import za.co.neroland.nerodecor.content.block.gantry.RailingBlock;
import za.co.neroland.nerodecor.content.block.lab.CabinetBlock;
import za.co.neroland.nerodecor.content.block.lab.CrewBunkBlock;
import za.co.neroland.nerodecor.content.block.lab.HorizontalRunBlock;
import za.co.neroland.nerodecor.content.block.lab.LabStoolBlock;
import za.co.neroland.nerodecor.content.block.lab.LockerBlock;
import za.co.neroland.nerodecor.content.block.lab.PlanterBlock;
import za.co.neroland.nerodecor.content.block.lab.SpecimenTankBlock;
import za.co.neroland.nerodecor.content.entity.SeatEntity;
import za.co.neroland.nerodecor.registry.RegistrationProvider.RegistryEntry;

/**
 * Work package: the lab_habitat, gantry and conduits collections (resources from the matching modules under
 * {@code tools/collections/}), plus the {@code nerodecor:seat} entity. Owned by one agent; keep every registration
 * for these collections in this class. Block ids match the modules' {@code Block(id=...)}; the blockstate
 * properties match each module's docstring contract. Render layers:
 * {@link za.co.neroland.nerodecor.client.collections.LabGantryConduitsClient}.
 */
public final class LabGantryConduitsBlocks {

    // 26.3 renamed PushReaction (DESTROY -> POPPED); pistons pop the bunk like a bed.
    //? if >=26.3 {
    private static final PushReaction BUNK_PUSH = PushReaction.POPPED;
    //?} else {
    /*private static final PushReaction BUNK_PUSH = PushReaction.DESTROY;
    *///?}

    /** The invisible vehicle a player rides on a seat block (see {@link SeatEntity#sit}). Never saved. */
    public static final RegistryEntry<EntityType<SeatEntity>> SEAT = CollectionRegistries.ENTITY_TYPES.register("seat",
            key -> EntityType.Builder.<SeatEntity>of(SeatEntity::new, MobCategory.MISC)
                    .sized(0.001F, 0.001F)
                    .noSummon()
                    .clientTrackingRange(10)
                    .updateInterval(20)
                    //? if >=1.21.2 {
                    .build(key));
                    //?} else {
                    /*.build(key.identifier().toString()));
                    *///?}

    // --- lab_habitat ---------------------------------------------------------------------------------------------
    public static final RegistryEntry<Block> LAB_BENCH = CollectionRegistries.block("lab_bench",
            key -> new HorizontalRunBlock(furniture(key), HorizontalRunBlock::benchShape));
    public static final RegistryEntry<Block> FUME_HOOD = CollectionRegistries.block("fume_hood",
            key -> new CabinetBlock(furniture(key), ModelShapes.box(0, 0, 0, 16, 16, 16),
                    ModelShapes.box(0, 0, 0, 16, 16, 16), SoundEvents.IRON_TRAPDOOR_OPEN, SoundEvents.IRON_TRAPDOOR_CLOSE));
    public static final RegistryEntry<Block> SPECIMEN_TANK = CollectionRegistries.block("specimen_tank",
            key -> new SpecimenTankBlock(furniture(key).sound(SoundType.GLASS).lightLevel(state -> 6)));
    public static final RegistryEntry<Block> SAMPLE_FRIDGE = CollectionRegistries.block("sample_fridge",
            key -> new CabinetBlock(furniture(key), ModelShapes.box(0, 0, 0, 16, 16, 16),
                    ModelShapes.box(0, 0, 2, 16, 16, 16), SoundEvents.BARREL_OPEN, SoundEvents.BARREL_CLOSE));
    public static final RegistryEntry<Block> LAB_STOOL = CollectionRegistries.block("lab_stool",
            key -> new LabStoolBlock(furniture(key)));
    public static final RegistryEntry<Block> CREW_BUNK = CollectionRegistries.block("crew_bunk",
            key -> new CrewBunkBlock(furniture(key).strength(1.5F).pushReaction(BUNK_PUSH)));
    public static final RegistryEntry<Block> LOCKER = CollectionRegistries.block("locker",
            key -> new LockerBlock(furniture(key)));
    public static final RegistryEntry<Block> PLANTER = CollectionRegistries.block("planter",
            key -> new PlanterBlock(furniture(key)));

    // --- gantry --------------------------------------------------------------------------------------------------
    public static final RegistryEntry<Block> CATWALK = CollectionRegistries.block("catwalk",
            key -> new CatwalkBlock(furniture(key)));
    public static final RegistryEntry<Block> CATWALK_STAIRS = CollectionRegistries.block("catwalk_stairs",
            key -> new CatwalkStairsBlock(furniture(key)));
    public static final RegistryEntry<Block> RAILING = CollectionRegistries.block("railing",
            key -> new RailingBlock(furniture(key)));
    public static final RegistryEntry<Block> GANTRY_LADDER = CollectionRegistries.block("gantry_ladder",
            key -> new GantryLadderBlock(furniture(key).sound(SoundType.LADDER)));
    public static final RegistryEntry<Block> FLOOR_GRATE = CollectionRegistries.block("floor_grate",
            key -> new FloorGrateBlock(furniture(key)));
    public static final RegistryEntry<Block> FLOOR_GRATE_SLAB = CollectionRegistries.block("floor_grate_slab",
            key -> new FloorGrateSlabBlock(furniture(key)));
    public static final RegistryEntry<Block> STRUT = CollectionRegistries.block("strut",
            key -> new AxisLinkBlock(furniture(key), AxisLinkBlock::strutShape));
    public static final RegistryEntry<Block> TRUSS_BEAM = CollectionRegistries.block("truss_beam",
            key -> new AxisLinkBlock(furniture(key), AxisLinkBlock::trussShape));

    // --- conduits ------------------------------------------------------------------------------------------------
    public static final RegistryEntry<Block> DECOR_PIPE = CollectionRegistries.block("decor_pipe",
            key -> new ConduitPipeBlock(furniture(key)));
    public static final RegistryEntry<Block> PIPE_VALVE = CollectionRegistries.block("pipe_valve",
            key -> InlineFittingBlock.valve(furniture(key)));
    public static final RegistryEntry<Block> PIPE_GAUGE = CollectionRegistries.block("pipe_gauge",
            key -> InlineFittingBlock.gauge(furniture(key)));
    public static final RegistryEntry<Block> CABLE_TRAY = CollectionRegistries.block("cable_tray",
            key -> new CableTrayBlock(furniture(key)));
    public static final RegistryEntry<Block> HANGING_CABLES = CollectionRegistries.block("hanging_cables",
            key -> new HangingCablesBlock(furniture(key).strength(0.5F).sound(SoundType.WOOL)));
    public static final RegistryEntry<Block> DUCT = CollectionRegistries.block("duct",
            key -> new AxisLinkBlock(metal(key), AxisLinkBlock::fullShape));
    public static final RegistryEntry<Block> DUCT_FAN = CollectionRegistries.block("duct_fan",
            key -> new FacingDecorBlock(metal(key), ModelShapes.box(0, 0, 0, 16, 16, 16), false));
    public static final RegistryEntry<Block> COOLANT_TUBE = CollectionRegistries.block("coolant_tube",
            key -> new ConduitPipeBlock(furniture(key)));
    public static final RegistryEntry<Block> BEAM_EMITTER_PROP = CollectionRegistries.block("beam_emitter_prop",
            key -> new FacingDecorBlock(furniture(key), ModelShapes.box(3, 3, 1, 13, 13, 16), true));

    private LabGantryConduitsBlocks() {
    }

    /** Called once from {@link CollectionRegistries#init()}; the static fields above register on class load. */
    public static void init() {
        // Touching the class runs the registrations above.
    }

    /** Solid, full metal blocks (duct, duct fan). */
    private static BlockBehaviour.Properties metal(ResourceKey<Block> key) {
        return BlockBehaviour.Properties.of().setId(key).mapColor(MapColor.METAL).strength(3.0F, 6.0F)
                .requiresCorrectToolForDrops().sound(SoundType.METAL);
    }

    /** Shaped metal furniture: no occlusion, never suffocates, conducts no redstone, no mob spawns. */
    private static BlockBehaviour.Properties furniture(ResourceKey<Block> key) {
        return metal(key).noOcclusion().isValidSpawn((s, l, p, t) -> false).isRedstoneConductor((s, l, p) -> false)
                .isSuffocating((s, l, p) -> false);
    }
}
