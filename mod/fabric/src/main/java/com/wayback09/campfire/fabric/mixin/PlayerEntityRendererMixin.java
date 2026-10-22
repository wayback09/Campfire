package com.wayback09.campfire.fabric.mixin;

import com.wayback09.campfire.fabric.CampfireBadge;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Prepends the Campfire orange flame badge to player nametags above player heads.
 *
 * <p>History: this used to be a {@code @ModifyVariable} on
 * {@code EntityRenderer#renderLabelIfPresent}. Minecraft 1.21.9 moved the label
 * text out of that method's parameter list and into
 * {@code EntityRenderState#displayName}, so the injection point no longer exists.
 * The text is now sourced from {@code EntityRenderer#getDisplayName}, which is
 * the stable place to rewrite it.
 */
@Mixin(EntityRenderer.class)
public class PlayerEntityRendererMixin {
    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void campfireBadgeOnDisplayName(Entity entity, CallbackInfoReturnable<Text> cir) {
        Text original = cir.getReturnValue();
        if (original == null || !(entity instanceof AbstractClientPlayerEntity player)) {
            return;
        }
        Text modified = CampfireBadge.applyBadge(original, player.getUuid(), player.getName().getString());
        if (modified != null && modified != original) {
            cir.setReturnValue(modified);
        }
    }
}
