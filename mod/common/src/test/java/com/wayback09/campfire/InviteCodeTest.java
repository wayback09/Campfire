package com.wayback09.campfire;

import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.*;

class InviteCodeTest {

    @Test
    void encodesIPv4AndPortToCAMPFormat() {
        String code = InviteCode.encode("192.168.1.10", 25565);
        assertTrue(code.startsWith("CAMP-"), "expected CAMP- prefix, got " + code);
        // CAMP-XXXXX-XXXXX
        assertEquals(16, code.length(), "unexpected invite code length: " + code);
        assertTrue(code.matches("CAMP-[A-Z2-9]{5}-[A-Z2-9]{5}"), "unexpected shape: " + code);
    }

    @Test
    void roundTripsIPv4AndPort() {
        String[] hosts = {"192.168.1.10", "10.0.0.1", "255.255.255.255", "0.0.0.0"};
        int[] ports = {25565, 1, 65535, 8080};
        for (String host : hosts) {
            for (int port : ports) {
                String code = InviteCode.encode(host, port);
                InetSocketAddress decoded = InviteCode.decode(code);
                assertNotNull(decoded, "failed to decode " + code);
                assertEquals(host, decoded.getAddress().getHostAddress(), "host mismatch for " + code);
                assertEquals(port, decoded.getPort(), "port mismatch for " + code);
            }
        }
    }

    @Test
    void decodesUnhyphenatedAndLowercaseForms() {
        String code = InviteCode.encode("192.168.1.10", 25565);
        String raw = code.substring("CAMP-".length()).replace("-", "");

        assertEquals("192.168.1.10", InviteCode.decode(raw).getAddress().getHostAddress());
        assertEquals("192.168.1.10", InviteCode.decode(code.toLowerCase()).getAddress().getHostAddress());
    }

    @Test
    void decodesPlainHostAndPort() {
        InetSocketAddress a = InviteCode.decode("example.com:1234");
        assertEquals("example.com", a.getHostString(), "host casing should be preserved");
        assertEquals(1234, a.getPort());
    }

    @Test
    void defaultsToMinecraftPortWhenNoneGiven() {
        assertEquals(25565, InviteCode.decode("example.com").getPort());
    }

    @Test
    void rejectsMalformedInput() {
        assertNull(InviteCode.decode(null));
        assertNull(InviteCode.decode(""));
        assertNull(InviteCode.decode("   "));
        // '0' is not in the alphabet, so this cannot be a valid invite code and
        // must not be reinterpreted as a hostname.
        assertNull(InviteCode.decode("CAMP-0000-0000"));
        assertNull(InviteCode.decode("CAMP-ABCDE-FGHIJ"));
    }

    @Test
    void rejectsInvalidPortInDirectForm() {
        assertNull(InviteCode.decode("example.com:notaport"));
        assertNull(InviteCode.decode("example.com:"));
    }

    @Test
    void detectsRelayCodes() {
        assertTrue(InviteCode.isRelayCode("CAMP-ABCD"));
        assertTrue(InviteCode.isRelayCode("abcd"));
        assertTrue(InviteCode.isRelayCode("RELAY-ABCD"));
        assertFalse(InviteCode.isRelayCode("CAMP-ABCDE"));
        assertFalse(InviteCode.isRelayCode(null));
    }

    @Test
    void extractsRelayCode() {
        assertEquals("ABCD", InviteCode.extractRelayCode("CAMP-ABCD"));
        assertEquals("ABCD", InviteCode.extractRelayCode("relay-abcd"));
        assertEquals("ABCD", InviteCode.extractRelayCode("ABCD"));
    }
}
