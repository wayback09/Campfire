// Pure-Java, zero-dependency config model. YACL binds to this in mod:fabric.
package com.wayback09.campfire;

/** Campfire configuration model. */
public final class CampfireConfig {
    public boolean appearOffline = false;
    public boolean shareActivity = true;
    public boolean shareLastOnline = true;
    public boolean screenshotPreview = true;
    public boolean screenshotSounds = true;
    public int screenshotPreviewSeconds = 5;
    public boolean smoothZoomCamera = true;
    public boolean toggleToZoom = false;
    public boolean showFlameBadges = true;
    public boolean enableTelemetry = true;
}
