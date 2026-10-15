package com.wayback09.campfire.fabric;

import com.wayback09.campfire.PresenceManager.PendingInvite;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.Text;

/**
 * In-game toast shown when a friend sends an invite via campfire/pending_invite.json.
 * Shows "Steve invited you to [World]! Join Code: CAMP-XXXXX-XXXXX"
 * with a copy-to-clipboard action.
 */
public final class InviteToast {
    private InviteToast() {}

    public static void show(MinecraftClient client, PendingInvite invite) {
        if (client == null || client.getToastManager() == null) return;

        String worldPart = (invite.worldName() != null && !invite.worldName().isBlank())
            ? " to \"" + invite.worldName() + "\""
            : "";

        Text title = Text.literal("🔥 " + invite.fromUsername() + " invited you" + worldPart + "!");
        Text desc  = Text.literal("Code: " + invite.inviteCode() + "  (copied to clipboard)");

        // Auto-copy invite code so they can paste immediately into Join screen
        if (client.keyboard != null) {
            client.keyboard.setClipboard(invite.inviteCode());
        }

        SystemToast.show(client.getToastManager(), SystemToast.Type.NARRATOR_TOGGLE, title, desc);
    }
}
