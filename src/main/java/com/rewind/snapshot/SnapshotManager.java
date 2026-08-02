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

    private final ConcurrentHashMap<String, ConcurrentHashMap<SnapshotKey, Long>> snapshotIndex = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<SnapshotQueueItem> snapshotQueue = new ConcurrentLinkedQueue<>();

    private static final int SNAPSHOTS_PER_TICK = 20;

    private static class SnapshotQueueItem {
        final String regionName;
        final SnapshotKey key;

        SnapshotQueueItem(String regionName, SnapshotKey key) {
            this.regionName = regionName.toLowerCase();
            this.key = key;
        }
    }

    public SnapshotManager(Logger logger, File dataFolder) {
        this.logger = logger;
        this.serializer = new SnapshotSerializer(logger);
        this.snapshotDir = new File(dataFolder, "snapshots");
        this.snapshotDir.mkdirs();
        rebuildIndex();
    }

    public void loadWhitelist(java.util.List<String> blocks) {
        serializer.loadWhitelist(blocks);
    }

    public SnapshotSerializer getSerializer() {
        return serializer;
    }

    private void rebuildIndex() {
        snapshotIndex.clear();
        File[] regionDirs = snapshotDir.listFiles(File::isDirectory);
        if (regionDirs == null) return;

        for (File regionDir : regionDirs) {
            ConcurrentHashMap<SnapshotKey, Long> regionIndex = new ConcurrentHashMap<>();
            File[] files = regionDir.listFiles((dir, name) -> name.endsWith(".rewind"));
            if (files != null) {
                for (File file : files) {
                    try {
                        SnapshotKey key = parseFileName(file.getName());
                        regionIndex.put(key, file.lastModified());
                    } catch (Exception ignored) {}
                }
            }
            snapshotIndex.put(regionDir.getName(), regionIndex);
        }
    }

    public boolean hasSnapshot(String regionName, String worldName, int chunkX, int chunkZ) {
        regionName = regionName.toLowerCase();
        ConcurrentHashMap<SnapshotKey, Long> regionIndex = snapshotIndex.get(regionName);
        if (regionIndex == null) return false;

        SnapshotKey key = new SnapshotKey(worldName, chunkX, chunkZ);
        File file = getDiskFile(regionName, key);
        if (!file.exists()) {
            regionIndex.remove(key);
            return false;
        }
        return true;
    }

    public void queueSnapshot(String regionName, World world, int chunkX, int chunkZ) {
        regionName = regionName.toLowerCase();
        SnapshotKey key = new SnapshotKey(world.getName(), chunkX, chunkZ);
        File file = getDiskFile(regionName, key);

        if (file.exists()) {
            ConcurrentHashMap<SnapshotKey, Long> regionIndex = snapshotIndex.computeIfAbsent(regionName, k -> new ConcurrentHashMap<>());
            if (!regionIndex.containsKey(key)) {
                regionIndex.put(key, file.lastModified());
            }
            return;
        }

        snapshotIndex.computeIfAbsent(regionName, k -> new ConcurrentHashMap<>()).remove(key);
        snapshotQueue.add(new SnapshotQueueItem(regionName, key));
    }

    public void processSnapshotQueue() {
        int processed = 0;
        while (processed < SNAPSHOTS_PER_TICK) {
            SnapshotQueueItem item = snapshotQueue.peek();
            if (item == null) break;

            if (hasSnapshot(item.regionName, item.key.getWorldName(), item.key.getChunkX(), item.key.getChunkZ())) {
                snapshotQueue.poll();
                continue;
            }

            World world = org.bukkit.Bukkit.getWorld(item.key.getWorldName());
            if (world == null) {
                snapshotQueue.poll();
                continue;
            }

            snapshotQueue.poll();

            org.bukkit.Chunk chunk = world.getChunkAt(item.key.getChunkX(), item.key.getChunkZ(), false);
            if (chunk.isLoaded()) {
                try {
                    ChunkSnapshot snapshot = chunk.getChunkSnapshot();
                    File file = getDiskFile(item.regionName, item.key);
                    if (!snapshotIndex.containsKey(item.regionName)) {
                        continue;
                    }
                    serializer.serialize(snapshot, file);
                    ConcurrentHashMap<SnapshotKey, Long> regionIndex = snapshotIndex.computeIfAbsent(item.regionName, k -> new ConcurrentHashMap<>());
                    regionIndex.put(item.key, file.lastModified());
                    processed++;
                } catch (Exception e) {
                    logger.warning("Failed to save snapshot for chunk " + item.key + ": " + e.getMessage());
                }
            } else {
                String regionName = item.regionName;
                SnapshotKey key = item.key;
                world.getChunkAtAsync(item.key.getChunkX(), item.key.getChunkZ(), true).thenAccept(loadedChunk -> {
                    try {
                        ChunkSnapshot snapshot = loadedChunk.getChunkSnapshot();
                        File file = getDiskFile(regionName, key);
                        if (!snapshotIndex.containsKey(regionName)) {
                            return;
                        }
                        serializer.serialize(snapshot, file);
                        ConcurrentHashMap<SnapshotKey, Long> regionIndex = snapshotIndex.computeIfAbsent(regionName, k -> new ConcurrentHashMap<>());
                        regionIndex.put(key, file.lastModified());
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

    public SnapshotSerializer.SnapshotData loadSnapshotData(String regionName, String worldName, int chunkX, int chunkZ) {
        regionName = regionName.toLowerCase();
        SnapshotKey key = new SnapshotKey(worldName, chunkX, chunkZ);
        File file = getDiskFile(regionName, key);

        if (!file.exists()) return null;

        try {
            return serializer.deserialize(file);
        } catch (IOException e) {
            logger.warning("Failed to load snapshot from disk: " + e.getMessage());
            return null;
        }
    }

    public void removeSnapshot(String regionName, String worldName, int chunkX, int chunkZ) {
        regionName = regionName.toLowerCase();
        ConcurrentHashMap<SnapshotKey, Long> regionIndex = snapshotIndex.get(regionName);
        if (regionIndex != null) {
            SnapshotKey key = new SnapshotKey(worldName, chunkX, chunkZ);
            regionIndex.remove(key);
        }

        File file = getDiskFile(regionName, new SnapshotKey(worldName, chunkX, chunkZ));
        if (file.exists() && !file.delete()) {
            logger.warning("Failed to delete snapshot file: " + file.getAbsolutePath());
        }
    }

    public void cleanupAll() {
        snapshotIndex.clear();
        snapshotQueue.clear();
    }

    public Set<SnapshotKey> getSnapshotKeysInRegion(String regionName) {
        regionName = regionName.toLowerCase();
        ConcurrentHashMap<SnapshotKey, Long> regionIndex = snapshotIndex.get(regionName);
        if (regionIndex == null) return java.util.Collections.emptySet();

        java.util.Set<SnapshotKey> result = new java.util.HashSet<>();
        for (SnapshotKey key : regionIndex.keySet()) {
            if (getDiskFile(regionName, key).exists()) {
                result.add(key);
            }
        }
        return result;
    }

    public Set<SnapshotKey> getSnapshotKeysInRegion(String regionName, String worldName,
                                                     int minChunkX, int minChunkZ,
                                                     int maxChunkX, int maxChunkZ) {
        regionName = regionName.toLowerCase();
        ConcurrentHashMap<SnapshotKey, Long> regionIndex = snapshotIndex.get(regionName);
        if (regionIndex == null) return java.util.Collections.emptySet();

        java.util.Set<SnapshotKey> result = new java.util.HashSet<>();
        for (SnapshotKey key : regionIndex.keySet()) {
            if (key.getWorldName().equals(worldName) &&
                key.getChunkX() >= minChunkX && key.getChunkX() <= maxChunkX &&
                key.getChunkZ() >= minChunkZ && key.getChunkZ() <= maxChunkZ) {
                if (getDiskFile(regionName, key).exists()) {
                    result.add(key);
                }
            }
        }
        return result;
    }

    public int queueRegionSnapshots(com.rewind.regions.Region region, World world) {
        int count = 0;
        for (int cx = region.getMinChunkX(); cx <= region.getMaxChunkX(); cx++) {
            for (int cz = region.getMinChunkZ(); cz <= region.getMaxChunkZ(); cz++) {
                queueSnapshot(region.getName(), world, cx, cz);
                count++;
            }
        }
        return count;
    }

    public void removeRegionSnapshots(com.rewind.regions.Region region) {
        String regionName = region.getName().toLowerCase();

        snapshotQueue.removeIf(item -> item.regionName.equals(regionName));

        snapshotIndex.remove(regionName);

        File regionDir = new File(snapshotDir, regionName);
        if (regionDir.exists()) {
            File[] files = regionDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (!file.delete()) {
                        logger.warning("Failed to delete snapshot file: " + file.getAbsolutePath());
                    }
                }
            }
            if (!regionDir.delete()) {
                logger.warning("Failed to delete region snapshot directory: " + regionDir.getAbsolutePath());
            }
        }
    }

    public int getSnapshotCount() {
        int count = 0;
        for (ConcurrentHashMap<SnapshotKey, Long> regionIndex : snapshotIndex.values()) {
            count += regionIndex.size();
        }
        return count;
    }

    private File getDiskFile(String regionName, SnapshotKey key) {
        return new File(snapshotDir, regionName.toLowerCase() + File.separator + key.toString() + ".rewind");
    }

    private SnapshotKey parseFileName(String fileName) {
        String name = fileName.replace(".rewind", "");
        String[] parts = name.split("_");
        return new SnapshotKey(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
    }
}
