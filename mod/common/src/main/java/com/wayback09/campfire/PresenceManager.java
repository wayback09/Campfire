package com.wayback09.campfire;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Manages live player presence and one-click friend invites.
 * Connects to a Cloudflare Worker (or the local {@code SocialServer}) with
 * offline disk fallbacks.
 *
 * <p>The backend URL is configurable via the {@code campfire.social_url}
 * system property or {@code campfire.properties} in the Minecraft game
 * directory, e.g.:
 * <pre>
 *   campfire.social_url=https://campfire-social.example.workers.dev
 * </pre>
 */
public final class PresenceManager {
    public static final String DEFAULT_URL = "https://campfire-social.wayback145b.workers.dev";
    public static final String PROP_SOCIAL_URL = "campfire.social_url";

    /** Mirrors the social worker's 90-second freshness window. */
    static final long PRESENCE_TTL_MS = 90_000L;

    private final Path presencePath;
    private final Path pendingInvitePath;
    private final String apiUrl;
    private PresenceState current;

    public PresenceManager(Path runDir) {
        this(runDir, CampfireProperties.load(runDir));
    }

    public PresenceManager(Path runDir, CampfireProperties props) {
        Path campfire = runDir.resolve("campfire");
        this.presencePath = campfire.resolve("presence.json");
        this.pendingInvitePath = campfire.resolve("pending_invite.json");
        this.apiUrl = props.get(PROP_SOCIAL_URL, DEFAULT_URL).replaceAll("/+$", "");
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
        postPresenceAsync(state);
    }

    /**
     * Re-sends the current presence so the backend keeps it alive.
     *
     * <p>The worker expires presence rows after ~90 seconds, so a client that
     * stays in a world must heartbeat or it will be treated as offline. That
     * previously made friends (and the flame badge) flicker offline after a
     * couple of minutes.
     */
    public void heartbeat() {
        PresenceState state = this.current;
        if (state != null) {
            postPresenceAsync(state);
        }
    }

