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
 * terrain samples are captured once while that chunk is already loaded. HUD
 * rendering then uses only this cache and never asks the world for terrain.
 */
public final class VBVWorldMap {
    private static final Map<String, Set<Long>> EXPLORED = new HashMap<>();
    private static final Map<String, Map<Long, int[]>> TERRAIN = new HashMap<>();
    private static final Map<String, Map<String, VBVWaypoint>> WAYPOINTS = new HashMap<>();
    private static long lastChunk = Long.MIN_VALUE;
    private static String lastWorld = "";

    private VBVWorldMap() {}

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.getCameraEntity() instanceof Player player) || minecraft.level == null) return;

        BlockPos pos = player.blockPosition();
        if (!minecraft.level.hasChunkAt(pos)) return;
        int chunkX = Math.floorDiv(pos.getX(), 16);
        int chunkZ = Math.floorDiv(pos.getZ(), 16);
        String world = worldKey(minecraft);
        long key = pack(chunkX, chunkZ);
        Set<Long> visited = exploredSet(world, true);
        if (!visited.add(key)) return;

        // This is the only terrain sampling done for the map. The chunk is
        // already loaded because the player is standing in it.
        captureTerrain(minecraft, world, key, chunkX, chunkZ);
        lastChunk = key;
        lastWorld = world;
    }

    private static void captureTerrain(Minecraft minecraft, String world, long key, int chunkX, int chunkZ) {
        Map<Long, int[]> terrain = TERRAIN.computeIfAbsent(world, ignored -> new HashMap<>());
        if (terrain.containsKey(key)) return;
        int[] colors = new int[4];
        int index = 0;
        for (int sz = 0; sz < 2; sz++) {
            for (int sx = 0; sx < 2; sx++) {
                int x = chunkX * 16 + sx * 8 + 4;
                int z = chunkZ * 16 + sz * 8 + 4;
                if (!minecraft.level.hasChunkAt(new BlockPos(x, minecraft.level.getMinY(), z))) {
                    colors[index++] = 0xFF202020;
                    continue;
                }
                int y = minecraft.level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                if (y < minecraft.level.getMinY()) {
                    colors[index++] = 0xFF202020;
                    continue;
                }
                BlockPos sample = new BlockPos(x, y, z);
                BlockState state = minecraft.level.getBlockState(sample);
                int color = state.getMapColor(minecraft.level, sample).col;
                if (color == 0) color = 0x303030;
                int shade = Math.max(0x55, Math.min(0xFF, 190 + (y - 64) / 2));
                int r = ((color >> 16) & 255) * shade / 255;
                int g = ((color >> 8) & 255) * shade / 255;
                int b = (color & 255) * shade / 255;
                colors[index++] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        }
        terrain.put(key, colors);
    }

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
