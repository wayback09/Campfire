package com.wayback09.campfire.fabric;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.texture.PlayerSkinProvider;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.entity.player.SkinTextures;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves {@link SkinTextures} for arbitrary player profiles so the Campfire
 * UI can render avatars for friends who are not in the world.
 *
 * <p>In Minecraft 1.21.9+ the {@code SkinTextures} class moved from
 * {@code net.minecraft.client.util} to {@code net.minecraft.entity.player} and
 * {@link net.minecraft.client.network.PlayerListEntry} is now the supported way
 * to read an already-resolved skin. The old synchronous
 * {@code PlayerSkinProvider#getSkinTextures} no longer exists; fetching is
 * asynchronous, so this helper returns a default skin on the first call and
 * caches the real textures once they arrive.
 */
public final class SkinHelper {
    private SkinHelper() {}

    private static final Map<String, SkinTextures> CACHE = new ConcurrentHashMap<>();
    private static final Set<String> PENDING = ConcurrentHashMap.newKeySet();

    /**
     * @param provider the client skin provider (may be {@code null})
     * @param profile  the profile to look up (may be {@code null})
     * @param uuid     fallback UUID when the profile is unknown (may be {@code null})
     * @return textures that can be passed straight to
     *         {@link net.minecraft.client.gui.PlayerSkinDrawer#draw}; never
     *         {@code null} unless the client is not initialized
     */
    public static SkinTextures getSkinTextures(PlayerSkinProvider provider, GameProfile profile, UUID uuid) {
        String key = cacheKey(profile, uuid);

        SkinTextures cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }

        UUID id = uuid != null ? uuid : (profile != null ? profile.id() : null);
        MinecraftClient client = MinecraftClient.getInstance();

        // 1. In-game: the client already resolved this player's skin.
        if (client != null && client.getNetworkHandler() != null && id != null) {
            try {
                PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(id);
                if (entry != null) {
                    SkinTextures textures = entry.getSkinTextures();
                    if (textures != null) {
                        CACHE.put(key, textures);
                        return textures;
                    }
                }
            } catch (Throwable ignored) {
                // Fall through to the provider / default skin.
            }
        }

        // 2. Ask the provider to resolve it. This is asynchronous: the call
        //    returns immediately and the cache is filled once the textures are
        //    available, so the UI shows the default skin for a frame or two.
        if (provider != null && profile != null && PENDING.add(key)) {
            try {
                provider.fetchSkinTextures(profile).whenComplete((textures, error) -> {
                    PENDING.remove(key);
                    if (textures != null) {
                        textures.ifPresent(st -> CACHE.put(key, st));
                    }
                });
            } catch (Throwable ignored) {
                PENDING.remove(key);
            }
        }

        // 3. Immediate fallback (Steve / Alex) so the UI always has something.
        try {
            if (profile != null) {
                return DefaultSkinHelper.getSkinTextures(profile);
            }
            if (id != null) {
                return DefaultSkinHelper.getSkinTextures(id);
            }
        } catch (Throwable ignored) {
            // Not initialized yet.
        }

        return null;
    }

    private static String cacheKey(GameProfile profile, UUID uuid) {
        if (profile != null && profile.name() != null && !profile.name().isBlank()) {
            return profile.name().toLowerCase(java.util.Locale.ROOT);
        }
        UUID id = uuid != null ? uuid : (profile != null ? profile.id() : null);
        return id != null ? id.toString() : "unknown";
    }
}