    private void postPresenceAsync(PresenceState state) {
        final String json = toJson(state);
        CompletableFuture.runAsync(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) URI.create(apiUrl + "/presence").toURL().openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.getOutputStream().write(json.getBytes(StandardCharsets.UTF_8));
                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception ignored) {}
        });
    }

    public PresenceState getCurrent() {
        return current;
    }

    /**
     * Polls for pending in-game invites from friends (queued on the backend or
     * written to the local queue file).
     */
    public PendingInvite pollPendingInvite(String username) {
        // 1. Check backend first
        if (username != null && !username.isBlank()) {
            try {
                HttpURLConnection conn = (HttpURLConnection) URI.create(apiUrl + "/invites/poll?username=" + username).toURL().openConnection();
                conn.setConnectTimeout(2500);
                conn.setReadTimeout(2500);
                if (conn.getResponseCode() == 200) {
                    String json = readStream(conn.getInputStream());
                    Map<String, Object> root = CampfireJson.parseObjectOrEmpty(json);
                    Object inviteObj = root.get("invite");
                    if (inviteObj instanceof Map<?, ?> invite) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> inviteMap = (Map<String, Object>) invite;
                        String from = CampfireJson.getString(inviteMap, "from", "");
                        String code = CampfireJson.getString(inviteMap, "code", "");
                        String world = CampfireJson.getString(inviteMap, "world", "");
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
                Map<String, Object> obj = CampfireJson.parseObjectOrEmpty(json);
                String from = CampfireJson.getString(obj, "from", "");
                String code = CampfireJson.getString(obj, "code", "");
                String world = CampfireJson.getString(obj, "world", "");
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
                String payload = "{"
                    + "\"from\":" + CampfireJson.quote(fromUsername) + ","
                    + "\"to\":" + CampfireJson.quote(toUsername) + ","
                    + "\"code\":" + CampfireJson.quote(inviteCode) + ","
                    + "\"world\":" + CampfireJson.quote(worldName != null ? worldName : "World")
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
                if (i > 0) sb.append(",");
                sb.append(CampfireJson.quote(friendUsernames.get(i)));
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

    /**
     * Looks up which of the supplied usernames currently have live Campfire
     * presence. Used to badge other Campfire users in the tab list and above
     * their nametags, so badge visibility is mutual instead of depending on the
     * viewer's local friends list.
     *
     * @return lowercase usernames that are online, {@link Optional#empty()} if
     *         the backend could not be reached (callers should keep their
     *         previous cache instead of clearing it)
     */
    public Optional<Set<String>> fetchOnlineUsernames(Collection<String> usernames) {
        if (usernames == null || usernames.isEmpty()) {
            return Optional.of(Collections.emptySet());
        }

        StringBuilder sb = new StringBuilder("{\"usernames\":[");
        int count = 0;
        for (String username : usernames) {
            if (username == null || username.isBlank()) continue;
            if (count++ > 0) sb.append(',');
            sb.append(CampfireJson.quote(username));
        }
        sb.append("]}");
        if (count == 0) return Optional.of(Collections.emptySet());

        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(apiUrl + "/presence/query").toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.getOutputStream().write(sb.toString().getBytes(StandardCharsets.UTF_8));

            if (conn.getResponseCode() == 200) {
                Set<String> online = parseOnlineUsernames(readStream(conn.getInputStream()));
                conn.disconnect();
                return Optional.of(online);
            }
            conn.disconnect();
        } catch (Exception ignored) {}
        return Optional.empty();
    }

    /** Parses a {@code /presence/query} response into live lowercase usernames. */
    static Set<String> parseOnlineUsernames(String json) {
        Set<String> online = new HashSet<>();
        Object parsed = CampfireJson.parseOrNull(json);
        if (!(parsed instanceof Map<?, ?> root)) return online;

        Object results = root.get("results");
        if (!(results instanceof List<?> entries)) return online;

        long now = System.currentTimeMillis();
        for (Object entry : entries) {
            if (!(entry instanceof Map<?, ?> raw)) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> obj = (Map<String, Object>) raw;

            String user = CampfireJson.getString(obj, "username", "");
            String status = CampfireJson.getString(obj, "status", "OFFLINE");
            long updatedAt = CampfireJson.getLong(obj, "updatedAt", 0);

            if (user.isBlank() || "OFFLINE".equalsIgnoreCase(status)) continue;
            // Some backends leave stale rows behind; honour the same window
            // the Cloudflare worker uses.
            if (updatedAt > 0 && now - updatedAt > PRESENCE_TTL_MS) continue;

            online.add(user.toLowerCase(Locale.ROOT));
        }
        return online;
    }

    private static List<FriendPresence> parsePresenceList(String json) {
        List<FriendPresence> list = new ArrayList<>();

        Object parsed = CampfireJson.parseOrNull(json);
        List<Object> entries = null;

        if (parsed instanceof Map<?, ?> root) {
            @SuppressWarnings("unchecked")
            Map<String, Object> rootMap = (Map<String, Object>) root;
            Object results = rootMap.get("results");
            if (results instanceof List<?> l) {
                entries = new ArrayList<>(l);
            } else {
                // Single presence object response.
                entries = List.of(rootMap);
            }
        } else if (parsed instanceof List<?> l) {
            entries = new ArrayList<>(l);
        }

        if (entries == null) return list;

        for (Object entry : entries) {
            if (!(entry instanceof Map<?, ?> raw)) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> obj = (Map<String, Object>) raw;

            String user = CampfireJson.getString(obj, "username", "");
            if (user.isBlank()) continue;

            String status = CampfireJson.getString(obj, "status", "OFFLINE");
            String world = CampfireJson.getString(obj, "worldName", "");
            String code = CampfireJson.getString(obj, "inviteCode", "");
            long updated = CampfireJson.getLong(obj, "updatedAt", 0);

            list.add(new FriendPresence(user, status.isBlank() ? "OFFLINE" : status, world, code, updated));
        }
        return list;
    }

    private static String readStream(InputStream is) throws IOException {
        try (is) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String toJson(PresenceState s) {
        return "{"
            + "\"username\":" + CampfireJson.quote(s.username()) + ","
            + "\"status\":" + CampfireJson.quote(s.status()) + ","
            + "\"worldName\":" + CampfireJson.quote(s.worldName() != null ? s.worldName() : "") + ","
            + "\"inviteCode\":" + CampfireJson.quote(s.inviteCode() != null ? s.inviteCode() : "") + ","
            + "\"updatedAt\":" + s.updatedAt()
            + "}";
    }

    public record PendingInvite(String fromUsername, String inviteCode, String worldName) {}
}
