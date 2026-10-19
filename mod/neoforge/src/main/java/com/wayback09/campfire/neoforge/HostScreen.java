package com.wayback09.campfire.neoforge;

import com.wayback09.campfire.CampfireTheme;
import com.wayback09.campfire.InviteCode;
import com.wayback09.campfire.UpnpHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;

/**
 * World hosting screen for Campfire with pre-host rules and live guest moderation (NeoForge).
 */
public final class HostScreen extends Screen {
    private final Screen parent;
    private String inviteCode = "Generating...";
    private String statusText = "Configuring world host...";
    private String directAddress = "";
    private boolean isHosting = false;

    // Pre-host settings
    private GameType selectedMode = GameType.SURVIVAL;
    private boolean cheatsAllowed = false;
    private boolean pvpEnabled = true;
    private boolean upnpResolved = false;

    public HostScreen(Screen parent) {
        this(parent, false);
    }

    public HostScreen(Screen parent, boolean isHosting) {
        super(Component.literal("Campfire World Host"));
        this.parent = parent;
        this.isHosting = isHosting;
    }

    @Override
    protected void init() {
        Minecraft client = Minecraft.getInstance();
        IntegratedServer server = client.getSingleplayerServer();

        if (server == null) {
            statusText = "Choose a singleplayer world to host with Campfire:";
            addRenderableWidget(Button.builder(Component.literal("Select & Host World..."), b -> {
                CampfireNeoForge.pendingHostWorld = true;
                client.setScreen(new SelectWorldScreen(parent));
            }).bounds(this.width / 2 - 100, this.height / 2 - 10, 200, 20).build());

            addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
                    .bounds(this.width / 2 - 100, this.height - 40, 200, 20).build());
            return;
        }

