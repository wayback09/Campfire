// Local screenshot index. Pure Java; mod:fabric hooks capture events into this.
package com.wayback09.campfire;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Local screenshot store index. */
public final class ScreenshotStore {
    public record Entry(Path file, Instant takenAt) {}

    private final List<Entry> entries = Collections.synchronizedList(new ArrayList<>());
    private final Path dir;

    public ScreenshotStore(Path dir) {
        this.dir = dir;
    }

    public void add(Path file) {
        entries.add(0, new Entry(file, Instant.now()));
    }

    public List<Entry> list() {
        synchronized (entries) {
            return List.copyOf(entries);
        }
    }

    public Path dir() {
        return dir;
    }
}
