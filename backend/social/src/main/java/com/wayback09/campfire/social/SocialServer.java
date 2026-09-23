package com.wayback09.campfire.social;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Author: wayback09.
 * Local social + signaling server (Option B). Zero deps, runs on localhost.
 * Endpoints: GET /health, POST /tickets (host creates), GET /tickets/{code} (guest redeems).
 * Friends/DM persistence (SQLite) lands in the next step; tickets unblock hosting now.
 */
public final class SocialServer {
    private final HttpServer http;
    private final Map<String, String> tickets = new ConcurrentHashMap<>();

    public SocialServer(int port) throws IOException {
        http = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 32);
        http.createContext("/health", ex -> {
            byte[] b = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, b.length);
            try (var os = ex.getResponseBody()) {
                os.write(b);
            }
        });
        http.createContext("/tickets", ex -> {
            if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
                String code = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
                int slot = 10000 + (Math.abs(code.hashCode()) % 20000);
                tickets.put(code, String.valueOf(slot));
                byte[] b = ("{\"code\":\"" + code + "\",\"relaySlot\":" + slot + "}").getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().add("Content-Type", "application/json");
                ex.sendResponseHeaders(200, b.length);
                try (var os = ex.getResponseBody()) {
                    os.write(b);
                }
            } else {
                // GET /tickets/{code}
                String path = ex.getRequestURI().getPath();
                String code = path.substring(path.lastIndexOf('/') + 1).toUpperCase();
                String slot = tickets.get(code);
                if (slot == null) {
                    ex.sendResponseHeaders(404, -1);
                } else {
                    byte[] b = ("{\"code\":\"" + code + "\",\"relaySlot\":" + slot + "}").getBytes(StandardCharsets.UTF_8);
                    ex.getResponseHeaders().add("Content-Type", "application/json");
                    ex.sendResponseHeaders(200, b.length);
                    try (var os = ex.getResponseBody()) {
                        os.write(b);
                    }
                }
            }
        });
    }

    public void start() {
        http.start();
    }

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8090;
        var s = new SocialServer(port);
        s.start();
        System.out.println("Campfire social listening on 127.0.0.1:" + port + " (wayback09)");
    }
}
