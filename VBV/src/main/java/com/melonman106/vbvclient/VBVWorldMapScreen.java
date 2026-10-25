package com.melonman106.vbvclient;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.entity.player.Player;

public final class VBVWorldMapScreen extends Screen {
    private final Screen parent;
    private double centerX;
    private double centerZ;
    private double zoom = 12.0D;
    private final Map<Long, Integer> terrainColorCache = new HashMap<>();

    public VBVWorldMapScreen(Screen parent) {
        this(parent, Double.NaN, Double.NaN);
    }

    /** Opens the map centred on a block position (used by "Center on Map"). */
    public VBVWorldMapScreen(Screen parent, double focusX, double focusZ) {
        super(Component.literal("World Map"));
        this.parent = parent;
        if (Double.isNaN(focusX) || Double.isNaN(focusZ)) {
            Player player = Minecraft.getInstance().player;
            this.centerX = player == null ? 0 : player.getX();
            this.centerZ = player == null ? 0 : player.getZ();
        } else {
            this.centerX = focusX;
            this.centerZ = focusZ;
        }
    }

    private int chunkPixels() {
        return Math.max(3, (int) Math.round(zoom));
    }
    private int terrainColor(Minecraft minecraft, int blockX, int blockZ) {
        long key = ((long) blockX << 32) ^ (blockZ & 0xFFFFFFFFL);
        Integer cached = terrainColorCache.get(key);
        if (cached != null) return cached;
        int surfaceY = minecraft.level.getHeight(Heightmap.Types.WORLD_SURFACE, blockX, blockZ) - 1;
        if (surfaceY < minecraft.level.getMinY()) return 0xFF202020;
        BlockPos pos = new BlockPos(blockX, surfaceY, blockZ);
        BlockState state = minecraft.level.getBlockState(pos);
        int color = state.getMapColor(minecraft.level, pos).col;
        if (color == 0) color = 0x303030;
        int shade = Math.max(0x55, Math.min(0xFF, 190 + (surfaceY - 64) / 2));
        int r = ((color >> 16) & 255) * shade / 255;
        int g = ((color >> 8) & 255) * shade / 255;
        int b = (color & 255) * shade / 255;
        int result = 0xFF000000 | (r << 16) | (g << 8) | b;
        if (terrainColorCache.size() > 120000) terrainColorCache.clear();
        terrainColorCache.put(key, result);
        return result;
    }

    /** Pan distance in blocks: roughly 60 screen pixels regardless of zoom. */
    private double panBlocks() {
        return Math.max(16.0D, 60.0D / chunkPixels() * 16.0D);
    }

    private void recenterOnPlayer() {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;
        centerX = player.getX();
        centerZ = player.getZ();
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.literal("Mark Location"), button -> VBVWaypointScreen.openCreate(this))
            .bounds(8, 8, 120, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Waypoints"), button -> VBVNav.open(new VBVWaypointsScreen(this)))
            .bounds(134, 8, 90, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), button -> VBVNav.open(parent))
            .bounds(width - 108, 8, 100, 20).build());

        addRenderableWidget(Button.builder(Component.literal("-"), button -> zoom = Math.max(2.0D, zoom / 1.35D))
            .bounds(width / 2 - 50, height - 30, 40, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), button -> zoom = Math.min(80.0D, zoom * 1.35D))
            .bounds(width / 2 + 10, height - 30, 40, 20).build());

        // Panning (the map previously could not be moved off the player).
        addRenderableWidget(Button.builder(Component.literal("<"), button -> centerX -= panBlocks())
            .bounds(8, height - 30, 22, 20).build());
        addRenderableWidget(Button.builder(Component.literal("^"), button -> centerZ -= panBlocks())
            .bounds(32, height - 30, 22, 20).build());
        addRenderableWidget(Button.builder(Component.literal("v"), button -> centerZ += panBlocks())
            .bounds(56, height - 30, 22, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), button -> centerX += panBlocks())
            .bounds(80, height - 30, 22, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Me"), button -> recenterOnPlayer())
            .bounds(106, height - 30, 30, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xFF101010);

        Minecraft minecraft = Minecraft.getInstance();
        String world = VBVWorldMap.worldKey(minecraft);
        int centerChunkX = (int) Math.floor(centerX / 16.0D);
        int centerChunkZ = (int) Math.floor(centerZ / 16.0D);
        int chunkPixels = chunkPixels();
        double blocksPerPixel = 16.0D / chunkPixels;
        java.util.Set<Long> explored = VBVWorldMap.explored(world);
        int mapTop = 30, mapBottom = height - 38, mapLeft = 4, mapRight = width - 4;
        // Sample actual surface block map colours instead of one flat colour per chunk.
        if (minecraft.level != null) {
            for (int py = mapTop; py < mapBottom; py += 2) {
                int blockZ = (int) Math.floor(centerZ + (py - height / 2.0D) * blocksPerPixel);
                for (int px = mapLeft; px < mapRight; px += 2) {
                    int blockX = (int) Math.floor(centerX + (px - width / 2.0D) * blocksPerPixel);
                    long chunk = VBVWorldMap.pack(Math.floorDiv(blockX, 16), Math.floorDiv(blockZ, 16));
                    if (!explored.contains(chunk)) continue;
                    graphics.fill(px, py, Math.min(px + 2, mapRight), Math.min(py + 2, mapBottom), terrainColor(minecraft, blockX, blockZ));
                }
            }
        }

        for (VBVWaypoint waypoint : VBVWorldMap.waypoints(world).values()) {
            int x = width / 2 + (int) Math.round((waypoint.x() / 16.0D - centerChunkX) * chunkPixels);
            int z = height / 2 + (int) Math.round((waypoint.z() / 16.0D - centerChunkZ) * chunkPixels);
            // Don't draw markers/labels over the button bars or off-screen.
            if (x < 4 || x > width - 4 || z < 32 || z > height - 38) continue;
            graphics.fill(x - 3, z - 3, x + 4, z + 4, 0xFFE0B020);
            graphics.text(minecraft.font, waypoint.name(), x + 6, z - 4, 0xFFFFFFFF, true);
        }

        if (minecraft.player != null) {
            int px = width / 2 + (int) Math.round((minecraft.player.getX() / 16.0D - centerChunkX) * chunkPixels);
            int pz = height / 2 + (int) Math.round((minecraft.player.getZ() / 16.0D - centerChunkZ) * chunkPixels);
            if (px >= 4 && px <= width - 4 && pz >= 32 && pz <= height - 38) {
                graphics.fill(px - 4, pz - 4, px + 5, pz + 5, 0xFFFFFFFF);
                graphics.fill(px - 2, pz - 2, px + 3, pz + 3, 0xFF3A74D8);
            }
        }

        graphics.text(minecraft.font, "Explored chunks: " + VBVWorldMap.explored(world).size(), 8, height - 48, 0xFFFFFFFF, true);
        graphics.text(minecraft.font, "Zoom: " + String.format(java.util.Locale.ROOT, "%.1fx", zoom), 8, height - 60, 0xFFCCCCCC, true);
        graphics.text(minecraft.font, "Use + / - to zoom, arrows to pan, Me to recenter.", 8, 32, 0xFFCCCCCC, true);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }
}