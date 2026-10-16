package com.wayback09.campfire.fabric;

import com.wayback09.campfire.CampfireConfig;
import com.wayback09.campfire.PresenceManager;
import com.wayback09.campfire.PresenceState;
import com.wayback09.campfire.ScreenshotStore;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;

/** Fabric client entrypoint. */
public final class CampfireFabric implements ClientModInitializer {
    public static final String MOD_ID = "campfire";
    public static final CampfireConfig CONFIG = new CampfireConfig();
    public static ScreenshotStore screenshots;
    public static PresenceManager presence;
    public static boolean pendingHostWorld = false;

    @Override
    public void onInitializeClient() {
        var gameDir = FabricLoader.getInstance().getGameDir();

        screenshots = new ScreenshotStore(gameDir.resolve("screenshots"));
        presence = new PresenceManager(gameDir);

        // Set initial presence to MENU
        presence.update(PresenceState.menu(getUsername()));

        PicturesKeybinds.register();
        ScreenshotCapture.register(screenshots, CONFIG);
        CampfireMenuHooks.register();

        // Poll for pending invites every 40 ticks (~2 seconds)
        final int[] tickCounter = {0};
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (++tickCounter[0] < 40) return;
            tickCounter[0] = 0;
            var invite = presence.pollPendingInvite(getUsername());
            if (invite != null) {
                InviteToast.show(client, invite);
            }
        });

        // Update presence on server join
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (pendingHostWorld) {
                pendingHostWorld = false;
                client.execute(() -> {
                    if (client.getServer() != null) {
                        client.setScreen(new HostScreen(null));
                    }
                });
            }
            client.execute(() -> {
                if (client.getServer() != null && client.getServer().isSingleplayer()) {
                    presence.update(PresenceState.singleplayer(getUsername(),
                        client.getServer().getSaveProperties().getLevelName()));
                } else if (client.getCurrentServerEntry() != null) {
                    presence.update(PresenceState.multiplayer(getUsername(),
                        client.getCurrentServerEntry().name));
                }
            });
        });

        // Reset presence to MENU on disconnect
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
            presence.update(PresenceState.menu(getUsername())));
    }

    public static String getUsername() {
        var client = net.minecraft.client.MinecraftClient.getInstance();
        return (client != null && client.getSession() != null)
            ? client.getSession().getUsername()
            : "Player";
    }
}

