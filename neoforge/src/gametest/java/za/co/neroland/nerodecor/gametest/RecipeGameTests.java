package za.co.neroland.nerodecor.gametest;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Recipe;
import net.neoforged.neoforge.registries.DeferredRegister;

import za.co.neroland.nerodecor.NeroDecorCommon;
import za.co.neroland.nerodecor.content.item.NeonShimItem;

/**
 * Stage 6 recipe coverage: every NeroDecor recipe file loaded (a broken ingredient would drop it, or fail the whole
 * datapack on 26.3), every one has its recipe-book unlock advancement (tools/gen_recipe_unlocks.py) that rewards it,
 * and every visible NeroDecor item (each block item, the hub, linker, paint gun and scene chip; not the hidden neon
 * shims) is the output of at least one recipe.
 */
final class RecipeGameTests {

    private RecipeGameTests() {
    }

    static void register(DeferredRegister<Consumer<GameTestHelper>> functions) {
        functions.register("every_item_has_recipe_and_unlock", () -> RecipeGameTests::everyItemHasRecipeAndUnlock);
    }

    static void everyItemHasRecipeAndUnlock(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        String ns = NeroDecorCommon.MOD_ID;
        Map<Identifier, Resource> files = server.getResourceManager().listResources("recipe",
                id -> ns.equals(id.getNamespace()) && id.getPath().endsWith(".json"));
        List<String> problems = new ArrayList<>();
        Set<Item> made = new HashSet<>();
        for (Map.Entry<Identifier, Resource> file : files.entrySet()) {
            String path = file.getKey().getPath();
            path = path.substring("recipe/".length(), path.length() - ".json".length());
            ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath(ns, path));
            if (server.getRecipeManager().byKey(key).isEmpty()) {
                problems.add("not loaded: " + path);
            }
            JsonObject json;
            try (Reader reader = file.getValue().openAsReader()) {
                json = JsonParser.parseReader(reader).getAsJsonObject();
            } catch (IOException | RuntimeException e) {
                problems.add("unreadable: " + path);
                continue;
            }
            made.add(BuiltInRegistries.ITEM.getValue(
                    Identifier.parse(json.getAsJsonObject("result").get("id").getAsString())));
            String category = json.has("category") ? json.get("category").getAsString() : "misc";
            AdvancementHolder unlock = server.getAdvancements()
                    .get(Identifier.fromNamespaceAndPath(ns, "recipes/" + category + "/" + path));
            if (unlock == null || !unlock.value().rewards().recipes().contains(key)) {
                problems.add("no unlock: " + path);
            }
        }
        int items = 0;
        for (Item item : BuiltInRegistries.ITEM) {
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            if (!ns.equals(id.getNamespace()) || item instanceof NeonShimItem) {
                continue;
            }
            items++;
            if (!made.contains(item)) {
                problems.add("no recipe makes " + id.getPath());
            }
        }
        helper.assertTrue(items > 150, "only " + items + " NeroDecor items registered");
        helper.assertTrue(problems.isEmpty(), problems.size() + " recipe problems: " + String.join(", ",
                problems.subList(0, Math.min(problems.size(), 12))));
        helper.succeed();
    }
}
