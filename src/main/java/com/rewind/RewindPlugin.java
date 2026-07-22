package com.rewind;

import com.rewind.commands.RewindCommand;
import com.rewind.listeners.BlockChangeListener;
import com.rewind.regions.RegionManager;
import com.rewind.regions.RegionStorage;
import com.rewind.scheduler.RestoreScheduler;
import com.rewind.snapshot.SnapshotManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public class RewindPlugin extends JavaPlugin {

    private RegionManager regionManager;
    private SnapshotManager snapshotManager;
    private RestoreScheduler restoreScheduler;
    private RegionStorage regionStorage;
    private DebugManager debugManager;

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

        restoreScheduler = new RestoreScheduler(this, snapshotManager);
        restoreScheduler.start();

        RewindCommand cmd = new RewindCommand(this, regionManager, snapshotManager, restoreScheduler, debugManager);
        getCommand("rewind").setExecutor(cmd);
        getCommand("rewind").setTabCompleter(cmd);

        getServer().getPluginManager().registerEvents(
            new BlockChangeListener(this, regionManager, snapshotManager, restoreScheduler, debugManager), this);

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

    public Object getWorldEdit() {
        try {
            return getServer().getPluginManager().getPlugin("WorldEdit");
        } catch (Exception e) {
            return null;
        }
    }
}
