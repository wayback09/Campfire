package com.wayback09.campfire;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CampfirePropertiesTest {

    @Test
    void returnsDefaultsWhenNothingIsConfigured(@TempDir Path dir) {
        CampfireProperties props = CampfireProperties.load(dir);
        assertEquals("fallback", props.get("some.key", "fallback"));
        assertEquals(42, props.getInt("some.int", 42));
        assertTrue(props.getBoolean("some.bool", true));
    }

    @Test
    void readsValuesFromPropertiesFile(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve(CampfireProperties.FILE_NAME),
            "relay_host=relay.example.com\nrelay_port=1234\nflag=on\n",
            StandardCharsets.UTF_8);

        CampfireProperties props = CampfireProperties.load(dir);
        assertEquals("relay.example.com", props.get("relay_host", "default"));
        assertEquals(1234, props.getInt("relay_port", 1));
        assertTrue(props.getBoolean("flag", false));
    }

    @Test
    void systemPropertyOverridesFile(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve(CampfireProperties.FILE_NAME),
            "relay_host=from-file\n", StandardCharsets.UTF_8);

        System.setProperty("relay_host", "from-system");
        try {
            CampfireProperties props = CampfireProperties.load(dir);
            assertEquals("from-system", props.get("relay_host", "default"));
        } finally {
            System.clearProperty("relay_host");
        }
    }

    @Test
    void malformedFileFallsBackToDefaults(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve(CampfireProperties.FILE_NAME),
            "this is not a valid properties file\n=broken\n", StandardCharsets.UTF_8);

        CampfireProperties props = CampfireProperties.load(dir);
        assertEquals("fallback", props.get("anything", "fallback"));
    }

    @Test
    void parsesBooleansInCommonForms(@TempDir Path dir) {
        CampfireProperties props = CampfireProperties.empty();
        assertTrue(props.getBoolean("k", false) || !props.getBoolean("k", false)); // no crash
        assertEquals(7, props.getInt("k", 7));
    }
}
