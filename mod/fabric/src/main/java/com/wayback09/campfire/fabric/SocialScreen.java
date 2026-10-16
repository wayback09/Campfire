package com.wayback09.campfire.fabric;

import com.wayback09.campfire.CampfireTheme;
import com.wayback09.campfire.FriendPresence;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.text.Text;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Social & Friends screen for Campfire.
 * Saves and loads friends persistently from campfire/friends.json.
 * Shows live presence status, one-click joins, and direct friend invites.
 */
public final class SocialScreen extends Screen {
    private final Screen parent;
    private final List<String> friends = new ArrayList<>();
    private final Path storagePath;
    private TextFieldWidget addFriendField;
    private String statusMsg = "";

    // Cached presence for the current render pass
    private List<FriendPresence> presenceCache = new ArrayList<>();

    public SocialScreen(Screen parent) {
        super(Text.literal("Campfire Social"));
        this.parent = parent;
        this.storagePath = FabricLoader.getInstance().getGameDir().resolve("campfire/friends.json");
        loadFriends();
    }

    private void loadFriends() {
        friends.clear();
        if (Files.exists(storagePath)) {
            try {
                List<String> lines = Files.readAllLines(storagePath);
                for (String line : lines) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty() && !friends.contains(trimmed)) {
                        friends.add(trimmed);
                    }
                }
            } catch (IOException ignored) {}
        }
    }

    private void saveFriends() {
        try {
            Files.createDirectories(storagePath.getParent());
            Files.write(storagePath, friends);
        } catch (IOException ignored) {}
    }

    @Override
    protected void init() {
        // Refresh live presence from Cloudflare / SocialServer backend
        if (CampfireFabric.presence != null) {
            CompletableFuture.supplyAsync(() -> CampfireFabric.presence.fetchFriendsPresence(friends))
                .thenAccept(list -> presenceCache = list);
        }

        // Friend username input
        addFriendField = new TextFieldWidget(textRenderer, width / 2 - 130, 50, 160, 20, Text.literal("Player Name"));
        addFriendField.setPlaceholder(Text.literal("Minecraft username..."));
        addSelectableChild(addFriendField);

        // Add button with Mojang validation
        addDrawableChild(ButtonWidget.builder(Text.literal("Add Friend"), b -> {
            String name = addFriendField.getText().trim();
            if (name.isEmpty()) return;

            if (friends.contains(name)) {
                statusMsg = name + " is already in your friends list.";
                return;
            }

            statusMsg = "Validating player...";
            // Validate existence against Mojang API asynchronously
            CompletableFuture.runAsync(() -> {
                String validated = validateMinecraftUsername(name);
                if (validated != null) {
                    if (!friends.contains(validated)) {
                        friends.add(validated);
                        saveFriends();
                    }
                    if (client != null) {
                        client.execute(() -> {
                            addFriendField.setText("");
                            statusMsg = "Added " + validated + " to friends!";
                            clearAndInit();
                        });
                    }
                } else {
                    if (client != null) {
                        client.execute(() -> statusMsg = "Player '" + name + "' not found on Minecraft.");
                    }
                }
            });
        }).dimensions(width / 2 + 35, 50, 95, 20).build());

        boolean isUserHosting = CampfireFabric.presence != null
            && CampfireFabric.presence.getCurrent() != null
            && "HOSTING".equals(CampfireFabric.presence.getCurrent().status());

        // Dynamic friend list items
        int listY = 105;
        for (int i = 0; i < friends.size(); i++) {
            if (listY > height - 60) break;
            final String friendName = friends.get(i);
            final int index = i;

            // Check if this friend is hosting
            FriendPresence fp = presenceCache.stream()
                .filter(p -> p.username().equalsIgnoreCase(friendName))
                .findFirst().orElse(null);

            int btnOffset = width / 2 + 130;

            // Remove button [✕]
            addDrawableChild(ButtonWidget.builder(Text.literal("✕"), b -> {
                friends.remove(index);
                saveFriends();
                statusMsg = "Removed " + friendName;
                clearAndInit();
            }).dimensions(btnOffset - 22, listY - 2, 22, 18).build());

            // If friend is hosting -> [▶ Join]
            if (fp != null && fp.isHosting()) {
                addDrawableChild(ButtonWidget.builder(Text.literal("▶ Join"), b -> {
                    if (client != null) {
                        ServerAddress addr = ServerAddress.parse(fp.inviteCode());
                        ServerInfo info = new ServerInfo(friendName + "'s World", addr.getAddress(), ServerInfo.ServerType.OTHER);
                        ConnectScreen.connect(parent, client, addr, info, false, null);
                    }
                }).dimensions(btnOffset - 72, listY - 2, 46, 18).build());
            } else if (isUserHosting) {
                // If local player is hosting -> [✉ Invite]
                addDrawableChild(ButtonWidget.builder(Text.literal("✉ Invite"), b -> {
                    if (CampfireFabric.presence != null && CampfireFabric.presence.getCurrent() != null) {
                        var curr = CampfireFabric.presence.getCurrent();
                        CampfireFabric.presence.sendNetworkInvite(
                            CampfireFabric.getUsername(),
                            friendName,
                            curr.inviteCode(),
                            curr.worldName()
                        );
                        statusMsg = "Sent invite to " + friendName + "!";
                    }
                }).dimensions(btnOffset - 72, listY - 2, 46, 18).build());
            }

            listY += 22;
        }

        // Done button at bottom
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(width / 2 - 100, height - 32, 200, 20).build());
    }

    private static String validateMinecraftUsername(String username) {
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create("https://api.mojang.com/users/profiles/minecraft/" + username).toURL().openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            if (conn.getResponseCode() == 200) {
                String json = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                // Extract official casing: "name":"..."
                int idx = json.indexOf("\"name\":\"");
                if (idx != -1) {
                    int s = idx + 8;
                    int e = json.indexOf("\"", s);
                    if (e != -1) return json.substring(s, e);
                }
                return username;
            }
            conn.disconnect();
        } catch (Exception ignored) {
            // If offline, allow adding by typed name
            return username;
        }
        return null;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);

        ctx.drawCenteredTextWithShadow(textRenderer, "Campfire Friends", width / 2, 22, CampfireTheme.TEXT);

        addFriendField.render(ctx, mouseX, mouseY, delta);

        if (!statusMsg.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, statusMsg, width / 2, 75, CampfireTheme.ACCENT);
        }

        // Friends list header
        int listY = 90;
        ctx.drawTextWithShadow(textRenderer, "Friends (" + friends.size() + "):", width / 2 - 130, listY, CampfireTheme.ACCENT);
        listY += 15;

        if (friends.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, "No friends added yet.", width / 2, height / 2, 0xFFAAAAAA);
            ctx.drawCenteredTextWithShadow(textRenderer, "Enter a player's Minecraft username above to connect!", width / 2, height / 2 + 14, 0xFF888888);
        } else {
            for (String f : friends) {
                if (listY > height - 60) break;

                // Look up presence
                FriendPresence fp = presenceCache.stream()
                    .filter(p -> p.username().equalsIgnoreCase(f))
                    .findFirst().orElse(null);

                String presenceLabel = "💤 In Menu";
                int presenceColor = 0xFF888888;

                if (fp != null) {
                    if (fp.isHosting()) {
                        presenceLabel = "🔥 Hosting: " + (fp.worldName().isBlank() ? "World" : fp.worldName());
                        presenceColor = 0xFFFF7A29;
                    } else if ("SINGLEPLAYER".equalsIgnoreCase(fp.status())) {
                        presenceLabel = "🌿 Playing Singleplayer";
                        presenceColor = 0xFF55FF55;
                    } else if ("OFFLINE".equalsIgnoreCase(fp.status())) {
                        presenceLabel = "💤 Offline";
                        presenceColor = 0xFF666666;
                    } else if ("MULTIPLAYER".equalsIgnoreCase(fp.status())) {
                        presenceLabel = "🖧 In Multiplayer";
                        presenceColor = 0xFF55FFFF;
                    }
                }

                // Card background
                ctx.fill(width / 2 - 130, listY - 2, width / 2 + 130, listY + 18, 0x55000000);

                // Name
                ctx.drawTextWithShadow(textRenderer, "● " + f, width / 2 - 122, listY + 3, CampfireTheme.TEXT);

                // Presence label (right of name)
                ctx.drawTextWithShadow(textRenderer, presenceLabel, width / 2 - 50, listY + 3, presenceColor);

                listY += 22;
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
