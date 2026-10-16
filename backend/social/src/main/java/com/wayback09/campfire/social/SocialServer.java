package com.wayback09.campfire.social;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Local social + signaling server. Zero deps, runs on localhost or VPS.
 * Matches Cloudflare Worker API 1-to-1.
 *
 * Endpoints:
 *  - GET /health
 *  - POST /presence
 *  - POST /presence/query
 *  - POST /invites/send
 *  - GET /invites/poll?username=...
 *  - GET /friends/search?username=...
 *  - POST /tickets
 *  - GET /tickets/{code}
 */
public final class SocialServer {
    private final HttpServer http;
    private final Map<String, String> tickets = new ConcurrentHashMap<>();
    private final Map<String, PresenceEntry> presenceMap = new ConcurrentHashMap<>();
    private final Map<String, InviteEntry> pendingInvites = new ConcurrentHashMap<>();

    public record PresenceEntry(String username, String status, String worldName, String inviteCode, long updatedAt) {}
    public record InviteEntry(String from, String to, String code, String world, long sentAt) {}

    public SocialServer(int port) throws IOException {
        http = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 32);

        // 1. Health
        http.createContext("/health", ex -> replyJson(ex, 200, "{\"ok\":true,\"service\":\"campfire-social\"}"));

        // 2. Presence Heartbeat
        http.createContext("/presence", ex -> {
            if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
                String body = readBody(ex);
                String username = extract(body, "username");
                if (username.isBlank()) {
                    replyJson(ex, 400, "{\"error\":\"Missing username\"}");
                    return;
                }
                String status = extract(body, "status");
                String worldName = extract(body, "worldName");
                String inviteCode = extract(body, "inviteCode");
                PresenceEntry entry = new PresenceEntry(
                    username,
                    status.isBlank() ? "MENU" : status,
                    worldName,
                    inviteCode,
                    System.currentTimeMillis()
                );
                presenceMap.put(username.toLowerCase(), entry);
                replyJson(ex, 200, "{\"ok\":true}");
            } else {
                ex.sendResponseHeaders(405, -1);
            }
        });

        // 3. Batch Query Presence
        http.createContext("/presence/query", ex -> {
            if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
                String body = readBody(ex);
                StringBuilder sb = new StringBuilder("{\"results\":[");
                String[] parts = body.split("\"");
                boolean first = true;
                for (int i = 0; i < parts.length; i++) {
                    if (parts[i].equals("usernames")) {
                        // Scan subsequent strings in the array
                        for (int j = i + 1; j < parts.length; j += 2) {
                            String name = parts[j].trim();
                            if (name.equals("]") || name.contains("}")) break;
                            if (name.isEmpty() || name.equals(",") || name.equals(":")) continue;

                            PresenceEntry p = presenceMap.get(name.toLowerCase());
                            boolean online = p != null && (System.currentTimeMillis() - p.updatedAt() < 90000);

                            if (!first) sb.append(",");
                            first = false;

                            sb.append("{\"username\":\"").append(name).append("\",")
                              .append("\"status\":\"").append(online ? p.status() : "OFFLINE").append("\",")
                              .append("\"worldName\":\"").append(online ? escape(p.worldName()) : "").append("\",")
                              .append("\"inviteCode\":\"").append(online ? escape(p.inviteCode()) : "").append("\",")
                              .append("\"updatedAt\":").append(p != null ? p.updatedAt() : 0).append("}");
                        }
                        break;
                    }
                }
                sb.append("]}");
                replyJson(ex, 200, sb.toString());
            } else {
                ex.sendResponseHeaders(405, -1);
            }
        });

        // 4. Send Invite
        http.createContext("/invites/send", ex -> {
            if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
                String body = readBody(ex);
                String from = extract(body, "from");
                String to = extract(body, "to");
                String code = extract(body, "code");
                String world = extract(body, "world");
                if (from.isBlank() || to.isBlank() || code.isBlank()) {
                    replyJson(ex, 400, "{\"error\":\"Missing fields\"}");
                    return;
                }
                pendingInvites.put(to.toLowerCase(), new InviteEntry(from, to, code, world, System.currentTimeMillis()));
                replyJson(ex, 200, "{\"ok\":true,\"message\":\"Invite queued\"}");
            } else {
                ex.sendResponseHeaders(405, -1);
            }
        });

        // 5. Poll Pending Invites
        http.createContext("/invites/poll", ex -> {
            if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
                String query = ex.getRequestURI().getQuery();
                String username = "";
                if (query != null && query.contains("username=")) {
                    username = query.substring(query.indexOf("username=") + 9).split("&")[0];
                }
                if (username.isBlank()) {
                    replyJson(ex, 200, "{\"invite\":null}");
                    return;
                }
                InviteEntry invite = pendingInvites.remove(username.toLowerCase());
                if (invite != null && (System.currentTimeMillis() - invite.sentAt() < 900000)) {
                    String json = "{\"invite\":{\"from\":\"" + escape(invite.from())
                        + "\",\"code\":\"" + escape(invite.code())
                        + "\",\"world\":\"" + escape(invite.world()) + "\"}}";
                    replyJson(ex, 200, json);
                } else {
                    replyJson(ex, 200, "{\"invite\":null}");
                }
            } else {
                ex.sendResponseHeaders(405, -1);
            }
        });

        // 6. Search Mojang Player
        http.createContext("/friends/search", ex -> {
            String query = ex.getRequestURI().getQuery();
            String username = "";
            if (query != null && query.contains("username=")) {
                username = query.substring(query.indexOf("username=") + 9).split("&")[0];
            }
            if (username.isBlank()) {
                replyJson(ex, 400, "{\"error\":\"Missing username\"}");
                return;
            }
            try {
                HttpURLConnection conn = (HttpURLConnection) URI.create("https://api.mojang.com/users/profiles/minecraft/" + username).toURL().openConnection();
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);
                if (conn.getResponseCode() == 200) {
                    String data = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                    replyJson(ex, 200, "{\"found\":true,\"data\":" + data + "}");
                } else {
                    replyJson(ex, 404, "{\"found\":false,\"error\":\"Player not found\"}");
                }
                conn.disconnect();
            } catch (Exception e) {
                replyJson(ex, 500, "{\"error\":\"" + escape(e.getMessage()) + "\"}");
            }
        });

        // 7. Relay Tickets
        http.createContext("/tickets", ex -> {
            if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
                String code = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
                int slot = 10000 + (Math.abs(code.hashCode()) % 20000);
                tickets.put(code, String.valueOf(slot));
                replyJson(ex, 200, "{\"code\":\"" + code + "\",\"relaySlot\":" + slot + "}");
            } else {
                String path = ex.getRequestURI().getPath();
                String code = path.substring(path.lastIndexOf('/') + 1).toUpperCase();
                String slot = tickets.get(code);
                if (slot == null) {
                    ex.sendResponseHeaders(404, -1);
                } else {
                    replyJson(ex, 200, "{\"code\":\"" + code + "\",\"relaySlot\":" + slot + "}");
                }
            }
        });
    }

    public void start() {
        http.start();
    }

    private static String readBody(HttpExchange ex) throws IOException {
        try (InputStream is = ex.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void replyJson(HttpExchange ex, int status, String json) throws IOException {
        byte[] b = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        ex.sendResponseHeaders(status, b.length);
        try (var os = ex.getResponseBody()) {
            os.write(b);
        }
    }

    private static String extract(String json, String key) {
        String p1 = "\"" + key + "\": \"";
        int idx = json.indexOf(p1);
        if (idx != -1) {
            int s = idx + p1.length();
            int e = json.indexOf("\"", s);
            return e == -1 ? "" : json.substring(s, e);
        }
        String p2 = "\"" + key + "\":\"";
        idx = json.indexOf(p2);
        if (idx != -1) {
            int s = idx + p2.length();
            int e = json.indexOf("\"", s);
            return e == -1 ? "" : json.substring(s, e);
        }
        return "";
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8090;
        var s = new SocialServer(port);
        s.start();
        System.out.println("Campfire social listening on 127.0.0.1:" + port + " (wayback09)");
    }
}
