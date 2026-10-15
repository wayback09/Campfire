package com.wayback09.campfire.fabric;

import com.wayback09.campfire.CampfireTheme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Campfire Profile & Information screen.
 */
public final class AboutScreen extends Screen {
    private final Screen parent;

    public AboutScreen(Screen parent) {
        super(Text.literal("Campfire Profile"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        addDrawableChild(ButtonWidget.builder(Text.literal("Settings"), b -> {
            if (client != null) {
                client.setScreen(CampfireSettings.screen(this, CampfireFabric.CONFIG));
            }
        }).dimensions(width / 2 - 105, height - 35, 100, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(width / 2 + 5, height - 35, 100, 20).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);

        MinecraftClient mc = MinecraftClient.getInstance();
        String username = mc.getSession() != null ? mc.getSession().getUsername() : "Player";

        ctx.drawCenteredTextWithShadow(textRenderer, "🔥 Campfire", width / 2, height / 2 - 50, CampfireTheme.ACCENT);
        ctx.drawCenteredTextWithShadow(textRenderer, "Version 0.1.0 (Fabric 1.21.8)", width / 2, height / 2 - 35, CampfireTheme.TEXT);
        ctx.drawCenteredTextWithShadow(textRenderer, "Signed in as: " + username, width / 2, height / 2 - 15, 0xFF55FF55);
        ctx.drawCenteredTextWithShadow(textRenderer, "Peer-to-Peer World Hosting • Social • Screenshot Gallery", width / 2, height / 2 + 5, 0xFFAAAAAA);
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }
}
