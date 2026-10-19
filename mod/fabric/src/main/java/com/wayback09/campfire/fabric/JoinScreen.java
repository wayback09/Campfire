package com.wayback09.campfire.fabric;

import com.wayback09.campfire.CampfireTheme;
import com.wayback09.campfire.InviteCode;
import java.net.InetSocketAddress;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;

/**
 * Join screen allowing players to paste a Campfire Invite Code (CAMP-XXXXX-XXXXX)
 * or direct IP:port to immediately connect to a hosted world.
 */
public final class JoinScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget inputField;
    private String errorText = "";

    public JoinScreen(Screen parent) {
        super(Text.literal("Join Campfire World"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        inputField = new TextFieldWidget(textRenderer, width / 2 - 120, height / 2 - 20, 240, 20, Text.literal("Invite Code"));
        inputField.setMaxLength(40);
        inputField.setPlaceholder(Text.literal("Paste CAMP-XXXXX-XXXXX or IP:Port"));
        addSelectableChild(inputField);
        setInitialFocus(inputField);

        addDrawableChild(ButtonWidget.builder(Text.literal("Join World"), b -> join())
                .dimensions(width / 2 - 100, height / 2 + 15, 200, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), b -> close())
                .dimensions(width / 2 - 100, height / 2 + 40, 200, 20).build());
    }

    private void join() {
        String input = inputField.getText();
        if (input == null || input.isBlank()) {
            errorText = "Please enter an invite code.";
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();

        if (InviteCode.isRelayCode(input)) {
            String code = InviteCode.extractRelayCode(input);
            errorText = "Connecting via Campfire Relay...";
            com.wayback09.campfire.RelayClient.startGuestForwarder(code).thenAccept(target -> {
                client.execute(() -> {
                    ServerAddress address = new ServerAddress(target.getHostString(), target.getPort());
                    ServerInfo info = new ServerInfo("Campfire: " + code, address.getAddress(), ServerInfo.ServerType.OTHER);
                    ConnectScreen.connect(parent, client, address, info, false, null);
                });
            }).exceptionally(ex -> {
                client.execute(() -> {
                    errorText = "Relay connection failed: " + ex.getMessage();
                });
                return null;
            });
            return;
        }

        InetSocketAddress target = InviteCode.decode(input);
        if (target == null) {
            errorText = "Invalid invite code or address format.";
            return;
        }

        ServerAddress address = new ServerAddress(target.getHostString(), target.getPort());
        ServerInfo info = new ServerInfo("Campfire Hosted World", address.getAddress(), ServerInfo.ServerType.OTHER);

        ConnectScreen.connect(parent, client, address, info, false, null);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);

        ctx.drawCenteredTextWithShadow(textRenderer, "Join with Campfire", width / 2, 40, CampfireTheme.TEXT);
        ctx.drawCenteredTextWithShadow(textRenderer, "Enter the Invite Code shared by the host", width / 2, 60, 0xFFAAAAAA);

        inputField.render(ctx, mouseX, mouseY, delta);

        if (!errorText.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, errorText, width / 2, height / 2 - 35, 0xFFFF5555);
        }
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }
}
