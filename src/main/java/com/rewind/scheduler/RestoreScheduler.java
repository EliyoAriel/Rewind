package com.rewind.scheduler;

import com.rewind.RewindPlugin;
import com.rewind.snapshot.SnapshotKey;
import com.rewind.snapshot.SnapshotManager;
import com.rewind.snapshot.SnapshotSerializer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class RestoreScheduler {

    private final RewindPlugin plugin;
    private final SnapshotManager snapshotManager;
    private int taskId = -1;

    private final ConcurrentHashMap<String, RestoreTask> pendingRestores = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<ChunkCoord> gradualRestoreQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<ChunkCoord> skippedQueue = new ConcurrentLinkedQueue<>();
    private int tickCounter = 0;
    private int restoreCooldown = 0;

    private int chunksPerInterval;
    private int intervalTicks;
    private int minPlayerDistance;

    public RestoreScheduler(RewindPlugin plugin, SnapshotManager snapshotManager) {
        this.plugin = plugin;
        this.snapshotManager = snapshotManager;
        loadConfig();
    }

    public void loadConfig() {
        chunksPerInterval = plugin.getConfig().getInt("restore.chunks-per-interval", 1);
        intervalTicks = plugin.getConfig().getInt("restore.interval-ticks", 20);
        minPlayerDistance = plugin.getConfig().getInt("restore.min-distance-chunks", 5);
    }

    public void start() {
        taskId = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L).getTaskId();
    }

    public void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        pendingRestores.clear();
        gradualRestoreQueue.clear();
        skippedQueue.clear();
    }

    private void tick() {
        tickCounter++;

        snapshotManager.processSnapshotQueue();

        var iterator = pendingRestores.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, RestoreTask> entry = iterator.next();
            RestoreTask task = entry.getValue();
            if (tickCounter >= task.restoreAt) {
                gradualRestoreQueue.add(new ChunkCoord(task.worldName, task.chunkX, task.chunkZ));
                iterator.remove();
            }
        }

        if (restoreCooldown <= 0 && !gradualRestoreQueue.isEmpty()) {
            int processed = 0;
            while (processed < chunksPerInterval) {
                ChunkCoord coord = gradualRestoreQueue.poll();
                if (coord == null) {
                    gradualRestoreQueue.addAll(skippedQueue);
                    skippedQueue.clear();
                    break;
                }

                if (isPlayerNearby(coord.worldName, coord.chunkX, coord.chunkZ)) {
                    skippedQueue.add(coord);
                    continue;
                }

                World world = Bukkit.getWorld(coord.worldName);
                if (world != null) {
                    SnapshotSerializer.SnapshotData data = snapshotManager.loadSnapshotData(coord.worldName, coord.chunkX, coord.chunkZ);
                    if (data != null) {
                        data.applyToWorld(world);
                    }
                }
                processed++;
            }
            restoreCooldown = intervalTicks;
        }

        if (restoreCooldown > 0) {
            restoreCooldown--;
        }
    }

    private boolean isPlayerNearby(String worldName, int chunkX, int chunkZ) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return false;

        for (Player player : world.getPlayers()) {
            int playerChunkX = player.getLocation().getBlockX() >> 4;
            int playerChunkZ = player.getLocation().getBlockZ() >> 4;

            int distance = Math.max(Math.abs(playerChunkX - chunkX), Math.abs(playerChunkZ - chunkZ));
            if (distance <= minPlayerDistance) {
                return true;
            }
        }
        return false;
    }

    public void scheduleRestore(String worldName, int chunkX, int chunkZ, int timerSeconds) {
        String key = worldName + ":" + chunkX + ":" + chunkZ;

        RestoreTask existing = pendingRestores.get(key);
        if (existing != null) {
            existing.restoreAt = tickCounter + timerSeconds;
            return;
        }

        RestoreTask task = new RestoreTask(worldName, chunkX, chunkZ, tickCounter + timerSeconds);
        pendingRestores.put(key, task);
    }

    public void restoreRegion(String worldName, int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ) {
        Set<SnapshotKey> keys = snapshotManager.getSnapshotKeysInRegion(worldName, minChunkX, minChunkZ, maxChunkX, maxChunkZ);

        for (SnapshotKey key : keys) {
            String taskKey = worldName + ":" + key.getChunkX() + ":" + key.getChunkZ();
            pendingRestores.remove(taskKey);
            gradualRestoreQueue.add(new ChunkCoord(worldName, key.getChunkX(), key.getChunkZ()));
        }
    }

    public void restoreChunk(String worldName, int chunkX, int chunkZ) {
        String taskKey = worldName + ":" + chunkX + ":" + chunkZ;
        pendingRestores.remove(taskKey);
        gradualRestoreQueue.add(new ChunkCoord(worldName, chunkX, chunkZ));
    }

    public void cancelRegionRestores(String worldName, int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ) {
        pendingRestores.entrySet().removeIf(entry -> {
            String[] parts = entry.getKey().split(":");
            String wn = parts[0];
            int cx = Integer.parseInt(parts[1]);
            int cz = Integer.parseInt(parts[2]);
            return wn.equals(worldName) && cx >= minChunkX && cx <= maxChunkX && cz >= minChunkZ && cz <= maxChunkZ;
        });

        gradualRestoreQueue.removeIf(coord ->
            coord.worldName.equals(worldName) &&
            coord.chunkX >= minChunkX && coord.chunkX <= maxChunkX &&
            coord.chunkZ >= minChunkZ && coord.chunkZ <= maxChunkZ
        );

        skippedQueue.removeIf(coord ->
            coord.worldName.equals(worldName) &&
            coord.chunkX >= minChunkX && coord.chunkX <= maxChunkX &&
            coord.chunkZ >= minChunkZ && coord.chunkZ <= maxChunkZ
        );
    }

    public int getPendingCount() {
        return pendingRestores.size() + gradualRestoreQueue.size() + skippedQueue.size();
    }

    private static class ChunkCoord {
        final String worldName;
        final int chunkX;
        final int chunkZ;

        ChunkCoord(String worldName, int chunkX, int chunkZ) {
            this.worldName = worldName;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }
    }

    private static class RestoreTask {
        final String worldName;
        final int chunkX;
        final int chunkZ;
        volatile int restoreAt;

        RestoreTask(String worldName, int chunkX, int chunkZ, int restoreAt) {
            this.worldName = worldName;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.restoreAt = restoreAt;
        }
    }
}
