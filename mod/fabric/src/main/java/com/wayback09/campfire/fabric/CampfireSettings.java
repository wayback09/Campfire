package com.wayback09.campfire.fabric;

import com.wayback09.campfire.CampfireConfig;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Settings via YACL. */
public final class CampfireSettings {
    private CampfireSettings() {}

    public static Screen screen(Screen parent, CampfireConfig c) {
        return YetAnotherConfigLib.createBuilder()
            .title(Text.literal("Campfire Settings"))
            .category(dev.isxander.yacl3.api.ConfigCategory.createBuilder()
                .name(Text.literal("General"))
                .option(Option.<Boolean>createBuilder().name(Text.literal("Appear offline"))
                    .binding(false, () -> c.appearOffline, v -> c.appearOffline = v)
                    .controller(BooleanControllerBuilder::create).build())
                .option(Option.<Boolean>createBuilder().name(Text.literal("Share activity"))
                    .binding(true, () -> c.shareActivity, v -> c.shareActivity = v)
                    .controller(BooleanControllerBuilder::create).build())
                .option(Option.<Boolean>createBuilder().name(Text.literal("Screenshot preview"))
                    .binding(true, () -> c.screenshotPreview, v -> c.screenshotPreview = v)
                    .controller(BooleanControllerBuilder::create).build())
                .option(Option.<Integer>createBuilder().name(Text.literal("Preview seconds"))
                    .binding(5, () -> c.screenshotPreviewSeconds, v -> c.screenshotPreviewSeconds = v)
                    .controller(o -> IntegerSliderControllerBuilder.create(o).range(3, 10).step(1)).build())
                .build())
            .save(() -> {})
            .build()
            .generateScreen(parent);
    }
}
