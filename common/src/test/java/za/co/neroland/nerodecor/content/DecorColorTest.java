package za.co.neroland.nerodecor.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class DecorColorTest {

    @Test
    void naturalIsTheMultiplyIdentity() {
        assertEquals(0xFFFFFF, DecorColor.NATURAL.tint());
        assertTrue(DecorColor.NATURAL.isNatural());
    }

    @Test
    void seventeenUniqueSerializedNames() {
        Set<String> names = new HashSet<>();
        for (DecorColor c : DecorColor.values()) {
            names.add(c.getSerializedName());
        }
        assertEquals(17, names.size());
    }

    /** Every neon colour folded into chroma_cube in 0.8.0 must have a matching dye. */
    @Test
    void everyNeonColourHasADecorColor() {
        String[] neon = {"red", "orange", "yellow", "lime", "green", "cyan",
                "light_blue", "blue", "purple", "magenta", "pink", "white"};
        for (String n : neon) {
            boolean found = false;
            for (DecorColor c : DecorColor.values()) {
                found |= c.getSerializedName().equals(n);
            }
            assertTrue(found, n);
        }
    }

    //? if <1.20.5 {
    /*@org.junit.jupiter.api.BeforeAll
    static void bootstrapLegacyItems() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void legacyPaintSurvivesItemSaveAndReload() {
        var stack = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PAPER);
        var paint = za.co.neroland.nerodecor.registry.ModDataComponents.COLOR;
        paint.set(stack, DecorColor.RED);
        var loaded = net.minecraft.world.item.ItemStack.of(stack.save(new net.minecraft.nbt.CompoundTag()));
        assertEquals(DecorColor.RED, paint.get(loaded));
    }

    @Test
    void invalidLegacyPaintFallsBackToTheNaturalColour() {
        var stack = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PAPER);
        stack.getOrCreateTag().putString("nerodecor:color", "unknown");
        var paint = za.co.neroland.nerodecor.registry.ModDataComponents.COLOR;
        assertEquals(DecorColor.NATURAL, paint.getOrDefault(stack, DecorColor.NATURAL));
    }
    *///?}
}
