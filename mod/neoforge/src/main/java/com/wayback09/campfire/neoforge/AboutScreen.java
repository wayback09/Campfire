package com.wayback09.campfire.neoforge;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;

import java.util.UUID;

/**
 * Modern card modal for Campfire Profile & Information on NeoForge.
 */
public final class AboutScreen extends Screen {
    private final Screen parent;
    private static final int CARD_W = 280;
    private static final int CARD_H = 190;

    public AboutScreen(Screen parent) {
        super(Component.literal("Campfire Profile"));
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
        int btnY = cardY + CARD_H - 30;

        addRenderableWidget(Button.builder(Component.literal("Settings  ⚙"), b -> {
            if (minecraft != null) {
                minecraft.setScreen(CampfireSettings.screen(this, CampfireNeoForge.CONFIG));
            }
        }).bounds(cardX + (CARD_W - (btnW * 2 + gap)) / 2, btnY, btnW, btnH).build());

        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
            .bounds(cardX + (CARD_W - (btnW * 2 + gap)) / 2 + btnW + gap, btnY, btnW, btnH).build());

        // Close '✕' button in top-right of dialog card
        addRenderableWidget(
            Button.builder(Component.literal("✕"), b -> onClose())
                .bounds(cardX + CARD_W - 22, cardY + 8, 16, 16)
                .build()
        );
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int cardX = (width - CARD_W) / 2;
        int cardY = (height - CARD_H) / 2;

        if (mouseX < cardX || mouseX > cardX + CARD_W || mouseY < cardY || mouseY > cardY + CARD_H) {
            onClose();
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        // 1. Render parent screen underneath
        if (parent != null) {
            parent.render(graphics, -1, -1, delta);
        }

        // 2. Dim background
        graphics.fill(0, 0, width, height, 0x80000000);

        int cardX = (width - CARD_W) / 2;
        int cardY = (height - CARD_H) / 2;

        // 3. Card container & border
        graphics.fill(cardX, cardY, cardX + CARD_W, cardY + CARD_H, 0xFF18181D);
        drawBorder(graphics, cardX, cardY, CARD_W, CARD_H, 0xFF2F313D);

        // 4. Header title: 🔥 Campfire
        GuiCompat.drawCenteredString(graphics, font, "🔥 Campfire", width / 2, cardY + 14, 0xFFFF9900);

        String mcVer = "1.21.x";
        try {
            mcVer = net.neoforged.fml.loading.FMLLoader.versionInfo().mcVersion();
        } catch (Throwable ignored) {}

        GuiCompat.drawCenteredString(graphics, font, "v0.1.0 • NeoForge (" + mcVer + ")", width / 2, cardY + 27, 0xFF94A3B8);

        // 5. User Profile Card — 32px centred head, username & Online badge centred below
        int userRowX = cardX + 16;
        int userRowY = cardY + 44;
        int userRowW = CARD_W - 32;
        int userRowH = 80;

        graphics.fill(userRowX, userRowY, userRowX + userRowW, userRowY + userRowH, 0xFF141519);
        drawBorder(graphics, userRowX, userRowY, userRowW, userRowH, 0xFF2C2E38);

        int faceSize = 32;
        int faceX = userRowX + (userRowW - faceSize) / 2;
        int faceY = userRowY + 8;

        boolean drawnFace = false;
        try {
            if (minecraft != null && minecraft.getUser() != null) {
                UUID uuid = minecraft.getUser().getProfileId() != null ? minecraft.getUser().getProfileId() : UUID.randomUUID();
                String name = minecraft.getUser().getName();
                GameProfile profile = new GameProfile(uuid, name);
                PlayerSkin skin = minecraft.getSkinManager().getInsecureSkin(profile);
                if (skin != null) {
                    PlayerFaceRenderer.draw(graphics, skin, faceX, faceY, faceSize);
                    drawnFace = true;
                }
            }
        } catch (Throwable ignored) {}

        if (!drawnFace) {
            graphics.fill(faceX, faceY, faceX + faceSize, faceY + faceSize, 0xFF2A2D3A);
            GuiCompat.drawCenteredString(graphics, font, "👤", faceX + faceSize / 2, faceY + 10, 0xFFFFFFFF);
        }

        // Username & Online status centred below head
        String username = minecraft != null && minecraft.getUser() != null ? minecraft.getUser().getName() : "Player";
        int textCx = userRowX + userRowW / 2;
        GuiCompat.drawCenteredString(graphics, font, username, textCx, faceY + faceSize + 4, 0xFFFFFFFF);
        GuiCompat.drawCenteredString(graphics, font, "● Online", textCx, faceY + faceSize + 16, 0xFF22C55E);

        // Render buttons & widgets
        super.render(graphics, mouseX, mouseY, delta);
    }

    private static void drawBorder(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y + 1, x + 1, y + height - 1, color);
        graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }
}
