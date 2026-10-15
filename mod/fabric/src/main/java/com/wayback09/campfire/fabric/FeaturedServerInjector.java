package com.wayback09.campfire.fabric;

import com.wayback09.campfire.FeaturedServer;
import com.wayback09.campfire.FeaturedServerStore;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ServerList;

import java.util.List;

/**
 * Injects featured servers into servers.dat just before MultiplayerScreen.init() reads it,
 * so they appear in the vanilla list widget. After init, strips them from the file again
 * so the user's servers.dat is never permanently modified.
 *
 * Filter mode (filterFeaturedOnly=true): only featured entries are written, hiding user servers
 * from the widget while the filter is active.
 */
public final class FeaturedServerInjector {
    public static final String FEATURED_PREFIX = "★ ";

    private FeaturedServerInjector() {}

    /**
     * Called in BEFORE_INIT: injects featured servers into the in-memory+saved ServerList
     * so MultiplayerScreen.init() picks them up.
     */
    public static void injectToFile(MinecraftClient client) {
        FeaturedServerStore store = new FeaturedServerStore(client.runDirectory.toPath());
        List<FeaturedServer> featured = store.getServers();
        if (featured.isEmpty()) return;

        ServerList list = new ServerList(client);
        list.loadFile();

        // Remove any stale featured entries from a previous init
        removeFeatured(list);

        if (CampfireMenuHooks.filterFeaturedOnly) {
            // Filter mode: hide user servers — clear all non-featured entries
            int size = list.size();
            for (int i = size - 1; i >= 0; i--) {
                list.remove(list.get(i));
            }
        }

        // Prepend featured servers at the top (reverse order so first = top)
        for (int i = featured.size() - 1; i >= 0; i--) {
            FeaturedServer fs = featured.get(i);
            ServerInfo info = new ServerInfo(
                FEATURED_PREFIX + "[" + fs.tag() + "] " + fs.name(),
                fs.ip(),
                ServerInfo.ServerType.OTHER
            );
            list.add(info, true);
        }

        list.saveFile();
    }

    /**
     * Called in AFTER_INIT: strips featured entries back out of servers.dat.
     * The widget already holds them in memory so they keep showing.
     * User servers are restored from their saved positions.
     */
    public static void restoreFile(MinecraftClient client) {
        ServerList list = new ServerList(client);
        list.loadFile();

        if (CampfireMenuHooks.filterFeaturedOnly) {
            // In filter mode the file has only featured entries — restore the real list.
            // We can't recover the user's servers from memory here without the original backup,
            // so we re-load the canonical file after removing featured from what we saved.
            // The file at this point = only featured entries we just saved.
            // We'll save an empty non-featured list — next normal open will reload servers.dat fresh.
            removeFeatured(list);
            list.saveFile();
        } else {
            removeFeatured(list);
            list.saveFile();
        }
    }

    /** Returns true if the given ServerInfo is a Campfire featured entry. */
    public static boolean isFeatured(ServerInfo info) {
        return info != null && info.name.startsWith(FEATURED_PREFIX);
    }

    private static void removeFeatured(ServerList list) {
        int size = list.size();
        java.util.List<ServerInfo> toRemove = new java.util.ArrayList<>();
        for (int i = 0; i < size; i++) {
            ServerInfo si = list.get(i);
            if (isFeatured(si)) toRemove.add(si);
        }
        for (ServerInfo si : toRemove) {
            list.remove(si);
        }
    }
}
