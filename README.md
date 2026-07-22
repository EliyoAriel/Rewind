# Rewind - Temporary Region Plugin

A Minecraft Paper plugin that makes blocks in designated regions temporarily change, then automatically restore to their original state.

## Features

- **Per-chunk snapshots** - Each chunk is independently backed up and restored
- **Gradual restore** - Chunks restore one-by-one, not all at once
- **Player-aware** - Chunks won't restore while players are nearby
- **Multiple triggers** - Tracks player changes, fire, explosions, pistons, water/lava, and more
- **Disk-only storage** - Snapshots stored on disk with gzip compression, minimal memory usage
- **Palette compression** - ~2-8KB per chunk (vs ~500KB raw)

## Installation

1. Download `Rewind-1.0.0.jar`
2. Place in your server's `plugins/` folder
3. Restart the server

## Configuration

```yaml
# config.yml

general:
  default-timer: 60          # Default restore timer in seconds
  debug-mode: false          # Enable debug logging

restore:
  chunks-per-interval: 1     # Chunks restored per interval
  interval-ticks: 20         # Delay between restore intervals (20 = 1 second)
  min-distance-chunks: 5     # Skip restore if player within this many chunks

storage:
  snapshot-dir: snapshots    # Directory for snapshot files

messages:
  prefix: "&6[Rewind] &r"
  region-created: "&aRegion &e%name% &acreated!"
  region-deleted: "&cRegion &e%name% &cdeleted."
  region-not-found: "&cRegion &e%name% &cnot found."
  restore-complete: "&aAll regions restored!"
  no-permission: "&cYou don't have permission to do this."
```

## Commands

| Command | Description |
|---------|-------------|
| `/rewind create <name> [radius]` | Create a region at your location |
| `/rewind delete <name>` | Delete a region and its snapshots |
| `/rewind list` | List all regions |
| `/rewind restore` | Restore all regions |
| `/rewind restore <name>` | Restore entire region |
| `/rewind restore <name> <chunkX> <chunkZ>` | Restore specific chunk |
| `/rewind restore <name> <x1> <z1> <x2> <z2>` | Restore chunk area |
| `/rewind info` | View plugin info |
| `/rewind info <name>` | View region info |
| `/rewind debug` | Toggle debug mode |

## Permissions

| Permission | Description |
|------------|-------------|
| `rewind.create` | Create regions |
| `rewind.delete` | Delete regions |
| `rewind.restore` | Restore regions |
| `rewind.info` | View info |
| `rewind.bypass` | Bypass region tracking |
| `rewind.admin` | All permissions |

## How It Works

### 1. Region Creation

When you create a region, the plugin:
- Queues all chunks in the region for snapshot
- Processes 10 chunks per tick (only loaded chunks)
- Saves compressed snapshots to disk

### 2. Block Changes

When a block changes in a region:
- The chunk's original state is already saved
- A restore timer starts (default 60 seconds)
- If another change happens, timer resets

### 3. Restore Process

When timer expires:
- Chunk added to restore queue
- Every 1 second, 1 chunk is restored
- If player is within 5 chunks, restore is delayed
- Player moves away, restore happens

## Triggered Events

| Event | Source |
|-------|--------|
| `BlockBreakEvent` | Player mining |
| `BlockPlaceEvent` | Player building |
| `BlockBurnEvent` | Fire burning |
| `BlockIgniteEvent` | Block catching fire |
| `BlockExplodeEvent` | TNT, etc. |
| `EntityExplodeEvent` | Creepers, dragons |
| `BlockSpreadEvent` | Fire/mushroom/tree |
| `BlockFadeEvent` | Ice melting |
| `BlockFormEvent` | Snow/ice forming |
| `BlockFromToEvent` | Water/lava flow |
| `BlockGrowEvent` | Crops growing |
| `BlockPistonExtendEvent` | Pistons pushing |
| `BlockPistonRetractEvent` | Pistons pulling |
| `LeavesDecayEvent` | Leaves decaying |

## File Format

Snapshots use a compressed binary format:

- **Magic bytes**: `REW`
- **Format version**: 2
- **Palette + indices**: Stores unique materials once, references by index
- **Gzip compression**: ~2-8KB per chunk

## Performance

| Metric | Value |
|--------|-------|
| Snapshot size | ~2-8KB per chunk |
| Snapshot creation | 10 chunks/tick |
| Restore speed | 1 chunk/second |
| Memory usage | Minimal (index only) |

## Multiverse Support

Fully compatible with Multiverse. Worlds are resolved by name automatically.

## Debug Mode

Enable with `/rewind debug` or set `debug-mode: true` in config.

Debug logs:
- Snapshot queue events
- Restore queue events
- Region create/delete
- Performance timing
