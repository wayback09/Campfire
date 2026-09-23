package com.wayback09.campfire.fabric;

import com.wayback09.campfire.CampfireConfig;
import com.wayback09.campfire.ScreenshotStore;
import java.nio.file.Files;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

/** Author: wayback09. Indexes vanilla screenshots into the local gallery. */
public final class ScreenshotCapture {
    private ScreenshotCapture() {}

    public static void register(ScreenshotStore store, CampfireConfig config) {
        try {
            Files.createDirectories(store.dir());
        } catch (Exception ignored) {}
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client == null || !config.screenshotPreview) return;
            var dir = MinecraftClient.getInstance().runDirectory.toPath().resolve("screenshots");
            // Lazy index: pick up new files without-hooking the screenshot event (1.21-safe).
            try (var stream = Files.list(dir)) {
                stream.filter(p -> p.toString().endsWith(".png")).forEach(p -> {
                    boolean known = store.list().stream().anyMatch(e -> e.file().equals(p));
                    if (!known) store.add(p);
                });
            } catch (Exception ignored) {}
        });
    }
}
