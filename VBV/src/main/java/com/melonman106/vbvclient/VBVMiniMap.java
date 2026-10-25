package com.melonman106.vbvclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;

/** Nearby loaded terrain minimap; it does not require player exploration. */
public final class VBVMiniMap {
    private static final int SIZE = 96;
    public static boolean enabled = true;
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
        int pixelsPerChunk = 6;
        int radius = SIZE / (2 * pixelsPerChunk) + 1;
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int[] colors = VBVWorldMap.terrain(world, VBVWorldMap.pack(centerX + dx, centerZ + dz));
                if (colors == null || colors.length < VBVWorldMap.terrainSampleCount()) continue;
                int baseX = x + SIZE / 2 + dx * pixelsPerChunk - pixelsPerChunk / 2;
                int baseY = y + SIZE / 2 + dz * pixelsPerChunk - pixelsPerChunk / 2;
                int samples = VBVWorldMap.terrainSide();
                // Downsample the detailed cache to the minimap's six-pixel chunk cells.
                for (int py = 0; py < pixelsPerChunk; py++) {
                    for (int px = 0; px < pixelsPerChunk; px++) {
                        int sampleX = px * samples / pixelsPerChunk;
                        int sampleZ = py * samples / pixelsPerChunk;
                        int left = Math.max(x, baseX + px), top = Math.max(y, baseY + py);
                        int right = Math.min(x + SIZE, baseX + px + 1);
                        int bottom = Math.min(y + SIZE, baseY + py + 1);
                        if (left < right && top < bottom) {
                            graphics.fill(left, top, right, bottom,
                                colors[sampleZ * samples + sampleX]);
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
