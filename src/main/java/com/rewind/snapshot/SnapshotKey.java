package com.rewind.snapshot;

import java.util.Objects;

public final class SnapshotKey {

    private final String worldName;
    private final int chunkX;
    private final int chunkZ;

    public SnapshotKey(String worldName, int chunkX, int chunkZ) {
        this.worldName = worldName;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    public String getWorldName() { return worldName; }
    public int getChunkX() { return chunkX; }
    public int getChunkZ() { return chunkZ; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SnapshotKey that)) return false;
        return chunkX == that.chunkX && chunkZ == that.chunkZ && worldName.equals(that.worldName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(worldName, chunkX, chunkZ);
    }

    @Override
    public String toString() {
        return worldName + "_" + chunkX + "_" + chunkZ;
    }
}
