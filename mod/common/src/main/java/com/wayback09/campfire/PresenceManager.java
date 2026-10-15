package com.wayback09.campfire;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes and reads the local presence file at campfire/presence.json.
 * Also reads incoming invite toasts dropped to campfire/pending_invite.json by friends.
 */
public final class PresenceManager {
    private final Path presencePath;
    private final Path pendingInvitePath;
    private PresenceState current;

    public PresenceManager(Path runDir) {
        Path campfire = runDir.resolve("campfire");
        this.presencePath = campfire.resolve("presence.json");
        this.pendingInvitePath = campfire.resolve("pending_invite.json");
    }

    public void update(PresenceState state) {
        this.current = state;
        try {
            Files.createDirectories(presencePath.getParent());
            Files.writeString(presencePath, toJson(state), StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public PresenceState getCurrent() {
        return current;
    }

    /**
     * Returns and clears a pending invite if one was written by another Campfire client.
     */
    public PendingInvite pollPendingInvite() {
        if (!Files.exists(pendingInvitePath)) return null;
        try {
            String json = Files.readString(pendingInvitePath, StandardCharsets.UTF_8);
            Files.deleteIfExists(pendingInvitePath);
            String from = extract(json, "from");
            String code = extract(json, "code");
            String world = extract(json, "world");
            if (from.isBlank() || code.isBlank()) return null;
            return new PendingInvite(from, code, world);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Writes a pending invite file for a target user's campfire dir (LAN scenario).
     * In practice, over the internet friends share codes manually; this enables LAN one-click.
     */
    public void sendInvite(Path targetCampfireDir, String fromUsername, String inviteCode, String worldName) {
        try {
            Files.createDirectories(targetCampfireDir);
            String json = "{\n"
                + "  \"from\": \"" + escape(fromUsername) + "\",\n"
                + "  \"code\": \"" + escape(inviteCode) + "\",\n"
                + "  \"world\": \"" + escape(worldName != null ? worldName : "") + "\"\n"
                + "}\n";
            Files.writeString(targetCampfireDir.resolve("pending_invite.json"), json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String toJson(PresenceState s) {
        return "{\n"
            + "  \"username\": \"" + escape(s.username()) + "\",\n"
            + "  \"status\": \"" + escape(s.status()) + "\",\n"
            + "  \"worldName\": \"" + escape(s.worldName() != null ? s.worldName() : "") + "\",\n"
            + "  \"inviteCode\": \"" + escape(s.inviteCode() != null ? s.inviteCode() : "") + "\",\n"
            + "  \"updatedAt\": " + s.updatedAt() + "\n"
            + "}\n";
    }

    private static String extract(String json, String key) {
        String pattern = "\"" + key + "\": \"";
        int idx = json.indexOf(pattern);
        if (idx == -1) {
            pattern = "\"" + key + "\":\"";
            idx = json.indexOf(pattern);
            if (idx == -1) return "";
        }
        int start = idx + pattern.length();
        int end = json.indexOf("\"", start);
        return end == -1 ? "" : json.substring(start, end).replace("\\\"", "\"");
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public record PendingInvite(String fromUsername, String inviteCode, String worldName) {}
}
