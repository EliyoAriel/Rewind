package com.rewind.regions;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

public class RegionStorage {

    private final Logger logger;
    private final File dataFolder;
    private File regionsFile;
    private FileConfiguration config;

    public RegionStorage(Logger logger, File dataFolder) {
        this.logger = logger;
        this.dataFolder = dataFolder;
    }

    public void load() {
        regionsFile = new File(dataFolder, "regions.yml");
        if (!regionsFile.exists()) {
            try {
                regionsFile.createNewFile();
            } catch (IOException e) {
                logger.warning("Failed to create regions.yml: " + e.getMessage());
            }
        }
        config = YamlConfiguration.loadConfiguration(regionsFile);
    }

    public void save() {
        if (config != null && regionsFile != null) {
            try {
                config.save(regionsFile);
            } catch (IOException e) {
                logger.warning("Failed to save regions.yml: " + e.getMessage());
            }
        }
    }

    public void addRegion(Region region) {
        String path = "regions." + region.getName();
        config.set(path + ".world", region.getWorldName());
        config.set(path + ".type", region.getType().name());
        config.set(path + ".timer", region.getTimer());

        if (region.getType() == Region.Type.CUBOID) {
            config.set(path + ".min-x", region.getMinX());
            config.set(path + ".min-y", region.getMinY());
            config.set(path + ".min-z", region.getMinZ());
            config.set(path + ".max-x", region.getMaxX());
            config.set(path + ".max-y", region.getMaxY());
            config.set(path + ".max-z", region.getMaxZ());
        } else {
            config.set(path + ".center-x", region.getCenterX());
            config.set(path + ".center-y", region.getCenterY());
            config.set(path + ".center-z", region.getCenterZ());
            config.set(path + ".radius", region.getRadius());
        }

        save();
    }

    public void removeRegion(String name) {
        config.set("regions." + name, null);
        save();
    }

    public Region loadRegion(String name) {
        String path = "regions." + name;
        if (!config.contains(path)) return null;

        String world = config.getString(path + ".world", "world");
        String typeStr = config.getString(path + ".type", "CUBOID");
        int timer = config.getInt(path + ".timer", 60);

        Region.Type type = Region.Type.valueOf(typeStr);
        Region region = new Region(name, world, type, timer);

        if (type == Region.Type.CUBOID) {
            region.setMinX(config.getInt(path + ".min-x"));
            region.setMinY(config.getInt(path + ".min-y"));
            region.setMinZ(config.getInt(path + ".min-z"));
            region.setMaxX(config.getInt(path + ".max-x"));
            region.setMaxY(config.getInt(path + ".max-y"));
            region.setMaxZ(config.getInt(path + ".max-z"));
        } else {
            region.setCenterX(config.getInt(path + ".center-x"));
            region.setCenterY(config.getInt(path + ".center-y"));
            region.setCenterZ(config.getInt(path + ".center-z"));
            region.setRadius(config.getInt(path + ".radius"));
        }

        return region;
    }

    public Set<String> getAllRegionNames() {
        if (!config.contains("regions")) return Set.of();
        return config.getConfigurationSection("regions").getKeys(false);
    }
}
