package com.wayback09.campfire.fabric;

import com.wayback09.campfire.CampfireConfig;
import com.wayback09.campfire.ScreenshotStore;
import net.fabricmc.api.ClientModInitializer;

/** Author: wayback09. Fabric entrypoint (see fabric.mod.json: campfire.client). */
public final class CampfireFabric implements ClientModInitializer {
    public static final String MOD_ID = "campfire";
    public static final CampfireConfig CONFIG = new CampfireConfig();
    public static ScreenshotStore screenshots;

    @Override
    public void onInitializeClient() {
        screenshots = new ScreenshotStore(
            net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("campfire/pictures"));
        PicturesKeybinds.register();
        ScreenshotCapture.register(screenshots, CONFIG);
    }
}
