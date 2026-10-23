package com.wayback09.campfire.fabric;

import com.wayback09.campfire.CampfireConfig;
import com.wayback09.campfire.PresenceManager;
import com.wayback09.campfire.PresenceState;
import com.wayback09.campfire.ScreenshotStore;
import com.wayback09.campfire.TelemetryManager;
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
    public static TelemetryManager telemetry;
    public static boolean pendingHostWorld = false;

    @Override
    public void onInitializeClient() {
        var gameDir = FabricLoader.getInstance().getGameDir();

        // Resolve campfire.properties + system property overrides once, then
        // hand them to every component that needs an endpoint.
        var props = com.wayback09.campfire.CampfireProperties.load(gameDir);
        com.wayback09.campfire.RelayClient.configure(props);

        screenshots = new ScreenshotStore(gameDir.resolve("screenshots"));
        presence = new PresenceManager(gameDir, props);
        telemetry = new TelemetryManager(gameDir, CONFIG, props);

        // Send anonymous launch telemetry event
        String modVer = FabricLoader.getInstance().getModContainer("campfire")
            .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("0.1.0");
        String mcVer = FabricLoader.getInstance().getModContainer("minecraft")
            .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("1.21.x");
        telemetry.sendLaunch(modVer, mcVer, "fabric");

        // Set initial presence to MENU
        presence.update(PresenceState.menu(getUsername()));

        PicturesKeybinds.register();
        ScreenshotCapture.register(screenshots, CONFIG);
        CampfireMenuHooks.register();

        // Poll for pending invites asynchronously every 60 ticks (~3 seconds)
        // Never block the client render loop with synchronous network calls!
        final int[] tickCounter = {0};
        final boolean[] isPollingInvites = {false};
        final long[] lastHeartbeat = {System.currentTimeMillis()};
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Keep the live "who is a Campfire user" cache fresh for badges.
            CampfireBadge.tick(client);

            // The social backend expires presence after ~90 seconds, so
            // heartbeat while in a world or other clients stop seeing us.
            if (client.getNetworkHandler() != null
                && System.currentTimeMillis() - lastHeartbeat[0] > 45_000L) {
                lastHeartbeat[0] = System.currentTimeMillis();
                presence.heartbeat();
            }

            if (++tickCounter[0] < 60) return;
            tickCounter[0] = 0;

            if (isPollingInvites[0]) return;
            isPollingInvites[0] = true;

            java.util.concurrent.CompletableFuture.supplyAsync(() -> presence.pollPendingInvite(getUsername()))
                .thenAccept(invite -> {
                    isPollingInvites[0] = false;
                    if (invite != null) {
                        client.execute(() -> InviteToast.show(client, invite));
                    }
                })
                .exceptionally(ex -> {
                    isPollingInvites[0] = false;
                    return null;
                });
        });

        // Update presence on server join
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            CampfireBadge.reset();
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

        // Reset presence to MENU on disconnect and close active relay tunnel
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            CampfireBadge.reset();
            com.wayback09.campfire.RelayClient.closeActiveSession();
            presence.update(PresenceState.menu(getUsername()));
        });
    }

    public static String getUsername() {
        var client = net.minecraft.client.MinecraftClient.getInstance();
        return (client != null && client.getSession() != null)
            ? client.getSession().getUsername()
            : "Player";
    }
}

