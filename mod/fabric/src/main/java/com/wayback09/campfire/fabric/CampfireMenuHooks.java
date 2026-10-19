package com.wayback09.campfire.fabric;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerServerListWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

/**
 * Injects the Campfire sidebar onto TitleScreen and GameMenuScreen, and wires
 * the Join with Code + Featured filter toggle onto MultiplayerScreen.
 *
 * Featured servers are injected into the vanilla server list via BEFORE_INIT
 * so they appear natively in the multiplayer screen list.
 * Featured partner servers are protected: Edit and Delete buttons are locked/disabled
 * whenever a featured server is selected.
 */
public final class CampfireMenuHooks {
    private static final int SIDEBAR_WIDTH = 104;
    private static final int BUTTON_HEIGHT = 20;
    private static final int MARGIN_RIGHT = 12;

    /** When true, the vanilla list shows only featured servers. */
    public static boolean filterFeaturedOnly = false;

    private CampfireMenuHooks() {}

    public static void register() {
        // BEFORE_INIT: write featured servers to servers.dat right before MultiplayerScreen
        // initializes and reads servers.dat from disk.
        ScreenEvents.BEFORE_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof MultiplayerScreen) {
                FeaturedServerInjector.syncToServerList(client);
            }
        });

        // AFTER_INIT: add buttons and sidebar
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof TitleScreen) {
                // Pre-seed servers.dat on title screen so it is ready before user clicks multiplayer
                FeaturedServerInjector.syncToServerList(client);
                injectSidebar(client, screen, scaledWidth, scaledHeight, false);
            } else if (screen instanceof GameMenuScreen) {
                injectSidebar(client, screen, scaledWidth, scaledHeight, true);
            } else if (screen instanceof MultiplayerScreen ms) {
                injectMultiplayerButtons(client, ms, scaledWidth);

                // Lock Edit and Delete buttons whenever a featured partner server is selected
                ScreenEvents.afterRender(screen).register((scr, drawContext, mouseX, mouseY, delta) -> {
                    if (scr instanceof MultiplayerScreen currentMs) {
                        lockFeaturedButtons(currentMs);
                    }
                });

                // Prevent Delete key from removing a featured server
                ScreenKeyboardEvents.allowKeyPress(screen).register((scr, key, scancode, modifiers) -> {
                    if (key == 261 && scr instanceof MultiplayerScreen currentMs) { // 261 = GLFW_KEY_DELETE
                        return !isFeaturedSelected(currentMs);
                    }
                    return true;
                });
            }
        });
    }

    private static boolean isFeaturedSelected(MultiplayerScreen ms) {
        for (Element child : ms.children()) {
            if (child instanceof MultiplayerServerListWidget widget) {
                MultiplayerServerListWidget.Entry entry = widget.getSelectedOrNull();
                if (entry instanceof MultiplayerServerListWidget.ServerEntry se) {
                    return FeaturedServerInjector.isFeatured(se.getServer());
                }
                break;
            }
        }
        return false;
    }

    private static void lockFeaturedButtons(MultiplayerScreen ms) {
        if (!isFeaturedSelected(ms)) return;

        for (ClickableWidget widget : Screens.getButtons(ms)) {
            if (widget instanceof ButtonWidget btn) {
                String text = btn.getMessage().getString().toLowerCase();
                // If this is the Edit or Delete button, keep it disabled
                if (text.contains("edit") || text.contains("delete")) {
                    btn.active = false;
                }
            }
        }
    }

    private static void injectMultiplayerButtons(MinecraftClient client, MultiplayerScreen screen, int scaledWidth) {
        // Top-left: "🔥 Join with Code"
        Screens.getButtons(screen).add(
            CampfireButton.builder(Text.literal("🔥 Join with Code"), b ->
                client.setScreen(new JoinScreen(screen))
            ).dimensions(10, 8, 115, 20).build()
        );

        // Top-right: filter toggle — "⭐ Featured Servers" / "✦ Featured Only  ✕"
        String label = filterFeaturedOnly ? "✦ Featured Only  ✕" : "⭐ Featured Servers";
        Screens.getButtons(screen).add(
            CampfireButton.builder(Text.literal(label), b -> {
                filterFeaturedOnly = !filterFeaturedOnly;
                if (!filterFeaturedOnly) {
                    FeaturedServerInjector.restoreUserServers(client);
                } else {
                    FeaturedServerInjector.syncToServerList(client);
                }
                // Reload multiplayer screen with the filter applied
                client.setScreen(new MultiplayerScreen(null));
            }).dimensions(scaledWidth - 135, 8, 125, 20).build()
        );
    }

    private static void injectSidebar(MinecraftClient client, Screen screen, int width, int height, boolean inGame) {
        int availableRight = width - (width / 2 + 100);
        boolean compact = (availableRight < 135) || (width < 520);
        String username = CampfireFabric.getUsername();

        // Dynamically align with the top-most center menu button (e.g. Singleplayer or Return to Game)
        int startY = inGame ? (height / 4 + 24) : (height / 4 + 48);
        for (Element child : screen.children()) {
            if (child instanceof ClickableWidget widget) {
                int centerX = widget.getX() + widget.getWidth() / 2;
                if (Math.abs(centerX - width / 2) < 25 && widget.getY() >= 40 && widget.getY() < startY) {
                    startY = widget.getY();
                }
            }
        }

        if (compact) {
            // === COMPACT MODE (Window reduced, small screens) ===
            // 1. Top-Left: Compact 20x20 avatar icon
            Screens.getButtons(screen).add(
                CampfireButton.builder(Text.literal("👤"), b ->
                    client.setScreen(new AccountScreen(screen))
                ).dimensions(8, 8, 20, BUTTON_HEIGHT)
                 .tooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("Accounts: " + username)))
                 .build()
            );

            // 2. Top-Right: Compact 20x20 brand icon
            Screens.getButtons(screen).add(
                CampfireButton.builder(Text.literal("🔥"), b ->
                    client.setScreen(new AboutScreen(screen))
                ).dimensions(width - 20 - 8, 8, 20, BUTTON_HEIGHT)
                 .tooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("Campfire")))
                 .build()
            );

            // 3. Right Sidebar: 20x20 square icon-only buttons along right edge
            int x = width - 20 - 6;

            // Row 1: Host / Invite
            Text hostTooltip = inGame ? Text.literal("Invite / Host World") : Text.literal("Host World");
            Text hostIcon = inGame ? Text.literal("✉") : Text.literal("🌐");
            Screens.getButtons(screen).add(
                CampfireButton.builder(hostIcon, b ->
                    client.setScreen(new HostScreen(screen))
                ).dimensions(x, startY, 20, BUTTON_HEIGHT)
                 .tooltip(net.minecraft.client.gui.tooltip.Tooltip.of(hostTooltip))
                 .build()
            );

            // Row 2: Social
            Screens.getButtons(screen).add(
                CampfireButton.builder(Text.literal("👥"), b ->
                    client.setScreen(new SocialScreen(screen))
                ).dimensions(x, startY + 24, 20, BUTTON_HEIGHT)
                 .tooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("Social & Friends")))
                 .build()
            );

            // Row 3: Pictures
            Screens.getButtons(screen).add(
                CampfireButton.builder(Text.literal("🖼"), b ->
                    client.setScreen(new PicturesScreen(screen, CampfireFabric.screenshots))
                ).dimensions(x, startY + 48, 20, BUTTON_HEIGHT)
                 .tooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("Pictures & Gallery")))
                 .build()
            );

            // Row 4: Settings
            Screens.getButtons(screen).add(
                CampfireButton.builder(Text.literal("⚙"), b ->
                    client.setScreen(CampfireSettings.screen(screen, CampfireFabric.CONFIG))
                ).dimensions(x, startY + 72, 20, BUTTON_HEIGHT)
                 .tooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("Campfire Settings")))
                 .build()
            );

            // Row 5: Screenshots folder
            Screens.getButtons(screen).add(
                CampfireButton.builder(Text.literal("📁"), b -> {
                    if (CampfireFabric.screenshots != null) {
                        net.minecraft.util.Util.getOperatingSystem().open(CampfireFabric.screenshots.dir().toFile());
                    }
                }).dimensions(x, startY + 96, 20, BUTTON_HEIGHT)
                 .tooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("Open Screenshots Folder")))
                 .build()
            );
        } else {
            // === EXPANDED MODE (Wider screens) ===
            // 1. Top-Left: Player profile pill ("👤 Username")
            int userBtnWidth = Math.max(76, Math.min(120, client.textRenderer.getWidth("👤 " + username) + 16));
            Screens.getButtons(screen).add(
                CampfireButton.builder(Text.literal("👤 " + username), b ->
                    client.setScreen(new AccountScreen(screen))
                ).dimensions(10, 10, userBtnWidth, BUTTON_HEIGHT)
                 .tooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("Accounts: " + username)))
                 .build()
            );

            // 2. Top-Right: Campfire brand pill ("🔥 Campfire")
            int brandWidth = 84;
            Screens.getButtons(screen).add(
                CampfireButton.builder(Text.literal("🔥 Campfire"), b ->
                    client.setScreen(new AboutScreen(screen))
                ).dimensions(width - brandWidth - 10, 10, brandWidth, BUTTON_HEIGHT)
                 .tooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("Campfire")))
                 .build()
            );

            // 3. Right Sidebar — aligned with main menu options
            int x = width - SIDEBAR_WIDTH - MARGIN_RIGHT;

            // Row 1: Host / Invite
            Text hostText = inGame ? Text.literal("Invite / Host  ✉") : Text.literal("Host World  🌐");
            Screens.getButtons(screen).add(
                CampfireButton.builder(hostText, b ->
                    client.setScreen(new HostScreen(screen))
                ).dimensions(x, startY, SIDEBAR_WIDTH, BUTTON_HEIGHT)
                 .tooltip(net.minecraft.client.gui.tooltip.Tooltip.of(inGame ? Text.literal("Invite / Host World") : Text.literal("Host World")))
                 .build()
            );

            // Row 2: Social
            Screens.getButtons(screen).add(
                CampfireButton.builder(Text.literal("Social  👥"), b ->
                    client.setScreen(new SocialScreen(screen))
                ).dimensions(x, startY + 24, SIDEBAR_WIDTH, BUTTON_HEIGHT)
                 .tooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("Social & Friends")))
                 .build()
            );

            // Row 3: Pictures
            Screens.getButtons(screen).add(
                CampfireButton.builder(Text.literal("Pictures  🖼"), b ->
                    client.setScreen(new PicturesScreen(screen, CampfireFabric.screenshots))
                ).dimensions(x, startY + 48, SIDEBAR_WIDTH, BUTTON_HEIGHT)
                 .tooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("Pictures & Gallery")))
                 .build()
            );

            // Row 4: Split [ 📁 ] [ Settings  ⚙ ]
            int folderBtnWidth = 20;
            int settingsBtnWidth = SIDEBAR_WIDTH - folderBtnWidth - 4; // 104 - 20 - 4 = 80px

            Screens.getButtons(screen).add(
                CampfireButton.builder(Text.literal("📁"), b -> {
                    if (CampfireFabric.screenshots != null) {
                        net.minecraft.util.Util.getOperatingSystem().open(CampfireFabric.screenshots.dir().toFile());
                    }
                }).dimensions(x, startY + 72, folderBtnWidth, BUTTON_HEIGHT)
                 .tooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("Open Screenshots Folder")))
                 .build()
            );

            Screens.getButtons(screen).add(
                CampfireButton.builder(Text.literal("Settings  ⚙"), b ->
                    client.setScreen(CampfireSettings.screen(screen, CampfireFabric.CONFIG))
                ).dimensions(x + folderBtnWidth + 4, startY + 72, settingsBtnWidth, BUTTON_HEIGHT)
                 .tooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("Campfire Settings")))
                 .build()
            );
        }
    }
}
