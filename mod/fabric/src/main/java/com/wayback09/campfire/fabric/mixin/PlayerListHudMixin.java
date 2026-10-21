package com.wayback09.campfire.fabric.mixin;

import com.wayback09.campfire.fabric.CampfireBadge;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Prepends the Campfire orange flame badge to player names in the Tab list.
 */
@Mixin(PlayerListHud.class)
public class PlayerListHudMixin {
    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true)
    private void addCampfireFlameBadge(PlayerListEntry entry, CallbackInfoReturnable<Text> cir) {
        if (entry != null && entry.getProfile() != null) {
            Text original = cir.getReturnValue();
            if (original != null) {
                Text modified = CampfireBadge.applyBadge(original, entry.getProfile().getId(), entry.getProfile().getName());
                if (modified != original) {
                    cir.setReturnValue(modified);
                }
            }
        }
    }
}
