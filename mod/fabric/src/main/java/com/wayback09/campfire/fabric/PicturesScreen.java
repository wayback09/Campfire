package com.wayback09.campfire.fabric;

import com.wayback09.campfire.CampfireTheme;
import com.wayback09.campfire.ScreenshotStore;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Author: wayback09. Pictures gallery — vanilla widgets, ember theme. */
public final class PicturesScreen extends Screen {
    private final Screen parent;
    private final ScreenshotStore store;

    public PicturesScreen(Screen parent, ScreenshotStore store) {
        super(Text.literal("Campfire Pictures"));
        this.parent = parent;
        this.store = store;
    }

    @Override
    protected void init() {
        addDrawableChild(ButtonWidget.builder(Text.literal("Open folder"), b -> {
            net.minecraft.util.Util.getOperatingSystem().open(store.dir().toFile());
        }).dimensions(width / 2 - 100, height - 30, 200, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
            .dimensions(width / 2 - 100, 8, 200, 20).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, "Campfire Pictures (" + store.list().size() + ")",
            width / 2, 32, CampfireTheme.TEXT);
        int y = 56;
        for (var e : store.list()) {
            if (y > height - 60) break;
            ctx.drawTextWithShadow(textRenderer, e.file().getFileName().toString(), 24, y, CampfireTheme.TEXT);
            y += 12;
        }
        if (MinecraftClient.getInstance() == null) close();
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }
}
