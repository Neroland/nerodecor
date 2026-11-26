package za.co.neroland.nerodecor.registry;

//? if <1.20.5 {
/*import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import com.mojang.serialization.JsonOps;
import za.co.neroland.nerodecor.content.DecorColor;
import za.co.neroland.nerodecor.content.item.DecorBlockItem;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapelessRecipe;

import za.co.neroland.nerodecor.NeroDecorCommon;


public final class LegacyPaintRecipe extends ShapelessRecipe {
    private static final RecipeSerializer<ShapelessRecipe> VANILLA = RecipeSerializer.SHAPELESS_RECIPE;
    private static final RegistrationProvider.RegistryEntry<RecipeSerializer<LegacyPaintRecipe>> SERIALIZER = RegistrationProvider
            .get(Registries.RECIPE_SERIALIZER, NeroDecorCommon.MOD_ID)
            .register("painted_shapeless", key -> new Serializer());

    private final ShapelessRecipe base;
    private final DecorColor colour;

    private LegacyPaintRecipe(ShapelessRecipe base, DecorColor colour) {
        //? if >=1.20.2 {
        super(base.getGroup(), base.category(), result(base, colour), base.getIngredients());
        //?} else {
        super(base.getId(), base.getGroup(), base.category(), result(base, colour), base.getIngredients());
        //?}
        this.base = base;
        this.colour = colour;
    }

    private static ItemStack result(ShapelessRecipe base, DecorColor colour) {
        ItemStack result = base.getResultItem(RegistryAccess.EMPTY).copy();
        if (!(result.getItem() instanceof DecorBlockItem)) {
            throw new JsonSyntaxException("A painted recipe must output a decor block item");
        }
        ModDataComponents.COLOR.set(result, colour);
        return result;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return SERIALIZER.get();
    }

    public static void init() {
    }

    private static final class Serializer implements RecipeSerializer<LegacyPaintRecipe> {
        //? if >=1.20.2 {
        @Override
        public Codec<LegacyPaintRecipe> codec() {
            return RecordCodecBuilder.create(instance -> instance.group(
                    VANILLA.codec().fieldOf("recipe").forGetter(recipe -> recipe.base),
                    DecorColor.CODEC.fieldOf("colour").forGetter(recipe -> recipe.colour)
            ).apply(instance, LegacyPaintRecipe::new));
        }

        @Override
        public LegacyPaintRecipe fromNetwork(FriendlyByteBuf buf) {
            return new LegacyPaintRecipe(VANILLA.fromNetwork(buf), buf.readEnum(DecorColor.class));
        }
        //?} else {
        @Override
        public LegacyPaintRecipe fromJson(Identifier id, JsonObject json) {
            ShapelessRecipe base = VANILLA.fromJson(id, GsonHelper.getAsJsonObject(json, "recipe"));
            DecorColor colour = DecorColor.CODEC.parse(JsonOps.INSTANCE, json.get("colour")).result()
                    .orElseThrow(() -> new JsonSyntaxException("Expected a known decor colour"));
            return new LegacyPaintRecipe(base, colour);
        }

        @Override
        public LegacyPaintRecipe fromNetwork(Identifier id, FriendlyByteBuf buf) {
            return new LegacyPaintRecipe(VANILLA.fromNetwork(id, buf), buf.readEnum(DecorColor.class));
        }
        //?}

        @Override
        public void toNetwork(FriendlyByteBuf buf, LegacyPaintRecipe recipe) {
            VANILLA.toNetwork(buf, recipe.base);
            buf.writeEnum(recipe.colour);
        }
    }
}
*///?}
