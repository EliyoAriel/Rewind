# Rewind - Temporary Region Restore Plugin

A Minecraft Paper plugin that snapshots designated regions, then automatically restores them after block changes. Manual restore also supported.

## Features

- **Per-chunk snapshots** - Each chunk independently backed up and restored
- **Async restore** - GZIP decompression and material resolution off main thread
- **Gradual restore** - Chunks restore one-by-one, not all at once
- **Manual restore** - Force restore with `/rewind restore`, bypasses player distance check
- **Player-aware** - Automatic restore skips chunks near players, retries later
- **14 block triggers** - Tracks player changes, fire, explosions, pistons, water/lava, growth, and more
- **Interaction blocks** - Doors, plants, pistons etc. don't trigger restore (configurable)
- **Block whitelist** - Chests, signs, banners etc. skip restore (configurable)
- **Priority system** - Timer based on change count (low/normal/high)
- **WorldEdit support** - Create cuboid regions from WE selection
- **Disk-only storage** - Snapshots on disk with gzip + palette compression (~2-8KB per chunk)
- **Action bar notifications** - Progress bar and completion messages

## Installation

1. Download `Rewind-1.0.0.jar`
2. Place in your server's `plugins/` folder
3. Restart the server

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/rewind create <name> [radius]` | Create region (radius or WorldEdit selection) | `rewind.create` |
| `/rewind delete <name>` | Delete region and its snapshots | `rewind.delete` |
| `/rewind list` | List all regions | `rewind.list` |
| `/rewind restore` | Force restore all regions | `rewind.restore` |
| `/rewind restore <name>` | Force restore entire region | `rewind.restore` |
| `/rewind restore <name> <chunkX> <chunkZ>` | Force restore specific chunk | `rewind.restore` |
| `/rewind restore <name> <x1> <z1> <x2> <z2>` | Force restore chunk area | `rewind.restore` |
| `/rewind info` | View plugin info | `rewind.info` |
| `/rewind info <name>` | View region info | `rewind.info` |
| `/rewind reload` | Reload config | `rewind.admin` |
| `/rewind debug` | Toggle debug mode | `rewind.debug` |

## Permissions

| Permission | Default | Description |
|------------|---------|-------------|
| `rewind.create` | op | Create regions |
| `rewind.delete` | op | Delete regions |
| `rewind.list` | true | List regions |
| `rewind.restore` | op | Manual restore + progress bar |
| `rewind.info` | true | View info |
| `rewind.bypass` | false | Bypass block change tracking |
| `rewind.debug` | op | Toggle debug mode |
| `rewind.admin` | op | Reload config |

## Configuration

```yaml
priority:
  low-changes-threshold: 5         # 1-5 blocks = low priority
  normal-changes-threshold: 20     # 6-20 blocks = normal priority
  high-changes-threshold: 50       # 21+ blocks = high priority
  low-priority-seconds: 300        # Low priority timer (5 min)
  normal-priority-seconds: 60      # Normal priority timer (1 min)
  high-priority-seconds: 30        # High priority timer (30 sec)

restore:
  chunks-per-interval: 1           # Chunks restored per interval
  interval-ticks: 20               # Delay between restore intervals
  min-distance-chunks: 5           # Skip restore if player nearby

notifications:
  enabled: true
  chat: "&eChunk at &6%x%, %z% &erestoring in &6%seconds%s"
  seconds-before: 5

progress:
  enabled: true
  chat: "&eRestoring chunks... &6[%bar%] &e%done%/%total%"
  bar-length: 20
  bar-filled: "="
  bar-empty: "-"

sounds:
  enabled: true
  sound: "ENTITY_EXPERIENCE_ORB_PICKUP"
  volume: 1.0
  pitch: 1.0

interaction-blocks:
  enabled: true                    # State-only blocks skip restore
  blocks:
    - OAK_DOOR
    - LEVER
    - WHEAT
    - COMPOSTER
    # ... (80+ materials in default config)

whitelist:
  enabled: false                   # These blocks skip restore on snapshot
  blocks:
    - CHEST
    - OAK_SIGN
    - PAINTING
    # ... (60+ materials in default config)
```

## How It Works

### 1. Region Creation

When you create a region (`/rewind create`):
- All chunks in the region are queued for snapshot
- Processes 10 chunks/tick (only loaded chunks are snapshotted)
- Unloaded chunks are skipped (no async chunk loading for snapshots)
- Snapshots saved to disk as compressed binary files

### 2. Automatic Restore

When a block changes in a region:
- Timer starts based on region's configured time (default 60s)
- Once a timer starts, it does **not** reset on subsequent changes
- When timer expires, chunk added to gradual restore queue
- Every 1 second, 1 chunk restored (configurable)
- If player within 5 chunks, restore is delayed until they move away
- After 100 ticks of being stuck, forces restore anyway

### 3. Manual Restore

`/rewind restore <name>`:
- Bypasses player distance check (restores immediately)
- Bypasses timer (restores now)
- Shows action bar progress to online players
- Shows "Rewind complete!" when done
- Runs async — GZIP decompression off main thread, block placement on main thread

### 4. Interaction Blocks

State-only blocks (doors, plants, pistons, etc.) don't trigger restore timers. This prevents farms, redstone, and player interactions from starting unnecessary restore countdowns.

### 5. Block Whitelist

When enabled, whitelisted blocks (chests, signs, banners) are skipped during restore. Their contents/state are preserved.

## Triggered Events

| Event | Source |
|-------|--------|
| `BlockBreakEvent` | Player mining |
| `BlockPlaceEvent` | Player building |
| `BlockBurnEvent` | Fire burning |
| `BlockIgniteEvent` | Block catching fire |
| `BlockExplodeEvent` | TNT, etc. |
| `EntityExplodeEvent` | Creepers, dragons |
| `BlockSpreadEvent` | Fire/mushroom/tree spread |
| `BlockFadeEvent` | Ice melting |
| `BlockFormEvent` | Snow/ice forming |
| `BlockFromToEvent` | Water/lava flow |
| `BlockGrowEvent` | Crops growing |
| `BlockPistonExtendEvent` | Pistons pushing |
| `BlockPistonRetractEvent` | Pistons pulling |
| `LeavesDecayEvent` | Leaves decaying |

## Snapshot Format

Binary format with gzip compression (~2-8KB per chunk):

- **Magic bytes**: `REW`
- **Format version**: 2
- **Palette**: Stores unique material names once
- **Indexed block storage**: Each block references palette by index (byte or short)
- **Material cache**: Pre-built at deserialization for fast apply

## Performance

| Metric | Value |
|--------|-------|
| Snapshot size | ~2-8KB per chunk |
| Snapshot creation | 10 chunks/tick |
| Restore speed | 1 chunk/interval (configurable) |
| Memory usage | Minimal (ConcurrentHashMap index only) |
| Decompression | Async (off main thread) |
| Material resolution | Cached per-chunk at load time |

## Debug Mode

Enable with `/rewind debug` (requires `rewind.debug` permission).

Debug logs:
- Snapshot queue events
- Restore queue events  
- Timer management
- Player distance checks
- Performance timing
- Interaction block skips

Notifications and progress bar only show during automatic restores when debug is enabled. Manual restore always shows progress.
