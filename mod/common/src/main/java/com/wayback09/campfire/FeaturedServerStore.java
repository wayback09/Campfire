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
 * Loads featured partner servers from (in priority order):
 *  1. Remote URL (fetched async in background, updates cache)
 *  2. Local user/admin JSON: .minecraft/campfire/featured_servers.json
 *  3. Bundled resource inside the mod JAR: /campfire/featured_servers.json
 *  4. Built-in fallback defaults (ensures servers always show up)
 *
 * To add or remove partners dynamically, update the remote JSON at REMOTE_URL
 * or edit .minecraft/campfire/featured_servers.json.
 */
public final class FeaturedServerStore {
    /** Default remote endpoint for the featured server list. */
    public static final String DEFAULT_REMOTE_URL =
        "https://raw.githubusercontent.com/wayback09/campfire/main/campfire/featured_servers.json";

    private static final List<FeaturedServer> BUILTIN_DEFAULTS = List.of(
        new FeaturedServer(
            "islanders_mc",
            "IslandersMC",
            "IslandersMC.net",
            "PARTNER",
            "Island survival, community events, and Campfire P2P integration."
        )
    );

    private final Path cachePath;
    private final String remoteUrl;
    private final List<FeaturedServer> servers = new ArrayList<>();

    public FeaturedServerStore(Path runDir) {
        this.cachePath = runDir.resolve("campfire/featured_servers.json");
        this.remoteUrl = System.getProperty("campfire.featured_servers_url", DEFAULT_REMOTE_URL);
        load();
    }

    public synchronized List<FeaturedServer> getServers() {
        if (servers.isEmpty()) {
            return Collections.unmodifiableList(BUILTIN_DEFAULTS);
        }
        return Collections.unmodifiableList(new ArrayList<>(servers));
    }

    /**
     * Loads servers from cache, bundled resource, or built-in defaults,
     * then kicks off an async remote refresh.
     */
    public synchronized void load() {
        servers.clear();

        // 1. Try local cache first (allows user/admin customization without touching mod)
        if (Files.exists(cachePath)) {
            try {
                String content = Files.readString(cachePath, StandardCharsets.UTF_8);
                // If cache has old placeholder servers, purge it
                if (content.contains("Campfire Community SMP") || content.contains("Creator Playground")) {
                    Files.deleteIfExists(cachePath);
                } else {
                    parseInto(content);
                    if (!servers.isEmpty()) {
                        refreshRemoteAsync();
                        return;
                    }
                }
            } catch (Exception ignored) {}
        }

        // 2. Fall back to bundled resource inside JAR
        String bundled = loadBundled();
        if (bundled != null && !bundled.isBlank()) {
            parseInto(bundled);
            if (!servers.isEmpty()) {
                writeCache(bundled); // seed the local cache for user editing
                refreshRemoteAsync();
                return;
            }
        }

        // 3. Fall back to built-in defaults so servers ALWAYS show up
        servers.addAll(BUILTIN_DEFAULTS);
        writeCache(exportJson(servers));

        // 4. Try remote update in background
        refreshRemoteAsync();
    }

    /**
     * Fetches the remote list in the background and updates the cache.
     */
    public void refreshRemoteAsync() {
        CompletableFuture.runAsync(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) URI.create(remoteUrl).toURL().openConnection();
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(6000);
                conn.setRequestProperty("User-Agent", "Campfire-Mod/1.0");
                if (conn.getResponseCode() == 200) {
                    String json = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                    List<FeaturedServer> parsed = new ArrayList<>();
                    parseList(json, parsed);
                    if (!parsed.isEmpty()) {
                        writeCache(json);
                        synchronized (FeaturedServerStore.this) {
                            servers.clear();
                            servers.addAll(parsed);
                        }
                    }
                }
                conn.disconnect();
            } catch (Exception ignored) {
                // Network unavailable or URL not yet created — keeps existing cache / bundled
            }
        });
    }

    private String loadBundled() {
        String[] paths = {"/campfire/featured_servers.json", "campfire/featured_servers.json"};
        for (String p : paths) {
            try (InputStream in = FeaturedServerStore.class.getResourceAsStream(p)) {
                if (in != null) return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException ignored) {}

            try (InputStream in = FeaturedServerStore.class.getClassLoader().getResourceAsStream(p.startsWith("/") ? p.substring(1) : p)) {
                if (in != null) return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException ignored) {}

            try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(p.startsWith("/") ? p.substring(1) : p)) {
                if (in != null) return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException ignored) {}
        }
        return null;
    }

    private void writeCache(String json) {
        try {
            if (cachePath.getParent() != null) {
                Files.createDirectories(cachePath.getParent());
            }
            Files.writeString(cachePath, json, StandardCharsets.UTF_8);
        } catch (IOException ignored) {}
    }

    private void parseInto(String json) {
        parseList(json, servers);
    }

    private static void parseList(String json, List<FeaturedServer> target) {
        if (json == null || json.isBlank()) return;
        String[] blocks = json.split("\\{");
        for (String block : blocks) {
            if (!block.contains("name") || !block.contains("ip")) continue;
            String id   = extractStr(block, "id");
            String name = extractStr(block, "name");
            String ip   = extractStr(block, "ip");
            String tag  = extractStr(block, "tag");
            String desc = extractStr(block, "description");
            if (!name.isBlank() && !ip.isBlank()) {
                target.add(new FeaturedServer(
                    id.isBlank() ? "srv_" + target.size() : id,
                    name, ip,
                    tag.isBlank() ? "FEATURED" : tag,
                    desc
                ));
            }
        }
    }

    private static String extractStr(String block, String key) {
        for (String pattern : new String[]{"\"" + key + "\": \"", "\"" + key + "\":\"", "\"" + key + "\":"}) {
            int idx = block.indexOf(pattern);
            if (idx == -1) continue;
            int s = idx + pattern.length();
            while (s < block.length() && (block.charAt(s) == ' ' || block.charAt(s) == '"')) {
                if (block.charAt(s) == '"') { s++; break; }
                s++;
            }
            int e = block.indexOf("\"", s);
            return e == -1 ? "" : block.substring(s, e);
        }
        return "";
    }

    private static String exportJson(List<FeaturedServer> list) {
        StringBuilder sb = new StringBuilder("[\n");
        for (int i = 0; i < list.size(); i++) {
            FeaturedServer s = list.get(i);
            sb.append("  {\n");
            sb.append("    \"id\": \"").append(s.id()).append("\",\n");
            sb.append("    \"name\": \"").append(s.name()).append("\",\n");
            sb.append("    \"ip\": \"").append(s.ip()).append("\",\n");
            sb.append("    \"tag\": \"").append(s.tag()).append("\",\n");
            sb.append("    \"description\": \"").append(s.description()).append("\"\n");
            sb.append("  }").append(i < list.size() - 1 ? ",\n" : "\n");
        }
        sb.append("]\n");
        return sb.toString();
    }
}
