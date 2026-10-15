package com.wayback09.campfire.fabric;

import com.wayback09.campfire.FeaturedServer;
import com.wayback09.campfire.FeaturedServerStore;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ServerList;

import java.util.ArrayList;
import java.util.List;

/**
 * Injects featured servers into servers.dat so they appear natively at the top
 * of the vanilla MultiplayerScreen server list.
 *
 * Featured entries are identified by the "★ " prefix and are always placed at the
 * top of the visible list. User servers are preserved.
 */
public final class FeaturedServerInjector {
    public static final String FEATURED_PREFIX = "★ ";

    private FeaturedServerInjector() {}

    /**
     * Syncs featured servers into servers.dat.
     * Safe to call repeatedly — duplicate entries are prevented and user servers are preserved.
     */
    public static synchronized void syncToServerList(MinecraftClient client) {
        if (client == null || client.runDirectory == null) return;

        FeaturedServerStore store = new FeaturedServerStore(client.runDirectory.toPath());
        List<FeaturedServer> featured = store.getServers();

        ServerList list = new ServerList(client);
        list.loadFile();

        // 1. Collect all non-featured user servers currently in the list
        List<ServerInfo> userServers = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            ServerInfo si = list.get(i);
            if (!isFeatured(si)) {
                userServers.add(si);
            }
        }

        // Also merge any previously backed up user servers if we were in filter mode
        List<ServerInfo> backup = UserServerBackup.get();
        if (backup != null) {
            for (ServerInfo b : backup) {
                boolean alreadyIn = false;
                for (ServerInfo u : userServers) {
                    if (u.address.equalsIgnoreCase(b.address)) {
                        alreadyIn = true;
                        break;
                    }
                }
                if (!alreadyIn) userServers.add(b);
            }
        }

        if (CampfireMenuHooks.filterFeaturedOnly) {
            // In filter mode, save user servers to backup and do not re-add to visible list
            UserServerBackup.save(userServers);
        } else {
            // Not in filter mode, clear backup
            UserServerBackup.clear();
        }

        // 2. Clear all visible entries
        while (list.size() > 0) {
            list.remove(list.get(0));
        }

        // 3. Add featured servers at the top (hidden = false so they are VISIBLE!)
        for (FeaturedServer fs : featured) {
            ServerInfo info = new ServerInfo(
                FEATURED_PREFIX + "[" + fs.tag() + "] " + fs.name(),
                fs.ip(),
                ServerInfo.ServerType.OTHER
            );
            // In Minecraft Yarn, second param is 'boolean hidden'.
            // Passing FALSE makes it visible in the multiplayer server list!
            list.add(info, false);
        }

        // 4. Add user servers after featured servers (if not in featured-only filter mode)
        if (!CampfireMenuHooks.filterFeaturedOnly) {
            for (ServerInfo userServer : userServers) {
                list.add(userServer, false);
            }
        }

        list.saveFile();
    }

    /**
     * Restores user servers after leaving filter mode.
     */
    public static synchronized void restoreUserServers(MinecraftClient client) {
        CampfireMenuHooks.filterFeaturedOnly = false;
        syncToServerList(client);
    }

    public static boolean isFeatured(ServerInfo info) {
        return info != null && info.name != null && info.name.startsWith(FEATURED_PREFIX);
    }

    /** Holds a backup of user servers while in "featured only" filter mode. */
    private static final class UserServerBackup {
        private static List<ServerInfo> backup;
        static void save(List<ServerInfo> list) {
            backup = new ArrayList<>(list);
        }
        static List<ServerInfo> get() { return backup; }
        static void clear() { backup = null; }
    }
}
