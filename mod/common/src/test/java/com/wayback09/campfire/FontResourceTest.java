package com.wayback09.campfire;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the {@code minecraft:default} font overlay shipped by Campfire.
 *
 * <p>Bitmap font providers resolve {@code file} relative to
 * {@code assets/<namespace>/textures/}. Writing {@code campfire:textures/font/flame.png}
 * therefore resolves to {@code campfire:textures/textures/font/flame.png}, which does not
 * exist; Minecraft then aborts font loading, discards every resource pack and renders a
 * black screen. This test catches that mistake (and a missing texture) at build time.
 */
class FontResourceTest {

    @Test
    void defaultFontBitmapProvidersPointAtExistingTextures() throws Exception {
        String json;
        try (InputStream in = getClass().getResourceAsStream("/assets/minecraft/font/default.json")) {
            assertNotNull(in, "assets/minecraft/font/default.json is missing from the jar");
            json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        Map<String, Object> root = CampfireJson.parseObjectOrEmpty(json);
        assertFalse(root.isEmpty(), "default.json did not parse as a JSON object");

        List<Object> providers = root.get("providers") instanceof List<?> list
                ? castList(list)
                : List.of();
        assertFalse(providers.isEmpty(), "default.json declares no font providers");

        int bitmapProviders = 0;
        for (Object entry : providers) {
            if (!(entry instanceof Map<?, ?> raw)) {
                continue;
            }
            Map<String, Object> provider = castMap(raw);
            if (!"bitmap".equals(CampfireJson.getString(provider, "type", ""))) {
                continue;
            }
            bitmapProviders++;

            String file = CampfireJson.getString(provider, "file", "");
            assertFalse(file.isBlank(), "bitmap provider has no file");
            assertFalse(file.startsWith("textures/"),
                    "bitmap provider file must omit the leading 'textures/' (got " + file + ")");

            int colon = file.indexOf(':');
            String namespace = colon >= 0 ? file.substring(0, colon) : "minecraft";
            String path = colon >= 0 ? file.substring(colon + 1) : file;
            String resource = "/assets/" + namespace + "/textures/" + path;
            try (InputStream texture = getClass().getResourceAsStream(resource)) {
                assertNotNull(texture, "bitmap provider points at a missing texture: " + resource);
            }
        }
        assertTrue(bitmapProviders > 0, "default.json declares no bitmap providers");
    }

    @SuppressWarnings("unchecked")
    private static List<Object> castList(List<?> list) {
        return (List<Object>) list;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }
}
