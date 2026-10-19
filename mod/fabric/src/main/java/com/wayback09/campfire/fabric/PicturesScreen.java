package com.wayback09.campfire.fabric;

import com.wayback09.campfire.CampfireTheme;
import com.wayback09.campfire.ScreenshotStore;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Interactive in-game Pictures & Screenshots modal for Campfire on Fabric.
 * Sleek floating dark card matching the Campfire modal design system.
 */
public final class PicturesScreen extends Screen {
    private final Screen parent;
    private final ScreenshotStore store;
    private int currentIndex = 0;

    private Identifier currentTextureId = null;
    private NativeImageBackedTexture currentTexture = null;
    private int imageWidth = 0;
    private int imageHeight = 0;
    private String currentFileName = "";

    public PicturesScreen(Screen parent, ScreenshotStore store) {
        super(Text.literal("Campfire Pictures"));
        this.parent = parent;
        this.store = store;
    }

    private int getCardW() {
        return Math.min(width - 32, 460);
    }

    private int getCardH() {
        return Math.min(height - 32, 310);
    }

    @Override
    protected void init() {
        loadCurrentImage();

        int cardW = getCardW();
        int cardH = getCardH();
        int cardX = (width - cardW) / 2;
        int cardY = (height - cardH) / 2;

        // Close '✕' button in top-right of modal card
        addDrawableChild(
            CampfireButton.builder(Text.literal("✕"), b -> close())
                .dimensions(cardX + cardW - 22, cardY + 8, 16, 16)
                .build()
        );

        // Bottom control buttons
        int btnY = cardY + cardH - 28;
        int prevW = 56;
        int copyW = 92;
        int nextW = 56;
        int folderW = 72;
        int doneW = 56;
        int gap = 6;
        int totalW = prevW + copyW + nextW + folderW + doneW + gap * 4;
        int startX = cardX + (cardW - totalW) / 2;

        addDrawableChild(CampfireButton.builder(Text.literal("< Prev"), b -> previousImage())
                .dimensions(startX, btnY, prevW, 18).build());

        addDrawableChild(CampfireButton.builder(Text.literal("📋 Copy Image"), b -> {
            List<ScreenshotStore.Entry> list = store.list();
            if (!list.isEmpty() && currentIndex >= 0 && currentIndex < list.size()) {
                ClipboardHelper.copyImage(list.get(currentIndex).file());
                b.setMessage(Text.literal("✔ Copied!"));
            }
        }).dimensions(startX + prevW + gap, btnY, copyW, 18).build());

        addDrawableChild(CampfireButton.builder(Text.literal("Next >"), b -> nextImage())
                .dimensions(startX + prevW + copyW + gap * 2, btnY, nextW, 18).build());

        addDrawableChild(CampfireButton.builder(Text.literal("📁 Folder"), b -> {
            net.minecraft.util.Util.getOperatingSystem().open(store.dir().toFile());
        }).dimensions(startX + prevW + copyW + nextW + gap * 3, btnY, folderW, 18).build());

        addDrawableChild(CampfireButton.builder(Text.literal("Done"), b -> close())
                .dimensions(startX + prevW + copyW + nextW + folderW + gap * 4, btnY, doneW, 18).build());
    }

    private void previousImage() {
        List<ScreenshotStore.Entry> list = store.list();
        if (list.isEmpty()) return;
        currentIndex--;
        if (currentIndex < 0) {
            currentIndex = list.size() - 1;
        }
        loadCurrentImage();
    }

    private void nextImage() {
        List<ScreenshotStore.Entry> list = store.list();
        if (list.isEmpty()) return;
        currentIndex++;
        if (currentIndex >= list.size()) {
            currentIndex = 0;
        }
        loadCurrentImage();
    }

