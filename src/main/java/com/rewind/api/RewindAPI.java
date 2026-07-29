package com.rewind.api;

import com.rewind.RewindPlugin;
import com.rewind.regions.Region;
import com.rewind.regions.RegionManager;
import com.rewind.scheduler.RestoreScheduler;
import com.rewind.snapshot.SnapshotManager;
import org.bukkit.World;

public class RewindAPI {

    private final RewindPlugin plugin;

    public RewindAPI(RewindPlugin plugin) {
        this.plugin = plugin;
    }

    public RewindPlugin getPlugin() {
        return plugin;
    }

    public RegionManager getRegionManager() {
        return plugin.getRegionManager();
    }

    public SnapshotManager getSnapshotManager() {
        return plugin.getSnapshotManager();
    }

    public RestoreScheduler getRestoreScheduler() {
        return plugin.getRestoreScheduler();
    }

    public boolean createRegion(String name, String worldName, Region.Type type, int timerSeconds) {
        Region region = new Region(name, worldName, type, timerSeconds);
        return plugin.getRegionManager().createRegion(region);
    }

    public Region createCuboidRegion(String name, String worldName, int timerSeconds,
                                     int minX, int minY, int minZ,
                                     int maxX, int maxY, int maxZ) {
        Region region = new Region(name, worldName, Region.Type.CUBOID, timerSeconds);
        region.setMinX(minX);
        region.setMinY(minY);
        region.setMinZ(minZ);
        region.setMaxX(maxX);
        region.setMaxY(maxY);
        region.setMaxZ(maxZ);
        if (plugin.getRegionManager().createRegion(region)) {
            return region;
        }
        return null;
    }

    public Region createRadiusRegion(String name, String worldName, int timerSeconds,
                                     int centerX, int centerY, int centerZ, int radius) {
        Region region = new Region(name, worldName, Region.Type.RADIUS, timerSeconds);
        region.setCenterX(centerX);
        region.setCenterY(centerY);
        region.setCenterZ(centerZ);
        region.setRadius(radius);
        if (plugin.getRegionManager().createRegion(region)) {
            return region;
        }
        return null;
    }

    public boolean deleteRegion(String name) {
        Region region = plugin.getRegionManager().getRegion(name);
        if (region == null) return false;

        if (plugin.getRegionManager().deleteRegion(name)) {
            plugin.getSnapshotManager().removeRegionSnapshots(region);
            plugin.getRestoreScheduler().cancelRegionRestores(region.getName());
            return true;
        }
        return false;
    }

    public int queueRegionSnapshots(Region region, World world) {
        return plugin.getSnapshotManager().queueRegionSnapshots(region, world);
    }

    public void forceRestoreRegion(String regionName, String worldName,
                                    int minChunkX, int minChunkZ,
                                    int maxChunkX, int maxChunkZ) {
        plugin.getRestoreScheduler().restoreRegionForce(regionName, worldName, minChunkX, minChunkZ, maxChunkX, maxChunkZ);
    }

    public void gradualRestoreRegion(String regionName, String worldName,
                                      int minChunkX, int minChunkZ,
                                      int maxChunkX, int maxChunkZ) {
        plugin.getRestoreScheduler().restoreRegion(regionName, worldName, minChunkX, minChunkZ, maxChunkX, maxChunkZ);
    }

    public void scheduleAutoRestore(String regionName, String worldName, int chunkX, int chunkZ, int timerSeconds) {
        plugin.getRestoreScheduler().scheduleRestore(regionName, worldName, chunkX, chunkZ, timerSeconds);
    }

    public void cancelRestores(String regionName) {
        plugin.getRestoreScheduler().cancelRegionRestores(regionName);
    }

    public Region getRegion(String name) {
        return plugin.getRegionManager().getRegion(name);
    }

    public boolean hasSnapshot(String regionName, String worldName, int chunkX, int chunkZ) {
        return plugin.getSnapshotManager().hasSnapshot(regionName, worldName, chunkX, chunkZ);
    }

    public boolean isSnapshotQueueEmpty() {
        return plugin.getSnapshotManager().isSnapshotQueueEmpty();
    }

    public int getPendingRestoreCount() {
        return plugin.getRestoreScheduler().getPendingCount();
    }

    public boolean excludeChunk(String worldName, int chunkX, int chunkZ) {
        return plugin.excludeChunk(worldName, chunkX, chunkZ);
    }

    public void unexcludeChunk(String worldName, int chunkX, int chunkZ) {
        plugin.unexcludeChunk(worldName, chunkX, chunkZ);
    }

    public boolean isChunkExcluded(String worldName, int chunkX, int chunkZ) {
        return plugin.isChunkExcluded(worldName, chunkX, chunkZ);
    }

    public void excludeChunkArea(String worldName, int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ) {
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                plugin.excludeChunk(worldName, cx, cz);
            }
        }
    }

    public void unexcludeChunkArea(String worldName, int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ) {
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                plugin.unexcludeChunk(worldName, cx, cz);
            }
        }
    }
}
