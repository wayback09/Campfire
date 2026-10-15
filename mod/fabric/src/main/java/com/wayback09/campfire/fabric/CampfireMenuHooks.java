package com.wayback09.campfire.fabric;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Injects the sleek right-hand Campfire sidebar onto TitleScreen and GameMenuScreen (Pause Menu),
 * matching the compact vertical navigation layout without external frameworks.
 */
public final class CampfireMenuHooks {
    private static final int SIDEBAR_WIDTH = 104;
    private static final int BUTTON_HEIGHT = 20;
    private static final int MARGIN_RIGHT = 12;

    private CampfireMenuHooks() {}

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof TitleScreen) {
                injectSidebar(client, screen, scaledWidth, scaledHeight, false);
            } else if (screen instanceof GameMenuScreen) {
                injectSidebar(client, screen, scaledWidth, scaledHeight, true);
            } else if (screen instanceof MultiplayerScreen) {
                // Top-left: "🔥 Join with Code" (doesn't collide with any vanilla buttons)
                Screens.getButtons(screen).add(
                    ButtonWidget.builder(Text.literal("🔥 Join with Code"), b -> {
                        client.setScreen(new JoinScreen(screen));
                    }).dimensions(10, 8, 115, 20).build()
                );

                // Top-right: "⭐ Featured Servers"
                Screens.getButtons(screen).add(
                    ButtonWidget.builder(Text.literal("⭐ Featured Servers"), b -> {
                        client.setScreen(new FeaturedServersScreen(screen));
                    }).dimensions(scaledWidth - 135, 8, 125, 20).build()
                );
            }
        });
    }



    private static void injectSidebar(MinecraftClient client, Screen screen, int width, int height, boolean inGame) {
        int x = width - SIDEBAR_WIDTH - MARGIN_RIGHT;
        int startY = height / 2 - 58;

        // 1. Header / Campfire Brand Profile
        Screens.getButtons(screen).add(
            ButtonWidget.builder(Text.literal("🔥 Campfire"), b -> {
                client.setScreen(new AboutScreen(screen));
            }).dimensions(x, startY, SIDEBAR_WIDTH, BUTTON_HEIGHT).build()
        );

        // 2. Host / Invite
        Text hostText = inGame ? Text.literal("✉ Invite / Host") : Text.literal("🌐 Host World");
        Screens.getButtons(screen).add(
            ButtonWidget.builder(hostText, b -> {
                client.setScreen(new HostScreen(screen));
            }).dimensions(x, startY + 24, SIDEBAR_WIDTH, BUTTON_HEIGHT).build()
        );

        // 3. Social (Friends & DMs)
        Screens.getButtons(screen).add(
            ButtonWidget.builder(Text.literal("👥 Social"), b -> {
                client.setScreen(new SocialScreen(screen));
            }).dimensions(x, startY + 48, SIDEBAR_WIDTH, BUTTON_HEIGHT).build()
        );

        // 4. Pictures Gallery
        Screens.getButtons(screen).add(
            ButtonWidget.builder(Text.literal("📷 Pictures"), b -> {
                client.setScreen(new PicturesScreen(screen, CampfireFabric.screenshots));
            }).dimensions(x, startY + 72, SIDEBAR_WIDTH, BUTTON_HEIGHT).build()
        );

        // 5. Settings
        Screens.getButtons(screen).add(
            ButtonWidget.builder(Text.literal("⚙ Settings"), b -> {
                client.setScreen(CampfireSettings.screen(screen, CampfireFabric.CONFIG));
            }).dimensions(x, startY + 98, SIDEBAR_WIDTH, BUTTON_HEIGHT).build()
        );
    }
}
