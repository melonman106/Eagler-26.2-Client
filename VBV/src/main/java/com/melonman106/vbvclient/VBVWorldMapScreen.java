package com.melonman106.vbvclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

public final class VBVWorldMapScreen extends Screen {
    private final Screen parent;
    private double centerX;
    private double centerZ;
    private double zoom = 24.0D;

    public VBVWorldMapScreen(Screen parent) {
        this(parent, Double.NaN, Double.NaN);
    }

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

    private int chunkPixels() { return Math.max(8, (int)Math.round(zoom)); }
    private double panBlocks() { return Math.max(16.0D, 60.0D / chunkPixels() * 16.0D); }

    private void recenterOnPlayer() {
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            centerX = player.getX();
            centerZ = player.getZ();
        }
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.literal("Mark Location"), b -> VBVWaypointScreen.openCreate(this))
            .bounds(8, 8, 120, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Waypoints"), b -> VBVNav.open(new VBVWaypointsScreen(this)))
            .bounds(134, 8, 90, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Export Backup"), b ->
            VBVWorldMap.exportBackup(VBVWorldMap.worldKey(Minecraft.getInstance())))
            .bounds(230, 8, 112, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> VBVNav.open(parent))
            .bounds(width - 108, 8, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("-"), b -> zoom = Math.max(8.0D, zoom / 1.3D))
            .bounds(width / 2 - 50, height - 30, 40, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> zoom = Math.min(96.0D, zoom * 1.3D))
            .bounds(width / 2 + 10, height - 30, 40, 20).build());
        addRenderableWidget(Button.builder(Component.literal("<"), b -> centerX -= panBlocks()).bounds(8, height - 30, 22, 20).build());
        addRenderableWidget(Button.builder(Component.literal("^"), b -> centerZ -= panBlocks()).bounds(32, height - 30, 22, 20).build());
        addRenderableWidget(Button.builder(Component.literal("v"), b -> centerZ += panBlocks()).bounds(56, height - 30, 22, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> centerX += panBlocks()).bounds(80, height - 30, 22, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Me"), b -> recenterOnPlayer()).bounds(106, height - 30, 30, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xFF101010);
        Minecraft minecraft = Minecraft.getInstance();
        String world = VBVWorldMap.worldKey(minecraft);
        java.util.Set<Long> explored = VBVWorldMap.explored(world);
        int pixels = chunkPixels();
        int mapTop = 30, mapBottom = height - 38, mapLeft = 4, mapRight = width - 4;

        // Never query live terrain while rendering the map. It uses one-time
        // 16x16 cached surface samples and still displays only visited chunks.
        int minChunkX = (int)Math.floor((centerX / 16.0D) + (mapLeft - width / 2.0D) / (double)pixels) - 1;
        int maxChunkX = (int)Math.floor((centerX / 16.0D) + (mapRight - width / 2.0D) / (double)pixels) + 1;
        int minChunkZ = (int)Math.floor((centerZ / 16.0D) + (mapTop - height / 2.0D) / (double)pixels) - 1;
        int maxChunkZ = (int)Math.floor((centerZ / 16.0D) + (mapBottom - height / 2.0D) / (double)pixels) + 1;

        int samples = VBVWorldMap.terrainSide();
        for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                long key = VBVWorldMap.pack(chunkX, chunkZ);
                if (!explored.contains(key)) continue;
                int[] colors = VBVWorldMap.terrain(world, key);
                if (colors == null || colors.length < VBVWorldMap.terrainSampleCount()) continue;
                int chunkLeft = width / 2 + (int)Math.round((chunkX - centerX / 16.0D) * pixels);
                int chunkTop = height / 2 + (int)Math.round((chunkZ - centerZ / 16.0D) * pixels);
                for (int sz = 0; sz < samples; sz++) {
                    for (int sx = 0; sx < samples; sx++) {
                        int x1 = Math.max(mapLeft, chunkLeft + sx * pixels / samples);
                        int y1 = Math.max(mapTop, chunkTop + sz * pixels / samples);
                        int x2 = Math.min(mapRight, chunkLeft + (sx + 1) * pixels / samples);
                        int y2 = Math.min(mapBottom, chunkTop + (sz + 1) * pixels / samples);
                        if (x1 < x2 && y1 < y2) graphics.fill(x1, y1, x2, y2, colors[sz * samples + sx]);
                    }
                }
            }
        }

        for (VBVWaypoint waypoint : VBVWorldMap.waypoints(world).values()) {
            int x = width / 2 + (int)Math.round((waypoint.x() / 16.0D - centerX / 16.0D) * pixels);
            int z = height / 2 + (int)Math.round((waypoint.z() / 16.0D - centerZ / 16.0D) * pixels);
            if (x < 4 || x > width - 4 || z < 32 || z > height - 38) continue;
            graphics.fill(x - 3, z - 3, x + 4, z + 4, 0xFFE0B020);
            graphics.text(minecraft.font, waypoint.name(), x + 6, z - 4, 0xFFFFFFFF, true);
        }

        if (minecraft.player != null) {
            int px = width / 2 + (int)Math.round((minecraft.player.getX() / 16.0D - centerX / 16.0D) * pixels);
            int pz = height / 2 + (int)Math.round((minecraft.player.getZ() / 16.0D - centerZ / 16.0D) * pixels);
            if (px >= 4 && px <= width - 4 && pz >= 32 && pz <= height - 38) {
                graphics.fill(px - 4, pz - 4, px + 5, pz + 5, 0xFFFFFFFF);
                graphics.fill(px - 2, pz - 2, px + 3, pz + 3, 0xFF3A74D8);
            }
        }

        graphics.text(minecraft.font, "Explored chunks: " + explored.size(), 8, height - 48, 0xFFFFFFFF, true);
        graphics.text(minecraft.font, "Zoom: " + String.format(java.util.Locale.ROOT, "%.1fx", zoom), 8, height - 60, 0xFFCCCCCC, true);
        graphics.text(minecraft.font, "16×16 terrain detail · explored chunks only", 8, 32, 0xFFCCCCCC, true);
        if (VBVWorldMap.storageWriteFailed()) {
            graphics.fill(4, 50, Math.min(width - 4, 360), 72, 0xE0A02020);
            graphics.text(minecraft.font, "Browser storage is full or blocked. Click Export Backup!", 9, 56, 0xFFFFFFFF, true);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }
}
