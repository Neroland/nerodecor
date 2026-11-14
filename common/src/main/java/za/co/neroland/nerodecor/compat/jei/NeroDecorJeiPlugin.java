package za.co.neroland.nerodecor.compat.jei;

import java.util.List;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import za.co.neroland.nerodecor.NeroDecorCommon;
import za.co.neroland.nerodecor.compat.ViewerInfo;

/**
 * NeroDecor's Just Enough Items integration: an information page for the Chroma Hub, Chroma Linker, Paint Gun and
 * Scene Chip, and the legacy neon shim items hidden. Crafting, stonecutting and smelting recipes need no code; JEI
 * reads those itself.
 *
 * <p>Uses only the loader-agnostic JEI common API, so the class lives in {@code common}; NeoForge and Forge find it
 * through {@link JeiPlugin}, Fabric through the {@code jei_mod_plugin} entrypoint. JEI is compile-time only, and the
 * cells with no JEI API leave this package out, so without JEI this class never loads.
 */
@JeiPlugin
public final class NeroDecorJeiPlugin implements IModPlugin {

    private static final Identifier UID = Identifier.fromNamespaceAndPath(NeroDecorCommon.MOD_ID, "jei");

    @Override
    public Identifier getPluginUid() {
        return UID;
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        for (ViewerInfo.Page page : ViewerInfo.pages()) {
            registration.addIngredientInfo(page.item(), page.text());
        }
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        List<ItemStack> hidden = ViewerInfo.hidden().stream().map(ItemStack::new).toList();
        if (!hidden.isEmpty()) {
            runtime.getIngredientManager().removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, hidden);
        }
    }
}
