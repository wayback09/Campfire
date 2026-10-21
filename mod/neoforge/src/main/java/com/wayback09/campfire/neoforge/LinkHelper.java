package com.wayback09.campfire.neoforge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.Util;

import java.net.URI;

public final class LinkHelper {
    public static final String ENZONIC_RELAY_URL = "https://relay.enzonic.com/";
    public static final String ENZONIC_REF_URL = "https://enzonic.com/r/CQ3WBGRL";

    private LinkHelper() {}

    public static void open(Screen parent, String url) {
        Minecraft client = Minecraft.getInstance();
        if (client == null) return;
        try {
            ConfirmLinkScreen.confirmLink(parent, url);
        } catch (Throwable t) {
            try {
                client.setScreen(new ConfirmLinkScreen(confirmed -> {
                    if (confirmed) {
                        try {
                            Util.getPlatform().openUri(URI.create(url));
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
                    Util.getPlatform().openUri(URI.create(url));
                } catch (Throwable ignored) {}
            }
        }
    }
}
