package com.wayback09.campfire.neoforge;

import com.wayback09.campfire.CampfireConfig;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Settings via YACL for NeoForge. */
public final class CampfireSettings {
    private CampfireSettings() {}

    public static Screen screen(Screen parent, CampfireConfig c) {
        return YetAnotherConfigLib.createBuilder()
            .title(Component.literal("Campfire Settings"))
            .category(dev.isxander.yacl3.api.ConfigCategory.createBuilder()
                .name(Component.literal("General"))
                .option(Option.<Boolean>createBuilder().name(Component.literal("Appear offline"))
                    .description(dev.isxander.yacl3.api.OptionDescription.of(Component.literal("When enabled, hides your online presence from friends and peer discovery.")))
                    .binding(false, () -> c.appearOffline, v -> c.appearOffline = v)
                    .controller(BooleanControllerBuilder::create).build())
                .option(Option.<Boolean>createBuilder().name(Component.literal("Share activity"))
                    .description(dev.isxander.yacl3.api.OptionDescription.of(Component.literal("Broadcast your current world name and status to connected friends.")))
                    .binding(true, () -> c.shareActivity, v -> c.shareActivity = v)
                    .controller(BooleanControllerBuilder::create).build())
                .option(Option.<Boolean>createBuilder().name(Component.literal("Screenshot preview"))
                    .description(dev.isxander.yacl3.api.OptionDescription.of(Component.literal("Displays an instant preview notification overlay when pressing F2 to take a screenshot.")))
                    .binding(true, () -> c.screenshotPreview, v -> c.screenshotPreview = v)
                    .controller(BooleanControllerBuilder::create).build())
                .option(Option.<Integer>createBuilder().name(Component.literal("Preview seconds"))
                    .description(dev.isxander.yacl3.api.OptionDescription.of(Component.literal("How long (in seconds) the screenshot notification preview banner stays on your screen.")))
                    .binding(5, () -> c.screenshotPreviewSeconds, v -> c.screenshotPreviewSeconds = v)
                    .controller(o -> IntegerSliderControllerBuilder.create(o).range(3, 10).step(1)).build())
                .option(Option.<Boolean>createBuilder().name(Component.literal("Anonymous telemetry"))
                    .description(dev.isxander.yacl3.api.OptionDescription.of(Component.literal("Helps us improve Campfire by anonymously reporting error logs and basic session telemetry.")))
                    .binding(true, () -> c.enableTelemetry, v -> c.enableTelemetry = v)
                    .controller(BooleanControllerBuilder::create).build())
                .build())
            .save(() -> {})
            .build()
            .generateScreen(parent);
    }
}
