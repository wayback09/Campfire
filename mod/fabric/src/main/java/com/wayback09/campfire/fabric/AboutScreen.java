package com.wayback09.campfire.fabric;

import com.mojang.authlib.GameProfile;
import com.wayback09.campfire.CampfireTheme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.text.Text;

import java.util.UUID;

/**
 * Modern card modal for Campfire Profile & Information on Fabric.
 */
public final class AboutScreen extends Screen {
    private final Screen parent;
    private static final int CARD_W = 280;
    private static final int CARD_H = 210;

    public AboutScreen(Screen parent) {
        super(Text.literal("Campfire Profile"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cardX = (width - CARD_W) / 2;
        int cardY = (height - CARD_H) / 2;

        // Bottom action buttons inside card
        int btnW = 110;
        int btnH = 20;
        int gap = 12;
        int btnY = cardY + CARD_H - 32;

        addDrawableChild(ButtonWidget.builder(Text.literal("Settings  ⚙"), b -> {
            if (client != null) {
                client.setScreen(CampfireSettings.screen(this, CampfireFabric.CONFIG));
            }
        }).dimensions(cardX + (CARD_W - (btnW * 2 + gap)) / 2, btnY, btnW, btnH).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
            .dimensions(cardX + (CARD_W - (btnW * 2 + gap)) / 2 + btnW + gap, btnY, btnW, btnH).build());

        // Close '✕' button in top-right of dialog card
        addDrawableChild(
            ButtonWidget.builder(Text.literal("✕"), b -> close())
                .dimensions(cardX + CARD_W - 22, cardY + 8, 16, 16)
                .build()
        );
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
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // 1. Render parent screen underneath
        if (parent != null) {
            parent.render(context, -1, -1, delta);
        }

        // 2. Dim background
        context.fill(0, 0, width, height, 0x80000000);

        int cardX = (width - CARD_W) / 2;
        int cardY = (height - CARD_H) / 2;

        // 3. Card container & border
        context.fill(cardX, cardY, cardX + CARD_W, cardY + CARD_H, 0xFF18181D);
        drawBorder(context, cardX, cardY, CARD_W, CARD_H, 0xFF2F313D);

        // 4. Header title: 🔥 Campfire
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("🔥 Campfire"), width / 2, cardY + 16, 0xFFFF9900);

        String modVer = net.fabricmc.loader.api.FabricLoader.getInstance()
            .getModContainer("campfire")
            .map(c -> c.getMetadata().getVersion().getFriendlyString())
            .orElse("0.1.0");
        String mcVer = net.fabricmc.loader.api.FabricLoader.getInstance()
            .getModContainer("minecraft")
            .map(c -> c.getMetadata().getVersion().getFriendlyString())
            .orElse("1.21.x");

        context.drawCenteredTextWithShadow(textRenderer, Text.literal("v" + modVer + " • Fabric (" + mcVer + ")"), width / 2, cardY + 30, 0xFF94A3B8);

        // 5. User Profile Card
        int userRowX = cardX + 16;
        int userRowY = cardY + 50;
        int userRowW = CARD_W - 32;
        int userRowH = 36;

        context.fill(userRowX, userRowY, userRowX + userRowW, userRowY + userRowH, 0xFF141519);
        drawBorder(context, userRowX, userRowY, userRowW, userRowH, 0xFF2C2E38);

        // Avatar
        int faceX = userRowX + 8;
        int faceY = userRowY + 8;
        int faceSize = 20;

        boolean drawnFace = false;
        try {
            if (client != null && client.getSession() != null) {
                UUID uuid = client.getSession().getUuidOrNull();
                String name = client.getSession().getUsername();
                GameProfile profile = new GameProfile(uuid != null ? uuid : UUID.randomUUID(), name);
                SkinTextures skin = client.getSkinProvider().getSkinTextures(profile);
                if (skin != null) {
                    PlayerSkinDrawer.draw(context, skin, faceX, faceY, faceSize);
                    drawnFace = true;
                }
            }
        } catch (Throwable ignored) {}

        if (!drawnFace) {
            context.fill(faceX, faceY, faceX + faceSize, faceY + faceSize, 0xFF2A2D3A);
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("👤"), faceX + faceSize / 2, faceY + 6, 0xFFFFFFFF);
        }

        String username = client != null && client.getSession() != null ? client.getSession().getUsername() : "Player";
        context.drawTextWithShadow(textRenderer, Text.literal(username), userRowX + 36, userRowY + 9, 0xFFFFFFFF);
        context.drawTextWithShadow(textRenderer, Text.literal("● Online"), userRowX + 36, userRowY + 21, 0xFF22C55E);

        // 6. Feature highlights
        int featureY = userRowY + userRowH + 16;
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Peer-to-Peer World Hosting"), width / 2, featureY, 0xFFCBD5E1);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Friends & Social • Instant Gallery"), width / 2, featureY + 14, 0xFF94A3B8);

        // Render buttons & widgets
        super.render(context, mouseX, mouseY, delta);
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
