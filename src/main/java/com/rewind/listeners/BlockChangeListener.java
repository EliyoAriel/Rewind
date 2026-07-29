package com.rewind.listeners;

import com.rewind.DebugManager;
import com.rewind.RewindPlugin;
import com.rewind.regions.Region;
import com.rewind.regions.RegionManager;
import com.rewind.snapshot.SnapshotManager;
import com.rewind.scheduler.RestoreScheduler;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;

import java.util.EnumSet;
import java.util.Set;

public class BlockChangeListener implements Listener {

    private final RewindPlugin plugin;
    private final RegionManager regionManager;
    private final SnapshotManager snapshotManager;
    private final RestoreScheduler restoreScheduler;
    private final DebugManager debug;
    private final Set<Material> interactionBlocks = EnumSet.noneOf(Material.class);

    public BlockChangeListener(RewindPlugin plugin, RegionManager regionManager,
                               SnapshotManager snapshotManager, RestoreScheduler restoreScheduler,
                               DebugManager debug) {
        this.plugin = plugin;
        this.regionManager = regionManager;
        this.snapshotManager = snapshotManager;
        this.restoreScheduler = restoreScheduler;
        this.debug = debug;
        loadInteractionBlocks();
    }

    private void loadInteractionBlocks() {
        interactionBlocks.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("interaction-blocks");
        if (section == null || !section.getBoolean("enabled", true)) return;

        for (String name : section.getStringList("blocks")) {
            try {
                interactionBlocks.add(Material.valueOf(name.toUpperCase()));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid interaction block material: " + name);
            }
        }
        debug.log("Loaded %d interaction blocks", interactionBlocks.size());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("rewind.bypass")) return;

        scheduleRestore(event.getBlock().getLocation(), "block-break");
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("rewind.bypass")) return;

        scheduleRestore(event.getBlock().getLocation(), "block-place");
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        scheduleRestore(event.getBlock().getLocation(), "block-burn");
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        scheduleRestore(event.getBlock().getLocation(), "block-ignite");
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        for (org.bukkit.block.Block block : event.blockList()) {
            scheduleRestore(block.getLocation(), "block-explode");
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityExplode(org.bukkit.event.entity.EntityExplodeEvent event) {
        for (org.bukkit.block.Block block : event.blockList()) {
            scheduleRestore(block.getLocation(), "entity-explode");
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockGrow(BlockGrowEvent event) {
        if (isInteractionBlock(event.getBlock().getType())) {
            debug.log("Skipping interaction block grow: %s", event.getBlock().getType().name());
            return;
        }
        scheduleRestore(event.getBlock().getLocation(), "block-grow");
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockPistonExtend(BlockPistonExtendEvent event) {
        if (isInteractionBlock(event.getBlock().getType())) {
            debug.log("Skipping interaction block piston: %s", event.getBlock().getType().name());
            return;
        }
        for (org.bukkit.block.Block block : event.getBlocks()) {
            if (!isInteractionBlock(block.getType())) {
                scheduleRestore(block.getLocation(), "piston-extend");
            }
        }
        scheduleRestore(event.getBlock().getLocation(), "piston-extend-source");
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockPistonRetract(BlockPistonRetractEvent event) {
        if (isInteractionBlock(event.getBlock().getType())) {
            debug.log("Skipping interaction block piston: %s", event.getBlock().getType().name());
            return;
        }
        for (org.bukkit.block.Block block : event.getBlocks()) {
            if (!isInteractionBlock(block.getType())) {
                scheduleRestore(block.getLocation(), "piston-retract");
            }
        }
        scheduleRestore(event.getBlock().getLocation(), "piston-retract-source");
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onLeavesDecay(LeavesDecayEvent event) {
        if (isInteractionBlock(event.getBlock().getType())) {
            debug.log("Skipping interaction block decay: %s", event.getBlock().getType().name());
            return;
        }
        scheduleRestore(event.getBlock().getLocation(), "leaves-decay");
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        if (isInteractionBlock(event.getBlock().getType())) {
            debug.log("Skipping interaction block spread: %s", event.getBlock().getType().name());
            return;
        }
        scheduleRestore(event.getBlock().getLocation(), "block-spread");
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockFade(BlockFadeEvent event) {
        if (isInteractionBlock(event.getBlock().getType())) {
            debug.log("Skipping interaction block fade: %s", event.getBlock().getType().name());
            return;
        }
        scheduleRestore(event.getBlock().getLocation(), "block-fade");
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockForm(BlockFormEvent event) {
        if (isInteractionBlock(event.getBlock().getType())) {
            debug.log("Skipping interaction block form: %s", event.getBlock().getType().name());
            return;
        }
        scheduleRestore(event.getBlock().getLocation(), "block-form");
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent event) {
        if (isInteractionBlock(event.getBlock().getType())) {
            debug.log("Skipping interaction block flow: %s", event.getBlock().getType().name());
            return;
        }
        scheduleRestore(event.getBlock().getLocation(), "block-flow");
    }

    private boolean isInteractionBlock(Material material) {
        return interactionBlocks.contains(material);
    }

    private void scheduleRestore(Location loc, String reason) {
        World world = loc.getWorld();
        if (world == null) return;

        Region region = regionManager.getRegionAt(loc);
        if (region == null) return;

        String regionName = region.getName();
        int chunkX = loc.getBlockX() >> 4;
        int chunkZ = loc.getBlockZ() >> 4;

        if (plugin.isChunkExcluded(world.getName(), chunkX, chunkZ)) {
            debug.log("Chunk [%d, %d] in %s is excluded - skipping restore", chunkX, chunkZ, world.getName());
            return;
        }

        if (!snapshotManager.hasSnapshot(regionName, world.getName(), chunkX, chunkZ)) {
            debug.log("No snapshot for chunk [%d, %d] in %s - skipping restore", chunkX, chunkZ, world.getName());
            return;
        }

        debug.restore(world.getName(), chunkX, chunkZ, reason);
        restoreScheduler.scheduleRestore(regionName, world.getName(), chunkX, chunkZ, region.getTimer());
    }
}
