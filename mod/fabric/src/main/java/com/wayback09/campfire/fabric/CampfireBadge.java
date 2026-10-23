package com.wayback09.campfire.fabric;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Provides the Campfire flame badge for nametags and player lists.
 */
public final class CampfireBadge {
    public static final String FLAME_CHAR = "\uE0A0";
    public static final int FLAME_COLOR = 0xFF7A29; // Campfire ember orange

    private static long lastFriendsLoad = 0;
    private static final Set<String> friendNames = new HashSet<>();

    /** How often the live Campfire-user list is refreshed. */
    private static final long ONLINE_REFRESH_MS = 30_000L;
    /** Keep presence queries bounded on large public servers. */
    private static final int MAX_PRESENCE_QUERY = 50;

    private static volatile Set<String> onlineCampfireNames = Set.of();
    private static volatile boolean refreshingOnline = false;
    private static long lastOnlineRefresh = 0;

    private CampfireBadge() {}

    /**
     * Creates the flame badge text component with Campfire's ember coloring.
     */
    public static Text getBadge() {
        return Text.literal(FLAME_CHAR + " ")
            .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(FLAME_COLOR)));
    }

    /**
     * Checks if a player name is in the local friends cache.
     */
    public static boolean isFriend(String name) {
        if (name == null || name.isBlank()) return false;
        long now = System.currentTimeMillis();
        if (now - lastFriendsLoad > 3000) { // cache for 3 seconds
            lastFriendsLoad = now;
            friendNames.clear();
            try {
                Path p = FabricLoader.getInstance().getGameDir().resolve("campfire/friends.json");
                if (Files.exists(p)) {
                    for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                        String t = line.trim();
                        if (!t.isEmpty()) {
                            friendNames.add(t.toLowerCase(Locale.ROOT));
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        return friendNames.contains(name.toLowerCase(Locale.ROOT));
    }

    /** Clears cached remote presence (on disconnect / world change). */
    public static void reset() {
        onlineCampfireNames = Set.of();
        refreshingOnline = false;
        lastOnlineRefresh = 0;
    }

    /**
     * Refreshes the cached set of Campfire users in the current tab list.
     * Called from the client tick loop; never blocks the render thread.
     */
    public static void tick(MinecraftClient client) {
        if (client == null || client.getNetworkHandler() == null || client.player == null) {
            reset();
            return;
        }
        if (!CampfireFabric.CONFIG.showFlameBadges || CampfireFabric.presence == null) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastOnlineRefresh < ONLINE_REFRESH_MS || refreshingOnline) {
            return;
        }
        lastOnlineRefresh = now;

        List<String> names = new ArrayList<>();
        for (PlayerListEntry entry : client.getNetworkHandler().getPlayerList()) {
            if (entry == null || entry.getProfile() == null) continue;
            String name = entry.getProfile().name();
            if (name != null && !name.isBlank()) {
                names.add(name);
            }
            if (names.size() >= MAX_PRESENCE_QUERY) break;
        }
        if (names.size() <= 1) {
            onlineCampfireNames = Set.of();
            return;
        }

        refreshingOnline = true;
        final List<String> query = List.copyOf(names);
        CompletableFuture.supplyAsync(() -> CampfireFabric.presence.fetchOnlineUsernames(query))
            .thenAccept(result -> {
                refreshingOnline = false;
                // Keep the previous cache when the backend is unreachable.
                result.ifPresent(set -> onlineCampfireNames = Set.copyOf(set));
            })
            .exceptionally(ex -> {
                refreshingOnline = false;
                return null;
            });
    }

    /**
     * Determines whether the badge should be shown for a player.
     */
    public static boolean shouldShowBadge(UUID uuid, String name) {
        if (!CampfireFabric.CONFIG.showFlameBadges) {
            return false;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return false;
        }

        // Always show for the local player
        if (uuid != null && uuid.equals(client.player.getUuid())) {
            return true;
        }
        String selfName = CampfireFabric.getUsername();
        if (name != null && selfName != null && name.equalsIgnoreCase(selfName)) {
            return true;
        }

        // Show for any player that currently reports live Campfire presence.
        // This is what makes badges mutual for everyone in the same world.
        if (name != null && onlineCampfireNames.contains(name.toLowerCase(Locale.ROOT))) {
            return true;
        }

        // Show for friends
        if (isFriend(name)) {
            return true;
        }

        // Show for everyone if current world is actively hosted via Campfire
        if (CampfireFabric.presence != null && CampfireFabric.presence.getCurrent() != null) {
            if ("HOSTING".equals(CampfireFabric.presence.getCurrent().status())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Prepends the flame badge to a player's display name if eligible.
     */
    public static Text applyBadge(Text original, UUID uuid, String name) {
        if (original == null) {
            return original;
        }
        if (!shouldShowBadge(uuid, name)) {
            return original;
        }

        // Avoid adding duplicate flame badge
        if (original.getString().startsWith(FLAME_CHAR)) {
            return original;
        }

        MutableText modified = Text.empty();
        modified.append(getBadge());
        modified.append(original);
        return modified;
    }
}
