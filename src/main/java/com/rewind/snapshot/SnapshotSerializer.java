package com.rewind.snapshot;

import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.World;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class SnapshotSerializer {

    private static final byte[] MAGIC = {'R', 'E', 'W'};
    private static final byte FORMAT_VERSION = 2;
    private static final int MIN_Y = -64;
    private static final int MAX_Y = 320;
    private static final int SECTION_COUNT = (MAX_Y - MIN_Y) >> 4;

    private final Logger logger;
    private final Set<String> whitelist = new HashSet<>();

    public SnapshotSerializer(Logger logger) {
        this.logger = logger;
    }

    public void loadWhitelist(List<String> blocks) {
        whitelist.clear();
        for (String block : blocks) {
            whitelist.add(block.toUpperCase());
        }
    }

    public boolean isWhitelisted(String materialName) {
        return whitelist.contains(materialName.toUpperCase());
    }

    public Set<String> getWhitelist() {
        return whitelist;
    }

    public void serialize(ChunkSnapshot snapshot, File file) throws IOException {
        file.getParentFile().mkdirs();

        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(file))))) {

            out.write(MAGIC);
            out.writeByte(FORMAT_VERSION);

            byte[] worldBytes = snapshot.getWorldName().getBytes(StandardCharsets.UTF_8);
            out.writeShort(worldBytes.length);
            out.write(worldBytes);

            out.writeInt(snapshot.getX());
            out.writeInt(snapshot.getZ());
            out.writeLong(System.currentTimeMillis());

            List<String> palette = new ArrayList<>();
            Map<String, Short> paletteMap = new HashMap<>();

            short[][][] indices = new short[16][SECTION_COUNT * 16][16];

            for (int sectionIdx = 0; sectionIdx < SECTION_COUNT; sectionIdx++) {
                int baseY = MIN_Y + (sectionIdx << 4);
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int y = 0; y < 16; y++) {
                            int worldY = baseY + y;
                            int arrayY = worldY - MIN_Y;
                            Material mat = snapshot.getBlockType(x, worldY, z);
                            String name = mat.name();

                            Short idx = paletteMap.get(name);
                            if (idx == null) {
                                idx = (short) palette.size();
                                palette.add(name);
                                paletteMap.put(name, idx);
                            }
                            indices[x][arrayY][z] = idx;
                        }
                    }
                }
            }

            out.writeShort(palette.size());
            for (String name : palette) {
                out.writeUTF(name);
            }

            boolean useByte = palette.size() <= 256;
            out.writeBoolean(useByte);

            for (int x = 0; x < 16; x++) {
                for (int arrayY = 0; arrayY < SECTION_COUNT * 16; arrayY++) {
                    for (int z = 0; z < 16; z++) {
                        if (useByte) {
                            out.writeByte(indices[x][arrayY][z]);
                        } else {
                            out.writeShort(indices[x][arrayY][z]);
                        }
                    }
                }
            }
        }
    }

    public SnapshotData deserialize(File file) throws IOException {
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(new GZIPInputStream(new FileInputStream(file))))) {

            byte[] magic = new byte[3];
            in.readFully(magic);
            if (magic[0] != MAGIC[0] || magic[1] != MAGIC[1] || magic[2] != MAGIC[2]) {
                throw new IOException("Invalid .rewind file: bad magic bytes");
            }

            byte version = in.readByte();
            if (version != FORMAT_VERSION) {
                throw new IOException("Unsupported .rewind version: " + version);
            }

            short worldNameLen = in.readShort();
            byte[] worldBytes = new byte[worldNameLen];
            in.readFully(worldBytes);
            String worldName = new String(worldBytes, StandardCharsets.UTF_8);

            int chunkX = in.readInt();
            int chunkZ = in.readInt();
            long timestamp = in.readLong();

            short paletteSize = in.readShort();
            String[] palette = new String[paletteSize];
            for (int i = 0; i < paletteSize; i++) {
                palette[i] = in.readUTF();
            }

            boolean useByte = in.readBoolean();

            String[][][] blockNames = new String[16][SECTION_COUNT * 16][16];

            for (int x = 0; x < 16; x++) {
                for (int arrayY = 0; arrayY < SECTION_COUNT * 16; arrayY++) {
                    for (int z = 0; z < 16; z++) {
                        short idx;
                        if (useByte) {
                            idx = in.readByte();
                        } else {
                            idx = in.readShort();
                        }
                        blockNames[x][arrayY][z] = palette[idx];
                    }
                }
            }

            return new SnapshotData(worldName, chunkX, chunkZ, timestamp, blockNames, palette, this);
        }
    }

    public static class SnapshotData {
        private final String worldName;
        private final int chunkX;
        private final int chunkZ;
        private final long timestamp;
        private final String[][][] blockNames;
        private final Map<String, Material> materialCache;
        private final Set<String> whitelistedCache;

        public SnapshotData(String worldName, int chunkX, int chunkZ, long timestamp,
                           String[][][] blockNames, String[] palette, SnapshotSerializer serializer) {
            this.worldName = worldName;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.timestamp = timestamp;
            this.blockNames = blockNames;
            this.materialCache = new HashMap<>();
            this.whitelistedCache = new HashSet<>();

            for (String name : palette) {
                if (name.equals("AIR")) continue;
                if (serializer != null && serializer.isWhitelisted(name)) {
                    whitelistedCache.add(name);
                    continue;
                }
                Material mat = Material.matchMaterial(name);
                if (mat != null) {
                    materialCache.put(name, mat);
                }
            }
        }

        public String getWorldName() { return worldName; }
        public int getChunkX() { return chunkX; }
        public int getChunkZ() { return chunkZ; }
        public long getTimestamp() { return timestamp; }
        public String[][][] getBlockNames() { return blockNames; }

        public void applyToWorld(World world) {
            org.bukkit.Chunk chunk = world.getChunkAt(chunkX, chunkZ);

            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = MIN_Y; y < MAX_Y; y++) {
                        int arrayY = y - MIN_Y;
                        String blockName = blockNames[x][arrayY][z];

                        if (blockName.equals("AIR")) continue;
                        if (whitelistedCache.contains(blockName)) continue;

                        Material mat = materialCache.get(blockName);
                        if (mat != null) {
                            org.bukkit.block.Block block = chunk.getBlock(x, y, z);
                            if (block.getType() != mat) {
                                block.setType(mat, false);
                            }
                        }
                    }
                }
            }
        }
    }
}
