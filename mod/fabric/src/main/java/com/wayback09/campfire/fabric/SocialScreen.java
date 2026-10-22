package com.wayback09.campfire.fabric;

import com.mojang.authlib.GameProfile;
import com.wayback09.campfire.CampfireJson;
import com.wayback09.campfire.CampfireTheme;
import com.wayback09.campfire.FriendPresence;
import com.wayback09.campfire.InviteCode;
import com.wayback09.campfire.RelayClient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.text.Text;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Social & Friends modal dialog for Campfire on Fabric.
 * Sleek floating dark card matching the Campfire modal design system.
 */
public final class SocialScreen extends Screen {
    private final Screen parent;
    private static final int CARD_W = 340;
    private static final int CARD_H = 250;

    private final List<String> friends = new ArrayList<>();
    private final Path storagePath;
    private TextFieldWidget addFriendField;
    private String statusMsg = "";

    // Cached presence for the current render pass
    private List<FriendPresence> presenceCache = new ArrayList<>();

    // Tabs: 0 = Friends, 1 = Requests
    private int activeTab = 0;

    // Pending friend requests (sent by us)
    private final List<String> sentRequests = new ArrayList<>();
    // Incoming requests (received from others)
    private final Path requestsPath;
    private final List<String> incomingRequests = new ArrayList<>();

    public SocialScreen(Screen parent) {
        super(Text.literal("Campfire Social"));
        this.parent = parent;
        this.storagePath = FabricLoader.getInstance().getGameDir().resolve("campfire/friends.json");
        this.requestsPath = FabricLoader.getInstance().getGameDir().resolve("campfire/requests.json");
        loadFriends();
        loadRequests();
    }

    private void loadFriends() {
        friends.clear();
        if (Files.exists(storagePath)) {
            try {
                for (String line : Files.readAllLines(storagePath, StandardCharsets.UTF_8)) {
                    String t = line.trim();
                    if (!t.isEmpty() && !friends.contains(t)) friends.add(t);
                }
            } catch (IOException ignored) {}
        }
    }

    private void saveFriends() {
        try {
            Files.createDirectories(storagePath.getParent());
            Files.write(storagePath, friends, StandardCharsets.UTF_8);
        } catch (IOException ignored) {}
    }

    private void loadRequests() {
        incomingRequests.clear();
        sentRequests.clear();
        if (Files.exists(requestsPath)) {
            try {
                for (String line : Files.readAllLines(requestsPath, StandardCharsets.UTF_8)) {
                    String t = line.trim();
                    if (t.startsWith("IN:")) incomingRequests.add(t.substring(3));
                    else if (t.startsWith("OUT:")) sentRequests.add(t.substring(4));
                }
            } catch (IOException ignored) {}
        }
    }

    private void saveRequests() {
        try {
            Files.createDirectories(requestsPath.getParent());
            List<String> lines = new ArrayList<>();
            for (String r : incomingRequests) lines.add("IN:" + r);
            for (String r : sentRequests) lines.add("OUT:" + r);
            Files.write(requestsPath, lines, StandardCharsets.UTF_8);
        } catch (IOException ignored) {}
    }

    @Override
    protected void init() {
        if (CampfireFabric.presence != null) {
            CompletableFuture.supplyAsync(() -> CampfireFabric.presence.fetchFriendsPresence(friends))
                .thenAccept(list -> presenceCache = list);
        }

        int cardX = (width - CARD_W) / 2;
        int cardY = (height - CARD_H) / 2;

        // Close '✕' button in top-right of modal card
        addDrawableChild(
            CampfireButton.builder(Text.literal("✕"), b -> close())
                .dimensions(cardX + CARD_W - 22, cardY + 8, 16, 16)
                .build()
        );

        // Tabs
        int tabW = 120;
        int tabH = 18;
        int tabY = cardY + 28;
        int tabStartX = cardX + (CARD_W - (tabW * 2 + 8)) / 2;

        addDrawableChild(CampfireButton.builder(Text.literal("👥 Friends"), b -> {
            activeTab = 0;
            clearAndInit();
        }).dimensions(tabStartX, tabY, tabW, tabH).build());

        int reqCount = incomingRequests.size() + sentRequests.size();
        String reqLabel = "📨 Requests" + (reqCount > 0 ? " (" + reqCount + ")" : "");
        addDrawableChild(CampfireButton.builder(Text.literal(reqLabel), b -> {
            activeTab = 1;
            clearAndInit();
        }).dimensions(tabStartX + tabW + 8, tabY, tabW, tabH).build());

        if (activeTab == 0) {
            initFriendsTab(cardX, cardY);
        } else {
            initRequestsTab(cardX, cardY);
        }

        // Bottom Done button
        int doneW = 100;
        int doneH = 18;
        addDrawableChild(
            CampfireButton.builder(Text.literal("Done"), b -> close())
                .dimensions(cardX + (CARD_W - doneW) / 2, cardY + CARD_H - 26, doneW, doneH)
                .build()
        );
    }

