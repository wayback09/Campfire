package com.wayback09.campfire.fabric;

import com.wayback09.campfire.CampfireTheme;
import com.wayback09.campfire.FeaturedServer;
import com.wayback09.campfire.FeaturedServerStore;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;

/**
 * Screen showcasing featured partner and community servers with 1-click connect.
 * Featured servers are read-only — they cannot be removed or edited by the user.
 */
public final class FeaturedServersScreen extends Screen {
    private final Screen parent;
    private final FeaturedServerStore store;

    // Layout constants (relative to actual width)
    private static final int MARGIN = 20;   // side margin in px
    private static final int CARD_H = 52;
    private static final int START_Y = 58;
    private static final int CONNECT_BTN_W = 80;
    private static final int CONNECT_BTN_H = 20;

    public FeaturedServersScreen(Screen parent) {
        super(Text.literal("Featured Community Servers"));
        this.parent = parent;
        this.store = new FeaturedServerStore(MinecraftClient.getInstance().runDirectory.toPath());
    }

    @Override
    protected void init() {
        List<FeaturedServer> list = store.getServers();

        for (int i = 0; i < list.size() && i < 5; i++) {
            FeaturedServer server = list.get(i);
            int y = START_Y + (i * CARD_H);

            // Connect button — right-aligned with margin
            int btnX = width - MARGIN - CONNECT_BTN_W;
            int btnY = y + (CARD_H - CONNECT_BTN_H) / 2 - 4;

            addDrawableChild(ButtonWidget.builder(Text.literal("▶ Connect"), b ->
                connectToServer(server)
            ).dimensions(btnX, btnY, CONNECT_BTN_W, CONNECT_BTN_H).build());
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(width / 2 - 100, height - 36, 200, 20).build());
    }

    private void connectToServer(FeaturedServer server) {
        MinecraftClient client = MinecraftClient.getInstance();
        ServerAddress address = ServerAddress.parse(server.ip());
        ServerInfo info = new ServerInfo(server.name(), address.getAddress(), ServerInfo.ServerType.OTHER);
        ConnectScreen.connect(this, client, address, info, false, null);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);

        // Header
        ctx.drawCenteredTextWithShadow(textRenderer,
            "⭐ Featured Community & Partner Servers", width / 2, 16, CampfireTheme.ACCENT);
        ctx.drawCenteredTextWithShadow(textRenderer,
            "Vetted servers — connect directly or add to your server list.",
            width / 2, 30, 0xFF888888);

        List<FeaturedServer> list = store.getServers();
        int cardLeft  = MARGIN;
        int cardRight = width - MARGIN;
        int cardW     = cardRight - cardLeft;

        for (int i = 0; i < list.size() && i < 5; i++) {
            FeaturedServer server = list.get(i);
            int y = START_Y + (i * CARD_H);

            // Card background
            ctx.fill(cardLeft, y, cardRight, y + CARD_H - 6, 0x66000000);
            ctx.drawBorder(cardLeft, y, cardW, CARD_H - 6, 0x33FFFFFF);

            int textX = cardLeft + 8;
            int btnX  = cardRight - CONNECT_BTN_W - 6;

            // Server name & tag — stop text before the button
            String title = "[" + server.tag() + "] " + server.name();
            ctx.drawTextWithShadow(textRenderer, title, textX, y + 6, CampfireTheme.TEXT);

            // IP
            ctx.drawTextWithShadow(textRenderer, server.ip(), textX, y + 18, 0xFF55FF55);

            // Description — truncate if too wide
            int maxDescW = btnX - textX - 8;
            String desc = server.description();
            while (!desc.isEmpty() && textRenderer.getWidth(desc + "…") > maxDescW) {
                desc = desc.substring(0, desc.length() - 1);
            }
            if (desc.length() < server.description().length()) desc += "…";
            ctx.drawTextWithShadow(textRenderer, desc, textX, y + 30, 0xFFAAAAAA);
        }
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }
}
