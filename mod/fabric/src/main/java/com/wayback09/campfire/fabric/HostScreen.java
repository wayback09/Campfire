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
 * World hosting modal screen for Campfire on Fabric.
 */
public final class HostScreen extends Screen {
    private final Screen parent;
    private static final int CARD_W = 300;
    private static final int CARD_H = 220;

    private String inviteCode = "Generating...";
    private String statusText = "Configuring world host...";
    private String directAddress = "";
    private boolean isHosting = false;

    private GameMode selectedMode = GameMode.SURVIVAL;
    private boolean cheatsAllowed = false;
    private boolean pvpEnabled = true;
    private boolean upnpResolved = false;

    public HostScreen(Screen parent) {
        this(parent, false);
    }

    public HostScreen(Screen parent, boolean isHosting) {
        super(Text.literal("Campfire World Host"));
        this.parent = parent;
        this.isHosting = isHosting;
    }

    @Override
    protected void init() {
        MinecraftClient client = MinecraftClient.getInstance();
        IntegratedServer server = client.getServer();

        int cardX = (width - CARD_W) / 2;
        int cardY = (height - CARD_H) / 2;

        addDrawableChild(
            CampfireButton.builder(Text.literal("✕"), b -> close())
                .dimensions(cardX + CARD_W - 22, cardY + 8, 16, 16)
                .build()
        );

        if (server == null) {
            statusText = "Choose a singleplayer world to host:";
            addDrawableChild(CampfireButton.builder(Text.literal("Select & Host World..."), b -> {
                CampfireFabric.pendingHostWorld = true;
                client.setScreen(new net.minecraft.client.gui.screen.world.SelectWorldScreen(parent));
            }).dimensions(cardX + (CARD_W - 180) / 2, cardY + 80, 180, 20).build());

            addDrawableChild(CampfireButton.builder(Text.literal("Back"), b -> close())
                    .dimensions(cardX + (CARD_W - 100) / 2, cardY + CARD_H - 26, 100, 18).build());
            return;
        }

        boolean isAlreadyOpen = isHosting || server.isRemote() || server.getServerPort() > 0;
        if (isAlreadyOpen) {
            isHosting = true;
            setupActiveHostUI(server, cardX, cardY);
        } else {
            isHosting = false;
            setupPreHostUI(server, cardX, cardY);
        }
    }

    private void setupPreHostUI(IntegratedServer server, int cardX, int cardY) {
        statusText = "Configure rules before opening your world:";

        int btnW = 190;
        int btnH = 18;
        int btnX = cardX + (CARD_W - btnW) / 2;

        addDrawableChild(CampfireButton.builder(Text.literal("Game Mode: " + selectedMode.name()), b -> {
            selectedMode = switch (selectedMode) {
                case SURVIVAL -> GameMode.CREATIVE;
                case CREATIVE -> GameMode.ADVENTURE;
                case ADVENTURE -> GameMode.SPECTATOR;
                case SPECTATOR -> GameMode.SURVIVAL;
            };
            b.setMessage(Text.literal("Game Mode: " + selectedMode.name()));
        }).dimensions(btnX, cardY + 46, btnW, btnH).build());

        addDrawableChild(CampfireButton.builder(Text.literal("Allow Cheats: " + (cheatsAllowed ? "ON" : "OFF")), b -> {
            cheatsAllowed = !cheatsAllowed;
            b.setMessage(Text.literal("Allow Cheats: " + (cheatsAllowed ? "ON" : "OFF")));
        }).dimensions(btnX, cardY + 68, btnW, btnH).build());

        addDrawableChild(CampfireButton.builder(Text.literal("PVP: " + (pvpEnabled ? "ON" : "OFF")), b -> {
            pvpEnabled = !pvpEnabled;
            b.setMessage(Text.literal("PVP: " + (pvpEnabled ? "ON" : "OFF")));
        }).dimensions(btnX, cardY + 90, btnW, btnH).build());

        addDrawableChild(CampfireButton.builder(Text.literal("🔥 Start Hosting World"), b -> {
            int port = 25565;
            boolean opened = server.openToLan(selectedMode, cheatsAllowed, port);
            if (!opened) {
                port = 25566 + (int) (Math.random() * 1000);
                opened = server.openToLan(selectedMode, cheatsAllowed, port);
            }
            if (opened) {
                try {
                    server.setPvpEnabled(pvpEnabled);
                } catch (Throwable ignored) {}
                this.isHosting = true;
                clearAndInit();
            } else {
                statusText = "Failed to open world to LAN.";
            }
        }).dimensions(btnX, cardY + 120, btnW, 20).build());

        addDrawableChild(CampfireButton.builder(Text.literal("Cancel"), b -> close())
                .dimensions(cardX + (CARD_W - 100) / 2, cardY + CARD_H - 26, 100, 18).build());
    }

