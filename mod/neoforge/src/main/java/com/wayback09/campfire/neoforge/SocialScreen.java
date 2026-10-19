package com.wayback09.campfire.neoforge;

import com.wayback09.campfire.CampfireTheme;
import com.wayback09.campfire.FriendPresence;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.FMLPaths;

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
 * Social & Friends screen for Campfire (NeoForge).
 */
public final class SocialScreen extends Screen {
    private final Screen parent;
    private final List<String> friends = new ArrayList<>();
    private final Path storagePath;
    private EditBox addFriendField;
    private String statusMsg = "";
    private List<FriendPresence> presenceCache = new ArrayList<>();

    public SocialScreen(Screen parent) {
        super(Component.literal("Campfire Social"));
        this.parent = parent;
        this.storagePath = FMLPaths.GAMEDIR.get().resolve("campfire/friends.json");
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
        if (CampfireNeoForge.presence != null) {
            CompletableFuture.supplyAsync(() -> CampfireNeoForge.presence.fetchFriendsPresence(friends))
                .thenAccept(list -> presenceCache = list);
        }

        addFriendField = new EditBox(this.font, this.width / 2 - 130, 50, 160, 20, Component.literal("Player Name"));
        addFriendField.setHint(Component.literal("Minecraft username..."));
        addRenderableWidget(addFriendField);

        addRenderableWidget(Button.builder(Component.literal("Add Friend"), b -> {
            String name = addFriendField.getValue().trim();
            if (name.isEmpty()) return;

            if (friends.contains(name)) {
                statusMsg = name + " is already in your friends list.";
                return;
            }

            statusMsg = "Validating player...";
            CompletableFuture.runAsync(() -> {
                String validated = validateMinecraftUsername(name);
                if (validated != null) {
                    if (!friends.contains(validated)) {
                        friends.add(validated);
                        saveFriends();
                    }
                    if (this.minecraft != null) {
                        this.minecraft.execute(() -> {
                            addFriendField.setValue("");
                            statusMsg = "Added " + validated + " to friends!";
                            rebuildWidgets();
                        });
                    }
                } else {
                    if (this.minecraft != null) {
                        this.minecraft.execute(() -> statusMsg = "Player '" + name + "' not found on Minecraft.");
                    }
                }
            });
        }).bounds(this.width / 2 + 35, 50, 95, 20).build());

        boolean isUserHosting = CampfireNeoForge.presence != null
            && CampfireNeoForge.presence.getCurrent() != null
            && "HOSTING".equals(CampfireNeoForge.presence.getCurrent().status());

        int listY = 105;
        for (int i = 0; i < friends.size(); i++) {
            if (listY > this.height - 60) break;
            final String friendName = friends.get(i);
            final int index = i;

            FriendPresence fp = presenceCache.stream()
                .filter(p -> p.username().equalsIgnoreCase(friendName))
                .findFirst().orElse(null);

            int btnOffset = this.width / 2 + 130;

            addRenderableWidget(Button.builder(Component.literal("✕"), b -> {
                friends.remove(index);
                saveFriends();
                statusMsg = "Removed " + friendName;
                rebuildWidgets();
            }).bounds(btnOffset - 22, listY - 2, 22, 18).build());

            if (fp != null && fp.isHosting()) {
                addRenderableWidget(Button.builder(Component.literal("▶ Join"), b -> {
                    if (this.minecraft != null) {
                        ServerAddress addr = ServerAddress.parseString(fp.inviteCode());
                        ServerData info = new ServerData(friendName + "'s World", addr.getHost(), ServerData.Type.OTHER);
                        ConnectScreen.startConnecting(parent, this.minecraft, addr, info, false, null);
                    }
                }).bounds(btnOffset - 72, listY - 2, 46, 18).build());
            } else if (isUserHosting) {
                addRenderableWidget(Button.builder(Component.literal("✉ Invite"), b -> {
                    if (CampfireNeoForge.presence != null && CampfireNeoForge.presence.getCurrent() != null) {
                        var curr = CampfireNeoForge.presence.getCurrent();
                        CampfireNeoForge.presence.sendNetworkInvite(
                            CampfireNeoForge.getUsername(),
                            friendName,
                            curr.inviteCode(),
                            curr.worldName()
                        );
                        b.setMessage(Component.literal("✔ Sent!"));
                        b.active = false;
                    }
                }).bounds(btnOffset - 72, listY - 2, 46, 18).build());
            }

            listY += 24;
        }

        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(this.width / 2 - 50, this.height - 35, 100, 20).build());
    }

    private static String validateMinecraftUsername(String username) {
        try {
            URI uri = URI.create("https://api.mojang.com/users/profiles/minecraft/" + username);
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            if (conn.getResponseCode() == 200) {
                String json = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                int nameIdx = json.indexOf("\"name\": \"");
                if (nameIdx != -1) {
                    int start = nameIdx + 9;
                    int end = json.indexOf("\"", start);
                    if (end != -1) return json.substring(start, end);
                }
                return username;
            }
        } catch (Exception ignored) {}
        return null;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.drawCenteredString(this.font, "👥 Campfire Social", this.width / 2, 15, CampfireTheme.TEXT);
        guiGraphics.drawCenteredString(this.font, "Add friends to see their status, receive invites, and join worlds.", this.width / 2, 30, 0xFFAAAAAA);

        if (!statusMsg.isEmpty()) {
            int color = statusMsg.startsWith("Added") ? 0xFF55FF55 : (statusMsg.startsWith("Removed") ? 0xFFFFAA00 : 0xFFFF5555);
            guiGraphics.drawCenteredString(this.font, statusMsg, this.width / 2, 75, color);
        }

        GuiCompat.drawString(guiGraphics, this.font, "Friends (" + friends.size() + "):", this.width / 2 - 130, 92, CampfireTheme.ACCENT);

        int listY = 105;
        if (friends.isEmpty()) {
            GuiCompat.drawString(guiGraphics, this.font, "No friends added yet. Type a username above!", this.width / 2 - 130, listY + 5, 0xFF888888);
        } else {
            for (String friend : friends) {
                if (listY > this.height - 60) break;
                FriendPresence fp = presenceCache.stream()
                    .filter(p -> p.username().equalsIgnoreCase(friend))
                    .findFirst().orElse(null);

                String status = (fp != null) ? fp.status() : "OFFLINE";
                int statusColor = 0xFF888888;
                String displayStatus = "Offline";

                if ("HOSTING".equals(status)) {
                    statusColor = 0xFF55FF55;
                    displayStatus = "Hosting \"" + (fp.worldName() != null ? fp.worldName() : "World") + "\"";
                } else if ("ONLINE".equals(status) || "PLAYING".equals(status) || "SINGLEPLAYER".equals(status) || "MULTIPLAYER".equals(status)) {
                    statusColor = 0xFF55FFFF;
                    displayStatus = "In Menu / Playing";
                }

                GuiCompat.drawString(guiGraphics, this.font, friend, this.width / 2 - 130, listY + 3, CampfireTheme.TEXT);
                GuiCompat.drawString(guiGraphics, this.font, "• " + displayStatus, this.width / 2 - 30, listY + 3, statusColor);

                listY += 24;
            }
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }
}
