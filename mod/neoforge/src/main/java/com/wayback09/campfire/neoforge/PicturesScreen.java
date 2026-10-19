package com.wayback09.campfire.neoforge;

import com.wayback09.campfire.CampfireTheme;
import com.wayback09.campfire.ScreenshotStore;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Interactive in-game Photos App for Minecraft screenshots (NeoForge).
 */
public final class PicturesScreen extends Screen {
    private final Screen parent;
    private final ScreenshotStore store;
    private int currentIndex = 0;

    private ResourceLocation currentTextureId = null;
    private DynamicTexture currentTexture = null;
    private int imageWidth = 0;
    private int imageHeight = 0;
    private String currentFileName = "";

    public PicturesScreen(Screen parent, ScreenshotStore store) {
        super(Component.literal("Campfire Pictures"));
        this.parent = parent;
        this.store = store;
    }

    @Override
    protected void init() {
        loadCurrentImage();

        int bottomY = this.height - 32;

        addRenderableWidget(Button.builder(Component.literal("< Previous"), b -> previousImage())
                .bounds(this.width / 2 - 160, bottomY - 26, 75, 20).build());

        addRenderableWidget(Button.builder(Component.literal("📋 Copy Image"), b -> {
            List<ScreenshotStore.Entry> list = store.list();
            if (!list.isEmpty() && currentIndex >= 0 && currentIndex < list.size()) {
                ClipboardHelper.copyImage(list.get(currentIndex).file());
                b.setMessage(Component.literal("✔ Copied!"));
            }
        }).bounds(this.width / 2 - 50, bottomY - 26, 100, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Next >"), b -> nextImage())
                .bounds(this.width / 2 + 85, bottomY - 26, 75, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Open folder"), b -> {
            Util.getPlatform().openFile(store.dir().toFile());
        }).bounds(this.width / 2 - 105, bottomY, 100, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(this.width / 2 + 5, bottomY, 100, 20).build());
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
            currentTexture = new DynamicTexture(image);
            currentTextureId = ResourceLocation.fromNamespaceAndPath("campfire", "preview_" + System.currentTimeMillis());
            Minecraft.getInstance().getTextureManager().register(currentTextureId, currentTexture);
        } catch (Exception e) {
            cleanupTexture();
            currentFileName = "Error loading image: " + file.getFileName();
        }
    }

    private void cleanupTexture() {
        if (currentTextureId != null) {
            Minecraft.getInstance().getTextureManager().release(currentTextureId);
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
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        List<ScreenshotStore.Entry> list = store.list();
        String counter = list.isEmpty() ? "" : " (" + (currentIndex + 1) + " of " + list.size() + ")";
        guiGraphics.drawCenteredString(this.font, "Campfire Pictures" + counter, this.width / 2, 12, CampfireTheme.TEXT);
        guiGraphics.drawCenteredString(this.font, currentFileName, this.width / 2, 26, CampfireTheme.ACCENT);

        int maxPreviewW = this.width - 80;
        int maxPreviewH = this.height - 100;

        if (currentTextureId != null && imageWidth > 0 && imageHeight > 0) {
            float aspect = (float) imageWidth / imageHeight;
            int drawW = maxPreviewW;
            int drawH = (int) (drawW / aspect);

            if (drawH > maxPreviewH) {
                drawH = maxPreviewH;
                drawW = (int) (drawH * aspect);
            }

            int drawX = this.width / 2 - drawW / 2;
            int drawY = 40 + (maxPreviewH - drawH) / 2;

            guiGraphics.fill(drawX - 2, drawY - 2, drawX + drawW + 2, drawY + drawH + 2, 0xFF000000);
            GuiCompat.drawTexture(guiGraphics, currentTextureId, drawX, drawY, drawW, drawH);
        } else if (list.isEmpty()) {
            guiGraphics.drawCenteredString(this.font, "No screenshots in directory yet.", this.width / 2, this.height / 2 - 10, 0xFFAAAAAA);
            guiGraphics.drawCenteredString(this.font, "Take screenshots in-game using F2!", this.width / 2, this.height / 2 + 5, 0xFF888888);
        }
    }

    @Override
    public void onClose() {
        cleanupTexture();
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }
}