    private void setupActiveHostUI(IntegratedServer server, int cardX, int cardY) {
        int hostPort = server.getServerPort();
        statusText = "World is live! Connecting to Campfire Servers...";

        String worldName = server.getSaveProperties().getLevelName();
        String localIp = UpnpHelper.getLocalIp();
        if (inviteCode.startsWith("Gen")) {
            inviteCode = InviteCode.encode(localIp, hostPort);
            directAddress = localIp + ":" + hostPort;
        }

        if (!upnpResolved) {
            upnpResolved = true;
            statusText = "World is live! Connecting to Campfire Servers...";
            com.wayback09.campfire.RelayClient.openTunnel(hostPort).thenAccept(session -> {
                inviteCode = session.code();
                directAddress = session.host() + ":" + session.port();
                statusText = "Connected to Campfire Servers ✔";
                if (CampfireFabric.presence != null) {
                    CampfireFabric.presence.update(
                        com.wayback09.campfire.PresenceState.hosting(
                            CampfireFabric.getUsername(), worldName, inviteCode));
                }
            }).exceptionally(ex -> {
                Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                statusText = "Relay err: " + cause.getMessage();
                UpnpHelper.openPort(hostPort).thenAccept(info -> {
                    String bestIp = info.upnpSuccess() ? info.publicIp() : info.localIp();
                    inviteCode = InviteCode.encode(bestIp, hostPort);
                    directAddress = bestIp + ":" + hostPort;
                    statusText = info.upnpSuccess()
                            ? "Active: UPnP Port-Forwarded"
                            : "Active: LAN Mode (Local / VPN)";
                    if (CampfireFabric.presence != null) {
                        CampfireFabric.presence.update(
                            com.wayback09.campfire.PresenceState.hosting(
                                CampfireFabric.getUsername(), worldName, inviteCode));
                    }
                });
                return null;
            });
        }

        int btnW = 180;
        int btnH = 18;
        int btnX = cardX + (CARD_W - btnW) / 2;

        addDrawableChild(CampfireButton.builder(Text.literal("📋 Copy Invite Code"), b -> {
            if (!inviteCode.startsWith("Gen")) {
                if (client != null && client.keyboard != null) {
                    client.keyboard.setClipboard(inviteCode);
                }
                b.setMessage(Text.literal("✔ Code Copied!"));
            }
        }).dimensions(btnX, cardY + 98, btnW, btnH).build());

        addDrawableChild(CampfireButton.builder(Text.literal("📋 Copy Direct IP:Port"), b -> {
            if (!directAddress.isBlank()) {
                if (client != null && client.keyboard != null) {
                    client.keyboard.setClipboard(directAddress);
                }
                b.setMessage(Text.literal("✔ Address Copied!"));
            }
        }).dimensions(btnX, cardY + 120, btnW, btnH).build());

        addDrawableChild(CampfireButton.builder(Text.literal("📨 Invite All Friends"), b -> {
            if (!inviteCode.startsWith("Gen") && CampfireFabric.presence != null) {
                var gameDir = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir();
                java.nio.file.Path fPath = gameDir.resolve("config").resolve("campfire_friends.txt");
                int sent = 0;
                if (java.nio.file.Files.exists(fPath)) {
                    try {
                        for (String friend : java.nio.file.Files.readAllLines(fPath)) {
                            friend = friend.trim();
                            if (!friend.isEmpty()) {
                                CampfireFabric.presence.sendNetworkInvite(CampfireFabric.getUsername(), friend, inviteCode, worldName);
                                sent++;
                            }
                        }
                    } catch (Exception ignored) {}
                }
                b.setMessage(Text.literal(sent > 0 ? "✔ Sent to " + sent + " Friends!" : "No friends in list"));
            }
        }).dimensions(btnX, cardY + 142, btnW, btnH).build());

        addDrawableChild(CampfireButton.builder(Text.literal("Done"), b -> close())
                .dimensions(cardX + (CARD_W - 100) / 2, cardY + CARD_H - 26, 100, 18).build());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int cardX = (width - CARD_W) / 2;
        int cardY = (height - CARD_H) / 2;
        if (mouseX < cardX || mouseX > cardX + CARD_W || mouseY < cardY || mouseY > cardY + CARD_H) {
            close();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        if (parent != null) {
            parent.render(ctx, -1, -1, delta);
        }

        ctx.fill(0, 0, width, height, 0x80000000);

        int cardX = (width - CARD_W) / 2;
        int cardY = (height - CARD_H) / 2;

        ctx.fill(cardX, cardY, cardX + CARD_W, cardY + CARD_H, 0xFF18181D);
        drawBorder(ctx, cardX, cardY, CARD_W, CARD_H, 0xFF2F313D);

        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("🔥 Campfire World Host"), width / 2, cardY + 12, 0xFFFF9900);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(statusText), width / 2, cardY + 26, 0xFF94A3B8);

        if (isHosting) {
            int pillX = cardX + 16;
            int pillY = cardY + 44;
            int pillW = CARD_W - 32;
            int pillH = 34;

            ctx.fill(pillX, pillY, pillX + pillW, pillY + pillH, 0xFF141519);
            drawBorder(ctx, pillX, pillY, pillW, pillH, 0xFF2C2E38);

            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Invite Code"), width / 2, pillY + 4, 0xFF94A3B8);
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(inviteCode), width / 2, pillY + 16, 0xFFFF9900);

            IntegratedServer server = MinecraftClient.getInstance().getServer();
            if (server != null && server.getPlayerManager() != null) {
                int count = server.getPlayerManager().getCurrentPlayerCount();
                ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Connected: " + count + " player" + (count == 1 ? "" : "s")), width / 2, cardY + 84, 0xFF22C55E);
            }
        }

        super.render(ctx, mouseX, mouseY, delta);
    }

    private static void drawBorder(DrawContext context, int x, int y, int width, int height, int color) {
        context.fill(x, y, x + width, y + 1, color);
        context.fill(x, y + height - 1, x + width, y + height, color);
        context.fill(x, y + 1, x + 1, y + height - 1, color);
        context.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }
}
