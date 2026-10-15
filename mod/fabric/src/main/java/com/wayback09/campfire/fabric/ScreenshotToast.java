package com.wayback09.campfire.fabric;

import java.nio.file.Path;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.Text;

public final class ScreenshotToast {
    private ScreenshotToast() {}

    public static void show(MinecraftClient client, Path path) {
        if (client == null || client.getToastManager() == null) return;

        // Auto-copy image to clipboard on capture!
        ClipboardHelper.copyImage(path);

        String fileName = path.getFileName().toString();
        Text title = Text.literal("🔥 Screenshot Copied!");
        Text description = Text.literal(fileName + " (Press F7 for Gallery)");

        SystemToast.show(client.getToastManager(), SystemToast.Type.NARRATOR_TOGGLE, title, description);
    }
}
