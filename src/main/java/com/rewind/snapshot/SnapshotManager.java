package com.rewind.snapshot;

import org.bukkit.ChunkSnapshot;
import org.bukkit.World;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;

public class SnapshotManager {

    private final Logger logger;
    private final SnapshotSerializer serializer;
    private final File snapshotDir;

    private final ConcurrentHashMap<SnapshotKey, Long> snapshotIndex = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<SnapshotKey> snapshotQueue = new ConcurrentLinkedQueue<>();

    private static final int SNAPSHOTS_PER_TICK = 5;

    public SnapshotManager(Logger logger, File dataFolder) {
        this.logger = logger;
        this.serializer = new SnapshotSerializer(logger);
        this.snapshotDir = new File(dataFolder, "snapshots");
        this.snapshotDir.mkdirs();
        rebuildIndex();
    }

    private void rebuildIndex() {
        File[] files = snapshotDir.listFiles((dir, name) -> name.endsWith(".rewind"));
        if (files == null) return;

        for (File file : files) {
            try {
                SnapshotKey key = parseFileName(file.getName());
                snapshotIndex.put(key, file.lastModified());
            } catch (Exception ignored) {}
        }
    }

    public boolean hasSnapshot(String worldName, int chunkX, int chunkZ) {
        SnapshotKey key = new SnapshotKey(worldName, chunkX, chunkZ);
        return snapshotIndex.containsKey(key) && getDiskFile(key).exists();
    }

    public void queueSnapshot(World world, int chunkX, int chunkZ) {
        SnapshotKey key = new SnapshotKey(world.getName(), chunkX, chunkZ);
        if (!hasSnapshot(world.getName(), chunkX, chunkZ)) {
            snapshotQueue.add(key);
        }
    }

    public void processSnapshotQueue() {
        int processed = 0;
        while (processed < SNAPSHOTS_PER_TICK) {
            SnapshotKey key = snapshotQueue.peek();
            if (key == null) break;

            if (hasSnapshot(key.getWorldName(), key.getChunkX(), key.getChunkZ())) {
                snapshotQueue.poll();
                continue;
            }

            World world = org.bukkit.Bukkit.getWorld(key.getWorldName());
            if (world == null) {
                snapshotQueue.poll();
                continue;
            }

            snapshotQueue.poll();

            org.bukkit.Chunk chunk = world.getChunkAt(key.getChunkX(), key.getChunkZ(), false);
            if (chunk.isLoaded()) {
                try {
                    ChunkSnapshot snapshot = chunk.getChunkSnapshot();
                    File file = getDiskFile(key);
                    serializer.serialize(snapshot, file);
                    snapshotIndex.put(key, file.lastModified());
                    processed++;
                } catch (Exception e) {
                    logger.warning("Failed to save snapshot for chunk " + key + ": " + e.getMessage());
                }
            } else {
                world.getChunkAtAsync(key.getChunkX(), key.getChunkZ()).thenAccept(loadedChunk -> {
                    try {
                        ChunkSnapshot snapshot = loadedChunk.getChunkSnapshot();
                        File file = getDiskFile(key);
                        serializer.serialize(snapshot, file);
                        snapshotIndex.put(key, file.lastModified());
                    } catch (Exception e) {
                        logger.warning("Failed to save snapshot for chunk " + key + ": " + e.getMessage());
                    }
                });
                processed++;
            }
        }
    }

    public boolean isSnapshotQueueEmpty() {
        return snapshotQueue.isEmpty();
    }

    public int getSnapshotQueueSize() {
        return snapshotQueue.size();
    }

    public SnapshotSerializer.SnapshotData loadSnapshotData(String worldName, int chunkX, int chunkZ) {
        SnapshotKey key = new SnapshotKey(worldName, chunkX, chunkZ);
        File file = getDiskFile(key);

        if (!file.exists()) return null;

        try {
            return serializer.deserialize(file);
        } catch (IOException e) {
            logger.warning("Failed to load snapshot from disk: " + e.getMessage());
            return null;
        }
    }

    public void removeSnapshot(String worldName, int chunkX, int chunkZ) {
        SnapshotKey key = new SnapshotKey(worldName, chunkX, chunkZ);
        snapshotIndex.remove(key);

        File file = getDiskFile(key);
        if (file.exists()) {
            file.delete();
        }
    }

    public void cleanupAll() {
        snapshotIndex.clear();
        snapshotQueue.clear();

        File[] files = snapshotDir.listFiles();
        if (files != null) {
            for (File file : files) {
                file.delete();
            }
        }
    }

    public Set<SnapshotKey> getSnapshotKeysInRegion(String worldName,
                                                     int minChunkX, int minChunkZ,
                                                     int maxChunkX, int maxChunkZ) {
        java.util.Set<SnapshotKey> result = new java.util.HashSet<>();

        for (SnapshotKey key : snapshotIndex.keySet()) {
            if (key.getWorldName().equals(worldName) &&
                key.getChunkX() >= minChunkX && key.getChunkX() <= maxChunkX &&
                key.getChunkZ() >= minChunkZ && key.getChunkZ() <= maxChunkZ) {
                if (getDiskFile(key).exists()) {
                    result.add(key);
                }
            }
        }

        return result;
    }

    public void removeRegionSnapshots(com.rewind.regions.Region region) {
        String worldName = region.getWorldName();
        int minChunkX = region.getMinChunkX();
        int minChunkZ = region.getMinChunkZ();
        int maxChunkX = region.getMaxChunkX();
        int maxChunkZ = region.getMaxChunkZ();

        snapshotQueue.removeIf(key ->
            key.getWorldName().equals(worldName) &&
            key.getChunkX() >= minChunkX && key.getChunkX() <= maxChunkX &&
            key.getChunkZ() >= minChunkZ && key.getChunkZ() <= maxChunkZ
        );

        java.util.Set<SnapshotKey> toRemove = new java.util.HashSet<>();
        for (SnapshotKey key : snapshotIndex.keySet()) {
            if (key.getWorldName().equals(worldName) &&
                key.getChunkX() >= minChunkX && key.getChunkX() <= maxChunkX &&
                key.getChunkZ() >= minChunkZ && key.getChunkZ() <= maxChunkZ) {
                toRemove.add(key);
            }
        }

        for (SnapshotKey key : toRemove) {
            snapshotIndex.remove(key);
            File file = getDiskFile(key);
            if (file.exists()) {
                file.delete();
            }
        }
    }

    public int getSnapshotCount() {
        return snapshotIndex.size();
    }

    private File getDiskFile(SnapshotKey key) {
        return new File(snapshotDir, key.toString() + ".rewind");
    }

    private SnapshotKey parseFileName(String fileName) {
        String name = fileName.replace(".rewind", "");
        String[] parts = name.split("_");
        return new SnapshotKey(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
    }
}
