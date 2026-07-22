package com.rewind;

import java.util.logging.Logger;

public class DebugManager {

    private final Logger logger;
    private boolean enabled;

    public DebugManager(Logger logger, boolean enabled) {
        this.logger = logger;
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void log(String message) {
        if (enabled) {
            logger.info("[DEBUG] " + message);
        }
    }

    public void log(String format, Object... args) {
        if (enabled) {
            logger.info("[DEBUG] " + String.format(format, args));
        }
    }

    public void warn(String message) {
        if (enabled) {
            logger.warning("[DEBUG] " + message);
        }
    }

    public void snapshot(String world, int chunkX, int chunkZ, String action) {
        log("Snapshot %s in %s [%d, %d]", action, world, chunkX, chunkZ);
    }

    public void restore(String world, int chunkX, int chunkZ, String reason) {
        log("Restore queued in %s [%d, %d] - %s", world, chunkX, chunkZ, reason);
    }

    public void region(String name, String action) {
        log("Region '%s' %s", name, action);
    }

    public void queue(String type, int size) {
        log("Queue '%s' size: %d", type, size);
    }

    public void timer(String world, int chunkX, int chunkZ, int seconds) {
        log("Timer set in %s [%d, %d] - %ds", world, chunkX, chunkZ, seconds);
    }

    public void performance(String action, long startMs) {
        long elapsed = System.currentTimeMillis() - startMs;
        log("Performance - %s took %dms", action, elapsed);
    }
}
