package com.wayback09.campfire.neoforge;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Method;

/**
 * Cross-version GUI rendering helpers that bridge binary signature differences
 * between Minecraft 1.21.1 and 1.21.11+.
 */
public final class GuiCompat {
    private static Method blitModern = null;
    private static Method blitLegacy = null;

    static {
        try {
            blitModern = GuiGraphics.class.getMethod("blit", ResourceLocation.class, int.class, int.class, int.class, int.class, float.class, float.class, float.class, float.class);
        } catch (Throwable ignored) {}
        try {
            blitLegacy = GuiGraphics.class.getMethod("blit", ResourceLocation.class, int.class, int.class, float.class, float.class, int.class, int.class, int.class, int.class);
        } catch (Throwable ignored) {}
    }

    private GuiCompat() {}

    /**
     * Left-aligned text drawing compatible across all Minecraft 1.21.x releases.
     * Delegates to drawCenteredString with offset to preserve the void return type.
     */
    public static void drawString(GuiGraphics g, Font font, String text, int x, int y, int color) {
        if (text == null || text.isEmpty()) return;
        int w = font.width(text);
        g.drawCenteredString(font, text, x + w / 2, y, color);
    }

    /**
     * Centered text drawing compatible across all Minecraft 1.21.x releases.
     */
    public static void drawCenteredString(GuiGraphics g, Font font, String text, int x, int y, int color) {
        if (text == null || text.isEmpty()) return;
        g.drawCenteredString(font, text, x, y, color);
    }

    /**
     * Full-quad texture rendering compatible across 1.21.1 and 1.21.11+ pipelines.
     */
    public static void drawTexture(GuiGraphics g, ResourceLocation texture, int x, int y, int width, int height) {
        if (blitModern != null) {
            try {
                blitModern.invoke(g, texture, x, y, width, height, 0.0f, 0.0f, 1.0f, 1.0f);
                return;
            } catch (Throwable ignored) {}
        }
        if (blitLegacy != null) {
            try {
                blitLegacy.invoke(g, texture, x, y, 0.0f, 0.0f, width, height, width, height);
            } catch (Throwable ignored) {}
        }
    }
}
