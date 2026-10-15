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
import net.minecraft.client.option.ServerList;
import net.minecraft.text.Text;

/**
 * Screen showcasing featured partner and community servers with 1-click connect and add-to-list actions.
 */
public final class FeaturedServersScreen extends Screen {
    private final Screen parent;
    private final FeaturedServerStore store;

    public FeaturedServersScreen(Screen parent) {
        super(Text.literal("Featured Community Servers"));
        this.parent = parent;
        this.store = new FeaturedServerStore(MinecraftClient.getInstance().runDirectory.toPath());
    }

    @Override
    protected void init() {
        List<FeaturedServer> list = store.getServers();

        int startY = 55;
        int cardHeight = 52;

        for (int i = 0; i < list.size() && i < 4; i++) {
            FeaturedServer server = list.get(i);
            int y = startY + (i * cardHeight);

            // Connect button
            addDrawableChild(ButtonWidget.builder(Text.literal("▶ Connect"), b -> {
                connectToServer(server);
            }).dimensions(width / 2 + 105, y + 6, 75, 20).build());

            // Add to My Servers button
            addDrawableChild(ButtonWidget.builder(Text.literal("+ Add to List"), b -> {
                addToServerList(server);
                b.setMessage(Text.literal("✔ Added!"));
            }).dimensions(width / 2 + 185, y + 6, 80, 20).build());
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

    private void addToServerList(FeaturedServer server) {
        MinecraftClient client = MinecraftClient.getInstance();
        ServerList serverList = new ServerList(client);
        serverList.loadFile();
        ServerAddress address = ServerAddress.parse(server.ip());
        ServerInfo info = new ServerInfo(server.name(), address.getAddress(), ServerInfo.ServerType.OTHER);
        serverList.add(info, false);
        serverList.saveFile();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);

        ctx.drawCenteredTextWithShadow(textRenderer, "⭐ Featured Community & Partner Servers", width / 2, 20, CampfireTheme.ACCENT);
        ctx.drawCenteredTextWithShadow(textRenderer, "Discover vetted servers, play with friends, and join community events.", width / 2, 35, 0xFFAAAAAA);

        List<FeaturedServer> list = store.getServers();
        int startY = 55;
        int cardHeight = 52;

        for (int i = 0; i < list.size() && i < 4; i++) {
            FeaturedServer server = list.get(i);
            int y = startY + (i * cardHeight);

            // Background card outline / fill
            ctx.fill(width / 2 - 270, y, width / 2 + 270, y + 46, 0x55000000);
            ctx.drawBorder(width / 2 - 270, y, 540, 46, 0x44FFFFFF);

            // Server Name + Tag
            String titleText = "⭐ [" + server.tag() + "] " + server.name();
            ctx.drawTextWithShadow(textRenderer, titleText, width / 2 - 260, y + 6, CampfireTheme.TEXT);

            // IP Address
            ctx.drawTextWithShadow(textRenderer, server.ip(), width / 2 - 260, y + 18, 0xFF55FF55);

            // Description
            ctx.drawTextWithShadow(textRenderer, server.description(), width / 2 - 260, y + 30, 0xFFAAAAAA);
        }
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }
}
