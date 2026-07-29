package com.rewind.scheduler;

import com.rewind.RewindPlugin;
import com.rewind.DebugManager;
import com.rewind.snapshot.SnapshotKey;
import com.rewind.snapshot.SnapshotManager;
import com.rewind.snapshot.SnapshotSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public class RestoreScheduler {

    private final RewindPlugin plugin;
    private final SnapshotManager snapshotManager;
    private final DebugManager debug;
    private int taskId = -1;

    private final ConcurrentHashMap<String, RestoreTask> pendingRestores = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<ChunkCoord> gradualRestoreQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<ChunkCoord> skippedQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<ChunkCoord> manualForceQueue = new ConcurrentLinkedQueue<>();
    private int tickCounter = 0;
    private int restoreCooldown = 0;
    private int skippedTicks = 0;

    private static final int MAX_SKIPPED_TICKS = 100;

    private int chunksPerInterval;
    private int intervalTicks;
    private int minPlayerDistance;

    private boolean notificationsEnabled;
    private String notificationChat;
    private int notificationSeconds;

    private boolean progressEnabled;
    private String progressChat;
    private int barLength;
    private String barFilled;
    private String barEmpty;

    private boolean soundsEnabled;
    private Sound restoreSound;
    private float soundVolume;
    private float soundPitch;

    private int lowChangesThreshold;
    private int normalChangesThreshold;
    private int highChangesThreshold;
    private int lowPrioritySeconds;
    private int normalPrioritySeconds;
    private int highPrioritySeconds;

    private final AtomicInteger totalRestores = new AtomicInteger(0);
    private final AtomicInteger completedRestores = new AtomicInteger(0);
    private volatile boolean lastBatchManual = false;

    public RestoreScheduler(RewindPlugin plugin, SnapshotManager snapshotManager, DebugManager debug) {
        this.plugin = plugin;
        this.snapshotManager = snapshotManager;
        this.debug = debug;
        loadConfig();
    }

    public void loadConfig() {
        chunksPerInterval = plugin.getConfig().getInt("restore.chunks-per-interval", 1);
        intervalTicks = plugin.getConfig().getInt("restore.interval-ticks", 20);
        minPlayerDistance = plugin.getConfig().getInt("restore.min-distance-chunks", 5);

        notificationsEnabled = plugin.getConfig().getBoolean("notifications.enabled", true);
        notificationChat = plugin.getConfig().getString("notifications.chat", "&eChunk at &6%x%, %z% &erestoring in &6%seconds%s");
        notificationSeconds = plugin.getConfig().getInt("notifications.seconds-before", 5);

        progressEnabled = plugin.getConfig().getBoolean("progress.enabled", true);
        progressChat = plugin.getConfig().getString("progress.chat", "&eRestoring chunks... &6[%bar%] &e%done%/%total%");
        barLength = plugin.getConfig().getInt("progress.bar-length", 20);
        barFilled = plugin.getConfig().getString("progress.bar-filled", "=");
        barEmpty = plugin.getConfig().getString("progress.bar-empty", "-");

        soundsEnabled = plugin.getConfig().getBoolean("sounds.enabled", true);
        try {
            restoreSound = Sound.valueOf(plugin.getConfig().getString("sounds.sound", "ENTITY_EXPERIENCE_ORB_PICKUP"));
        } catch (Exception e) {
            restoreSound = Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
        }
        soundVolume = (float) plugin.getConfig().getDouble("sounds.volume", 1.0);
        soundPitch = (float) plugin.getConfig().getDouble("sounds.pitch", 1.0);

        lowChangesThreshold = plugin.getConfig().getInt("priority.low-changes-threshold", 5);
        normalChangesThreshold = plugin.getConfig().getInt("priority.normal-changes-threshold", 20);
        highChangesThreshold = plugin.getConfig().getInt("priority.high-changes-threshold", 50);
        lowPrioritySeconds = plugin.getConfig().getInt("priority.low-priority-seconds", 300);
        normalPrioritySeconds = plugin.getConfig().getInt("priority.normal-priority-seconds", 60);
        highPrioritySeconds = plugin.getConfig().getInt("priority.high-priority-seconds", 30);
    }

    public int getPrioritySeconds(int changeCount) {
        if (changeCount <= lowChangesThreshold) {
            return lowPrioritySeconds;
        } else if (changeCount <= normalChangesThreshold) {
            return normalPrioritySeconds;
        } else {
            return highPrioritySeconds;
        }
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
        manualForceQueue.clear();
    }

    private void tick() {
        tickCounter++;

        snapshotManager.processSnapshotQueue();

        var iterator = pendingRestores.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, RestoreTask> entry = iterator.next();
            RestoreTask task = entry.getValue();
            if (tickCounter >= task.restoreAt) {
                gradualRestoreQueue.add(new ChunkCoord(task.regionName, task.worldName, task.chunkX, task.chunkZ, false));
                iterator.remove();
            }
        }

        if (notificationsEnabled) {
            processNotifications();
        }

        while (!manualForceQueue.isEmpty()) {
            ChunkCoord coord = manualForceQueue.poll();
            loadAndApply(coord);
        }

        if (restoreCooldown <= 0 && !gradualRestoreQueue.isEmpty()) {
            skippedTicks = 0;
            int processed = 0;
            while (processed < chunksPerInterval) {
                ChunkCoord coord = gradualRestoreQueue.poll();
                if (coord == null) {
                    break;
                }

                if (isPlayerNearby(coord.worldName, coord.chunkX, coord.chunkZ)) {
                    skippedQueue.add(coord);
                    continue;
                }

                loadAndApply(coord);
                processed++;
            }

            if (!gradualRestoreQueue.isEmpty()) {
                restoreCooldown = intervalTicks;
            } else {
                gradualRestoreQueue.addAll(skippedQueue);
                skippedQueue.clear();
                if (!gradualRestoreQueue.isEmpty()) {
                    skippedTicks++;
                    if (skippedTicks >= MAX_SKIPPED_TICKS) {
                        debug.log("Skipped chunks stuck for %d ticks, forcing restore", skippedTicks);
                        while (!gradualRestoreQueue.isEmpty()) {
                            ChunkCoord forced = gradualRestoreQueue.poll();
                            loadAndApply(forced);
                        }
                        skippedTicks = 0;
                    } else {
                        restoreCooldown = intervalTicks;
                    }
                }
            }
        }

        if (restoreCooldown > 0) {
            restoreCooldown--;
        }
    }

    private void loadAndApply(ChunkCoord coord) {
        World world = Bukkit.getWorld(coord.worldName);
        if (world == null) return;

        CompletableFuture<SnapshotSerializer.SnapshotData> future = CompletableFuture.supplyAsync(() ->
            snapshotManager.loadSnapshotData(coord.regionName, coord.worldName, coord.chunkX, coord.chunkZ)
        );

        future.thenAcceptAsync(data -> {
            if (data == null) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                data.applyToWorld(world);
                completedRestores.incrementAndGet();
                playRestoreSound(world, coord.chunkX, coord.chunkZ);
                sendProgress(coord.manual);

                if (completedRestores.get() >= totalRestores.get()) {
                    if (lastBatchManual) {
                        for (Player player : Bukkit.getOnlinePlayers()) {
                            if (player.hasPermission("rewind.restore")) {
                                player.sendActionBar("§aRewind complete!");
                            }
                        }
                    }
                    totalRestores.set(0);
                    completedRestores.set(0);
                    lastBatchManual = false;
                }
            });
        });
    }

    public void scheduleRestore(String regionName, String worldName, int chunkX, int chunkZ, int timerSeconds) {
        regionName = regionName.toLowerCase();
        String key = regionName + ":" + worldName + ":" + chunkX + ":" + chunkZ;

        RestoreTask existing = pendingRestores.get(key);
        if (existing != null) {
            return;
        }

        RestoreTask task = new RestoreTask(regionName, worldName, chunkX, chunkZ, tickCounter + (timerSeconds * 20));
        pendingRestores.put(key, task);
    }

    public void restoreRegion(String regionName, String worldName, int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ) {
        regionName = regionName.toLowerCase();
        Set<SnapshotKey> keys = snapshotManager.getSnapshotKeysInRegion(regionName, worldName, minChunkX, minChunkZ, maxChunkX, maxChunkZ);

        totalRestores.set(keys.size());
        completedRestores.set(0);

        for (SnapshotKey key : keys) {
            String taskKey = regionName + ":" + worldName + ":" + key.getChunkX() + ":" + key.getChunkZ();
            pendingRestores.remove(taskKey);
            gradualRestoreQueue.add(new ChunkCoord(regionName, worldName, key.getChunkX(), key.getChunkZ(), false));
        }
    }

    public void restoreChunk(String regionName, String worldName, int chunkX, int chunkZ) {
        regionName = regionName.toLowerCase();
        String taskKey = regionName + ":" + worldName + ":" + chunkX + ":" + chunkZ;
        pendingRestores.remove(taskKey);
        gradualRestoreQueue.add(new ChunkCoord(regionName, worldName, chunkX, chunkZ, false));
        totalRestores.incrementAndGet();
    }

    public void restoreRegionForce(String regionName, String worldName, int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ) {
        regionName = regionName.toLowerCase();
        Set<SnapshotKey> keys = snapshotManager.getSnapshotKeysInRegion(regionName, worldName, minChunkX, minChunkZ, maxChunkX, maxChunkZ);

        totalRestores.set(keys.size());
        completedRestores.set(0);
        lastBatchManual = true;

        for (SnapshotKey key : keys) {
            String taskKey = regionName + ":" + worldName + ":" + key.getChunkX() + ":" + key.getChunkZ();
            pendingRestores.remove(taskKey);
            manualForceQueue.add(new ChunkCoord(regionName, worldName, key.getChunkX(), key.getChunkZ(), true));
        }
    }

    public void restoreChunkForce(String regionName, String worldName, int chunkX, int chunkZ) {
        regionName = regionName.toLowerCase();
        String taskKey = regionName + ":" + worldName + ":" + chunkX + ":" + chunkZ;
        pendingRestores.remove(taskKey);
        manualForceQueue.add(new ChunkCoord(regionName, worldName, chunkX, chunkZ, true));
        totalRestores.incrementAndGet();
        lastBatchManual = true;
    }

    public void cancelChunkRestores(String worldName, int chunkX, int chunkZ) {
        pendingRestores.entrySet().removeIf(entry -> {
            String[] parts = entry.getKey().split(":");
            return parts.length == 4 && parts[1].equals(worldName) &&
                   Integer.parseInt(parts[2]) == chunkX && Integer.parseInt(parts[3]) == chunkZ;
        });
        gradualRestoreQueue.removeIf(coord ->
            coord.worldName.equals(worldName) && coord.chunkX == chunkX && coord.chunkZ == chunkZ);
        skippedQueue.removeIf(coord ->
            coord.worldName.equals(worldName) && coord.chunkX == chunkX && coord.chunkZ == chunkZ);
        manualForceQueue.removeIf(coord ->
            coord.worldName.equals(worldName) && coord.chunkX == chunkX && coord.chunkZ == chunkZ);
    }

    public void cancelRegionRestores(String regionName) {
        String lower = regionName.toLowerCase();
        pendingRestores.entrySet().removeIf(entry -> entry.getKey().startsWith(lower + ":"));

        gradualRestoreQueue.removeIf(coord -> coord.regionName.equals(lower));
        skippedQueue.removeIf(coord -> coord.regionName.equals(lower));
        manualForceQueue.removeIf(coord -> coord.regionName.equals(lower));
    }

    public int getPendingCount() {
        return pendingRestores.size() + gradualRestoreQueue.size() + skippedQueue.size() + manualForceQueue.size();
    }

    private void processNotifications() {
        for (Map.Entry<String, RestoreTask> entry : pendingRestores.entrySet()) {
            RestoreTask task = entry.getValue();
            int ticksLeft = task.restoreAt - tickCounter;
            int secondsLeft = ticksLeft / 20;

            if (secondsLeft > 0 && secondsLeft <= notificationSeconds && ticksLeft % 20 == 0) {
                sendNotification(task.worldName, task.chunkX, task.chunkZ, secondsLeft);
            }
        }
    }

    private void sendNotification(String worldName, int chunkX, int chunkZ, int seconds) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return;

        String message = notificationChat.replace("&", "\u00a7")
            .replace("%seconds%", String.valueOf(seconds))
            .replace("%x%", String.valueOf(chunkX))
            .replace("%z%", String.valueOf(chunkZ));

        int minChunkX = chunkX - minPlayerDistance;
        int maxChunkX = chunkX + minPlayerDistance;
        int minChunkZ = chunkZ - minPlayerDistance;
        int maxChunkZ = chunkZ + minPlayerDistance;

        for (Player player : world.getPlayers()) {
            int playerChunkX = player.getLocation().getBlockX() >> 4;
            int playerChunkZ = player.getLocation().getBlockZ() >> 4;

            if (playerChunkX >= minChunkX && playerChunkX <= maxChunkX &&
                playerChunkZ >= minChunkZ && playerChunkZ <= maxChunkZ) {
                player.sendActionBar(message);
            }
        }
    }

    private void playRestoreSound(World world, int chunkX, int chunkZ) {
        if (!soundsEnabled) return;

        for (Player player : world.getPlayers()) {
            int playerChunkX = player.getLocation().getBlockX() >> 4;
            int playerChunkZ = player.getLocation().getBlockZ() >> 4;

            int distance = Math.max(Math.abs(playerChunkX - chunkX), Math.abs(playerChunkZ - chunkZ));
            if (distance <= minPlayerDistance + 5) {
                player.playSound(player.getLocation(), restoreSound, soundVolume, soundPitch);
            }
        }
    }

    private void sendProgress(boolean manual) {
        if (!progressEnabled) return;
        int total = totalRestores.get();
        if (total == 0) return;
        if (!manual && !debug.isEnabled()) return;

        int completed = completedRestores.get();
        int filled = (int) ((double) completed / total * barLength);
        int empty = barLength - filled;

        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < filled; i++) bar.append(barFilled);
        for (int i = 0; i < empty; i++) bar.append(barEmpty);

        String message = progressChat.replace("&", "\u00a7")
            .replace("%bar%", bar.toString())
            .replace("%done%", String.valueOf(completed))
            .replace("%total%", String.valueOf(total));

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("rewind.restore")) {
                player.sendActionBar(message);
            }
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

    private static class ChunkCoord {
        final String regionName;
        final String worldName;
        final int chunkX;
        final int chunkZ;
        final boolean manual;

        ChunkCoord(String regionName, String worldName, int chunkX, int chunkZ, boolean manual) {
            this.regionName = regionName.toLowerCase();
            this.worldName = worldName;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.manual = manual;
        }
    }

    private static class RestoreTask {
        final String regionName;
        final String worldName;
        final int chunkX;
        final int chunkZ;
        volatile int restoreAt;

        RestoreTask(String regionName, String worldName, int chunkX, int chunkZ, int restoreAt) {
            this.regionName = regionName.toLowerCase();
            this.worldName = worldName;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.restoreAt = restoreAt;
        }
    }
}
