package com.rewind.whitelist;

import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class WhitelistManager {

    private boolean enabled = false;
    private final Set<String> whitelistedWorlds = new HashSet<>();
    private int defaultTimer = 60;
    private int maxChunksPerWorld = 50000;

    public void load(FileConfiguration config) {
        whitelistedWorlds.clear();

        enabled = config.getBoolean("whitelist.enabled", false);
        defaultTimer = config.getInt("whitelist.default-timer", 60);
        maxChunksPerWorld = config.getInt("whitelist.max-chunks-per-world", 50000);

        List<String> worlds = config.getStringList("whitelist.worlds");
        for (String world : worlds) {
            whitelistedWorlds.add(world.toLowerCase());
        }
    }

    public boolean isWorldWhitelisted(String worldName) {
        if (!enabled) return false;
        return whitelistedWorlds.contains(worldName.toLowerCase());
    }

    public boolean isWorldWhitelisted(World world) {
        return isWorldWhitelisted(world.getName());
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Set<String> getWhitelistedWorlds() {
        return new HashSet<>(whitelistedWorlds);
    }

    public int getDefaultTimer() {
        return defaultTimer;
    }

    public int getMaxChunksPerWorld() {
        return maxChunksPerWorld;
    }
}
