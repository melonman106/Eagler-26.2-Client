package com.melonman106.vbvclient;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Lightweight client-side explored-chunk map.
 *
 * A chunk is recorded only when the local player actually enters it. Its four
 * terrain samples are captured once while that chunk is already loaded. The 12x12
 * grid preserves small terrain features better than a coarse 8x8 overview. HUD
 * rendering then uses only this cache and never asks the world for terrain.
 */
public final class VBVWorldMap {
    private static final Map<String, Set<Long>> EXPLORED = new HashMap<>();
    private static final Map<String, Map<Long, int[]>> TERRAIN = new HashMap<>();
    private static final Map<String, Map<String, VBVWaypoint>> WAYPOINTS = new HashMap<>();
    private static long lastChunk = Long.MIN_VALUE;
    private static String lastWorld = "";

    private VBVWorldMap() {}

    private static final int TERRAIN_SIDE = 12;
    private static final int TERRAIN_SAMPLES = TERRAIN_SIDE * TERRAIN_SIDE;
    private static final int MINIMAP_CACHE_RADIUS = 8;

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.getCameraEntity() instanceof Player player) || minecraft.level == null) return;
        BlockPos pos = player.blockPosition();
        if (!minecraft.level.hasChunkAt(pos)) return;
        int chunkX = Math.floorDiv(pos.getX(), 16);
        int chunkZ = Math.floorDiv(pos.getZ(), 16);
        String world = worldKey(minecraft);
        long key = pack(chunkX, chunkZ);
        if (key == lastChunk && world.equals(lastWorld)) return;
        lastChunk = key;
        lastWorld = world;
        exploredSet(world, true).add(key);
        captureTerrainIfLoaded(minecraft, world, key, chunkX, chunkZ);

        // Cache already-loaded nearby terrain for the minimap. Nearby chunks are
        // not added to EXPLORED, so the full world map remains fogged until visited.
        // This scan only happens on chunk changes, not every frame or game tick.
        for (int dz = -MINIMAP_CACHE_RADIUS; dz <= MINIMAP_CACHE_RADIUS; dz++) {
            for (int dx = -MINIMAP_CACHE_RADIUS; dx <= MINIMAP_CACHE_RADIUS; dx++) {
                int cx = chunkX + dx, cz = chunkZ + dz;
                long nearbyKey = pack(cx, cz);
                if (!hasTerrain(world, nearbyKey)) captureTerrainIfLoaded(minecraft, world, nearbyKey, cx, cz);
            }
        }
    }

    private static boolean hasTerrain(String world, long key) {
        Map<Long, int[]> map = TERRAIN.get(world);
        return map != null && map.containsKey(key);
    }

    private static void captureTerrainIfLoaded(Minecraft minecraft, String world, long key, int chunkX, int chunkZ) {
        if (hasTerrain(world, key)) return;
        int minY = minecraft.level.getMinY();
        if (!minecraft.level.hasChunkAt(new BlockPos(chunkX * 16 + 8, minY, chunkZ * 16 + 8))) return;
        captureTerrain(minecraft, world, key, chunkX, chunkZ);
    }

    private static void captureTerrain(Minecraft minecraft, String world, long key, int chunkX, int chunkZ) {
        Map<Long, int[]> terrain = TERRAIN.computeIfAbsent(world, ignored -> new HashMap<>());
        if (terrain.containsKey(key)) return;
        int[] colors = new int[TERRAIN_SAMPLES];
        int[] heights = new int[TERRAIN_SAMPLES];
        int minY = minecraft.level.getMinY();

        // 64 surface samples per chunk instead of four, with one-time capture.
        for (int sz = 0; sz < TERRAIN_SIDE; sz++) {
            for (int sx = 0; sx < TERRAIN_SIDE; sx++) {
                int index = sz * TERRAIN_SIDE + sx;
                int x = chunkX * 16 + sx * 2 + 1;
                int z = chunkZ * 16 + sz * 2 + 1;
                if (!minecraft.level.hasChunkAt(new BlockPos(x, minY, z))) {
                    colors[index] = 0xFF202020;
                    heights[index] = minY;
                    continue;
                }
                int y = minecraft.level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                heights[index] = y;
                if (y < minY) {
                    colors[index] = 0xFF202020;
                    continue;
                }
                BlockPos sample = new BlockPos(x, y, z);
                BlockState state = minecraft.level.getBlockState(sample);
                int color = state.getMapColor(minecraft.level, sample).col;
                if (color == 0) color = 0x303030;
                int shade = Math.max(0x70, Math.min(0xFF, 188 + (y - 64) / 2));
                int r = ((color >> 16) & 255) * shade / 255;
                int g = ((color >> 8) & 255) * shade / 255;
                int b = (color & 255) * shade / 255;
                colors[index] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        }

        // Slope shading adds relief to hills and ridges without per-frame world reads.
        for (int sz = 0; sz < TERRAIN_SIDE; sz++) {
            for (int sx = 0; sx < TERRAIN_SIDE; sx++) {
                int index = sz * TERRAIN_SIDE + sx;
                int right = sz * TERRAIN_SIDE + Math.min(TERRAIN_SIDE - 1, sx + 1);
                int down = Math.min(TERRAIN_SIDE - 1, sz + 1) * TERRAIN_SIDE + sx;
                int slope = heights[right] - heights[index] + heights[down] - heights[index];
                int shade = Math.max(0x65, Math.min(0x125, 0x100 - slope * 3));
                int color = colors[index];
                int r = ((color >> 16) & 255) * shade / 255;
                int g = ((color >> 8) & 255) * shade / 255;
                int b = (color & 255) * shade / 255;
                colors[index] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        }
        terrain.put(key, colors);
    }

    public static int terrainSide() { return TERRAIN_SIDE; }
    public static int terrainSampleCount() { return TERRAIN_SAMPLES; }

    public static int[] terrain(String world, long chunk) {
        Map<Long, int[]> terrain = TERRAIN.get(world);
        return terrain == null ? null : terrain.get(chunk);
    }

    public static long pack(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL);
    }

    public static int chunkX(long packed) { return (int) (packed >> 32); }
    public static int chunkZ(long packed) { return (int) packed; }

    public static void open(Screen parent) {
        VBVNav.open(new VBVWorldMapScreen(parent));
    }

    public static Set<Long> explored(String world) {
        return Collections.unmodifiableSet(exploredSet(world, false));
    }

    public static Map<String, VBVWaypoint> waypoints(String world) {
        return Collections.unmodifiableMap(WAYPOINTS.getOrDefault(world, Collections.emptyMap()));
    }

    public static void addWaypoint(String world, VBVWaypoint waypoint) {
        WAYPOINTS.computeIfAbsent(world, ignored -> new HashMap<>()).put(waypoint.id(), waypoint);
    }

    public static void removeWaypoint(String world, String id) {
        Map<String, VBVWaypoint> map = WAYPOINTS.get(world);
        if (map != null) map.remove(id);
    }

    public static String worldKey(Minecraft minecraft) {
        if (minecraft.level == null) return "unknown";
        String levelName = minecraft.level.dimension().toString();
        String server = minecraft.getCurrentServer() == null ? "singleplayer" : minecraft.getCurrentServer().ip;
        return server + "|" + levelName;
    }

    private static Set<Long> exploredSet(String world, boolean create) {
        if (create) return EXPLORED.computeIfAbsent(world, ignored -> new HashSet<>());
        return EXPLORED.getOrDefault(world, Collections.emptySet());
    }

    public static int playerBlockX() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player == null ? 0 : (int) minecraft.player.getX();
    }

    public static int playerBlockZ() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player == null ? 0 : (int) minecraft.player.getZ();
    }
}
