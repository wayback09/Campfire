package com.wayback09.campfire;

import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * Compact peer-to-peer invite code codec.
 * Encodes an IPv4 address (4 bytes) + port (2 bytes) into a clean Base32 string (CAMP-XXXX-XXXX).
 * Works 100% offline / P2P with ZERO external servers required.
 */
public final class InviteCode {
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // Base32 without 0, 1, I, O to avoid confusion
    private static final String PREFIX = "CAMP-";

    private InviteCode() {}

    /**
     * Encodes an IPv4 address and port into an invite code.
     */
    public static String encode(String host, int port) {
        try {
            InetAddress addr = InetAddress.getByName(host);
            byte[] ip = addr.getAddress();
            if (ip.length != 4) {
                // If not IPv4, fallback to direct string
                return host + ":" + port;
            }

            long value = 0;
            for (byte b : ip) {
                value = (value << 8) | (b & 0xFFL);
            }
            value = (value << 16) | (port & 0xFFFFL);

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 10; i++) {
                int index = (int) (value & 0x1F);
                sb.append(ALPHABET.charAt(index));
                value >>>= 5;
            }
            String raw = sb.reverse().toString();
            return PREFIX + raw.substring(0, 5) + "-" + raw.substring(5);
        } catch (Exception e) {
            return host + ":" + port;
        }
    }

    /**
     * Decodes an invite code into host and port.
     * Accepts:
     * - "CAMP-XXXXX-XXXXX" or "CAMPXXXXXX"
     * - "host:port" or "ip"
     */
    public static InetSocketAddress decode(String input) {
        if (input == null || input.isBlank()) return null;
        String trimmed = input.trim();

        boolean hadPrefix = trimmed.toUpperCase(java.util.Locale.ROOT).startsWith(PREFIX);
        String clean = hadPrefix ? trimmed.substring(PREFIX.length()) : trimmed;
        clean = clean.replace("-", "").toUpperCase(java.util.Locale.ROOT);

        if (clean.length() == 10) {
            long value = 0;
            for (int i = 0; i < 10; i++) {
                char c = clean.charAt(i);
                int index = ALPHABET.indexOf(c);
                if (index < 0) return null;
                value = (value << 5) | index;
            }
            int port = (int) (value & 0xFFFFL);
            value >>>= 16;
            int b4 = (int) (value & 0xFFL);
            value >>>= 8;
            int b3 = (int) (value & 0xFFL);
            value >>>= 8;
            int b2 = (int) (value & 0xFFL);
            value >>>= 8;
            int b1 = (int) (value & 0xFFL);

            String host = b1 + "." + b2 + "." + b3 + "." + b4;
            return new InetSocketAddress(host, port);
        }

        // A "CAMP-" prefix means the input was meant to be an invite code. If it
        // did not decode, fail instead of silently treating the characters as a
        // hostname (which used to turn typos into bogus DNS lookups).
        if (hadPrefix) {
            return null;
        }

        // Direct host:port fallback. Keep the original casing of the host.
        if (trimmed.contains(":")) {
            String[] parts = trimmed.split(":", 2);
            try {
                int port = Integer.parseInt(parts[1].trim());
                return new InetSocketAddress(parts[0], port);
            } catch (Exception ignored) {
                return null;
            }
        }

        // Default Minecraft port 25565
        return new InetSocketAddress(trimmed, 25565);
    }

    /**
     * Checks if the given code is a 4-letter Go Relay room code.
     */
    public static boolean isRelayCode(String input) {
        if (input == null || input.isBlank()) return false;
        String clean = input.trim().toUpperCase();
        if (clean.startsWith("CAMP-")) clean = clean.substring(5);
        if (clean.startsWith("RELAY-")) clean = clean.substring(6);
        clean = clean.replace("-", "").trim();
        return clean.length() == 4;
    }

    /**
     * Extracts the 4-letter relay room code from an invite string.
     */
    public static String extractRelayCode(String input) {
        if (input == null) return "";
        String clean = input.trim().toUpperCase();
        if (clean.startsWith("CAMP-")) clean = clean.substring(5);
        if (clean.startsWith("RELAY-")) clean = clean.substring(6);
        return clean.replace("-", "").trim();
    }
}
