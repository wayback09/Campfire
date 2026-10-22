package com.wayback09.campfire;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Central configuration source for Campfire.
 *
 * <p>Values are resolved in this order (first match wins):
 * <ol>
 *   <li>JVM system property, e.g. {@code -Dcampfire.relay_host=relay.example.com}</li>
 *   <li>{@code campfire.properties} inside the Minecraft game directory</li>
 *   <li>The built-in default</li>
 * </ol>
 *
 * <p>This lives in {@code mod:common} so it stays free of Minecraft classes and
 * can be unit tested with plain javac.
 */
public final class CampfireProperties {
    /** File name looked up in the game directory. */
    public static final String FILE_NAME = "campfire.properties";

    private final Map<String, String> fileValues;

    private CampfireProperties(Map<String, String> fileValues) {
        this.fileValues = fileValues;
    }

    /**
     * Loads configuration from the given game directory. Never throws; a missing
     * or malformed file simply yields an empty set of overrides.
     */
    public static CampfireProperties load(Path gameDir) {
        Map<String, String> values = new LinkedHashMap<>();
        if (gameDir != null) {
            Path file = gameDir.resolve(FILE_NAME);
            if (Files.exists(file)) {
                Properties props = new Properties();
                try (InputStream in = Files.newInputStream(file)) {
                    props.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
                } catch (IOException | IllegalArgumentException ignored) {
                    // Malformed file: fall back to defaults.
                }
                for (String name : props.stringPropertyNames()) {
                    values.put(name, props.getProperty(name));
                }
            }
        }
        return new CampfireProperties(values);
    }

    /** Creates an instance with no file-based overrides (useful for tests). */
    public static CampfireProperties empty() {
        return new CampfireProperties(new LinkedHashMap<>());
    }

    public String get(String key, String defaultValue) {
        String sys = System.getProperty(key);
        if (sys != null && !sys.isBlank()) {
            return sys.trim();
        }
        String fromFile = fileValues.get(key);
        if (fromFile != null && !fromFile.isBlank()) {
            return fromFile.trim();
        }
        return defaultValue;
    }

    public int getInt(String key, int defaultValue) {
        String raw = get(key, null);
        if (raw == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String raw = get(key, null);
        if (raw == null) {
            return defaultValue;
        }
        return switch (raw.toLowerCase(java.util.Locale.ROOT)) {
            case "true", "yes", "1", "on" -> true;
            case "false", "no", "0", "off" -> false;
            default -> defaultValue;
        };
    }
}
