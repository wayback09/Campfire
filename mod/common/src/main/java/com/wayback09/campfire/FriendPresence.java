package com.wayback09.campfire;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads a friend's presence.json from their Minecraft run dir (LAN/same-machine shared dir scenario)
 * or from the local campfire/friend_presence/ directory for manually imported statuses.
 *
 * File layout per friend (local mirror):
 *   campfire/friend_presence/<username>.json
 *
 * When a friend who has Campfire shares their run dir (LAN, same PC), their
 * campfire/presence.json is symlinked or copied into your friend_presence/ folder.
 */
public record FriendPresence(
    String username,
    String status,
    String worldName,
    String inviteCode,
    long updatedAt
) {
    private static final long STALE_MS = 5 * 60 * 1000L; // 5 min stale threshold

    public boolean isHosting() {
        return "HOSTING".equals(status) && inviteCode != null && !inviteCode.isBlank();
    }

    public boolean isFresh() {
        return System.currentTimeMillis() - updatedAt < STALE_MS;
    }

    public String label() {
        if (!isFresh()) return "💤 In Menu";
        return switch (status) {
            case "HOSTING" -> "🔥 Hosting: " + (worldName != null && !worldName.isBlank() ? worldName : "World");
            case "SINGLEPLAYER" -> "🌿 Playing: " + (worldName != null && !worldName.isBlank() ? worldName : "Singleplayer");
            case "MULTIPLAYER" -> "🖧 On: " + (worldName != null && !worldName.isBlank() ? worldName : "Server");
            default -> "💤 In Menu";
        };
    }

    /**
     * Loads presence data for all known friends from campfire/friend_presence/<username>.json.
     */
    public static List<FriendPresence> loadAll(Path gameDir, List<String> friendUsernames) {
        Path dir = gameDir.resolve("campfire/friend_presence");
        List<FriendPresence> out = new ArrayList<>();
        for (String username : friendUsernames) {
            Path file = dir.resolve(username + ".json");
            if (Files.exists(file)) {
                try {
                    FriendPresence fp = parse(Files.readString(file, StandardCharsets.UTF_8), username);
                    if (fp != null) out.add(fp);
                } catch (Exception ignored) {}
            }
        }
        return out;
    }

    private static FriendPresence parse(String json, String fallbackUsername) {
        java.util.Map<String, Object> obj = CampfireJson.parseObjectOrEmpty(json);

        String username = CampfireJson.getString(obj, "username", "");
        if (username.isBlank()) username = fallbackUsername;
        String status = CampfireJson.getString(obj, "status", "");
        if (status.isBlank()) status = "MENU";
        String worldName = CampfireJson.getString(obj, "worldName", "");
        String inviteCode = CampfireJson.getString(obj, "inviteCode", "");
        long updatedAt = CampfireJson.getLong(obj, "updatedAt", 0);

        return new FriendPresence(username, status, worldName, inviteCode, updatedAt);
    }
}
