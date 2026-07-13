package za.co.neroland.nerodecor.neoforge.data;

import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

import za.co.neroland.nerodecor.NeroDecorCommon;
import za.co.neroland.nerodecor.registry.DecorBlocks;
import za.co.neroland.nerodecor.registry.ModItems;

/**
 * en_US names for every decor block + its block item, derived from the registry path
 * (title-cased). Both the {@code block.nerodecor.*} and {@code item.nerodecor.*} keys are
 * emitted (a 26.x BlockItem shows its raw item key unless the mirrored item alias exists).
 */
public class DecorLanguageProvider extends LanguageProvider {

    public DecorLanguageProvider(PackOutput output) {
        super(output, NeroDecorCommon.MOD_ID, "en_us");
    }

    @Override
    protected void addTranslations() {
        DecorBlocks.ALL.forEach(block -> add(block.get(), titleCase(block.id().getPath())));
        ModItems.BLOCK_ITEMS.forEach(item -> add(item.get(), titleCase(item.id().getPath())));
    }

    private static String titleCase(String path) {
        String[] parts = path.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }
}
