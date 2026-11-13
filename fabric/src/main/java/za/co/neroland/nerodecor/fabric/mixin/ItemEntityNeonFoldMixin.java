package za.co.neroland.nerodecor.fabric.mixin;

import net.minecraft.world.entity.item.ItemEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import za.co.neroland.nerodecor.content.NeonFold;

/** Fabric's stand-in for NeoForge/Forge {@code onEntityItemUpdate}: a dropped legacy neon stack becomes a chroma cube. */
@Mixin(ItemEntity.class)
public abstract class ItemEntityNeonFoldMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void nerodecor$foldNeon(CallbackInfo ci) {
        NeonFold.convertItemEntity((ItemEntity) (Object) this);
    }
}
