package com.wayback09.campfire.fabric;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConfirmLinkScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.Util;

import java.net.URI;

public final class LinkHelper {
    public static final String ENZONIC_RELAY_URL = "https://relay.enzonic.com/";
    public static final String ENZONIC_REF_URL = "https://enzonic.com/r/CQ3WBGRL";

    private LinkHelper() {}

    public static void open(Screen parent, String url) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        try {
            ConfirmLinkScreen.open(parent, url);
        } catch (Throwable t) {
            try {
                client.setScreen(new ConfirmLinkScreen(confirmed -> {
                    if (confirmed) {
                        try {
                            Util.getOperatingSystem().open(URI.create(url));
                        } catch (Throwable ignored) {
                            try {
                                java.awt.Desktop.getDesktop().browse(URI.create(url));
                            } catch (Throwable ignored2) {}
                        }
                    }
                    client.setScreen(parent);
                }, url, true));
            } catch (Throwable t2) {
                try {
                    Util.getOperatingSystem().open(URI.create(url));
                } catch (Throwable ignored) {}
            }
        }
    }
}
