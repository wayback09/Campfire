package com.wayback09.campfire;

/**
 * Model representing a featured or community partner server.
 */
public record FeaturedServer(
    String id,
    String name,
    String ip,
    String tag,
    String description
) {}
