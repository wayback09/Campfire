package com.wayback09.campfire;

/**
 * Represents a friend's local presence state, serialized to campfire/presence.json.
 */
public record PresenceState(
    String username,
    String status,     // "MENU", "SINGLEPLAYER", "MULTIPLAYER", "HOSTING"
    String worldName,  // null unless HOSTING or SINGLEPLAYER
    String inviteCode, // non-null when HOSTING
    long updatedAt
) {
    public static PresenceState menu(String username) {
        return new PresenceState(username, "MENU", null, null, System.currentTimeMillis());
    }

    public static PresenceState singleplayer(String username, String worldName) {
        return new PresenceState(username, "SINGLEPLAYER", worldName, null, System.currentTimeMillis());
    }

    public static PresenceState hosting(String username, String worldName, String inviteCode) {
        return new PresenceState(username, "HOSTING", worldName, inviteCode, System.currentTimeMillis());
    }

    public static PresenceState multiplayer(String username, String serverName) {
        return new PresenceState(username, "MULTIPLAYER", serverName, null, System.currentTimeMillis());
    }

    /** Human-readable status label for the Social screen. */
    public String label() {
        return switch (status) {
            case "HOSTING" -> "🔥 Hosting: " + (worldName != null ? worldName : "World");
            case "SINGLEPLAYER" -> "🌿 Playing: " + (worldName != null ? worldName : "Singleplayer");
            case "MULTIPLAYER" -> "🖧 On: " + (worldName != null ? worldName : "Multiplayer");
            default -> "💤 In Menu";
        };
    }

    public boolean isHosting() {
        return "HOSTING".equals(status) && inviteCode != null && !inviteCode.isBlank();
    }
}
