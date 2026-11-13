package za.co.neroland.nerodecor.content;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import za.co.neroland.nerodecor.content.block.ChromaCubeBlock;
import za.co.neroland.nerodecor.content.block.NeonShimBlock;
import za.co.neroland.nerodecor.content.item.NeonShimItem;
import za.co.neroland.nerodecor.registry.ChromaContent;
import za.co.neroland.nerodecor.registry.ModDataComponents;

/**
 * The 0.8.0 neon fold: the twelve {@code neon_<colour>} blocks became one {@code chroma_cube} with an explicit
 * {@code COLOR}. Old ids stay registered as hidden shims; this class turns whatever still holds one into the chroma
 * equivalent with the same colour and light (15):
 *
 * <ul>
 *   <li><b>Blocks</b>: when a chunk loads (NeoForge/Forge {@code ChunkEvent.Load}, Fabric
 *       {@code ServerChunkEvents.CHUNK_LOAD}), sections that may hold a shim ({@link LevelChunkSection#maybeHas}) are
 *       scanned and the swap is queued for the next server tick ({@link #drainPending()}), never applied mid-load.</li>
 *   <li><b>Containers</b> in that chunk: shim stacks are converted in the same queued task.</li>
 *   <li><b>Entity inventories</b> and <b>dropped items</b>: {@link NeonShimItem}.</li>
 * </ul>
 *
 * <p>Logs nothing about players or positions.
 */
public final class NeonFold {

    private record Pending(ServerLevel level, List<BlockPos> blocks, List<BlockPos> containers) {
    }

    private static final java.util.Queue<Pending> PENDING = new java.util.concurrent.ConcurrentLinkedQueue<>();

    private NeonFold() {
    }

    public static boolean isShim(BlockState state) {
        return state.getBlock() instanceof NeonShimBlock;
    }

    /** The colour a shim block becomes: an explicit paint wins over the old neon's own dye. */
    public static DecorColor colourOf(BlockState shim) {
        DecorColor painted = shim.getValue(DecorProperties.COLOR);
        return painted.isNatural() ? ((NeonShimBlock) shim.getBlock()).dye() : painted;
    }

    /** A chroma cube of {@code colour} at full light, connected to its neighbours at {@code pos}. */
    public static BlockState cubeFor(DecorColor colour, BlockGetter level, BlockPos pos) {
        ChromaCubeBlock cube = (ChromaCubeBlock) ChromaContent.CUBE.get();
        BlockState state = cube.defaultBlockState().setValue(DecorProperties.COLOR, colour)
                .setValue(ChromaCubeBlock.LIGHT, 15);
        return level == null ? state : cube.withConnections(level, pos, state);
    }

    /** A chroma cube stack with the colour component, or {@code stack} itself when it is not a shim. */
    public static ItemStack convert(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof NeonShimItem item)) {
            return stack;
        }
        DecorColor colour = ((NeonShimBlock) item.getBlock()).dye();
        DecorColor carried = stack.get(ModDataComponents.COLOR.get());
        if (carried != null && !carried.isNatural()) {
            colour = carried;
        }
        ItemStack out = new ItemStack(ChromaContent.cubeItem(), stack.getCount());
        out.set(ModDataComponents.COLOR.get(), colour);
        if (stack.has(DataComponents.CUSTOM_NAME)) {
            out.set(DataComponents.CUSTOM_NAME, stack.get(DataComponents.CUSTOM_NAME));
        }
        return out;
    }

    /** Converts every shim stack in a container; returns how many slots changed. */
    public static int convertContainer(Container container) {
        int changed = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            ItemStack converted = convert(stack);
            if (converted != stack) {
                container.setItem(slot, converted);
                changed++;
            }
        }
        if (changed > 0) {
            container.setChanged();
        }
        return changed;
    }

    /** A shim stack ticking in an entity's inventory: replace it in place (players; other holders are left). */
    public static void convertHeld(Entity owner, ItemStack stack) {
        if (!(owner instanceof Player player) || owner.level().isClientSide()) {
            return;
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot) == stack) {
                inventory.setItem(slot, convert(stack));
                return;
            }
        }
    }

    /** A dropped shim stack: swap the entity's item (server side). */
    public static void convertItemEntity(ItemEntity entity) {
        if (entity.level().isClientSide()) {
            return;
        }
        ItemStack stack = entity.getItem();
        ItemStack converted = convert(stack);
        if (converted != stack) {
            entity.setItem(converted);
        }
    }

    /**
     * Chunk-load hook: finds shim blocks (only in sections whose palette may hold one) and containers holding shim
     * stacks, then queues the swap for the next server tick.
     */
    public static void onChunkLoad(ServerLevel level, LevelChunk chunk) {
        List<BlockPos> blocks = new ArrayList<>();
        LevelChunkSection[] sections = chunk.getSections();
        int baseX = chunk.getPos().getMinBlockX();
        int baseZ = chunk.getPos().getMinBlockZ();
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section == null || section.hasOnlyAir() || !section.maybeHas(NeonFold::isShim)) {
                continue;
            }
            int baseY = chunk.getSectionYFromSectionIndex(i) << 4;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (isShim(section.getBlockState(x, y, z))) {
                            blocks.add(new BlockPos(baseX + x, baseY + y, baseZ + z));
                        }
                    }
                }
            }
        }
        List<BlockPos> containers = new ArrayList<>();
        for (BlockEntity be : chunk.getBlockEntities().values()) {
            if (be instanceof Container container && holdsShim(container)) {
                containers.add(be.getBlockPos());
            }
        }
        if (blocks.isEmpty() && containers.isEmpty()) {
            return;
        }
        PENDING.add(new Pending(level, blocks, containers));
    }

    /**
     * Applies the swaps queued by {@link #onChunkLoad}. Called at the start of the server tick, so a swap never runs
     * inside chunk loading ({@code server.execute} runs a task inline when called on the server thread, which is
     * where chunk-load events fire, so the fold keeps its own queue instead).
     */
    public static void drainPending() {
        Pending next;
        int budget = 64;
        while (budget-- > 0 && (next = PENDING.poll()) != null) {
            apply(next.level(), next.blocks(), next.containers());
        }
    }

    /** Swaps the given shim blocks and converts the given containers, where still loaded. Server thread. */
    public static int apply(ServerLevel level, List<BlockPos> blocks, List<BlockPos> containers) {
        int swapped = 0;
        for (BlockPos pos : blocks) {
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (isShim(state)) {
                level.setBlock(pos, cubeFor(colourOf(state), level, pos), Block.UPDATE_ALL);
                swapped++;
            }
        }
        for (BlockPos pos : containers) {
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof Container container) {
                convertContainer(container);
            }
        }
        return swapped;
    }

    private static boolean holdsShim(Container container) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (container.getItem(slot).getItem() instanceof NeonShimItem) {
                return true;
            }
        }
        return false;
    }
}