    private void initFriendsTab(int cardX, int cardY) {
        int inputY = cardY + 52;
        int fieldW = 190;
        int fieldH = 18;

        addFriendField = new TextFieldWidget(textRenderer, cardX + 16, inputY, fieldW, fieldH, Text.literal("Player Name"));
        addFriendField.setPlaceholder(Text.literal("Minecraft username..."));
        addSelectableChild(addFriendField);

        int addBtnW = 110;
        addDrawableChild(CampfireButton.builder(Text.literal("Add Friend"), b -> {
            String name = addFriendField.getText().trim();
            if (name.isEmpty()) return;

            if (friends.contains(name)) {
                statusMsg = name + " is already in friends.";
                return;
            }
            if (sentRequests.contains(name)) {
                statusMsg = "Request already sent to " + name;
                return;
            }

            statusMsg = "Validating player...";
            CompletableFuture.runAsync(() -> {
                String validated = validateMinecraftUsername(name);
                if (validated != null) {
                    if (!sentRequests.contains(validated)) {
                        sentRequests.add(validated);
                        saveRequests();
                    }
                    if (client != null) {
                        client.execute(() -> {
                            addFriendField.setText("");
                            statusMsg = "Request sent to " + validated;
                            clearAndInit();
                        });
                    }
                } else {
                    if (client != null) {
                        client.execute(() -> statusMsg = "Player '" + name + "' not found.");
                    }
                }
            });
        }).dimensions(cardX + 16 + fieldW + 8, inputY, addBtnW, fieldH).build());

        boolean isUserHosting = CampfireFabric.presence != null
            && CampfireFabric.presence.getCurrent() != null
            && "HOSTING".equals(CampfireFabric.presence.getCurrent().status());

        // Friend list items
        int listY = cardY + (statusMsg.isEmpty() ? 76 : 84);
        int maxListY = cardY + CARD_H - 34;

        for (int i = 0; i < friends.size(); i++) {
            if (listY + 20 > maxListY) break;
            final String friendName = friends.get(i);
            final int index = i;

            FriendPresence fp = presenceCache.stream()
                .filter(p -> p.username().equalsIgnoreCase(friendName))
                .findFirst().orElse(null);

            int rowRight = cardX + CARD_W - 16;

            addDrawableChild(CampfireButton.builder(Text.literal("✕"), b -> {
                friends.remove(index);
                saveFriends();
                statusMsg = "Removed " + friendName;
                clearAndInit();
            }).dimensions(rowRight - 18, listY + 1, 18, 16).build());

            if (fp != null && fp.isHosting()) {
                addDrawableChild(CampfireButton.builder(Text.literal("▶ Join"), b -> {
                    if (client != null) {
                        String code = fp.inviteCode();
                        if (InviteCode.isRelayCode(code)) {
                            String relayCode = InviteCode.extractRelayCode(code);
                            com.wayback09.campfire.RelayClient.startGuestForwarder(relayCode).thenAccept(target -> {
                                client.execute(() -> {
                                    ServerAddress addr = new ServerAddress(target.getHostString(), target.getPort());
                                    ServerInfo info = new ServerInfo(friendName + "'s World", addr.getAddress(), ServerInfo.ServerType.OTHER);
                                    ConnectScreen.connect(parent, client, addr, info, false, null);
                                });
                            });
                        } else {
                            ServerAddress addr = ServerAddress.parse(code);
                            ServerInfo info = new ServerInfo(friendName + "'s World", addr.getAddress(), ServerInfo.ServerType.OTHER);
                            ConnectScreen.connect(parent, client, addr, info, false, null);
                        }
                    }
                }).dimensions(rowRight - 64, listY + 1, 42, 16).build());
            } else if (isUserHosting) {
                addDrawableChild(CampfireButton.builder(Text.literal("✉ Invite"), b -> {
                    if (CampfireFabric.presence != null && CampfireFabric.presence.getCurrent() != null) {
                        var curr = CampfireFabric.presence.getCurrent();
                        CampfireFabric.presence.sendNetworkInvite(
                            CampfireFabric.getUsername(), friendName, curr.inviteCode(), curr.worldName());
                        statusMsg = "Sent invite to " + friendName;
                    }
                }).dimensions(rowRight - 64, listY + 1, 42, 16).build());
            }

            listY += 22;
        }
    }

