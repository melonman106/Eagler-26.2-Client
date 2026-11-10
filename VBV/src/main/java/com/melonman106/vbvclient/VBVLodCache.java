package com.melonman106.vbvclient;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

/**
 * Remembers a coarse picture of every chunk the client has loaded: for each chunk a 4x4 grid of
 * "surface height + top-block colour" samples (one per 4x4 blocks). The LOD renderer draws this
 * remembered terrain beyond the real render distance, so it only knows terrain the client has
 * actually received so far (it fills in as you explore). Nothing here touches the GPU.
 */
public final class VBVLodCache {
    /** Samples per chunk side. 4x4 samples, one per 4x4 blocks. */
    public static final int GRID = 4;
    public static final short NO_DATA = Short.MIN_VALUE;
    /** Hard cap so memory stays bounded on a small device. */
    private static final int MAX_CHUNKS = 60_000;

    /** Sampled surface of one chunk. */
    public static final class Column {
        public final short[] height = new short[GRID * GRID];
        public final int[] rgb = new int[GRID * GRID];
    }

    private static final Long2ObjectOpenHashMap<Column> COLUMNS = new Long2ObjectOpenHashMap<>();
    private static ClientLevel owner;
    private static int version;

    private VBVLodCache() {}

    private static long key(int chunkX, int chunkZ) {
        return ((long) chunkX & 0xFFFFFFFFL) | (((long) chunkZ & 0xFFFFFFFFL) << 32);
    }

    /** Bumps whenever the stored terrain changes; the renderer uses it to know when to rebuild. */
    public static int version() {
        return version;
    }

    public static int size() {
        return COLUMNS.size();
    }

    public static Column get(int chunkX, int chunkZ) {
        return COLUMNS.get(key(chunkX, chunkZ));
    }

    public static void clear() {
        COLUMNS.clear();
        owner = null;
        version++;
    }

    /** Called when a chunk arrives from the server or is about to be dropped. */
    public static void capture(ClientLevel level, LevelChunk chunk) {
        if (!VBVLod.isActive() || level == null || chunk == null) {
            return;
        }
        try {
            if (!usable(level)) {
                return;
            }
            adopt(level);
            ChunkPos pos = chunk.getPos();
            long k = key(pos.x(), pos.z());
            Column column = COLUMNS.get(k);
            if (column == null) {
                if (COLUMNS.size() >= MAX_CHUNKS) {
                    trim(pos.x(), pos.z());
                }
                column = new Column();
                COLUMNS.put(k, column);
            }
            sample(level, chunk, column);
            version++;
        } catch (Throwable t) {
            VBVLod.fail("chunk capture: " + t);
        }
    }

    /** Walks the chunks the client already holds once, so enabling LOD mid-game shows them. */
    public static void scanLoaded(ClientLevel level, int centerChunkX, int centerChunkZ, int radius) {
        if (level == null || !usable(level)) {
            return;
        }
        adopt(level);
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                LevelChunk chunk = level.getChunkSource().getChunk(centerChunkX + dx, centerChunkZ + dz, false);
                if (chunk != null) {
                    capture(level, chunk);
                }
            }
        }
    }

    /** LOD only makes sense in overworld-like dimensions (sky light, no ceiling). */
    public static boolean usable(ClientLevel level) {
        return level.dimensionType().hasSkyLight() && !level.dimensionType().hasCeiling();
    }

    private static void adopt(ClientLevel level) {
        if (owner != level) {
            COLUMNS.clear();
            owner = level;
            version++;
        }
    }

    /** Drops the chunks farthest from (chunkX, chunkZ) to make room for new ones. */
    private static void trim(int chunkX, int chunkZ) {
        int target = MAX_CHUNKS - MAX_CHUNKS / 8;
        int radius = VBVLod.rangeChunks() + 8;
        long[] keys = COLUMNS.keySet().toLongArray();
        for (long k : keys) {
            int cx = (int) k;
            int cz = (int) (k >> 32);
            if (Math.max(Math.abs(cx - chunkX), Math.abs(cz - chunkZ)) > radius) {
                COLUMNS.remove(k);
            }
        }
        // Still too full (e.g. a huge reach): drop everything beyond a shrinking radius.
        while (COLUMNS.size() > target && radius > 8) {
            radius = radius * 3 / 4;
            for (long k : COLUMNS.keySet().toLongArray()) {
                int cx = (int) k;
                int cz = (int) (k >> 32);
                if (Math.max(Math.abs(cx - chunkX), Math.abs(cz - chunkZ)) > radius) {
                    COLUMNS.remove(k);
                }
            }
        }
    }

    private static void sample(ClientLevel level, LevelChunk chunk, Column column) {
        int minY = level.getMinY();
        ChunkPos chunkPos = chunk.getPos();
        int baseX = chunkPos.getMinBlockX();
        int baseZ = chunkPos.getMinBlockZ();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int gz = 0; gz < GRID; gz++) {
            for (int gx = 0; gx < GRID; gx++) {
                int index = gx + gz * GRID;
                int localX = gx * 4 + 2;
                int localZ = gz * 4 + 2;
                int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, localX, localZ) - 1;
                column.height[index] = NO_DATA;
                // Glass and similar blocks have no map colour; look a few blocks lower for one.
                for (int probe = 0; probe < 6 && y >= minY; probe++, y--) {
                    pos.set(baseX + localX, y, baseZ + localZ);
                    BlockState state = chunk.getBlockState(pos);
                    MapColor color = state.getMapColor(level, pos);
                    if (color != MapColor.NONE) {
                        column.height[index] = (short) (y + 1);
                        column.rgb[index] = color.col;
                        break;
                    }
                }
            }
        }
    }
}
