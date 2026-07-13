package za.co.neroland.nerodecor.neoforge.data;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;

import za.co.neroland.nerodecor.NeroDecorCommon;

/**
 * NeroDecor's single datagen entry point (NeoForge {@code runData}). Drives shared providers
 * that write the loader-agnostic blockstates/models/loot/recipes/tags/lang JSON into
 * {@code common/src/main/resources} (see the {@code data} run in {@code neoforge/build.gradle}),
 * which all six loader cells consume. Run with {@code :neoforge:<mc>:runData}.
 */
@EventBusSubscriber(modid = NeroDecorCommon.MOD_ID)
public final class NeroDecorDataGen {

    private NeroDecorDataGen() {
    }

    @SubscribeEvent
    public static void gatherData(GatherDataEvent.Client event) {
        event.createProvider(DecorLanguageProvider::new);
    }
}