    private void initRequestsTab(int cardX, int cardY) {
        int listY = cardY + 54;
        int maxListY = cardY + CARD_H - 34;
        int rowRight = cardX + CARD_W - 16;

        // Incoming requests
        for (int i = 0; i < incomingRequests.size(); i++) {
            if (listY + 20 > maxListY) break;
            final String requester = incomingRequests.get(i);
            final int idx = i;

            addDrawableChild(CampfireButton.builder(Text.literal("✔ Accept"), b -> {
                incomingRequests.remove(idx);
                if (!friends.contains(requester)) {
                    friends.add(requester);
                    saveFriends();
                }
                saveRequests();
                statusMsg = "Added " + requester + "!";
                clearAndInit();
            }).dimensions(rowRight - 76, listY + 1, 54, 16).build());

            addDrawableChild(CampfireButton.builder(Text.literal("✕"), b -> {
                incomingRequests.remove(idx);
                saveRequests();
                statusMsg = "Declined " + requester;
                clearAndInit();
            }).dimensions(rowRight - 18, listY + 1, 18, 16).build());

            listY += 22;
        }

        listY += 14;

        for (int i = 0; i < sentRequests.size(); i++) {
            if (listY + 20 > maxListY) break;
            final String pending = sentRequests.get(i);
            final int idx = i;

            addDrawableChild(CampfireButton.builder(Text.literal("✕ Cancel"), b -> {
                sentRequests.remove(idx);
                saveRequests();
                statusMsg = "Cancelled request to " + pending;
                clearAndInit();
            }).dimensions(rowRight - 54, listY + 1, 54, 16).build());

            listY += 22;
        }
    }

