package com.wayback09.campfire.fabric;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/** Author: wayback09. */
public final class PicturesKeybinds {
    private PicturesKeybinds() {}

    public static KeyBinding picturesKey;

    public static void register() {
        picturesKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.campfire.pictures", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F7, "category.campfire"));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (picturesKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new PicturesScreen(null, CampfireFabric.screenshots));
                }
            }
        });
    }
}
