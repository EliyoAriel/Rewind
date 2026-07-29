package com.rewind.commands;

import com.rewind.DebugManager;
import com.rewind.RewindPlugin;
import com.rewind.regions.Region;
import com.rewind.regions.RegionManager;
import com.rewind.scheduler.RestoreScheduler;
import com.rewind.snapshot.SnapshotManager;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import com.rewind.snapshot.SnapshotKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class RewindCommand implements CommandExecutor, TabCompleter {

    private final RewindPlugin plugin;
    private final RegionManager regionManager;
    private final SnapshotManager snapshotManager;
    private final RestoreScheduler restoreScheduler;
    private final DebugManager debug;

    private final ConcurrentHashMap<UUID, Map<String, BukkitTask>> showTasks = new ConcurrentHashMap<>();

    public RewindCommand(RewindPlugin plugin, RegionManager regionManager,
                         SnapshotManager snapshotManager, RestoreScheduler restoreScheduler,
                         DebugManager debug) {
        this.plugin = plugin;
        this.regionManager = regionManager;
        this.snapshotManager = snapshotManager;
        this.restoreScheduler = restoreScheduler;
        this.debug = debug;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create" -> {
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /rewind create <name> [radius]");
                    return true;
                }
                return handleCreate(sender, args);
            }
            case "delete" -> {
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /rewind delete <name>");
                    return true;
                }
                return handleDelete(sender, args[1]);
            }
            case "list" -> {
                return handleList(sender);
            }
            case "restore" -> {
                return handleRestore(sender, args);
            }
            case "info" -> {
                return handleInfo(sender, args);
            }
            case "debug" -> {
                return handleDebug(sender);
            }
            case "reload" -> {
                return handleReload(sender);
            }
            case "show" -> {
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /rewind show <name>");
                    return true;
                }
                return handleShow(sender, args);
            }
            default -> {
                sendHelp(sender);
                return true;
            }
        }
    }

    private boolean handleCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }

        String name = args[1];

        if (args.length >= 3) {
            try {
                int radius = Integer.parseInt(args[2]);
                Region region = new Region(name, player.getWorld().getName(), Region.Type.RADIUS, 60);
                region.setCenterX(player.getLocation().getBlockX());
                region.setCenterY(player.getLocation().getBlockY());
                region.setCenterZ(player.getLocation().getBlockZ());
                region.setRadius(radius);

                long start = System.currentTimeMillis();
                if (regionManager.createRegion(region)) {
                    queueRegionSnapshot(player.getWorld(), region);
                    debug.performance("create region " + name, start);
                    player.sendMessage("§aRegion §e" + name + " §acreated! (radius " + radius + ")");
                } else {
                    player.sendMessage("§cRegion §e" + name + " §calready exists.");
                }
            } catch (NumberFormatException e) {
                player.sendMessage("§cInvalid radius: " + args[2]);
            }
        } else {
            if (plugin.getWorldEdit() == null) {
                player.sendMessage("§cWorldEdit not found. Use /rewind create <name> <radius> instead.");
                return true;
            }

            Region region = createRegionFromWorldEdit(player, name);
            if (region == null) {
                player.sendMessage("§cPlease select a WorldEdit region first (//pos1, //pos2 or //sel cuboid).");
                return true;
            }

            long start = System.currentTimeMillis();
            if (regionManager.createRegion(region)) {
                queueRegionSnapshot(player.getWorld(), region);
                debug.performance("create region " + name, start);
                player.sendMessage("§aRegion §e" + name + " §acreated! (cuboid)");
            } else {
                player.sendMessage("§cRegion §e" + name + " §calready exists.");
            }
        }

        return true;
    }

    private Region createRegionFromWorldEdit(Player player, String name) {
        try {
            Class<?> weClass = Class.forName("com.sk89q.worldedit.bukkit.WorldEdit");
            Object weInstance = weClass.getMethod("getInstance").invoke(null);

            Class<?> bukkitAdapterClass = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            Object wePlayer = bukkitAdapterClass.getMethod("adapt", org.bukkit.entity.Player.class).invoke(null, player);

            Object sessionManager = weClass.getMethod("getSessionManager").invoke(weInstance);
            Class<?> sessionManagerClass = Class.forName("com.sk89q.worldedit.session.SessionManager");
            Object localSession = sessionManagerClass.getMethod("get", wePlayer.getClass()).invoke(sessionManager, wePlayer);

            Object weWorld = wePlayer.getClass().getMethod("getWorld").invoke(wePlayer);
            Class<?> localSessionClass = Class.forName("com.sk89q.worldedit.LocalSession");
            Object weRegion = localSessionClass.getMethod("selection", weWorld.getClass()).invoke(localSession, weWorld);

            if (weRegion == null) return null;

            Class<?> regionClass = Class.forName("com.sk89q.worldedit.regions.Region");
            Object minPoint = regionClass.getMethod("getMinimumPoint").invoke(weRegion);
            Object maxPoint = regionClass.getMethod("getMaximumPoint").invoke(weRegion);

            Class<?> vectorClass = Class.forName("com.sk89q.worldedit.Vector");
            int minX = (int) vectorClass.getMethod("getBlockX").invoke(minPoint);
            int minY = (int) vectorClass.getMethod("getBlockY").invoke(minPoint);
            int minZ = (int) vectorClass.getMethod("getBlockZ").invoke(minPoint);
            int maxX = (int) vectorClass.getMethod("getBlockX").invoke(maxPoint);
            int maxY = (int) vectorClass.getMethod("getBlockY").invoke(maxPoint);
            int maxZ = (int) vectorClass.getMethod("getBlockZ").invoke(maxPoint);

            Region region = new Region(name, player.getWorld().getName(), Region.Type.CUBOID, 60);
            region.setMinX(minX);
            region.setMinY(minY);
            region.setMinZ(minZ);
            region.setMaxX(maxX);
            region.setMaxY(maxY);
            region.setMaxZ(maxZ);

            return region;
        } catch (Exception e) {
            debug.log("Failed to get WorldEdit selection: %s", e.getMessage());
            return null;
        }
    }

    private void queueRegionSnapshot(World world, Region region) {
        int minChunkX = region.getMinChunkX();
        int minChunkZ = region.getMinChunkZ();
        int maxChunkX = region.getMaxChunkX();
        int maxChunkZ = region.getMaxChunkZ();

        int count = 0;
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                if (region.isInsideChunk(cx, cz)) {
                    snapshotManager.queueSnapshot(region.getName(), world, cx, cz);
                    count++;
                }
            }
        }

        debug.region(region.getName(), "queued " + count + " chunks for snapshot");
        plugin.getLogger().info("Queued " + count + " chunks for snapshot in region " + region.getName());
    }

    private boolean handleDelete(CommandSender sender, String name) {
        Region region = regionManager.getRegion(name);
        if (region == null) {
            sender.sendMessage("§cRegion §e" + name + " §cnot found.");
            return true;
        }

        if (regionManager.deleteRegion(name)) {
            snapshotManager.removeRegionSnapshots(region);
            restoreScheduler.cancelRegionRestores(region.getName());
            debug.region(name, "deleted");
            sender.sendMessage("§cRegion §e" + name + " §cdeleted.");
        } else {
            sender.sendMessage("§cRegion §e" + name + " §cnot found.");
        }
        return true;
    }

    private boolean handleList(CommandSender sender) {
        var regions = regionManager.getAllRegions();
        if (regions.isEmpty()) {
            sender.sendMessage("§eNo regions defined.");
            return true;
        }

        sender.sendMessage("§6=== Rewind Regions ===");
        for (Region region : regions) {
            sender.sendMessage("§e- §f" + region.getName() + " §7(" + region.getType().name().toLowerCase() +
                ", " + region.getTimer() + "s)");
        }
        return true;
    }

    private boolean handleRestore(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§eRestoring all regions...");
            for (Region region : regionManager.getAllRegions()) {
                restoreScheduler.restoreRegion(region.getName(), region.getWorldName(),
                    region.getMinChunkX(), region.getMinChunkZ(),
                    region.getMaxChunkX(), region.getMaxChunkZ());
            }
            sender.sendMessage("§aAll regions scheduled for restore!");
            return true;
        }

        String name = args[1];
        Region region = regionManager.getRegion(name);
        if (region == null) {
            sender.sendMessage("§cRegion §e" + name + " §cnot found.");
            return true;
        }

        if (args.length == 2) {
            sender.sendMessage("§eRestoring region §6" + name + "§e...");
            long start = System.currentTimeMillis();
            restoreScheduler.restoreRegionForce(region.getName(), region.getWorldName(),
                region.getMinChunkX(), region.getMinChunkZ(),
                region.getMaxChunkX(), region.getMaxChunkZ());
            debug.performance("restore region " + name, start);
            sender.sendMessage("§aRegion §6" + name + " §arestored!");
        } else if (args.length == 4) {
            try {
                int chunkX = Integer.parseInt(args[2]);
                int chunkZ = Integer.parseInt(args[3]);

                if (!snapshotManager.hasSnapshot(region.getName(), region.getWorldName(), chunkX, chunkZ)) {
                    sender.sendMessage("§cNo snapshot for chunk " + chunkX + ", " + chunkZ);
                    return true;
                }

                sender.sendMessage("§eRestoring chunk §6" + chunkX + ", " + chunkZ + "§e...");
                long start = System.currentTimeMillis();
                restoreScheduler.restoreChunkForce(region.getName(), region.getWorldName(), chunkX, chunkZ);
                debug.performance("restore chunk " + chunkX + "," + chunkZ, start);
                sender.sendMessage("§aChunk §6" + chunkX + ", " + chunkZ + " §arestored!");
            } catch (NumberFormatException e) {
                sender.sendMessage("§cInvalid chunk coordinates.");
            }
        } else if (args.length == 6) {
            try {
                int chunkX1 = Integer.parseInt(args[2]);
                int chunkZ1 = Integer.parseInt(args[3]);
                int chunkX2 = Integer.parseInt(args[4]);
                int chunkZ2 = Integer.parseInt(args[5]);

                int minCX = Math.min(chunkX1, chunkX2);
                int maxCX = Math.max(chunkX1, chunkX2);
                int minCZ = Math.min(chunkZ1, chunkZ2);
                int maxCZ = Math.max(chunkZ1, chunkZ2);

                sender.sendMessage("§eRestoring chunks §6" + minCX + "," + minCZ + " §eto §6" + maxCX + "," + maxCZ + "§e...");
                long start = System.currentTimeMillis();
                restoreScheduler.restoreRegionForce(region.getName(), region.getWorldName(), minCX, minCZ, maxCX, maxCZ);
                debug.performance("restore area", start);
                sender.sendMessage("§aChunks restored!");
            } catch (NumberFormatException e) {
                sender.sendMessage("§cInvalid chunk coordinates.");
            }
        } else {
            sender.sendMessage("§cUsage: /rewind restore <name> [chunkX chunkZ] [chunkX2 chunkZ2]");
        }

        return true;
    }

    private boolean handleInfo(CommandSender sender, String[] args) {
        if (args.length >= 2) {
            String name = args[1];
            Region region = regionManager.getRegion(name);
            if (region == null) {
                sender.sendMessage("§cRegion §e" + name + " §cnot found.");
                return true;
            }

            sender.sendMessage("§6=== Region Info ===");
            sender.sendMessage("§eName: §f" + region.getName());
            sender.sendMessage("§eWorld: §f" + region.getWorldName());
            sender.sendMessage("§eType: §f" + region.getType().name());
            sender.sendMessage("§eTimer: §f" + region.getTimer() + "s");

            if (region.getType() == Region.Type.CUBOID) {
                sender.sendMessage("§eBounds: §f" + region.getMinX() + "," + region.getMinY() + "," + region.getMinZ() +
                    " to " + region.getMaxX() + "," + region.getMaxY() + "," + region.getMaxZ());
            } else {
                sender.sendMessage("§eCenter: §f" + region.getCenterX() + "," + region.getCenterY() + "," + region.getCenterZ());
                sender.sendMessage("§eRadius: §f" + region.getRadius());
            }
        } else {
            sender.sendMessage("§6=== Rewind Info ===");
            sender.sendMessage("§eTotal Regions: §f" + regionManager.getRegionCount());
            sender.sendMessage("§ePending Restores: §f" + restoreScheduler.getPendingCount());
            sender.sendMessage("§eSnapshots: §f" + snapshotManager.getSnapshotCount());
            sender.sendMessage("§eSnapshot Queue: §f" + snapshotManager.getSnapshotQueueSize());
            sender.sendMessage("§eDebug Mode: §f" + (debug.isEnabled() ? "§aON" : "§cOFF"));
        }
        return true;
    }

    private boolean handleDebug(CommandSender sender) {
        if (!sender.hasPermission("rewind.debug")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        debug.setEnabled(!debug.isEnabled());
        sender.sendMessage("§eDebug mode: " + (debug.isEnabled() ? "§aON" : "§cOFF"));
        debug.log("Debug mode toggled " + (debug.isEnabled() ? "on" : "off"));
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("rewind.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        plugin.reloadConfig();
        plugin.getRestoreScheduler().loadConfig();
        sender.sendMessage("§aRewind config reloaded!");
        return true;
    }

    private boolean handleShow(CommandSender sender, String[] args) {
        if (!sender.hasPermission("rewind.show")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }

        String name = args[1];
        Region region = regionManager.getRegion(name);
        if (region == null) {
            sender.sendMessage("§cRegion §e" + name + " §cnot found.");
            return true;
        }

        if (!player.getWorld().getName().equals(region.getWorldName())) {
            sender.sendMessage("§cYou must be in the same world as the region.");
            return true;
        }

        java.util.Set<SnapshotKey> keys = snapshotManager.getSnapshotKeysInRegion(region.getName());

        UUID playerId = player.getUniqueId();
        Map<String, BukkitTask> playerShows = showTasks.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());

        BukkitTask existing = playerShows.get(region.getName());
        if (existing != null) {
            existing.cancel();
            playerShows.remove(region.getName());
            if (playerShows.isEmpty()) showTasks.remove(playerId);
            sender.sendMessage("§cShow off §7(" + region.getName() + ")");
            return true;
        }

        int y = player.getLocation().getBlockY() + 2;
        int total = keys.size();
        sender.sendMessage("§aShow on §7(" + total + " snapshotted chunks for §6" + region.getName() + "§7)");

        Particle.DustOptions dust = new Particle.DustOptions(org.bukkit.Color.LIME, 2f);
        BukkitTask task = org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (SnapshotKey key : keys) {
                drawChunkEdges(player, key, y, dust);
            }
        }, 0, 20);

        playerShows.put(region.getName(), task);

        return true;
    }

    private void drawChunkEdges(Player player, SnapshotKey key, int y, Particle.DustOptions dust) {
        int minX = key.getChunkX() << 4;
        int minZ = key.getChunkZ() << 4;
        int maxX = minX + 15;
        int maxZ = minZ + 15;

        for (int x = minX; x < maxX; x += 2) {
            player.spawnParticle(Particle.DUST, x + 0.5, y, minZ + 0.5, 1, 0, 0, 0, 0, dust);
            player.spawnParticle(Particle.DUST, x + 0.5, y, maxZ + 0.5, 1, 0, 0, 0, 0, dust);
        }
        for (int z = minZ; z < maxZ; z += 2) {
            player.spawnParticle(Particle.DUST, minX + 0.5, y, z + 0.5, 1, 0, 0, 0, 0, dust);
            player.spawnParticle(Particle.DUST, maxX + 0.5, y, z + 0.5, 1, 0, 0, 0, 0, dust);
        }
    }

    public void cancelAllShows() {
        for (Map<String, BukkitTask> playerShows : showTasks.values()) {
            for (BukkitTask task : playerShows.values()) {
                task.cancel();
            }
        }
        showTasks.clear();
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6=== Rewind Commands ===");
        sender.sendMessage("§e/rewind create <name> [radius] §7- Create region");
        sender.sendMessage("§e/rewind delete <name> §7- Delete region");
        sender.sendMessage("§e/rewind list §7- List regions");
        sender.sendMessage("§e/rewind restore <name> §7- Restore entire region");
        sender.sendMessage("§e/rewind restore <name> <chunkX> <chunkZ> §7- Restore specific chunk");
        sender.sendMessage("§e/rewind restore <name> <x1> <z1> <x2> <z2> §7- Restore chunk area");
        sender.sendMessage("§e/rewind info [name] §7- View info");
        sender.sendMessage("§e/rewind show <name> §7- Toggle snapshotted chunk grid");
        sender.sendMessage("§e/rewind reload §7- Reload config");
        sender.sendMessage("§e/rewind debug §7- Toggle debug mode");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return List.of("create", "delete", "list", "restore", "info", "show", "reload", "debug").stream()
                .filter(s -> s.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }

        if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "delete", "restore", "info", "show" -> {
                    return regionManager.getAllRegions().stream()
                        .map(Region::getName)
                        .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
                }
            }
        }

        return new ArrayList<>();
    }
}
