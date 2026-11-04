package com.melonman106.vbvclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;

/** Nearby loaded terrain minimap; it does not require player exploration. */
public final class VBVMiniMap {
    public static int size = 64;
    public static int scanRadius = 4;
    public static boolean showFrame = true;
    public static boolean showPlayerMarker = true;
    public static boolean showNorth = true;
    // Off by default: per-frame terrain drawing is expensive in a browser client.
    public static boolean enabled = false;
    public static int offsetX = 0;
    public static int offsetY = 0;
    private VBVMiniMap() {}
    public static void loadSettings(boolean active, int x, int y, int mapSize, int radius,
                                     boolean frame, boolean marker, boolean north) {
        enabled = active; offsetX = x; offsetY = y;
        size = Math.max(48, Math.min(96, mapSize));
        scanRadius = Math.max(3, Math.min(5, radius));
        showFrame = frame; showPlayerMarker = marker; showNorth = north;
    }
    public static void cycleSize() { size = size >= 80 ? 48 : size + 16; }
    public static void cycleScanRadius() { scanRadius = scanRadius >= 5 ? 3 : scanRadius + 1; }

    public static void render(GuiGraphicsExtractor graphics) {
        if (!enabled) return;
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null) return;
        int mapSize = Math.max(48, Math.min(96, size));
        int radius = Math.max(3, Math.min(5, scanRadius));
        int x = graphics.guiWidth() - mapSize - 8 + offsetX;
        int y = 8 + offsetY;
        int centerX = Math.floorDiv((int)Math.floor(player.getX()), 16);
        int centerZ = Math.floorDiv((int)Math.floor(player.getZ()), 16);
        String world = VBVWorldMap.worldKey(mc);
        if (showFrame) {
            graphics.fill(x - 3, y - 3, x + mapSize + 3, y + mapSize + 3, 0xCC111111);
            graphics.fill(x - 1, y - 1, x + mapSize + 1, y + mapSize + 1, 0xFFB0B0B0);
        }

        // Draw two representative samples per axis for each cached chunk.
        // This caps the terrain draw calls at 4 * (2r+1)^2 per frame.
        int cellSize = Math.max(4, mapSize / (radius * 2 + 1));
        int sampleStep = Math.max(2, cellSize / 2);
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int[] colors = VBVWorldMap.terrain(world, VBVWorldMap.pack(centerX + dx, centerZ + dz));
                if (colors == null || colors.length < VBVWorldMap.terrainSampleCount()) continue;
                int baseX = x + mapSize / 2 + dx * cellSize - cellSize / 2;
                int baseY = y + mapSize / 2 + dz * cellSize - cellSize / 2;
                int samples = VBVWorldMap.terrainSide();
                for (int py = 0; py < 2; py++) {
                    for (int px = 0; px < 2; px++) {
                        int sampleX = Math.min(samples - 1, (px * sampleStep + sampleStep / 2) * samples / cellSize);
                        int sampleZ = Math.min(samples - 1, (py * sampleStep + sampleStep / 2) * samples / cellSize);
                        int left = Math.max(x, baseX + px * sampleStep);
                        int top = Math.max(y, baseY + py * sampleStep);
                        int right = Math.min(x + mapSize, baseX + (px + 1) * sampleStep);
                        int bottom = Math.min(y + mapSize, baseY + (py + 1) * sampleStep);
                        if (left < right && top < bottom) {
                            graphics.fill(left, top, right, bottom, colors[sampleZ * samples + sampleX]);
                        }
                    }
                }
            }
        }
        int mx = x + mapSize / 2, my = y + mapSize / 2;
        if (showPlayerMarker) {
            graphics.fill(mx - 3, my - 3, mx + 4, my + 4, 0xFFFFFFFF);
            graphics.fill(mx - 1, my - 1, mx + 2, my + 2, 0xFF3B83E8);
        }
        if (showNorth) graphics.text(mc.font, "N", x + mapSize / 2 - 3, y + 3, 0xFFFFFFFF, true);
    }
}
