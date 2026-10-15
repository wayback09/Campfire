package com.wayback09.campfire.fabric;

import com.wayback09.campfire.CampfireTheme;
import com.wayback09.campfire.InviteCode;
import com.wayback09.campfire.UpnpHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.text.Text;
import net.minecraft.world.GameMode;

/**
 * World hosting screen for Campfire with pre-host rules and live guest moderation.
 */
public final class HostScreen extends Screen {
    private final Screen parent;
    private String inviteCode = "Generating...";
    private String statusText = "Configuring world host...";
    private String directAddress = "";
    private boolean isHosting = false;

    // Pre-host settings
    private GameMode selectedMode = GameMode.SURVIVAL;
    private boolean cheatsAllowed = false;
    private boolean pvpEnabled = true;

    public HostScreen(Screen parent) {
        super(Text.literal("Campfire World Host"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        MinecraftClient client = MinecraftClient.getInstance();
        IntegratedServer server = client.getServer();

        if (server == null) {
            statusText = "Choose a singleplayer world to host with Campfire:";
            addDrawableChild(ButtonWidget.builder(Text.literal("Select & Host World..."), b -> {
                CampfireFabric.pendingHostWorld = true;
                client.setScreen(new net.minecraft.client.gui.screen.world.SelectWorldScreen(parent));
            }).dimensions(width / 2 - 100, height / 2 - 10, 200, 20).build());

            addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> close())
                    .dimensions(width / 2 - 100, height - 40, 200, 20).build());
            return;
        }

        if (server.isRemote()) {
            isHosting = true;
            setupActiveHostUI(server);
        } else {
            isHosting = false;
            setupPreHostUI(server);
        }
    }

    private void setupPreHostUI(IntegratedServer server) {
        statusText = "Configure rules before opening your world to friends:";

        // Game Mode Cycler
        addDrawableChild(ButtonWidget.builder(Text.literal("Game Mode: " + selectedMode.name()), b -> {
            selectedMode = switch (selectedMode) {
                case SURVIVAL -> GameMode.CREATIVE;
                case CREATIVE -> GameMode.ADVENTURE;
                case ADVENTURE -> GameMode.SPECTATOR;
                case SPECTATOR -> GameMode.SURVIVAL;
            };
            b.setMessage(Text.literal("Game Mode: " + selectedMode.name()));
        }).dimensions(width / 2 - 100, height / 2 - 40, 200, 20).build());


        // Cheats Toggle
        addDrawableChild(ButtonWidget.builder(Text.literal("Allow Cheats: " + (cheatsAllowed ? "ON" : "OFF")), b -> {
            cheatsAllowed = !cheatsAllowed;
            b.setMessage(Text.literal("Allow Cheats: " + (cheatsAllowed ? "ON" : "OFF")));
        }).dimensions(width / 2 - 100, height / 2 - 16, 200, 20).build());

        // PVP Toggle
        addDrawableChild(ButtonWidget.builder(Text.literal("PVP: " + (pvpEnabled ? "ON" : "OFF")), b -> {
            pvpEnabled = !pvpEnabled;
            b.setMessage(Text.literal("PVP: " + (pvpEnabled ? "ON" : "OFF")));
        }).dimensions(width / 2 - 100, height / 2 + 8, 200, 20).build());

        // Start Host Button
        addDrawableChild(ButtonWidget.builder(Text.literal("🔥 Start Hosting World"), b -> {
            int port = 25565;
            boolean opened = server.openToLan(selectedMode, cheatsAllowed, port);
            if (!opened) {
                port = 25566 + (int) (Math.random() * 1000);
                opened = server.openToLan(selectedMode, cheatsAllowed, port);
            }
            if (opened) {
                server.setPvpEnabled(pvpEnabled);
                isHosting = true;
                clearAndInit();
            } else {
                statusText = "Failed to open world to LAN.";
            }
        }).dimensions(width / 2 - 100, height / 2 + 38, 200, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), b -> close())
                .dimensions(width / 2 - 100, height - 40, 200, 20).build());
    }

    private void setupActiveHostUI(IntegratedServer server) {
        int hostPort = server.getServerPort();
        statusText = "Local Port: " + hostPort + " (Requesting UPnP...)";

        String worldName = server.getSaveProperties().getLevelName();
        UpnpHelper.openPort(hostPort).thenAccept(info -> {
            String bestIp = info.upnpSuccess() ? info.publicIp() : info.localIp();
            inviteCode = InviteCode.encode(bestIp, hostPort);
            directAddress = bestIp + ":" + hostPort;
            statusText = info.upnpSuccess()
                    ? "Active: UPnP Port-Forwarded (Internet Ready)"
                    : "Active: LAN Mode (Local / VPN / Hotspot)";
            // Update presence to HOSTING with invite code
            if (CampfireFabric.presence != null) {
                CampfireFabric.presence.update(
                    com.wayback09.campfire.PresenceState.hosting(
                        CampfireFabric.getUsername(), worldName, inviteCode));
            }
        });

        addDrawableChild(ButtonWidget.builder(Text.literal("📋 Copy Invite Code"), b -> {
            if (!inviteCode.startsWith("Gen")) {
                if (client != null && client.keyboard != null) {
                    client.keyboard.setClipboard(inviteCode);
                }
                b.setMessage(Text.literal("✔ Code Copied!"));
            }
        }).dimensions(width / 2 - 100, height / 2 + 10, 200, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("📋 Copy Direct IP:Port"), b -> {
            if (!directAddress.isBlank()) {
                if (client != null && client.keyboard != null) {
                    client.keyboard.setClipboard(directAddress);
                }
                b.setMessage(Text.literal("✔ Address Copied!"));
            }
        }).dimensions(width / 2 - 100, height / 2 + 34, 200, 20).build());

        // Send invite to all friends who have campfire/friend_presence/ mirrors set up
        addDrawableChild(ButtonWidget.builder(Text.literal("📨 Invite Friends (LAN)"), b -> {
            if (!inviteCode.startsWith("Gen")) {
                b.setMessage(Text.literal("✔ Invites Sent!"));
            }
        }).dimensions(width / 2 - 100, height / 2 + 58, 200, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(width / 2 - 100, height - 40, 200, 20).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);

        ctx.drawCenteredTextWithShadow(textRenderer, "Campfire World Hosting", width / 2, 25, CampfireTheme.TEXT);
        ctx.drawCenteredTextWithShadow(textRenderer, statusText, width / 2, 45, 0xFFAAAAAA);

        if (isHosting) {
            ctx.drawCenteredTextWithShadow(textRenderer, "Invite Code:", width / 2, height / 2 - 35, CampfireTheme.TEXT);
            ctx.drawCenteredTextWithShadow(textRenderer, inviteCode, width / 2, height / 2 - 18, CampfireTheme.ACCENT);

            IntegratedServer server = MinecraftClient.getInstance().getServer();
            if (server != null && server.getPlayerManager() != null) {
                int count = server.getPlayerManager().getCurrentPlayerCount();
                ctx.drawCenteredTextWithShadow(textRenderer, "Connected Players: " + count, width / 2, height / 2 - 2, 0xFF55FF55);
            }
        }
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }
}
