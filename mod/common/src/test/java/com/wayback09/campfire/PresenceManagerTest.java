package com.wayback09.campfire;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit tests for the presence payloads used by the flame badge. */
class PresenceManagerTest {

    @Test
    void keepsOnlyLivePresenceRows() {
        long now = System.currentTimeMillis();
        String json = "{\"results\":["
            + "{\"username\":\"Alpha\",\"status\":\"HOSTING\",\"updatedAt\":" + now + "},"
            + "{\"username\":\"Beta\",\"status\":\"OFFLINE\",\"updatedAt\":" + now + "},"
            + "{\"username\":\"Gamma\",\"status\":\"MENU\",\"updatedAt\":" + (now - 120_000) + "},"
            + "{\"username\":\"Delta\",\"status\":\"MULTIPLAYER\",\"updatedAt\":" + now + "}"
            + "]}";

        Set<String> online = PresenceManager.parseOnlineUsernames(json);

        assertEquals(Set.of("alpha", "delta"), online);
    }

    @Test
    void acceptsRowsWithoutAnUpdatedAt() {
        String json = "{\"results\":[{\"username\":\"Echo\",\"status\":\"SINGLEPLAYER\"}]}";

        assertEquals(Set.of("echo"), PresenceManager.parseOnlineUsernames(json));
    }

    @Test
    void returnsEmptyForMalformedResponses() {
        assertTrue(PresenceManager.parseOnlineUsernames("not json").isEmpty());
        assertTrue(PresenceManager.parseOnlineUsernames("{}").isEmpty());
        assertTrue(PresenceManager.parseOnlineUsernames("{\"results\":[]}").isEmpty());
        assertTrue(PresenceManager.parseOnlineUsernames("{\"results\":[{\"status\":\"MENU\"}]}").isEmpty());
    }
}
