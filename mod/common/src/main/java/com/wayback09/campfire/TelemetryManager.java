package com.wayback09.campfire;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Anonymous, privacy-first telemetry manager for Campfire.
 * Never collects usernames, IPs, player locations, or sensitive info.
 * Opt-out is fully respected via CampfireConfig.enableTelemetry.
 */
public final class TelemetryManager {
    public static final String DEFAULT_URL = PresenceManager.DEFAULT_URL;

    private final Path installIdPath;
    private final String apiUrl;
    private final CampfireConfig config;
    private String installId;

    public TelemetryManager(Path runDir, CampfireConfig config) {
        this(runDir, config, CampfireProperties.load(runDir));
    }

    public TelemetryManager(Path runDir, CampfireConfig config, CampfireProperties props) {
        this.installIdPath = runDir.resolve("campfire/install_id.txt");
        this.apiUrl = props.get(PresenceManager.PROP_SOCIAL_URL, DEFAULT_URL).replaceAll("/+$", "");
        this.config = config;
        this.installId = loadOrCreateInstallId();
    }

    private String loadOrCreateInstallId() {
        try {
            if (Files.exists(installIdPath)) {
                String existing = Files.readString(installIdPath, StandardCharsets.UTF_8).trim();
                if (!existing.isEmpty()) return existing;
            }
            Files.createDirectories(installIdPath.getParent());
            String newId = UUID.randomUUID().toString();
            Files.writeString(installIdPath, newId, StandardCharsets.UTF_8);
            return newId;
        } catch (IOException e) {
            return UUID.randomUUID().toString();
        }
    }

    public String getInstallId() {
        return installId;
    }

    /**
     * Reports anonymous client launch (Mod version, MC version, OS, Loader).
     */
    public void sendLaunch(String modVersion, String mcVersion, String loader) {
        sendEvent("launch", modVersion, mcVersion, loader, "{}");
    }

    /**
     * Reports world hosting event with UPnP port forward status.
     */
    public void sendHostWorld(String modVersion, String mcVersion, String loader, boolean upnpSuccess) {
        sendEvent("host_world", modVersion, mcVersion, loader, "{\"upnpSuccess\":" + upnpSuccess + "}");
    }

    /**
     * Reports world join event.
     */
    public void sendJoinWorld(String modVersion, String mcVersion, String loader, boolean isInviteCode) {
        sendEvent("join_world", modVersion, mcVersion, loader, "{\"isInviteCode\":" + isInviteCode + "}");
    }

    /**
     * Sends an anonymous telemetry event asynchronously to the backend.
     */
    public void sendEvent(String eventName, String modVersion, String mcVersion, String loader, String detailsJson) {
        if (config != null && !config.enableTelemetry) {
            return; // Player opted out
        }

        CompletableFuture.runAsync(() -> {
            try {
                String osName = System.getProperty("os.name", "unknown").toLowerCase();
                String os = osName.contains("win") ? "windows"
                          : osName.contains("mac") ? "macos"
                          : osName.contains("nix") || osName.contains("nux") ? "linux"
                          : "other";
                String javaVer = System.getProperty("java.version", "21");

                String payload = "{"
                    + "\"installId\":" + CampfireJson.quote(installId) + ","
                    + "\"event\":" + CampfireJson.quote(eventName) + ","
                    + "\"modVersion\":" + CampfireJson.quote(modVersion) + ","
                    + "\"mcVersion\":" + CampfireJson.quote(mcVersion) + ","
                    + "\"loader\":" + CampfireJson.quote(loader) + ","
                    + "\"os\":" + CampfireJson.quote(os) + ","
                    + "\"java\":" + CampfireJson.quote(javaVer) + ","
                    + "\"details\":" + (detailsJson != null ? detailsJson : "{}")
                    + "}";

                HttpURLConnection conn = (HttpURLConnection) URI.create(apiUrl + "/telemetry/event").toURL().openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.getOutputStream().write(payload.getBytes(StandardCharsets.UTF_8));
                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception ignored) {
                // Silently ignore telemetry transmission failures
            }
        });
    }
}
