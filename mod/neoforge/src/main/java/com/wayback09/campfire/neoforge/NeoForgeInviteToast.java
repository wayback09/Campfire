package com.wayback09.campfire.neoforge;

import com.wayback09.campfire.PresenceManager.PendingInvite;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

/**
 * Displays a NeoForge-compatible invite toast notification.
 * Equivalent to InviteToast in the Fabric module.
 */
public final class NeoForgeInviteToast {
    private NeoForgeInviteToast() {}

    public static void show(Minecraft mc, PendingInvite invite) {
        if (mc == null || mc.getToasts() == null) return;

        String worldPart = (invite.worldName() != null && !invite.worldName().isBlank())
            ? " to \"" + invite.worldName() + "\""
            : "";

        Component title = Component.literal("🔥 " + invite.fromUsername() + " invited you" + worldPart + "!");
        Component desc = Component.literal("Code: " + invite.inviteCode() + "  (copied to clipboard)");

        if (mc.keyboardHandler != null && invite.inviteCode() != null) {
            mc.keyboardHandler.setClipboard(invite.inviteCode());
        }

        mc.getToasts().addToast(SystemToast.multiline(
            mc,
            SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
            title,
            desc
        ));
    }
}