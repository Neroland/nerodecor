package za.co.neroland.nerodecor.compat.emi;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiInfoRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

import za.co.neroland.nerodecor.NeroDecorCommon;
import za.co.neroland.nerodecor.compat.ViewerInfo;

/**
 * NeroDecor's EMI integration, the counterpart of {@code compat.jei.NeroDecorJeiPlugin}: the same information pages
 * for the Chroma tools and the same hidden neon shim items.
 *
 * <p>Built against the community "EMI Unofficial Port" (official EMI has no Minecraft 26.x release), which keeps the
 * upstream {@code dev.emi.emi.api} package. NeoForge finds it through {@link EmiEntrypoint}, Fabric through the
 * {@code emi} entrypoint. EMI is compile-time only; without it this class never loads. Nothing here touches a
 * {@code compat.jei} class, so an EMI-only install works.
 */
@EmiEntrypoint
public final class NeroDecorEmiPlugin implements EmiPlugin {

    @Override
    public void register(EmiRegistry registry) {
        for (ViewerInfo.Page page : ViewerInfo.pages()) {
            // A leading '/' marks a synthetic recipe id (no datapack recipe behind it), as EMI asks.
            Identifier id = Identifier.fromNamespaceAndPath(NeroDecorCommon.MOD_ID,
                    "/info/" + BuiltInRegistries.ITEM.getKey(page.item()).getPath());
            registry.addRecipe(new EmiInfoRecipe(List.<EmiIngredient>of(EmiStack.of(page.item())),
                    List.of(page.text()), id));
        }
        Set<Item> hidden = ViewerInfo.hidden().stream().collect(Collectors.toSet());
        if (!hidden.isEmpty()) {
            registry.removeEmiStacks(stack -> hidden.contains(stack.getItemStack().getItem()));
        }
    }
}
