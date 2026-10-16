package com.wayback09.campfire;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Manages live player presence and one-click friend invites.
 * Connects to Cloudflare Worker (or local SocialServer) with offline disk fallbacks.
 *
 * Configurable via System.getProperty("campfire.social_url", "http://127.0.0.1:8090").
 */
public final class PresenceManager {
    public static final String DEFAULT_URL = "https://campfire-social.wayback145b.workers.dev";

    private final Path presencePath;
    private final Path pendingInvitePath;
    private final String apiUrl;
    private PresenceState current;

    public PresenceManager(Path runDir) {
        Path campfire = runDir.resolve("campfire");
        this.presencePath = campfire.resolve("presence.json");
        this.pendingInvitePath = campfire.resolve("pending_invite.json");
        this.apiUrl = System.getProperty("campfire.social_url", DEFAULT_URL).replaceAll("/+$", "");
    }

    public String getApiUrl() {
        return apiUrl;
    }

    /**
     * Broadcasts updated presence to the backend and saves locally.
     */
    public void update(PresenceState state) {
        this.current = state;
        // 1. Write local fallback
        try {
            if (presencePath.getParent() != null) Files.createDirectories(presencePath.getParent());
            Files.writeString(presencePath, toJson(state), StandardCharsets.UTF_8);
        } catch (IOException ignored) {}

        // 2. Broadcast to Cloudflare / SocialServer asynchronously
        CompletableFuture.runAsync(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) URI.create(apiUrl + "/presence").toURL().openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.getOutputStream().write(toJson(state).getBytes(StandardCharsets.UTF_8));
                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception ignored) {}
        });
    }

    public PresenceState getCurrent() {
        return current;
    }

    /**
     * Polls for pending in-game invites from friends (queued on Cloudflare or local file).
     */
    public PendingInvite pollPendingInvite(String username) {
        // 1. Check Cloudflare / SocialServer first
        if (username != null && !username.isBlank()) {
            try {
                HttpURLConnection conn = (HttpURLConnection) URI.create(apiUrl + "/invites/poll?username=" + username).toURL().openConnection();
                conn.setConnectTimeout(2500);
                conn.setReadTimeout(2500);
                if (conn.getResponseCode() == 200) {
                    String json = readStream(conn.getInputStream());
                    if (json.contains("\"invite\":{") || json.contains("\"invite\": {")) {
                        String from = extract(json, "from");
                        String code = extract(json, "code");
                        String world = extract(json, "world");
                        if (!from.isBlank() && !code.isBlank()) {
                            return new PendingInvite(from, code, world);
                        }
                    }
                }
                conn.disconnect();
            } catch (Exception ignored) {}
        }

        // 2. Fall back to local file
        if (Files.exists(pendingInvitePath)) {
            try {
                String json = Files.readString(pendingInvitePath, StandardCharsets.UTF_8);
                Files.deleteIfExists(pendingInvitePath);
                String from = extract(json, "from");
                String code = extract(json, "code");
                String world = extract(json, "world");
                if (!from.isBlank() && !code.isBlank()) {
                    return new PendingInvite(from, code, world);
                }
            } catch (Exception ignored) {}
        }

        return null;
    }

    /**
     * Sends an invite directly to a friend over the network (with offline queuing).
     */
    public void sendNetworkInvite(String fromUsername, String toUsername, String inviteCode, String worldName) {
        CompletableFuture.runAsync(() -> {
            try {
                String payload = "{\n"
                    + "  \"from\": \"" + escape(fromUsername) + "\",\n"
                    + "  \"to\": \"" + escape(toUsername) + "\",\n"
                    + "  \"code\": \"" + escape(inviteCode) + "\",\n"
                    + "  \"world\": \"" + escape(worldName != null ? worldName : "World") + "\"\n"
                    + "}";

                HttpURLConnection conn = (HttpURLConnection) URI.create(apiUrl + "/invites/send").toURL().openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.getOutputStream().write(payload.getBytes(StandardCharsets.UTF_8));
                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception ignored) {}
        });
    }

    /**
     * Queries live presence for a list of friends from the backend.
     */
    public List<FriendPresence> fetchFriendsPresence(List<String> friendUsernames) {
        if (friendUsernames.isEmpty()) return Collections.emptyList();

        try {
            StringBuilder sb = new StringBuilder("{\"usernames\":[");
            for (int i = 0; i < friendUsernames.size(); i++) {
                sb.append("\"").append(escape(friendUsernames.get(i))).append("\"");
                if (i < friendUsernames.size() - 1) sb.append(",");
            }
            sb.append("]}");

            HttpURLConnection conn = (HttpURLConnection) URI.create(apiUrl + "/presence/query").toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.getOutputStream().write(sb.toString().getBytes(StandardCharsets.UTF_8));

            if (conn.getResponseCode() == 200) {
                String res = readStream(conn.getInputStream());
                List<FriendPresence> list = parsePresenceList(res);
                if (!list.isEmpty()) return list;
            }
            conn.disconnect();
        } catch (Exception ignored) {}

        // Fallback: mark all as offline/in menu
        List<FriendPresence> fallback = new ArrayList<>();
        for (String f : friendUsernames) {
            fallback.add(new FriendPresence(f, "MENU", "", "", 0));
        }
        return fallback;
    }

    private static List<FriendPresence> parsePresenceList(String json) {
        List<FriendPresence> list = new ArrayList<>();
        String[] blocks = json.split("\\{");
        for (String block : blocks) {
            if (!block.contains("username") || !block.contains("status")) continue;
            String user = extract(block, "username");
            String status = extract(block, "status");
            String world = extract(block, "worldName");
            String code = extract(block, "inviteCode");
            long updated = extractLong(block, "updatedAt");
            if (!user.isBlank()) {
                list.add(new FriendPresence(user, status.isBlank() ? "OFFLINE" : status, world, code, updated));
            }
        }
        return list;
    }

    private static String readStream(InputStream is) throws IOException {
        try (is) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
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

    private static long extractLong(String json, String key) {
        String p = "\"" + key + "\": ";
        int idx = json.indexOf(p);
        if (idx == -1) { p = "\"" + key + "\":"; idx = json.indexOf(p); if (idx == -1) return 0; }
        int s = idx + p.length();
        int e = s;
        while (e < json.length() && Character.isDigit(json.charAt(e))) e++;
        try { return Long.parseLong(json.substring(s, e)); } catch (Exception ex) { return 0; }
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public record PendingInvite(String fromUsername, String inviteCode, String worldName) {}
}
