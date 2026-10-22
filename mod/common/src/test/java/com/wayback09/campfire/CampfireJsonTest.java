package com.wayback09.campfire;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CampfireJsonTest {

    @Test
    void parsesFlatObject() {
        Map<String, Object> obj = CampfireJson.parseObjectOrEmpty(
            "{\"username\":\"Steve\",\"status\":\"HOSTING\",\"updatedAt\":1234}");

        assertEquals("Steve", CampfireJson.getString(obj, "username", ""));
        assertEquals("HOSTING", CampfireJson.getString(obj, "status", ""));
        assertEquals(1234L, CampfireJson.getLong(obj, "updatedAt", 0));
    }

    @Test
    void parsesArrayOfObjects() {
        List<Object> list = CampfireJson.parseArrayOrEmpty(
            "[{\"username\":\"a\",\"status\":\"MENU\"},{\"username\":\"b\",\"status\":\"OFFLINE\"}]");

        assertEquals(2, list.size());
        assertEquals("a", CampfireJson.getString((Map<String, Object>) list.get(0), "username", ""));
        assertEquals("OFFLINE", CampfireJson.getString((Map<String, Object>) list.get(1), "status", ""));
    }

    @Test
    void handlesValuesContainingDelimitersThatBrokeTheOldParser() {
        // The previous implementation split on '{' and searched for '": "',
        // which mangled any value containing those sequences.
        Map<String, Object> obj = CampfireJson.parseObjectOrEmpty(
            "{\"worldName\":\"My \\\"World\\\" {v2}\",\"note\":\"a: b\"}");

        assertEquals("My \"World\" {v2}", CampfireJson.getString(obj, "worldName", ""));
        assertEquals("a: b", CampfireJson.getString(obj, "note", ""));
    }

    @Test
    void handlesEscapesAndUnicode() {
        Map<String, Object> obj = CampfireJson.parseObjectOrEmpty(
            "{\"name\":\"line\\nbreak\\ttab \\u0041 \\\"q\\\" \\\\slash\"}");

        assertEquals("line\nbreak\ttab A \"q\" \\slash", CampfireJson.getString(obj, "name", ""));
    }

    @Test
    void handlesEmptyContainers() {
        assertTrue(CampfireJson.parseObjectOrEmpty("{}").isEmpty());
        assertTrue(CampfireJson.parseArrayOrEmpty("[]").isEmpty());
    }

    @Test
    void returnsEmptyOnMalformedInput() {
        assertTrue(CampfireJson.parseObjectOrEmpty("{not json").isEmpty());
        assertTrue(CampfireJson.parseArrayOrEmpty("[1,2").isEmpty());
        assertNull(CampfireJson.parseOrNull("{\"a\":}"));
    }

    @Test
    void rejectsTrailingContent() {
        assertThrows(IllegalArgumentException.class, () -> CampfireJson.parse("{} {}"));
    }

    @Test
    void escapesProperly() {
        assertEquals("a\\\"b", CampfireJson.escape("a\"b"));
        assertEquals("a\\\\b", CampfireJson.escape("a\\b"));
        assertEquals("a\\nb", CampfireJson.escape("a\nb"));
        assertEquals("a\\u0001b", CampfireJson.escape("a\u0001b"));
        assertEquals("", CampfireJson.escape(null));
    }

    @Test
    void escapedOutputCanBeParsedBack() {
        String original = "weird: {value} \"quoted\" \\ slash\nnewline";
        String json = "{\"v\":" + CampfireJson.quote(original) + "}";
        Map<String, Object> obj = CampfireJson.parseObjectOrEmpty(json);
        assertEquals(original, CampfireJson.getString(obj, "v", ""));
    }
}