    private static String validateMinecraftUsername(String username) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) URI.create(
                    "https://api.mojang.com/users/profiles/minecraft/" + username).toURL().openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            if (conn.getResponseCode() == 200) {
                String json;
                try (java.io.InputStream in = conn.getInputStream()) {
                    json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
                String name = CampfireJson.getString(CampfireJson.parseObjectOrEmpty(json), "name", "");
                return name.isBlank() ? username : name;
            }
            // 204 / 404 means the account does not exist.
            return username;
        } catch (Exception ignored) {
            return username;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();

        int cardX = (width - CARD_W) / 2;
        int cardY = (height - CARD_H) / 2;
        if (mouseX < cardX || mouseX > cardX + CARD_W || mouseY < cardY || mouseY > cardY + CARD_H) {
            close();
            return true;
        }
        return super.mouseClicked(click, doubled);
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

        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("🔥 Campfire Social"), width / 2, cardY + 12, 0xFFFF9900);

        // Tab underline indicator
        int tabW = 120;
        int tabY = cardY + 28;
        int tabStartX = cardX + (CARD_W - (tabW * 2 + 8)) / 2;
        int underlineX = tabStartX + activeTab * (tabW + 8);
        ctx.fill(underlineX, tabY + 18, underlineX + tabW, tabY + 19, CampfireTheme.ACCENT);

        if (!statusMsg.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(statusMsg), width / 2, cardY + 74, CampfireTheme.ACCENT);
        }

        if (activeTab == 0) {
            renderFriendsTab(ctx, cardX, cardY);
        } else {
            renderRequestsTab(ctx, cardX, cardY);
        }

        super.render(ctx, mouseX, mouseY, delta);
    }

    private void renderFriendsTab(DrawContext ctx, int cardX, int cardY) {
        if (addFriendField != null) addFriendField.render(ctx, 0, 0, 0);

        int listY = cardY + (statusMsg.isEmpty() ? 76 : 84);
        int maxListY = cardY + CARD_H - 34;

        if (friends.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("No friends yet. Add one above!"), width / 2, cardY + 130, 0xFF94A3B8);
        } else {
            for (String f : friends) {
                if (listY + 20 > maxListY) break;

                FriendPresence fp = presenceCache.stream()
                    .filter(p -> p.username().equalsIgnoreCase(f))
                    .findFirst().orElse(null);

                String presenceLabel = "💤 Offline";
                int presenceColor = 0xFF64748B;

                if (fp != null) {
                    if (fp.isHosting()) {
                        presenceLabel = "🔥 Hosting";
                        presenceColor = 0xFFFF7A29;
                    } else if ("SINGLEPLAYER".equalsIgnoreCase(fp.status())) {
                        presenceLabel = "🌿 Solo";
                        presenceColor = 0xFF22C55E;
                    } else if ("MULTIPLAYER".equalsIgnoreCase(fp.status())) {
                        presenceLabel = "🖥 Playing";
                        presenceColor = 0xFF38BDF8;
                    }
                }

                int rowW = CARD_W - 32;
                ctx.fill(cardX + 16, listY, cardX + 16 + rowW, listY + 18, 0xFF141519);
                drawBorder(ctx, cardX + 16, listY, rowW, 18, 0xFF2C2E38);

                renderUserHead(ctx, f, cardX + 18, listY + 1, 16);

                ctx.drawTextWithShadow(textRenderer, Text.literal(f), cardX + 38, listY + 5, 0xFFFFFFFF);
                ctx.drawTextWithShadow(textRenderer, Text.literal(presenceLabel), cardX + 130, listY + 5, presenceColor);

                listY += 22;
            }
        }
    }

    private void renderRequestsTab(DrawContext ctx, int cardX, int cardY) {
        int listY = cardY + 54;
        int maxListY = cardY + CARD_H - 34;
        int rowW = CARD_W - 32;

        if (incomingRequests.isEmpty() && sentRequests.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("No pending requests."), width / 2, cardY + 120, 0xFF94A3B8);
            return;
        }

        // Incoming
        if (!incomingRequests.isEmpty()) {
            for (String req : incomingRequests) {
                if (listY + 20 > maxListY) break;
                ctx.fill(cardX + 16, listY, cardX + 16 + rowW, listY + 18, 0xFF141519);
                drawBorder(ctx, cardX + 16, listY, rowW, 18, 0xFF2C2E38);

                renderUserHead(ctx, req, cardX + 18, listY + 1, 16);
                ctx.drawTextWithShadow(textRenderer, Text.literal(req), cardX + 38, listY + 5, 0xFFFFFFFF);
                listY += 22;
            }
        }

        // Sent
        if (!sentRequests.isEmpty()) {
            listY += 4;
            ctx.drawTextWithShadow(textRenderer, Text.literal("Sent (" + sentRequests.size() + "):"), cardX + 18, listY - 2, 0xFF94A3B8);
            listY += 10;

            for (String req : sentRequests) {
                if (listY + 20 > maxListY) break;
                ctx.fill(cardX + 16, listY, cardX + 16 + rowW, listY + 18, 0xFF141519);
                drawBorder(ctx, cardX + 16, listY, rowW, 18, 0xFF2C2E38);

                renderUserHead(ctx, req, cardX + 18, listY + 1, 16);
                ctx.drawTextWithShadow(textRenderer, Text.literal(req), cardX + 38, listY + 5, 0xFFCBD5E1);
                listY += 22;
            }
        }
    }

    private void renderUserHead(DrawContext ctx, String username, int x, int y, int size) {
        boolean drawn = false;
        try {
            if (client != null && client.getSkinProvider() != null) {
                GameProfile profile = null;
                if (client.getGameProfile() != null && username.equalsIgnoreCase(client.getGameProfile().name())) {
                    profile = client.getGameProfile();
                } else {
                    UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
                    profile = new GameProfile(uuid, username);
                }
                SkinTextures skin = SkinHelper.getSkinTextures(client.getSkinProvider(), profile, profile.id());
                if (skin != null) {
                    PlayerSkinDrawer.draw(ctx, skin, x, y, size);
                    drawn = true;
                }
            }
        } catch (Throwable ignored) {}

        if (!drawn) {
            ctx.fill(x, y, x + size, y + size, 0xFF2A2D3A);
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("👤"), x + size / 2, y + 4, 0xFFFFFFFF);
        }
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