        boolean isAlreadyOpen = isHosting || server.isPublished() || server.getPort() > 0;
        if (isAlreadyOpen) {
            isHosting = true;
            setupActiveHostUI(server);
        } else {
            isHosting = false;
            setupPreHostUI(server);
        }
    }

    private void setupPreHostUI(IntegratedServer server) {
        statusText = "Configure rules before opening your world to friends:";

        addRenderableWidget(Button.builder(Component.literal("Game Mode: " + selectedMode.getName()), b -> {
            selectedMode = switch (selectedMode) {
                case SURVIVAL -> GameType.CREATIVE;
                case CREATIVE -> GameType.ADVENTURE;
                case ADVENTURE -> GameType.SPECTATOR;
                case SPECTATOR -> GameType.SURVIVAL;
            };
            b.setMessage(Component.literal("Game Mode: " + selectedMode.getName()));
        }).bounds(this.width / 2 - 100, this.height / 2 - 40, 200, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Allow Cheats: " + (cheatsAllowed ? "ON" : "OFF")), b -> {
            cheatsAllowed = !cheatsAllowed;
            b.setMessage(Component.literal("Allow Cheats: " + (cheatsAllowed ? "ON" : "OFF")));
        }).bounds(this.width / 2 - 100, this.height / 2 - 16, 200, 20).build());

        addRenderableWidget(Button.builder(Component.literal("PVP: " + (pvpEnabled ? "ON" : "OFF")), b -> {
            pvpEnabled = !pvpEnabled;
            b.setMessage(Component.literal("PVP: " + (pvpEnabled ? "ON" : "OFF")));
        }).bounds(this.width / 2 - 100, this.height / 2 + 8, 200, 20).build());

        addRenderableWidget(Button.builder(Component.literal("🔥 Start Hosting World"), b -> {
            int port = 25565;
            boolean opened = server.publishServer(selectedMode, cheatsAllowed, port);
            if (!opened) {
                port = 25566 + (int) (Math.random() * 1000);
                opened = server.publishServer(selectedMode, cheatsAllowed, port);
            }
            if (opened) {
                try {
                    server.setPvpAllowed(pvpEnabled);
                } catch (Throwable ignored) {}
                this.isHosting = true;
                this.rebuildWidgets();
            } else {
                statusText = "Failed to open world to LAN.";
            }
        }).bounds(this.width / 2 - 100, this.height / 2 + 38, 200, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                .bounds(this.width / 2 - 100, this.height - 40, 200, 20).build());
    }

    private void setupActiveHostUI(IntegratedServer server) {
        int hostPort = server.getPort();
        statusText = "World is live! Connecting to Campfire Servers...";

        String worldName = server.getWorldData().getLevelName();
        // Immediately generate a local invite code so code is never "Generating..."
        String localIp = UpnpHelper.getLocalIp();
        if (inviteCode.startsWith("Gen")) {
            inviteCode = InviteCode.encode(localIp, hostPort);
            directAddress = localIp + ":" + hostPort;
        }

        // Connect to Cloud Relay first, fallback to UPnP if unreachable
        if (!upnpResolved) {
            upnpResolved = true;
            statusText = "World is live! Connecting to Campfire Servers...";
            com.wayback09.campfire.RelayClient.openTunnel(hostPort).thenAccept(session -> {
                inviteCode = session.code();
                directAddress = session.host() + ":" + session.port();
                statusText = "Connected to Campfire Servers ✔";
                if (CampfireNeoForge.presence != null) {
                    CampfireNeoForge.presence.update(
                        com.wayback09.campfire.PresenceState.hosting(
                            CampfireNeoForge.getUsername(), worldName, inviteCode));
                }
            }).exceptionally(ex -> {
                UpnpHelper.openPort(hostPort).thenAccept(info -> {
                    String bestIp = info.upnpSuccess() ? info.publicIp() : info.localIp();
                    inviteCode = InviteCode.encode(bestIp, hostPort);
                    directAddress = bestIp + ":" + hostPort;
                    statusText = info.upnpSuccess()
                            ? "Active: UPnP Port-Forwarded (Internet Ready)"
                            : "Active: LAN Mode (Local / VPN / Hotspot)";
                    if (CampfireNeoForge.presence != null) {
                        CampfireNeoForge.presence.update(
                            com.wayback09.campfire.PresenceState.hosting(
                                CampfireNeoForge.getUsername(), worldName, inviteCode));
                    }
                });
                return null;
            });
        }

        addRenderableWidget(Button.builder(Component.literal("📋 Copy Invite Code"), b -> {
            if (!inviteCode.startsWith("Gen")) {
                if (this.minecraft != null && this.minecraft.keyboardHandler != null) {
                    this.minecraft.keyboardHandler.setClipboard(inviteCode);
                }
                b.setMessage(Component.literal("✔ Code Copied!"));
            }
        }).bounds(this.width / 2 - 100, this.height / 2 + 10, 200, 20).build());

        addRenderableWidget(Button.builder(Component.literal("📋 Copy Direct IP:Port"), b -> {
            if (!directAddress.isBlank()) {
                if (this.minecraft != null && this.minecraft.keyboardHandler != null) {
                    this.minecraft.keyboardHandler.setClipboard(directAddress);
                }
                b.setMessage(Component.literal("✔ Address Copied!"));
            }
        }).bounds(this.width / 2 - 100, this.height / 2 + 36, 200, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Back to Game"), b -> onClose())
                .bounds(this.width / 2 - 100, this.height - 40, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.drawCenteredString(this.font, "🔥 Campfire World Host", this.width / 2, 20, CampfireTheme.TEXT);
        guiGraphics.drawCenteredString(this.font, statusText, this.width / 2, 40, isHosting ? 0xFF55FF55 : CampfireTheme.ACCENT);

        if (isHosting) {
            guiGraphics.drawCenteredString(this.font, "Share this code with your friends so they can join instantly:", this.width / 2, this.height / 2 - 25, 0xFFAAAAAA);
            guiGraphics.drawCenteredString(this.font, inviteCode, this.width / 2, this.height / 2 - 10, CampfireTheme.ACCENT);
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }
}
