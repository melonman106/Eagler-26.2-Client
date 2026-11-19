package com.melonman106.vbvclient;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Arrays;
import net.lax1dude.eaglercraft.v1_8.EagRuntime;
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
    /**
     * The remembered terrain is also written to browser storage, so far terrain is there again the
     * moment a world loads instead of filling in as you walk. Each chunk is 96 bytes, so the whole
     * saved set stays under a megabyte, which browser storage holds comfortably.
     */
    private static final String STORAGE_PREFIX = "vbv_lod_columns_v1.";
    private static final int MAX_PERSISTED = 8192;
    private static final int SAVE_INTERVAL_MS = 20_000;
    private static final int MAGIC = 0x56424C31;
    private static String persistKey = "";
    private static boolean dirty;
    private static long lastSaveMs;
    private static int restoredChunks;

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

    /** Chunks that came back from browser storage in this session, for the performance report. */
    public static int persistedChunks() {
        return restoredChunks;
    }

    /**
     * Called once per frame while LOD is on. On the first frame in a dimension it restores that
     * dimension's saved terrain, and it writes the samples back every {@link #SAVE_INTERVAL_MS}.
     */
    public static void maintain(ClientLevel level, int centerChunkX, int centerChunkZ) {
        if (level == null) {
            return;
        }
        String key;
        try {
            // ResourceKey has no location() in these mappings; the key's identity string is
            // stable for a given dimension and is only used to tell saved sets apart.
            key = String.valueOf(level.dimension());
        } catch (Throwable ignored) {
            return;
        }
        if (!key.equals(persistKey)) {
            persistKey = key;
            adopt(level);
            restore();
        }
        long now = System.currentTimeMillis();
        if (dirty && now - lastSaveMs >= SAVE_INTERVAL_MS) {
            lastSaveMs = now;
            save(centerChunkX, centerChunkZ);
        }
    }

    private static void restore() {
        restoredChunks = 0;
        try {
            byte[] bytes = EagRuntime.getStorage(STORAGE_PREFIX + persistKey);
            if (bytes == null || bytes.length < 8) {
                return;
            }
            int count;
            try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
                if (in.readInt() != MAGIC) {
                    return;
                }
                count = in.readInt();
                if (count <= 0 || count > MAX_PERSISTED) {
                    return;
                }
                for (int i = 0; i < count; i++) {
                    int chunkX = in.readInt();
                    int chunkZ = in.readInt();
                    Column column = new Column();
                    for (int j = 0; j < GRID * GRID; j++) {
                        column.height[j] = in.readShort();
                    }
                    for (int j = 0; j < GRID * GRID; j++) {
                        column.rgb[j] = in.readInt();
                    }
                    COLUMNS.put(key(chunkX, chunkZ), column);
                }
            }
            restoredChunks = COLUMNS.size();
            version++;
        } catch (Throwable ignored) {
            // A saved set from an older build must never stop the game from starting.
            COLUMNS.clear();
        }
    }

    private static void save(int centerChunkX, int centerChunkZ) {
        dirty = false;
        if (COLUMNS.isEmpty()) {
            return;
        }
        try {
            long[] keys = keysToPersist(centerChunkX, centerChunkZ);
            if (keys.length == 0) {
                return;
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(keys.length * (4 + GRID * GRID * 6) + 8);
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeInt(MAGIC);
                out.writeInt(keys.length);
                for (long k : keys) {
                    Column column = COLUMNS.get(k);
                    if (column == null) {
                        continue;
                    }
                    out.writeInt((int) k);
                    out.writeInt((int) (k >> 32));
                    for (short height : column.height) {
                        out.writeShort(height);
                    }
                    for (int rgb : column.rgb) {
                        out.writeInt(rgb);
                    }
                }
            }
            EagRuntime.setStorage(STORAGE_PREFIX + persistKey, bytes.toByteArray());
        } catch (Throwable ignored) {
            // Storage being full or blocked only costs the head start on the next load.
        }
    }

    /** All chunks when they fit, otherwise the ones nearest the player, so the next load looks right first. */
    private static long[] keysToPersist(int centerChunkX, int centerChunkZ) {
        long[] all = COLUMNS.keySet().toLongArray();
        if (all.length <= MAX_PERSISTED) {
            return all;
        }
        long[] picked = new long[MAX_PERSISTED];
        int radius = 16;
        while (true) {
            int n = 0;
            for (long k : all) {
                int x = (int) k;
                int z = (int) (k >> 32);
                if (Math.max(Math.abs(x - centerChunkX), Math.abs(z - centerChunkZ)) <= radius) {
                    picked[n++] = k;
                    if (n == MAX_PERSISTED) {
                        return picked;
                    }
                }
            }
            if (radius >= 4096) {
                return Arrays.copyOf(picked, n);
            }
            radius *= 2;
        }
    }

    /**
     * Drops the remembered terrain outside a shrinking radius until the map is at most
     * {@code numerator/denominator} of its former size. Used by the heap watchdog, which prefers
     * losing far-away terrain to losing the tab.
     */
    public static int shed(int numerator, int denominator, int centerChunkX, int centerChunkZ) {
        int target = COLUMNS.size() * Math.max(0, numerator) / Math.max(1, denominator);
        int radius = 8;
        while (COLUMNS.size() > target && radius < 4096) {
            for (long k : COLUMNS.keySet().toLongArray()) {
                int x = (int) k;
                int z = (int) (k >> 32);
                if (Math.max(Math.abs(x - centerChunkX), Math.abs(z - centerChunkZ)) > radius) {
                    COLUMNS.remove(k);
                }
            }
            radius *= 2;
        }
        version++;
        dirty = true;
        return COLUMNS.size();
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
            dirty = true;
        } catch (Throwable t) {
            VBVLod.fail("chunk capture: " + t);
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
