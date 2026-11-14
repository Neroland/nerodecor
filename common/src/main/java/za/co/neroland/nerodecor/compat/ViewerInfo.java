package za.co.neroland.nerodecor.compat;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

import za.co.neroland.nerodecor.registry.ChromaContent;
import za.co.neroland.nerodecor.registry.RegistrationProvider.RegistryEntry;

/**
 * What NeroDecor tells recipe viewers, shared by the JEI ({@code compat.jei}) and EMI ({@code compat.emi}) plugins so
 * both show the same thing: one information page for each Chroma tool, and the twelve legacy {@code neon_<colour>}
 * shim items hidden (they only exist to fold old worlds into Chroma Cubes; they have no recipe and no tab).
 *
 * <p>Plain Minecraft types only, so this class compiles on every cell; the viewer plugins themselves are left out of
 * the cells that have no viewer API.
 */
public final class ViewerInfo {

    /** One information page: the item and its English-source text ({@code info.nerodecor.<id>}). */
    public record Page(Item item, Component text) {
    }

    private ViewerInfo() {
    }

    public static List<Page> pages() {
        List<Page> pages = new ArrayList<>();
        page(pages, ChromaContent.HUB_ITEM);
        page(pages, ChromaContent.LINKER);
        page(pages, ChromaContent.PAINT_GUN);
        page(pages, ChromaContent.SCENE_CHIP_ITEM);
        return pages;
    }

    /** Items recipe viewers must not list: the neon shims. */
    public static List<Item> hidden() {
        List<Item> items = new ArrayList<>();
        for (RegistryEntry<Item> shim : ChromaContent.NEON_SHIM_ITEMS) {
            items.add(shim.get());
        }
        return items;
    }

    private static void page(List<Page> pages, RegistryEntry<Item> entry) {
        if (entry != null) {
            pages.add(new Page(entry.get(), Component.translatable("info.nerodecor." + entry.id().getPath())));
        }
    }
}
