package com.rewind.regions;

import org.bukkit.Location;

public class Region {

    public enum Type {
        CUBOID,
        RADIUS
    }

    private final String name;
    private final String worldName;
    private final Type type;
    private int timer;

    private int minX, minY, minZ;
    private int maxX, maxY, maxZ;
    private int centerX, centerY, centerZ;
    private int radius;

    public Region(String name, String worldName, Type type, int timer) {
        this.name = name;
        this.worldName = worldName;
        this.type = type;
        this.timer = timer;
    }

    public boolean contains(Location loc) {
        if (loc.getWorld() == null) return false;
        if (!loc.getWorld().getName().equals(worldName)) return false;

        if (type == Type.CUBOID) {
            int x = loc.getBlockX();
            int y = loc.getBlockY();
            int z = loc.getBlockZ();
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        } else {
            int dx = loc.getBlockX() - centerX;
            int dy = loc.getBlockY() - centerY;
            int dz = loc.getBlockZ() - centerZ;
            return (dx * dx + dy * dy + dz * dz) <= (radius * radius);
        }
    }

    public int getMinChunkX() {
        if (type == Type.CUBOID) return minX >> 4;
        return (centerX - radius) >> 4;
    }

    public int getMinChunkZ() {
        if (type == Type.CUBOID) return minZ >> 4;
        return (centerZ - radius) >> 4;
    }

    public int getMaxChunkX() {
        if (type == Type.CUBOID) return maxX >> 4;
        return (centerX + radius) >> 4;
    }

    public int getMaxChunkZ() {
        if (type == Type.CUBOID) return maxZ >> 4;
        return (centerZ + radius) >> 4;
    }

    public boolean isInsideChunk(int chunkX, int chunkZ) {
        int blockX = (chunkX << 4) + 8;
        int blockZ = (chunkZ << 4) + 8;

        if (type == Type.CUBOID) {
            return blockX >= minX && blockX <= maxX && blockZ >= minZ && blockZ <= maxZ;
        } else {
            int dx = blockX - centerX;
            int dz = blockZ - centerZ;
            return (dx * dx + dz * dz) <= (radius * radius);
        }
    }

    public String getName() { return name; }
    public String getWorldName() { return worldName; }
    public Type getType() { return type; }
    public int getTimer() { return timer; }
    public void setTimer(int timer) { this.timer = timer; }

    public int getMinX() { return minX; }
    public void setMinX(int minX) { this.minX = minX; }
    public int getMinY() { return minY; }
    public void setMinY(int minY) { this.minY = minY; }
    public int getMinZ() { return minZ; }
    public void setMinZ(int minZ) { this.minZ = minZ; }
    public int getMaxX() { return maxX; }
    public void setMaxX(int maxX) { this.maxX = maxX; }
    public int getMaxY() { return maxY; }
    public void setMaxY(int maxY) { this.maxY = maxY; }
    public int getMaxZ() { return maxZ; }
    public void setMaxZ(int maxZ) { this.maxZ = maxZ; }
    public int getCenterX() { return centerX; }
    public void setCenterX(int centerX) { this.centerX = centerX; }
    public int getCenterY() { return centerY; }
    public void setCenterY(int centerY) { this.centerY = centerY; }
    public int getCenterZ() { return centerZ; }
    public void setCenterZ(int centerZ) { this.centerZ = centerZ; }
    public int getRadius() { return radius; }
    public void setRadius(int radius) { this.radius = radius; }
}
