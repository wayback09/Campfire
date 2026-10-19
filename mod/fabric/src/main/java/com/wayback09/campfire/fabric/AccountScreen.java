package com.wayback09.campfire.fabric;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.text.Text;

import java.util.UUID;

/**
 * "Your Accounts" modal popup screen for Campfire on Fabric.
 * Displays the current active player session, skin face, active indicator,
 * and account options in a sleek floating dark-mode dialog.
 */
public final class AccountScreen extends Screen {
    private final Screen parent;
    private static final int CARD_WIDTH = 260;
    private static final int CARD_HEIGHT = 150;
    private String feedbackMessage = "";

    public AccountScreen(Screen parent) {
        super(Text.literal("Your Accounts"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cardX = (width - CARD_WIDTH) / 2;
        int cardY = (height - CARD_HEIGHT) / 2;

        // "Add Account" button centered below the account row
        int btnWidth = 140;
        int btnHeight = 22;
        int btnX = cardX + (CARD_WIDTH - btnWidth) / 2;
        int btnY = cardY + 94;

        addDrawableChild(
            ButtonWidget.builder(Text.literal("Add Account"), b -> {
                feedbackMessage = "Managed via your launcher profile";
            }).dimensions(btnX, btnY, btnWidth, btnHeight).build()
        );

        // Close '✕' button in top-right of dialog card
        addDrawableChild(
            ButtonWidget.builder(Text.literal("✕"), b -> close())
                .dimensions(cardX + CARD_WIDTH - 22, cardY + 8, 16, 16)
                .build()
        );
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int cardX = (width - CARD_WIDTH) / 2;
        int cardY = (height - CARD_HEIGHT) / 2;

        // If clicked outside the modal card, dismiss it
        if (mouseX < cardX || mouseX > cardX + CARD_WIDTH || mouseY < cardY || mouseY > cardY + CARD_HEIGHT) {
            close();
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // 1. Render parent screen underneath so the menu/panorama remains visible
        if (parent != null) {
            parent.render(context, -1, -1, delta);
        }

        // 2. Dark translucent backdrop (dimming background)
        context.fill(0, 0, width, height, 0x80000000);

        int cardX = (width - CARD_WIDTH) / 2;
        int cardY = (height - CARD_HEIGHT) / 2;

        // 3. Floating Modal Card background (100% opaque) & border
        context.fill(cardX, cardY, cardX + CARD_WIDTH, cardY + CARD_HEIGHT, 0xFF18181D);
        drawBorder(context, cardX, cardY, CARD_WIDTH, CARD_HEIGHT, 0xFF2F313D);

        // 4. Header title: "Your Accounts"
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Your Accounts"), width / 2, cardY + 16, 0xFFFFFFFF);

        // 5. Account Row Pill
        int rowX = cardX + 16;
        int rowY = cardY + 44;
        int rowWidth = CARD_WIDTH - 32;
        int rowHeight = 34;

        context.fill(rowX, rowY, rowX + rowWidth, rowY + rowHeight, 0xFF141519);
        drawBorder(context, rowX, rowY, rowWidth, rowHeight, 0xFF2C2E38);

        // 6. Player avatar face
        int faceX = rowX + 7;
        int faceY = rowY + 7;
        int faceSize = 20;

        boolean drawnFace = false;
        try {
            if (client != null) {
                GameProfile profile = client.getGameProfile();
                if (profile == null && client.getSession() != null) {
                    UUID uuid = client.getSession().getUuidOrNull();
                    String name = client.getSession().getUsername();
                    profile = new GameProfile(uuid != null ? uuid : UUID.randomUUID(), name);
                }
                UUID uuid = profile != null ? profile.getId() : null;
                SkinTextures skin = SkinHelper.getSkinTextures(client.getSkinProvider(), profile, uuid);
                if (skin != null) {
                    PlayerSkinDrawer.draw(context, skin, faceX, faceY, faceSize);
                    drawnFace = true;
                }
            }
        } catch (Throwable ignored) {}

        if (!drawnFace) {
            // Fallback avatar icon
            context.fill(faceX, faceY, faceX + faceSize, faceY + faceSize, 0xFF2A2D3A);
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("👤"), faceX + faceSize / 2, faceY + 6, 0xFFFFFFFF);
        }

        // 7. Username
        String username = client != null && client.getSession() != null ? client.getSession().getUsername() : "Player";
        context.drawTextWithShadow(textRenderer, Text.literal(username), rowX + 34, rowY + 13, 0xFFFFFFFF);

        // 8. Active Checkmark icon on the right
        int checkX = rowX + rowWidth - 22;
        int checkY = rowY + 13;
        context.drawTextWithShadow(textRenderer, Text.literal("✔"), checkX, checkY, 0xFF38BDF8);

        // 9. Feedback message if user clicked Add Account
        if (!feedbackMessage.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(feedbackMessage), width / 2, cardY + CARD_HEIGHT - 18, 0xFF94A3B8);
        }

        // Render buttons & children on top
        super.render(context, mouseX, mouseY, delta);
    }

    private static void drawBorder(DrawContext context, int x, int y, int width, int height, int color) {
        context.fill(x, y, x + width, y + 1, color);
        context.fill(x, y + height - 1, x + width, y + height, color);
        context.fill(x, y + 1, x + 1, y + height - 1, color);
        context.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }
}
