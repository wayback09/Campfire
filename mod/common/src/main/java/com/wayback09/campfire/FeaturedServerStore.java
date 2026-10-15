package com.wayback09.campfire;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Manages loading and caching of featured and community partner servers.
 */
public final class FeaturedServerStore {
    private final Path storagePath;
    private final List<FeaturedServer> servers = new ArrayList<>();

    public FeaturedServerStore(Path runDir) {
        this.storagePath = runDir.resolve("campfire").resolve("featured_servers.json");
        load();
    }

    public synchronized List<FeaturedServer> getServers() {
        return Collections.unmodifiableList(new ArrayList<>(servers));
    }

    public synchronized void load() {
        servers.clear();
        if (!Files.exists(storagePath)) {
            // Populate defaults
            servers.add(new FeaturedServer(
                "campfire_smp",
                "Campfire Community SMP",
                "play.campfiremc.net",
                "PARTNER",
                "Official community survival server with free-tier P2P world events."
            ));
            servers.add(new FeaturedServer(
                "creator_network",
                "Creator Playground",
                "hub.creatorsmp.net",
                "FEATURED",
                "Minigames, creative building, and custom community events."
            ));
            save();
            return;
        }

        try {
            String json = Files.readString(storagePath, StandardCharsets.UTF_8);
            // Lightweight line-by-line / block parser to avoid external JSON dependencies in common
            parseJson(json);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public synchronized void save() {
        try {
            Files.createDirectories(storagePath.getParent());
            StringBuilder sb = new StringBuilder("[\n");
            for (int i = 0; i < servers.size(); i++) {
                FeaturedServer s = servers.get(i);
                sb.append("  {\n");
                sb.append("    \"id\": \"").append(escape(s.id())).append("\",\n");
                sb.append("    \"name\": \"").append(escape(s.name())).append("\",\n");
                sb.append("    \"ip\": \"").append(escape(s.ip())).append("\",\n");
                sb.append("    \"tag\": \"").append(escape(s.tag())).append("\",\n");
                sb.append("    \"description\": \"").append(escape(s.description())).append("\"\n");
                sb.append("  }").append(i < servers.size() - 1 ? "," : "").append("\n");
            }
            sb.append("]\n");
            Files.writeString(storagePath, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void parseJson(String json) {
        String[] blocks = json.split("\\{");
        for (String block : blocks) {
            if (!block.contains("name") || !block.contains("ip")) continue;
            String id = extract(block, "id");
            String name = extract(block, "name");
            String ip = extract(block, "ip");
            String tag = extract(block, "tag");
            String desc = extract(block, "description");
            if (!name.isBlank() && !ip.isBlank()) {
                servers.add(new FeaturedServer(
                    id.isBlank() ? "srv_" + servers.size() : id,
                    name,
                    ip,
                    tag.isBlank() ? "FEATURED" : tag,
                    desc
                ));
            }
        }
    }

    private String extract(String block, String key) {
        String pattern = "\"" + key + "\":\\s*\"";
        int idx = block.indexOf(pattern);
        if (idx == -1) {
            pattern = "\"" + key + "\": \"";
            idx = block.indexOf(pattern);
            if (idx == -1) return "";
        }
        int start = idx + pattern.length();
        int end = block.indexOf("\"", start);
        if (end == -1) return "";
        return block.substring(start, end).replace("\\\"", "\"");
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\"", "\\\"");
    }
}
