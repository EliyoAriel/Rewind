package com.rewind.regions;

import org.bukkit.Location;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class RegionManager {

    private final RegionStorage storage;
    private final Map<String, Region> regions = new ConcurrentHashMap<>();

    public RegionManager(RegionStorage storage) {
        this.storage = storage;
    }

    public void loadAll() {
        storage.load();
        Set<String> names = storage.getAllRegionNames();
        for (String name : names) {
            Region region = storage.loadRegion(name);
            if (region != null) {
                regions.put(name.toLowerCase(), region);
            }
        }
    }

    public boolean createRegion(Region region) {
        if (regions.containsKey(region.getName().toLowerCase())) {
            return false;
        }
        regions.put(region.getName().toLowerCase(), region);
        storage.addRegion(region);
        return true;
    }

    public boolean deleteRegion(String name) {
        Region removed = regions.remove(name.toLowerCase());
        if (removed != null) {
            storage.removeRegion(name);
            return true;
        }
        return false;
    }

    public Region getRegion(String name) {
        return regions.get(name.toLowerCase());
    }

    public Region getRegionAt(Location loc) {
        for (Region region : regions.values()) {
            if (region.contains(loc)) {
                return region;
            }
        }
        return null;
    }

    public Collection<Region> getAllRegions() {
        return regions.values();
    }

    public int getRegionCount() {
        return regions.size();
    }
}
