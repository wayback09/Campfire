package com.wayback09.campfire.fabric.mixin;

import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Mixin accessor exposing Screen.addDrawableChild() so Campfire buttons
 * are registered for both rendering AND input (mouse/keyboard) events.
 */
@Mixin(Screen.class)
public interface ScreenAccessor {
    @Invoker("addDrawableChild")
    <T extends Element & Drawable & Selectable> T campfire$addDrawableChild(T drawableElement);
}
