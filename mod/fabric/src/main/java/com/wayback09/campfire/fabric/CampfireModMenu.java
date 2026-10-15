package com.wayback09.campfire.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Mod Menu integration for Campfire.
 * Hooks the Mod Menu "Settings" button to the YACL configuration screen.
 */
public final class CampfireModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> CampfireSettings.screen(parent, CampfireFabric.CONFIG);
    }
}
