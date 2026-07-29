package com.rewind;

import com.rewind.api.RewindAPI;
import com.rewind.commands.RewindCommand;
import com.rewind.listeners.BlockChangeListener;
import com.rewind.regions.RegionManager;
import com.rewind.regions.RegionStorage;
import com.rewind.scheduler.RestoreScheduler;
import com.rewind.snapshot.SnapshotManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class RewindPlugin extends JavaPlugin {

    private static RewindAPI api;

    private RegionManager regionManager;
    private SnapshotManager snapshotManager;
    private RestoreScheduler restoreScheduler;
    private RegionStorage regionStorage;
    private DebugManager debugManager;
    private RewindCommand rewindCommand;

    private final ConcurrentHashMap<String, Integer> excludedChunks = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();

        boolean debugMode = getConfig().getBoolean("general.debug-mode", false);
        debugManager = new DebugManager(getLogger(), debugMode);

        regionStorage = new RegionStorage(getLogger(), getDataFolder());
        regionManager = new RegionManager(regionStorage);
        regionManager.loadAll();

        snapshotManager = new SnapshotManager(getLogger(), getDataFolder());

        if (getConfig().getBoolean("whitelist.enabled", false)) {
            snapshotManager.loadWhitelist(getConfig().getStringList("whitelist.blocks"));
        }

        restoreScheduler = new RestoreScheduler(this, snapshotManager, debugManager);
        restoreScheduler.start();

        rewindCommand = new RewindCommand(this, regionManager, snapshotManager, restoreScheduler, debugManager);
        getCommand("rewind").setExecutor(rewindCommand);
        getCommand("rewind").setTabCompleter(rewindCommand);

        getServer().getPluginManager().registerEvents(
            new BlockChangeListener(this, regionManager, snapshotManager, restoreScheduler, debugManager), this);

        api = new RewindAPI(this);

        getLogger().log(Level.INFO, "Rewind has been enabled!" + (debugMode ? " (Debug mode)" : ""));
    }

    @Override
    public void onDisable() {
        if (restoreScheduler != null) {
            restoreScheduler.stop();
        }
        if (snapshotManager != null) {
            snapshotManager.cleanupAll();
        }
        if (rewindCommand != null) {
            rewindCommand.cancelAllShows();
        }
        getLogger().log(Level.INFO, "Rewind has been disabled!");
    }

    public RegionManager getRegionManager() {
        return regionManager;
    }

    public SnapshotManager getSnapshotManager() {
        return snapshotManager;
    }

    public RestoreScheduler getRestoreScheduler() {
        return restoreScheduler;
    }

    public DebugManager getDebugManager() {
        return debugManager;
    }

    public static RewindAPI getAPI() {
        return api;
    }

    public boolean excludeChunk(String worldName, int chunkX, int chunkZ) {
        String key = worldName + ":" + chunkX + ":" + chunkZ;
        Integer prev = excludedChunks.put(key, excludedChunks.getOrDefault(key, 0) + 1);
        boolean newlyExcluded = (prev == null || prev == 0);
        if (newlyExcluded && restoreScheduler != null) {
            restoreScheduler.cancelChunkRestores(worldName, chunkX, chunkZ);
        }
        return newlyExcluded;
    }

    public boolean unexcludeChunk(String worldName, int chunkX, int chunkZ) {
        String key = worldName + ":" + chunkX + ":" + chunkZ;
        Integer count = excludedChunks.get(key);
        if (count == null || count <= 0) {
            excludedChunks.remove(key);
            return false;
        }
        if (count == 1) {
            excludedChunks.remove(key);
            return true;
        }
        excludedChunks.put(key, count - 1);
        return false;
    }

    public boolean isChunkExcluded(String worldName, int chunkX, int chunkZ) {
        if (excludedChunks.isEmpty()) return false;
        String key = worldName + ":" + chunkX + ":" + chunkZ;
        Integer count = excludedChunks.get(key);
        return count != null && count > 0;
    }

    public Object getWorldEdit() {
        try {
            return getServer().getPluginManager().getPlugin("WorldEdit");
        } catch (Exception e) {
            return null;
        }
    }
}
