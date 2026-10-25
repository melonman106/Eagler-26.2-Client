package com.melonman106.vbvclient;

import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;

/** Explored-terrain minimap. Rendering never samples or loads live chunks. */
public final class VBVMiniMap {
    private static final int SIZE = 96;
    public static boolean enabled = true;
    public static int offsetX = 0;
    public static int offsetY = 0;
    private VBVMiniMap() {}

    public static void render(GuiGraphicsExtractor graphics) {
        if (!enabled) return;
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.level == null) return;

        int x = graphics.guiWidth() - SIZE - 8 + offsetX;
        int y = 8 + offsetY;
        int centerChunkX = Math.floorDiv((int)Math.floor(player.getX()), 16);
        int centerChunkZ = Math.floorDiv((int)Math.floor(player.getZ()), 16);
        String world = VBVWorldMap.worldKey(minecraft);
        Set<Long> explored = VBVWorldMap.explored(world);

        graphics.fill(x - 3, y - 3, x + SIZE + 3, y + SIZE + 3, 0xCC111111);
        graphics.fill(x - 1, y - 1, x + SIZE + 1, y + SIZE + 1, 0xFFB0B0B0);

        // 6x6 pixels per chunk. This is intentionally coarse: the detailed
        // terrain samples are captured once when a chunk is visited, not per frame.
        final int pixelsPerChunk = 6;
        final int radius = SIZE / pixelsPerChunk + 1;
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int chunkX = centerChunkX + dx;
                int chunkZ = centerChunkZ + dz;
                long key = VBVWorldMap.pack(chunkX, chunkZ);
                if (!explored.contains(key)) continue;
                int[] colors = VBVWorldMap.terrain(world, key);
                if (colors == null || colors.length < 4) continue;

                int baseX = x + SIZE / 2 + dx * pixelsPerChunk;
                int baseY = y + SIZE / 2 + dz * pixelsPerChunk;
                for (int sz = 0; sz < 2; sz++) {
                    for (int sx = 0; sx < 2; sx++) {
                        int px = baseX + sx * 3;
                        int py = baseY + sz * 3;
                        if (px < x || py < y || px >= x + SIZE || py >= y + SIZE) continue;
                        graphics.fill(px, py, Math.min(px + 3, x + SIZE), Math.min(py + 3, y + SIZE), colors[sz * 2 + sx]);
                    }
                }
            }
        }

        int markerX = x + SIZE / 2;
        int markerY = y + SIZE / 2;
        graphics.fill(markerX - 3, markerY - 3, markerX + 4, markerY + 4, 0xFFFFFFFF);
        graphics.fill(markerX - 1, markerY - 1, markerX + 2, markerY + 2, 0xFF3B83E8);
        graphics.text(minecraft.font, "N", x + SIZE / 2 - 3, y + 3, 0xFFFFFFFF, true);
    }
}
