package com.wayback09.campfire.neoforge;

import com.wayback09.campfire.CampfireConfig;
import com.wayback09.campfire.PresenceManager;
import com.wayback09.campfire.PresenceState;
import com.wayback09.campfire.ScreenshotStore;
import com.wayback09.campfire.TelemetryManager;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * NeoForge client-only entrypoint for Campfire.
 * Mirrors the functionality of CampfireFabric but uses the NeoForge event bus.
 */
@Mod(CampfireNeoForge.MOD_ID)
public final class CampfireNeoForge {
    public static final String MOD_ID = "campfire";
    public static final CampfireConfig CONFIG = new CampfireConfig();

    public static ScreenshotStore screenshots;
    public static PresenceManager  presence;
    public static TelemetryManager telemetry;
    public static boolean pendingHostWorld = false;

    private int tickCounter = 0;

    public CampfireNeoForge(IEventBus modBus, ModContainer modContainer) {
        var gameDir = FMLPaths.GAMEDIR.get();

        screenshots = new ScreenshotStore(gameDir.resolve("screenshots"));
        presence    = new PresenceManager(gameDir);
        telemetry   = new TelemetryManager(gameDir, CONFIG);

        // Anonymous launch telemetry
        String modVer = "0.1.0";
        try {
            if (modContainer != null && modContainer.getModInfo() != null) {
                modVer = modContainer.getModInfo().getVersion().toString();
            }
        } catch (Throwable ignored) {}

        String mcVer = "1.21.x";
        try {
            var mc = Minecraft.getInstance();
            if (mc != null && mc.getLaunchedVersion() != null) {
                mcVer = mc.getLaunchedVersion();
            }
        } catch (Throwable ignored) {}

        try {
            telemetry.sendLaunch(modVer, mcVer, "neoforge");
        } catch (Throwable ignored) {}

        // Set initial presence to menu
        presence.update(PresenceState.menu(getUsername()));

        // Register client-side events on the global NeoForge event bus
        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(CampfireMenuHooks.class);
    }

    private volatile boolean isPollingInvites = false;

    // ---- Client tick: poll for pending invites asynchronously every ~3 seconds ----
    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        if (++tickCounter < 60) return;
        tickCounter = 0;

        if (isPollingInvites) return;
        isPollingInvites = true;

        java.util.concurrent.CompletableFuture.supplyAsync(() -> presence.pollPendingInvite(getUsername()))
            .thenAccept(invite -> {
                isPollingInvites = false;
                if (invite != null) {
                    var mc = Minecraft.getInstance();
                    mc.execute(() -> NeoForgeInviteToast.show(mc, invite));
                }
            })
            .exceptionally(ex -> {
                isPollingInvites = false;
                return null;
            });
    }

    // ---- Update presence on server join ----
    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        var mc = Minecraft.getInstance();
        if (pendingHostWorld) {
            pendingHostWorld = false;
            mc.execute(() -> {
                if (mc.getSingleplayerServer() != null) {
                    mc.setScreen(new HostScreen(null));
                }
            });
        }
        mc.execute(() -> {
            if (mc.getSingleplayerServer() != null) {
                presence.update(PresenceState.singleplayer(getUsername(),
                    mc.getSingleplayerServer().getWorldData().getLevelName()));
            } else if (mc.getCurrentServer() != null) {
                presence.update(PresenceState.multiplayer(getUsername(),
                    mc.getCurrentServer().name));
            }
        });
    }

    // ---- Reset presence to MENU on disconnect ----
    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        presence.update(PresenceState.menu(getUsername()));
    }

    public static String getUsername() {
        var mc = Minecraft.getInstance();
        return (mc != null && mc.getUser() != null) ? mc.getUser().getName() : "Player";
    }
}
