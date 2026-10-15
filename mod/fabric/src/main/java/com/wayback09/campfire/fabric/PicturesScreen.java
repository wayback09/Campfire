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
 * Interactive in-game Photos App for Minecraft screenshots.
 * Renders actual screenshot textures, supports Previous/Next browsing and arrow keys,
 * with clean bottom button controls.
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

    @Override
    protected void init() {
        loadCurrentImage();

        int bottomY = height - 32;

        // Navigation buttons: Previous (<), Copy Image, and Next (>)
        addDrawableChild(ButtonWidget.builder(Text.literal("< Previous"), b -> previousImage())
                .dimensions(width / 2 - 160, bottomY - 26, 75, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("📋 Copy Image"), b -> {
            List<ScreenshotStore.Entry> list = store.list();
            if (!list.isEmpty() && currentIndex >= 0 && currentIndex < list.size()) {
                ClipboardHelper.copyImage(list.get(currentIndex).file());
                b.setMessage(Text.literal("✔ Copied!"));
            }
        }).dimensions(width / 2 - 50, bottomY - 26, 100, 20).build());


        addDrawableChild(ButtonWidget.builder(Text.literal("Next >"), b -> nextImage())
                .dimensions(width / 2 + 85, bottomY - 26, 75, 20).build());


        // Both action buttons at the bottom side-by-side
        addDrawableChild(ButtonWidget.builder(Text.literal("Open folder"), b -> {
            net.minecraft.util.Util.getOperatingSystem().open(store.dir().toFile());
        }).dimensions(width / 2 - 105, bottomY, 100, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(width / 2 + 5, bottomY, 100, 20).build());
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
            currentFileName = "Error loading image: " + file.getFileName();
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
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);

        List<ScreenshotStore.Entry> list = store.list();
        String counter = list.isEmpty() ? "" : " (" + (currentIndex + 1) + " of " + list.size() + ")";
        ctx.drawCenteredTextWithShadow(textRenderer, "Campfire Pictures" + counter, width / 2, 12, CampfireTheme.TEXT);
        ctx.drawCenteredTextWithShadow(textRenderer, currentFileName, width / 2, 26, CampfireTheme.ACCENT);

        // Center preview box area
        int maxPreviewW = width - 80;
        int maxPreviewH = height - 100;

        if (currentTextureId != null && imageWidth > 0 && imageHeight > 0) {
            float aspect = (float) imageWidth / imageHeight;
            int drawW = maxPreviewW;
            int drawH = (int) (drawW / aspect);

            if (drawH > maxPreviewH) {
                drawH = maxPreviewH;
                drawW = (int) (drawH * aspect);
            }

            int drawX = width / 2 - drawW / 2;
            int drawY = 40 + (maxPreviewH - drawH) / 2;

            // Draw dark backdrop card
            ctx.fill(drawX - 2, drawY - 2, drawX + drawW + 2, drawY + drawH + 2, 0xFF000000);

            // Draw screenshot texture
            ctx.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, currentTextureId, drawX, drawY, 0.0f, 0.0f, drawW, drawH, drawW, drawH);
        } else if (list.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, "No screenshots in directory yet.", width / 2, height / 2 - 10, 0xFFAAAAAA);
            ctx.drawCenteredTextWithShadow(textRenderer, "Take screenshots in-game using F2!", width / 2, height / 2 + 5, 0xFF888888);
        }
    }

    @Override
    public void close() {
        cleanupTexture();
        if (client != null) {
            client.setScreen(parent);
        }
    }
}
