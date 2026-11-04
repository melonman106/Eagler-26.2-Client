package com.melonman106.vbvclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;

/** Nearby loaded terrain minimap; it does not require player exploration. */
public final class VBVMiniMap {
    private static final int SIZE = 64;
    // Off by default: per-frame terrain drawing is expensive in a browser client.
    public static boolean enabled = false;
    public static int offsetX = 0;
    public static int offsetY = 0;
    private VBVMiniMap() {}

    public static void render(GuiGraphicsExtractor graphics) {
        if (!enabled) return;
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null) return;
        int x = graphics.guiWidth() - SIZE - 8 + offsetX;
        int y = 8 + offsetY;
        int centerX = Math.floorDiv((int)Math.floor(player.getX()), 16);
        int centerZ = Math.floorDiv((int)Math.floor(player.getZ()), 16);
        String world = VBVWorldMap.worldKey(mc);
        graphics.fill(x - 3, y - 3, x + SIZE + 3, y + SIZE + 3, 0xCC111111);
        graphics.fill(x - 1, y - 1, x + SIZE + 1, y + SIZE + 1, 0xFFB0B0B0);

        // No explored-set lookup: show cached terrain from any nearby chunk that
        // was already loaded by Minecraft, even if the player has never entered it.
        int pixelsPerChunk = 8;
        int radius = SIZE / (2 * pixelsPerChunk);
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int[] colors = VBVWorldMap.terrain(world, VBVWorldMap.pack(centerX + dx, centerZ + dz));
                if (colors == null || colors.length < VBVWorldMap.terrainSampleCount()) continue;
                int baseX = x + SIZE / 2 + dx * pixelsPerChunk - pixelsPerChunk / 2;
                int baseY = y + SIZE / 2 + dz * pixelsPerChunk - pixelsPerChunk / 2;
                int samples = VBVWorldMap.terrainSide();
                // Draw a 2x2 representative sample per chunk. The previous
                // 8x8 loop issued over 5,000 fill calls every frame; this keeps
                // the same nearby-chunk coverage with at most 324 terrain fills.
                int sampleStep = pixelsPerChunk / 2;
                for (int py = 0; py < 2; py++) {
                    for (int px = 0; px < 2; px++) {
                        int sampleX = Math.min(samples - 1, (px * sampleStep + sampleStep / 2) * samples / pixelsPerChunk);
                        int sampleZ = Math.min(samples - 1, (py * sampleStep + sampleStep / 2) * samples / pixelsPerChunk);
                        int left = Math.max(x, baseX + px * sampleStep);
                        int top = Math.max(y, baseY + py * sampleStep);
                        int right = Math.min(x + SIZE, baseX + (px + 1) * sampleStep);
                        int bottom = Math.min(y + SIZE, baseY + (py + 1) * sampleStep);
                        if (left < right && top < bottom) {
                            graphics.fill(left, top, right, bottom, colors[sampleZ * samples + sampleX]);
                        }
                    }
                }
            }
        }
        int mx = x + SIZE / 2, my = y + SIZE / 2;
        graphics.fill(mx - 3, my - 3, mx + 4, my + 4, 0xFFFFFFFF);
        graphics.fill(mx - 1, my - 1, mx + 2, my + 2, 0xFF3B83E8);
        graphics.text(mc.font, "N", x + SIZE / 2 - 3, y + 3, 0xFFFFFFFF, true);
    }
}
