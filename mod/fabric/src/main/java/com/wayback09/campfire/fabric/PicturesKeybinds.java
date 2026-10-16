package com.wayback09.campfire.fabric;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Constructor;

/** Keybind registration for in-game pictures. */
public final class PicturesKeybinds {
    private PicturesKeybinds() {}

    public static KeyBinding picturesKey;

    public static void register() {
        try {
            picturesKey = createKeyBinding("key.campfire.pictures", GLFW.GLFW_KEY_F7, "category.campfire");
            if (picturesKey != null) {
                KeyBindingHelper.registerKeyBinding(picturesKey);
            }

            ClientTickEvents.END_CLIENT_TICK.register(client -> {
                if (picturesKey != null && picturesKey.wasPressed()) {
                    if (client.currentScreen == null) {
                        client.setScreen(new PicturesScreen(null, CampfireFabric.screenshots));
                    }
                }
            });
        } catch (Throwable t) {
            System.err.println("[Campfire] Warning: could not register pictures keybinding: " + t.getMessage());
        }
    }

    private static KeyBinding createKeyBinding(String translationKey, int defaultKey, String category) {
        // 1. Try standard 3-parameter constructor: KeyBinding(String, int, String)
        try {
            Constructor<KeyBinding> ctor = KeyBinding.class.getConstructor(String.class, int.class, String.class);
            return ctor.newInstance(translationKey, defaultKey, category);
        } catch (Throwable ignored) {}

        // 2. Try 4-parameter constructor: KeyBinding(String, InputUtil.Type, int, String)
        try {
            Constructor<KeyBinding> ctor = KeyBinding.class.getConstructor(String.class, InputUtil.Type.class, int.class, String.class);
            return ctor.newInstance(translationKey, InputUtil.Type.KEYSYM, defaultKey, category);
        } catch (Throwable ignored) {}

        // 3. Fallback: inspect all declared constructors on KeyBinding
        for (Constructor<?> ctor : KeyBinding.class.getConstructors()) {
            Class<?>[] types = ctor.getParameterTypes();
            try {
                if (types.length == 3 && types[0] == String.class && types[1] == int.class && types[2] == String.class) {
                    return (KeyBinding) ctor.newInstance(translationKey, defaultKey, category);
                }
                if (types.length == 4 && types[0] == String.class && types[2] == int.class && types[3] == String.class) {
                    return (KeyBinding) ctor.newInstance(translationKey, InputUtil.Type.KEYSYM, defaultKey, category);
                }
            } catch (Throwable ignored) {}
        }

        return null;
    }
}
