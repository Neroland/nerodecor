package za.co.neroland.nerodecor.platform;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

import za.co.neroland.nerodecor.NeroDecorCommon;

/** Fabric implementation of {@link IPlatformHelper}. */
public final class FabricPlatformHelper implements IPlatformHelper {

    @Override
    public String getPlatformName() {
        return "Fabric";
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public boolean isClient() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
    }

    @Override
    public String getModVersion() {
        return FabricLoader.getInstance().getModContainer(NeroDecorCommon.MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }
}
