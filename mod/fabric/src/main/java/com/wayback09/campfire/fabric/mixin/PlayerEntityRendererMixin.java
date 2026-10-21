package com.wayback09.campfire.fabric.mixin;

import com.wayback09.campfire.fabric.CampfireBadge;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Prepends the Campfire orange flame badge to player nametags above player heads.
 */
@Mixin(EntityRenderer.class)
public abstract class PlayerEntityRendererMixin<T extends Entity> {
    @ModifyVariable(
        method = "renderLabelIfPresent",
        at = @At("HEAD"),
        argsOnly = true
    )
    private Text modifyPlayerNametag(Text text, T entity) {
        if (entity instanceof AbstractClientPlayerEntity player && text != null) {
            return CampfireBadge.applyBadge(text, player.getUuid(), player.getName().getString());
        }
        return text;
    }
}
