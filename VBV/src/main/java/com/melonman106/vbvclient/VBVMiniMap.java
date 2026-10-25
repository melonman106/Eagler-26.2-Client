package com.melonman106.vbvclient;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/** Small explored-terrain minimap rendered in the HUD without a client mod loader. */
public final class VBVMiniMap {
    private static final int SIZE = 96;
    private static final Map<Long, Integer> COLORS = new HashMap<>();
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
        int centerX = (int)Math.floor(player.getX());
        int centerZ = (int)Math.floor(player.getZ());
        String world = VBVWorldMap.worldKey(minecraft);
        Set<Long> explored = VBVWorldMap.explored(world);
        graphics.fill(x - 3, y - 3, x + SIZE + 3, y + SIZE + 3, 0xCC111111);
        graphics.fill(x - 1, y - 1, x + SIZE + 1, y + SIZE + 1, 0xFFB0B0B0);

        // Each map cell represents about two blocks; draw one sample per 2x2 pixels
        // to keep the live browser renderer inexpensive while preserving terrain shape.
        for (int py = 0; py < SIZE; py += 6) {
            int blockZ = centerZ + (py - SIZE / 2) * 2;
            for (int px = 0; px < SIZE; px += 6) {
                int blockX = centerX + (px - SIZE / 2) * 2;
                long chunk = VBVWorldMap.pack(Math.floorDiv(blockX, 16), Math.floorDiv(blockZ, 16));
                if (!explored.contains(chunk)) continue;
                int color = terrainColor(minecraft, blockX, blockZ);
                graphics.fill(x + px, y + py, Math.min(x + SIZE, x + px + 6), Math.min(y + SIZE, y + py + 6), color);
            }
        }
        int markerX = x + SIZE / 2;
        int markerY = y + SIZE / 2;
        graphics.fill(markerX - 3, markerY - 3, markerX + 4, markerY + 4, 0xFFFFFFFF);
        graphics.fill(markerX - 1, markerY - 1, markerX + 2, markerY + 2, 0xFF3B83E8);
        graphics.text(minecraft.font, "N", x + SIZE / 2 - 3, y + 3, 0xFFFFFFFF, true);
    }

    private static int terrainColor(Minecraft minecraft, int blockX, int blockZ) {
        long key = ((long)blockX << 32) ^ (blockZ & 0xFFFFFFFFL);
        Integer cached = COLORS.get(key);
        if (cached != null) return cached;
        if (!minecraft.level.hasChunkAt(new BlockPos(blockX, minecraft.player.blockPosition().getY(), blockZ))) return 0xFF202020;
        int surfaceY = minecraft.level.getHeight(Heightmap.Types.WORLD_SURFACE, blockX, blockZ) - 1;
        if (surfaceY < minecraft.level.getMinY()) return 0xFF202020;
        BlockPos pos = new BlockPos(blockX, surfaceY, blockZ);
        BlockState state = minecraft.level.getBlockState(pos);
        int color = state.getMapColor(minecraft.level, pos).col;
        if (color == 0) color = 0xFF202020;
        else if ((color >>> 24) == 0) color |= 0xFF000000;
        int shade = Math.max(80, Math.min(255, 190 + (surfaceY - 64) / 2));
        int result = 0xFF000000 | ((((color >> 16) & 255) * shade / 255) << 16)
            | ((((color >> 8) & 255) * shade / 255) << 8) | ((color & 255) * shade / 255);
        if (COLORS.size() > 30000) COLORS.clear();
        COLORS.put(key, result);
        return result;
    }
}
