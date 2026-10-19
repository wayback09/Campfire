package com.wayback09.campfire.fabric;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.PlayerSkinProvider;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.SkinTextures;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class SkinHelper {
    private SkinHelper() {}

    private static final Map<String, SkinTextures> CACHE = new ConcurrentHashMap<>();

    public static SkinTextures getSkinTextures(PlayerSkinProvider provider, GameProfile profile, UUID uuid) {
        String key = profile != null ? profile.getName() : (uuid != null ? uuid.toString() : "unknown");
        SkinTextures cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }

        MinecraftClient client = MinecraftClient.getInstance();

        // 1. If in-game, get directly from the local player entity
        if (client != null && client.player != null && profile != null
                && client.getSession() != null && profile.getName().equalsIgnoreCase(client.getSession().getUsername())) {
            try {
                SkinTextures st = client.player.getSkinTextures();
                if (st != null) {
                    CACHE.put(key, st);
                    return st;
                }
            } catch (Throwable ignored) {}
        }

        // 2. If this is the logged-in client user, use client.getGameProfile() which contains Mojang textures
        GameProfile effectiveProfile = profile;
        if (client != null && client.getGameProfile() != null && profile != null) {
            String clientName = client.getGameProfile().getName();
            UUID clientUuid = client.getGameProfile().getId();
            if ((clientName != null && clientName.equalsIgnoreCase(profile.getName()))
                    || (clientUuid != null && clientUuid.equals(profile.getId()))) {
                effectiveProfile = client.getGameProfile();
            }
        }

        // 3. Try to get skin from PlayerSkinProvider specifically for GameProfile
        if (provider != null && effectiveProfile != null) {
            for (Method m : provider.getClass().getMethods()) {
                if (m.getParameterCount() == 1 && m.getParameterTypes()[0].isAssignableFrom(GameProfile.class)) {
                    try {
                        m.setAccessible(true);
                        Object res = m.invoke(provider, effectiveProfile);
                        if (res instanceof SkinTextures st) {
                            CACHE.put(key, st);
                            return st;
                        } else if (res instanceof Supplier<?> supp) {
                            Object inner = supp.get();
                            if (inner instanceof SkinTextures st) {
                                CACHE.put(key, st);
                                return st;
                            }
                        } else if (res instanceof CompletableFuture<?> future) {
                            future.thenAccept(obj -> {
                                if (obj instanceof SkinTextures st) {
                                    CACHE.put(key, st);
                                }
                            });
                        }
                    } catch (Throwable ignored) {}
                }
            }
        }

        // 4. Fallback to DefaultSkinHelper (Steve / Alex) while loading or offline
        UUID fallbackUuid = (effectiveProfile != null && effectiveProfile.getId() != null)
                ? effectiveProfile.getId()
                : (uuid != null ? uuid : UUID.randomUUID());
        try {
            return DefaultSkinHelper.getSkinTextures(fallbackUuid);
        } catch (Throwable ignored) {}

        return null;
    }
}
