package com.wayback09.campfire.neoforge;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Injects the Campfire sidebar, top-left user badge, and top-right brand pill
 * onto TitleScreen and PauseScreen (in-game menu) for NeoForge with responsive
 * collapse support for reduced/compact window dimensions.
 */
public final class CampfireMenuHooks {
    private static final int SIDEBAR_WIDTH = 104;
    private static final int BUTTON_HEIGHT = 20;
    private static final int MARGIN_RIGHT = 12;

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        Minecraft mc = Minecraft.getInstance();

        if (screen instanceof TitleScreen) {
            injectMenu(event, mc, screen, screen.width, screen.height, false);
        } else if (screen instanceof PauseScreen) {
            injectMenu(event, mc, screen, screen.width, screen.height, true);
        }
    }

    private static void injectMenu(ScreenEvent.Init.Post event, Minecraft mc, Screen screen, int width, int height, boolean inGame) {
        int availableRight = width - (width / 2 + 100);
        boolean compact = (availableRight < 135) || (width < 520);
        String username = CampfireNeoForge.getUsername();

        // Dynamically align with the top-most center menu button (e.g. Singleplayer or Return to Game)
        int startY = inGame ? (height / 4 + 24) : (height / 4 + 48);
        for (net.minecraft.client.gui.components.events.GuiEventListener child : screen.children()) {
            if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget) {
                int centerX = widget.getX() + widget.getWidth() / 2;
                if (Math.abs(centerX - width / 2) < 25 && widget.getY() >= 40 && widget.getY() < startY) {
                    startY = widget.getY();
                }
            }
        }

        if (compact) {
            // === COMPACT MODE (Window reduced, small screens) ===
            // 1. Top-Left: Compact 20x20 avatar icon
            event.addListener(
                Button.builder(Component.literal("👤"), b ->
                    mc.setScreen(new AccountScreen(screen))
                ).bounds(8, 8, 20, BUTTON_HEIGHT)
                 .tooltip(Tooltip.create(Component.literal("Accounts: " + username)))
                 .build()
            );

            // 2. Top-Right: Compact 20x20 brand icon
            event.addListener(
                Button.builder(Component.literal("🔥"), b ->
                    mc.setScreen(new AboutScreen(screen))
                ).bounds(width - 20 - 8, 8, 20, BUTTON_HEIGHT)
                 .tooltip(Tooltip.create(Component.literal("Campfire")))
                 .build()
            );

            // 3. Right Sidebar: 20x20 square icon-only buttons along right edge
            int x = width - 20 - 6;

            // Row 1: Host / Invite
            Component hostTooltip = inGame ? Component.literal("Invite / Host World") : Component.literal("Host World");
            Component hostIcon = inGame ? Component.literal("✉") : Component.literal("🌐");
            event.addListener(
                Button.builder(hostIcon, b ->
                    mc.setScreen(new HostScreen(screen))
                ).bounds(x, startY, 20, BUTTON_HEIGHT)
                 .tooltip(Tooltip.create(hostTooltip))
                 .build()
            );

            // Row 2: Social
            event.addListener(
                Button.builder(Component.literal("👥"), b ->
                    mc.setScreen(new SocialScreen(screen))
                ).bounds(x, startY + 24, 20, BUTTON_HEIGHT)
                 .tooltip(Tooltip.create(Component.literal("Social & Friends")))
                 .build()
            );

            // Row 3: Pictures
            event.addListener(
                Button.builder(Component.literal("🖼"), b ->
                    mc.setScreen(new PicturesScreen(screen, CampfireNeoForge.screenshots))
                ).bounds(x, startY + 48, 20, BUTTON_HEIGHT)
                 .tooltip(Tooltip.create(Component.literal("Pictures & Gallery")))
                 .build()
            );

            // Row 4: Settings
            event.addListener(
                Button.builder(Component.literal("⚙"), b ->
                    mc.setScreen(CampfireSettings.screen(screen, CampfireNeoForge.CONFIG))
                ).bounds(x, startY + 72, 20, BUTTON_HEIGHT)
                 .tooltip(Tooltip.create(Component.literal("Campfire Settings")))
                 .build()
            );

            // Row 5: Screenshots folder
            event.addListener(
                Button.builder(Component.literal("📁"), b -> {
                    if (CampfireNeoForge.screenshots != null) {
                        Util.getPlatform().openFile(CampfireNeoForge.screenshots.dir().toFile());
                    }
                }).bounds(x, startY + 96, 20, BUTTON_HEIGHT)
                 .tooltip(Tooltip.create(Component.literal("Open Screenshots Folder")))
                 .build()
            );
        } else {
            // === EXPANDED MODE (Wider screens) ===
            // 1. Top-Left: Player profile pill ("👤 Username")
            int userBtnWidth = Math.max(76, Math.min(120, mc.font.width("👤 " + username) + 16));
            event.addListener(
                Button.builder(Component.literal("👤 " + username), b ->
                    mc.setScreen(new AccountScreen(screen))
                ).bounds(10, 10, userBtnWidth, BUTTON_HEIGHT)
                 .tooltip(Tooltip.create(Component.literal("Accounts: " + username)))
                 .build()
            );

            // 2. Top-Right: Campfire brand pill ("🔥 Campfire")
            int brandWidth = 84;
            event.addListener(
                Button.builder(Component.literal("🔥 Campfire"), b ->
                    mc.setScreen(new AboutScreen(screen))
                ).bounds(width - brandWidth - 10, 10, brandWidth, BUTTON_HEIGHT)
                 .tooltip(Tooltip.create(Component.literal("Campfire")))
                 .build()
            );

            // 3. Right Sidebar — aligned with main menu options
            int x = width - SIDEBAR_WIDTH - MARGIN_RIGHT;

            // Row 1: Host / Invite
            Component hostText = inGame ? Component.literal("Invite / Host  ✉") : Component.literal("Host World  🌐");
            event.addListener(
                Button.builder(hostText, b ->
                    mc.setScreen(new HostScreen(screen))
                ).bounds(x, startY, SIDEBAR_WIDTH, BUTTON_HEIGHT)
                 .tooltip(Tooltip.create(inGame ? Component.literal("Invite / Host World") : Component.literal("Host World")))
                 .build()
            );

            // Row 2: Social
            event.addListener(
                Button.builder(Component.literal("Social  👥"), b ->
                    mc.setScreen(new SocialScreen(screen))
                ).bounds(x, startY + 24, SIDEBAR_WIDTH, BUTTON_HEIGHT)
                 .tooltip(Tooltip.create(Component.literal("Social & Friends")))
                 .build()
            );

            // Row 3: Pictures
            event.addListener(
                Button.builder(Component.literal("Pictures  🖼"), b ->
                    mc.setScreen(new PicturesScreen(screen, CampfireNeoForge.screenshots))
                ).bounds(x, startY + 48, SIDEBAR_WIDTH, BUTTON_HEIGHT)
                 .tooltip(Tooltip.create(Component.literal("Pictures & Gallery")))
                 .build()
            );

            // Row 4: Split [ 📁 ] [ Settings  ⚙ ]
            int folderBtnWidth = 20;
            int settingsBtnWidth = SIDEBAR_WIDTH - folderBtnWidth - 4; // 104 - 20 - 4 = 80px

            event.addListener(
                Button.builder(Component.literal("📁"), b -> {
                    if (CampfireNeoForge.screenshots != null) {
                        Util.getPlatform().openFile(CampfireNeoForge.screenshots.dir().toFile());
                    }
                }).bounds(x, startY + 72, folderBtnWidth, BUTTON_HEIGHT)
                 .tooltip(Tooltip.create(Component.literal("Open Screenshots Folder")))
                 .build()
            );

            event.addListener(
                Button.builder(Component.literal("Settings  ⚙"), b ->
                    mc.setScreen(CampfireSettings.screen(screen, CampfireNeoForge.CONFIG))
                ).bounds(x + folderBtnWidth + 4, startY + 72, settingsBtnWidth, BUTTON_HEIGHT)
                 .tooltip(Tooltip.create(Component.literal("Campfire Settings")))
                 .build()
            );
        }
    }
}
