package com.melonman106.vbvclient;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.teavm.jso.JSBody;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Lightweight client-side explored-chunk map.
 *
 * A chunk is recorded only when the local player actually enters it. Its 16x16
 * surface samples are cached only after all samples are available. If a chunk is
 * still loading, capture is retried on later ticks instead of caching blank cells.
 * Rendering uses the cache and never queries live terrain.
 */
public final class VBVWorldMap {
    private static final Map<String, Set<Long>> EXPLORED = new HashMap<>();
    private static final Map<String, Map<Long, int[]>> TERRAIN = new HashMap<>();
    private static final Map<String, Map<String, VBVWaypoint>> WAYPOINTS = new HashMap<>();
    private static final Set<String> LOADED_WORLDS = new HashSet<>();
    private static final String STORAGE_PREFIX = "vbv.worldmap.v1.";
    private static long lastChunk = Long.MIN_VALUE;
    private static String lastWorld = "";
    private static int nearbyScanTicks;
    private static boolean storageWriteFailed;

    private VBVWorldMap() {}

    private static final int TERRAIN_SIDE = 16;
    private static final int TERRAIN_SAMPLES = TERRAIN_SIDE * TERRAIN_SIDE;
    private static final int MINIMAP_CACHE_RADIUS = 4;

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.getCameraEntity() instanceof Player player) || minecraft.level == null) return;
        BlockPos pos = player.blockPosition();
        if (!minecraft.level.hasChunkAt(pos)) return;

        int chunkX = Math.floorDiv(pos.getX(), 16);
        int chunkZ = Math.floorDiv(pos.getZ(), 16);
        String world = worldKey(minecraft);
        loadWorld(world);
        long key = pack(chunkX, chunkZ);
        boolean enteredNewChunk = key != lastChunk || !world.equals(lastWorld);

        if (enteredNewChunk) {
            lastChunk = key;
            lastWorld = world;
            // Only the chunk the player actually enters is marked explored.
            if (exploredSet(world, true).add(key)) saveWorld(world);
        }
        // Retry the player's current chunk until its terrain data is actually
        // available. A one-time failed capture must not leave the map blank forever.
        if (!hasTerrain(world, key)) {
            captureTerrainIfLoaded(minecraft, world, key, chunkX, chunkZ);
        }

        // Refresh the nearby terrain cache periodically so newly loaded chunks
        // appear on the minimap even when the player stands still. This never
        // adds nearby chunks to EXPLORED, so the world map keeps its fog of war.
        if (VBVMiniMap.enabled && (enteredNewChunk || ++nearbyScanTicks >= 100)) {
            nearbyScanTicks = 0;
            for (int dz = -MINIMAP_CACHE_RADIUS; dz <= MINIMAP_CACHE_RADIUS; dz++) {
                for (int dx = -MINIMAP_CACHE_RADIUS; dx <= MINIMAP_CACHE_RADIUS; dx++) {
                    int cx = chunkX + dx, cz = chunkZ + dz;
                    long nearbyKey = pack(cx, cz);
                    if (!hasTerrain(world, nearbyKey)) {
                        captureTerrainIfLoaded(minecraft, world, nearbyKey, cx, cz);
                    }
                }
            }
            pruneUnexploredTerrain(world, chunkX, chunkZ);
        }
    }

    private static boolean hasTerrain(String world, long key) {
        Map<Long, int[]> map = TERRAIN.get(world);
        return map != null && map.containsKey(key);
    }

    /** Keep minimap-only terrain bounded; explored chunks are retained for the world map. */
    private static void pruneUnexploredTerrain(String world, int playerChunkX, int playerChunkZ) {
        Map<Long, int[]> terrain = TERRAIN.get(world);
        if (terrain == null) return;
        Set<Long> explored = exploredSet(world, false);
        int keepRadius = MINIMAP_CACHE_RADIUS + 2;
        terrain.keySet().removeIf(key -> {
            if (explored.contains(key)) return false;
            return Math.abs(chunkX(key) - playerChunkX) > keepRadius
                || Math.abs(chunkZ(key) - playerChunkZ) > keepRadius;
        });
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

        // 256 surface samples per chunk, captured once and reused by both maps.
        for (int sz = 0; sz < TERRAIN_SIDE; sz++) {
            for (int sx = 0; sx < TERRAIN_SIDE; sx++) {
                int index = sz * TERRAIN_SIDE + sx;
                // One sample per block gives the world map much finer terrain detail.
                int x = chunkX * 16 + sx;
                int z = chunkZ * 16 + sz;
                // Do not cache partial chunks: otherwise unloaded samples become
                // permanent dark holes because the terrain map is captured once.
                if (!minecraft.level.hasChunkAt(new BlockPos(x, minY, z))) return;
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
                int shade = Math.max(0x65, Math.min(0xFF, 0x100 - slope * 3));
                int color = colors[index];
                int r = ((color >> 16) & 255) * shade / 255;
                int g = ((color >> 8) & 255) * shade / 255;
                int b = (color & 255) * shade / 255;
                colors[index] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        }
        terrain.put(key, colors);
        // Persist terrain only for chunks the player has actually explored.
        if (exploredSet(world, false).contains(key)) saveWorld(world);
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
        loadWorld(world);
        WAYPOINTS.computeIfAbsent(world, ignored -> new HashMap<>()).put(waypoint.id(), waypoint);
        saveWorld(world);
    }

    public static void removeWaypoint(String world, String id) {
        loadWorld(world);
        Map<String, VBVWaypoint> map = WAYPOINTS.get(world);
        if (map != null && map.remove(id) != null) saveWorld(world);
    }

    private static void loadWorld(String world) {
        if (!LOADED_WORLDS.add(world)) return;
        String storageKey = STORAGE_PREFIX + Base64.getUrlEncoder().withoutPadding()
            .encodeToString(world.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String stored = readStorage(storageKey);
        if (stored == null || stored.isEmpty()) return;
        try {
            Set<Long> explored = exploredSet(world, true);
            Map<Long, int[]> terrain = TERRAIN.computeIfAbsent(world, ignored -> new HashMap<>());
            String[] lines = stored.split("\\n");
            for (String line : lines) {
                if (line.startsWith("E:")) {
                    explored.add(Long.parseLong(line.substring(2)));
                } else if (line.startsWith("T:")) {
                    int separator = line.indexOf(':', 2);
                    if (separator < 0) continue;
                    long chunk = Long.parseLong(line.substring(2, separator));
                    byte[] bytes = Base64.getDecoder().decode(line.substring(separator + 1));
                    ByteBuffer buffer = ByteBuffer.wrap(bytes);
                    int[] colors = new int[TERRAIN_SAMPLES];
                    if (bytes.length == TERRAIN_SAMPLES * Integer.BYTES) {
                        // Backward compatibility with the original full-ARGB storage format.
                        for (int i = 0; i < colors.length; i++) colors[i] = buffer.getInt();
                    } else if (bytes.length == TERRAIN_SAMPLES * Short.BYTES) {
                        // New saves use RGB565 to halve localStorage usage.
                        for (int i = 0; i < colors.length; i++) {
                            int rgb = buffer.getShort() & 0xFFFF;
                            int r = ((rgb >> 11) & 31) * 255 / 31;
                            int g = ((rgb >> 5) & 63) * 255 / 63;
                            int b = (rgb & 31) * 255 / 31;
                            colors[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
                        }
                    } else {
                        continue;
                    }
                    terrain.put(chunk, colors);
                } else if (line.startsWith("W:")) {
                    String[] parts = line.substring(2).split(":", 5);
                    if (parts.length == 5) {
                        String id = new String(Base64.getUrlDecoder().decode(parts[0]), java.nio.charset.StandardCharsets.UTF_8);
                        String name = new String(Base64.getUrlDecoder().decode(parts[1]), java.nio.charset.StandardCharsets.UTF_8);
                        int x = Integer.parseInt(parts[2]);
                        int y = Integer.parseInt(parts[3]);
                        int z = Integer.parseInt(parts[4]);
                        WAYPOINTS.computeIfAbsent(world, ignored -> new HashMap<>())
                            .put(id, new VBVWaypoint(id, name, x, y, z));
                    }
                }
            }
        } catch (Exception exception) {
            System.err.println("[VBV World Map] Could not restore saved map data: " + exception.getMessage());
        }
    }

    private static void saveWorld(String world) {
        String data = serializeWorld(world);
        String storageKey = STORAGE_PREFIX + Base64.getUrlEncoder().withoutPadding()
            .encodeToString(world.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        storageWriteFailed = !writeStorage(storageKey, data);
        if (storageWriteFailed) {
            System.err.println("[VBV World Map] Browser storage write failed; use Export Backup on the map screen.");
        }
    }

    /** Exports the current in-memory map even when browser storage is full. */
    public static void exportBackup(String world) {
        loadWorld(world);
        String safeName = world.replaceAll("[^A-Za-z0-9._-]", "_");
        downloadBackup("VBV-WorldMap-" + safeName + ".vbvmap.txt",
            "VBV-WORLDMAP-BACKUP-v1\nWORLD:" +
            Base64.getUrlEncoder().withoutPadding().encodeToString(world.getBytes(java.nio.charset.StandardCharsets.UTF_8)) +
            "\n" + serializeWorld(world));
    }

    public static boolean storageWriteFailed() { return storageWriteFailed; }

    private static String serializeWorld(String world) {
        StringBuilder output = new StringBuilder();
        Set<Long> explored = exploredSet(world, false);
        Map<Long, int[]> terrain = TERRAIN.getOrDefault(world, Collections.emptyMap());
        for (long chunk : explored) {
            output.append("E:").append(chunk).append('\n');
            int[] colors = terrain.get(chunk);
            if (colors != null && colors.length == TERRAIN_SAMPLES) {
                ByteBuffer buffer = ByteBuffer.allocate(colors.length * Short.BYTES);
                for (int color : colors) {
                    int r = (color >> 19) & 31;
                    int g = (color >> 10) & 63;
                    int b = (color >> 3) & 31;
                    buffer.putShort((short)((r << 11) | (g << 5) | b));
                }
                output.append("T:").append(chunk).append(':')
                    .append(Base64.getEncoder().encodeToString(buffer.array())).append('\n');
            }
        }
        for (VBVWaypoint waypoint : WAYPOINTS.getOrDefault(world, Collections.emptyMap()).values()) {
            output.append("W:")
                .append(Base64.getUrlEncoder().withoutPadding().encodeToString(waypoint.id().getBytes(java.nio.charset.StandardCharsets.UTF_8))).append(':')
                .append(Base64.getUrlEncoder().withoutPadding().encodeToString(waypoint.name().getBytes(java.nio.charset.StandardCharsets.UTF_8))).append(':')
                .append(waypoint.x()).append(':').append(waypoint.y()).append(':').append(waypoint.z()).append('\n');
        }
        return output.toString();
    }

    @JSBody(params = {"filename", "content"}, script = "try { var blob = new Blob([content], {type: 'text/plain;charset=utf-8'}); var url = URL.createObjectURL(blob); var a = document.createElement('a'); a.href = url; a.download = filename; a.style.display = 'none'; document.body.appendChild(a); a.click(); a.remove(); window.setTimeout(function(){ URL.revokeObjectURL(url); }, 1500); } catch (e) { console.error('[VBV World Map] Backup download failed', e); }")
    private static native void downloadBackup(String filename, String content);

    /** Opens a browser file picker and restores a previously exported backup. */
    public static void importBackup() {
        importBackupFromFile();
    }

    @JSBody(script = "try { var input = document.createElement('input'); input.type = 'file'; input.accept = '.txt,.vbvmap'; input.onchange = function(){ var file = input.files && input.files[0]; if (!file) return; var reader = new FileReader(); reader.onload = function(){ try { var text = String(reader.result || ''); var lines = text.split(/\\r?\\n/); if (lines[0] !== 'VBV-WORLDMAP-BACKUP-v1' || !lines[1] || lines[1].indexOf('WORLD:') !== 0) throw new Error('This is not a VBV World Map backup'); var token = lines[1].substring(6); var payload = lines.slice(2).join('\\n'); window.localStorage.setItem('vbv.worldmap.v1.' + token, payload); window.alert('World map backup imported. Reload the Eaglercraft page to load it.'); } catch (e) { window.alert('Import failed. Browser storage may be full or blocked. If it is full, clear this site\'s storage, reload, then import the backup again. Details: ' + e); } }; reader.readAsText(file); }; input.click(); } catch (e) { window.alert('Could not open the backup file picker: ' + e); }")
    private static native void importBackupFromFile();

    @JSBody(params = {"key"}, script = "try { return window.localStorage.getItem(key) || ''; } catch (e) { return ''; }")
    private static native String readStorage(String key);

    @JSBody(params = {"key", "value"}, script = "try { window.localStorage.setItem(key, value); return true; } catch (e) { return false; }")
    private static native boolean writeStorage(String key, String value);

    public static String worldKey(Minecraft minecraft) {
        if (minecraft.level == null) return "unknown";
        String levelName = minecraft.level.dimension().toString();
        String server;
        if (minecraft.getCurrentServer() != null) {
            server = "server|" + minecraft.getCurrentServer().ip;
        } else {
            // Dimension alone is not a world identity: separate local saves even
            // when each is in the same dimension.
            var localServer = minecraft.getSingleplayerServer();
            String worldName = localServer == null ? "singleplayer" : localServer.getWorldData().getLevelName();
            server = "singleplayer|" + worldName;
        }
        return server + "|" + levelName;
    }

    private static Set<Long> exploredSet(String world, boolean create) {
        if (create) return EXPLORED.computeIfAbsent(world, ignored -> new HashSet<>());
        return EXPLORED.getOrDefault(world, Collections.emptySet());
    }

    public static int playerBlockX() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player == null ? 0 : minecraft.player.blockPosition().getX();
    }

    public static int playerBlockZ() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player == null ? 0 : minecraft.player.blockPosition().getZ();
    }
}
