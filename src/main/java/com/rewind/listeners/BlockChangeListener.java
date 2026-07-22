package com.rewind.listeners;

import com.rewind.DebugManager;
import com.rewind.RewindPlugin;
import com.rewind.regions.Region;
import com.rewind.regions.RegionManager;
import com.rewind.snapshot.SnapshotManager;
import com.rewind.scheduler.RestoreScheduler;
import org.bukkit.Location;
import org.bukkit.World;
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

public class BlockChangeListener implements Listener {

    private final RewindPlugin plugin;
    private final RegionManager regionManager;
    private final SnapshotManager snapshotManager;
    private final RestoreScheduler restoreScheduler;
    private final DebugManager debug;

    public BlockChangeListener(RewindPlugin plugin, RegionManager regionManager,
                               SnapshotManager snapshotManager, RestoreScheduler restoreScheduler,
                               DebugManager debug) {
        this.plugin = plugin;
        this.regionManager = regionManager;
        this.snapshotManager = snapshotManager;
        this.restoreScheduler = restoreScheduler;
        this.debug = debug;
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
    public void onBlockSpread(BlockSpreadEvent event) {
        scheduleRestore(event.getBlock().getLocation(), "block-spread");
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockFade(BlockFadeEvent event) {
        scheduleRestore(event.getBlock().getLocation(), "block-fade");
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockForm(BlockFormEvent event) {
        scheduleRestore(event.getBlock().getLocation(), "block-form");
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent event) {
        scheduleRestore(event.getBlock().getLocation(), "block-flow");
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockGrow(BlockGrowEvent event) {
        scheduleRestore(event.getBlock().getLocation(), "block-grow");
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockPistonExtend(BlockPistonExtendEvent event) {
        for (org.bukkit.block.Block block : event.getBlocks()) {
            scheduleRestore(block.getLocation(), "piston-extend");
        }
        scheduleRestore(event.getBlock().getLocation(), "piston-extend-source");
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockPistonRetract(BlockPistonRetractEvent event) {
        for (org.bukkit.block.Block block : event.getBlocks()) {
            scheduleRestore(block.getLocation(), "piston-retract");
        }
        scheduleRestore(event.getBlock().getLocation(), "piston-retract-source");
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onLeavesDecay(LeavesDecayEvent event) {
        scheduleRestore(event.getBlock().getLocation(), "leaves-decay");
    }

    private void scheduleRestore(Location loc, String reason) {
        World world = loc.getWorld();
        if (world == null) return;

        Region region = regionManager.getRegionAt(loc);
        if (region == null) return;

        int chunkX = loc.getBlockX() >> 4;
        int chunkZ = loc.getBlockZ() >> 4;

        if (!snapshotManager.hasSnapshot(world.getName(), chunkX, chunkZ)) {
            debug.log("No snapshot for chunk [%d, %d] in %s - skipping restore", chunkX, chunkZ, world.getName());
            return;
        }

        debug.restore(world.getName(), chunkX, chunkZ, reason);
        restoreScheduler.scheduleRestore(world.getName(), chunkX, chunkZ, region.getTimer());
    }
}
