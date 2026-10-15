package com.wayback09.campfire.fabric;

import com.wayback09.campfire.CampfireConfig;
import com.wayback09.campfire.ScreenshotStore;
import net.fabricmc.api.ClientModInitializer;

/** Fabric client entrypoint. */
public final class CampfireFabric implements ClientModInitializer {
    public static final String MOD_ID = "campfire";
    public static final CampfireConfig CONFIG = new CampfireConfig();
    public static ScreenshotStore screenshots;
    public static boolean pendingHostWorld = false;

    @Override
    public void onInitializeClient() {
        screenshots = new ScreenshotStore(
            net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("screenshots"));
        PicturesKeybinds.register();
        ScreenshotCapture.register(screenshots, CONFIG);
        CampfireMenuHooks.register();

        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (pendingHostWorld) {
                pendingHostWorld = false;
                client.execute(() -> {
                    if (client.getServer() != null) {
                        client.setScreen(new HostScreen(null));
                    }
                });
            }
        });
    }
}
