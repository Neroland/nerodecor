package za.co.neroland.nerodecor.content.item;

//? if >=1.21.5 {
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
//?} else {
/*import net.minecraft.world.level.Level;
*///?}
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerodecor.content.NeonFold;

/**
 * The item of a legacy {@code neon_<colour>} block: hidden (no creative tab, no recipe) and converted to a
 * same-colour chroma cube stack wherever it shows up: in an entity inventory on its next tick, as a dropped item
 * (NeoForge/Forge {@code onEntityItemUpdate}; Fabric through a mixin on {@code ItemEntity#tick}), in containers when
 * their chunk loads.
 */
public class NeonShimItem extends BlockItem {

    public NeonShimItem(Block block, Properties properties) {
        super(block, properties);
    }

    // 1.21.5 changed inventoryTick to (stack, ServerLevel, entity, equipment slot); before it was (stack, level,
    // entity, slot index, selected) and ran on both sides.
    //? if >=1.21.5 {
    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
        NeonFold.convertHeld(owner, stack);
    }
    //?} else {
    /*@Override
    public void inventoryTick(ItemStack stack, Level level, Entity owner, int slot, boolean selected) {
        if (!level.isClientSide()) {
            NeonFold.convertHeld(owner, stack);
        }
    }
    *///?}

    /**
     * NeoForge's {@code IItemExtension} and Forge's {@code IForgeItem} call this every tick for a dropped stack (no
     * {@code @Override}: Fabric's {@code Item} has no such method, so there it is reached from the mixin instead).
     *
     * @return false, so the item entity keeps ticking normally
     */
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        NeonFold.convertItemEntity(entity);
        return false;
    }
}