    private void loadCurrentImage() {
        cleanupTexture();
        List<ScreenshotStore.Entry> list = store.list();
        if (list.isEmpty()) {
            currentFileName = "No screenshots found";
            return;
        }

        if (currentIndex < 0 || currentIndex >= list.size()) {
            currentIndex = 0;
        }

        Path file = list.get(currentIndex).file();
        currentFileName = file.getFileName().toString();

        try (InputStream is = Files.newInputStream(file)) {
            NativeImage image = NativeImage.read(is);
            imageWidth = image.getWidth();
            imageHeight = image.getHeight();
            currentTexture = new NativeImageBackedTexture(() -> "campfire_preview", image);
            currentTextureId = Identifier.of("campfire", "preview_" + System.currentTimeMillis());
            MinecraftClient.getInstance().getTextureManager().registerTexture(currentTextureId, currentTexture);
        } catch (Exception e) {
            cleanupTexture();
            currentFileName = "Error loading: " + file.getFileName();
        }
    }

    private void cleanupTexture() {
        if (currentTextureId != null) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(currentTextureId);
            currentTextureId = null;
        }
        if (currentTexture != null) {
            currentTexture.close();
            currentTexture = null;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_A) {
            previousImage();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_D) {
            nextImage();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int cardW = getCardW();
        int cardH = getCardH();
        int cardX = (width - cardW) / 2;
        int cardY = (height - cardH) / 2;
        if (mouseX < cardX || mouseX > cardX + cardW || mouseY < cardY || mouseY > cardY + cardH) {
            close();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // 1. Render parent underneath
        if (parent != null) {
            parent.render(ctx, -1, -1, delta);
        }

        // 2. Translucent backdrop
        ctx.fill(0, 0, width, height, 0x80000000);

        int cardW = getCardW();
        int cardH = getCardH();
        int cardX = (width - cardW) / 2;
        int cardY = (height - cardH) / 2;

        // 3. Floating Modal Card background & border
        ctx.fill(cardX, cardY, cardX + cardW, cardY + cardH, 0xFF18181D);
        drawBorder(ctx, cardX, cardY, cardW, cardH, 0xFF2F313D);

        // 4. Header title & counter
        List<ScreenshotStore.Entry> list = store.list();
        String counter = list.isEmpty() ? "" : " (" + (currentIndex + 1) + " of " + list.size() + ")";
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("🖼 Campfire Pictures" + counter), width / 2, cardY + 10, 0xFFFF9900);

        // Subtitle filename
        String truncatedName = currentFileName;
        if (textRenderer.getWidth(truncatedName) > cardW - 60) {
            truncatedName = textRenderer.trimToWidth(truncatedName, cardW - 70) + "...";
        }
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(truncatedName), width / 2, cardY + 22, 0xFF94A3B8);

        // 5. Image preview box area
        int previewAreaX = cardX + 16;
        int previewAreaY = cardY + 36;
        int previewAreaW = cardW - 32;
        int previewAreaH = cardH - 72;

        ctx.fill(previewAreaX, previewAreaY, previewAreaX + previewAreaW, previewAreaY + previewAreaH, 0xFF141519);
        drawBorder(ctx, previewAreaX, previewAreaY, previewAreaW, previewAreaH, 0xFF2C2E38);

        if (currentTextureId != null && imageWidth > 0 && imageHeight > 0) {
            float aspect = (float) imageWidth / imageHeight;
            int drawW = previewAreaW - 4;
            int drawH = (int) (drawW / aspect);

            if (drawH > previewAreaH - 4) {
                drawH = previewAreaH - 4;
                drawW = (int) (drawH * aspect);
            }

            int drawX = previewAreaX + (previewAreaW - drawW) / 2;
            int drawY = previewAreaY + (previewAreaH - drawH) / 2;

            ctx.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, currentTextureId, drawX, drawY, 0.0f, 0.0f, drawW, drawH, drawW, drawH);
        } else if (list.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("No screenshots in folder yet."), width / 2, previewAreaY + previewAreaH / 2 - 8, 0xFF94A3B8);
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Press F2 in-game to capture screenshots!"), width / 2, previewAreaY + previewAreaH / 2 + 6, 0xFF64748B);
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
        cleanupTexture();
        if (client != null) {
            client.setScreen(parent);
        }
    }
}
