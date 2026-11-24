package com.melonman106.vbvclient;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

/**
 * The explored-world map. Terrain is painted into one cached texture and only repainted when the
 * view moves, the zoom changes or new terrain is recorded, so an idle map costs a single blit per
 * frame instead of thousands of fills. Scroll to zoom toward the cursor, drag to move.
 */
public final class VBVWorldMapScreen extends Screen {
    private static final Identifier VIEW_ID = Identifier.fromNamespaceAndPath("vbvclient", "worldmap/view");
    /** GUI pixels per block. 0.125 shows a whole chunk in 2 pixels; 12 shows a block as 12. */
    private static final double MIN_SCALE = 0.125D;
    private static final double MAX_SCALE = VBVMapPainter.MAX_SCALE;
    private static final double WHEEL_STEP = 1.15D;
    private static final int UNEXPLORED = VBVMapPainter.abgr(0xFF181818);
    private static final int TOP_BAR = 26;
    private static final int BOTTOM_BAR = 26;
    private static final int MAP_MARGIN = 6;

    private final Screen parent;
    private double centerX;
    private double centerZ;
    private double scale = 1.5D;
    private boolean follow = true;
    private boolean showGrid = true;

    private int mapLeft;
    private int mapTop;
    private int mapRight;
    private int mapBottom;
    private int viewW;
    private int viewH;

    private DynamicTexture view;
    private String viewError = "";
    private boolean repaint = true;
    private double paintedCenterX = Double.NaN;
    private double paintedCenterZ = Double.NaN;
    private double paintedScale = Double.NaN;
    private int paintedVersion = -1;
    private String paintedWorld = "";
    private boolean dragging;
    private boolean draggingZoom;
    private int zoomTrackX;
    private int zoomTrackW;
    private VBVUi.ThemedButton followButton;
    private VBVUi.ThemedButton gridButton;
    private final int[] columnSample = VBVMapPainter.newColumnScratch();
    private int[] frame = new int[0];

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
        // Opening the map is what turns terrain sampling on; until then it costs nothing while walking.
        if (!VBVWorldMap.isTerrainCaptureEnabled()) {
            VBVWorldMap.setTerrainCaptureEnabled(true);
            VBVWorldMap.captureCurrentChunk();
            VBVConfigStore.saveAll();
        }
    }

    private void recenterOnPlayer() {
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            centerX = player.getX();
            centerZ = player.getZ();
        }
        follow = true;
        refreshToggleButtons();
    }

    private void zoomBy(double factor) {
        scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale * factor));
    }

    private boolean inMap(double x, double y) {
        return x >= mapLeft && x < mapRight && y >= mapTop && y < mapBottom;
    }

    private double zoomT() {
        return Math.max(0.0D, Math.min(1.0D,
            Math.log(scale / MIN_SCALE) / Math.log(MAX_SCALE / MIN_SCALE)));
    }

    private void setZoomFromMouse(double mouseX) {
        double t = (mouseX - zoomTrackX) / Math.max(1.0D, zoomTrackW - 1.0D);
        t = Math.max(0.0D, Math.min(1.0D, t));
        scale = MIN_SCALE * Math.pow(MAX_SCALE / MIN_SCALE, t);
    }

    private boolean inZoomTrack(double x, double y) {
        return x >= zoomTrackX - 6 && x <= zoomTrackX + zoomTrackW + 6 && y >= height - BOTTOM_BAR && y < height;
    }

    private void refreshToggleButtons() {
        if (followButton != null) followButton.setLabel(follow ? "FOLLOW ON" : "FOLLOW OFF");
        if (gridButton != null) gridButton.setLabel(showGrid ? "GRID ON" : "GRID OFF");
    }

    private VBVUi.ThemedButton addSmall(int x, int y, int w, String label, Button.OnPress press, String tooltip) {
        VBVUi.ThemedButton button = new VBVUi.ThemedButton(x, y, w, 16, label, VBVUi.Style.SMALL, press);
        if (tooltip != null) button.setTooltip(Tooltip.create(Component.literal(tooltip)));
        addRenderableWidget(button);
        return button;
    }

    @Override
    protected void init() {
        super.init();
        mapTop = TOP_BAR + 4;
        mapBottom = Math.max(mapTop + 24, height - BOTTOM_BAR - 4);
        mapLeft = MAP_MARGIN;
        mapRight = Math.max(mapLeft + 48, width - MAP_MARGIN);
        int newW = mapRight - mapLeft;
        int newH = mapBottom - mapTop;
        if (view == null || newW != viewW || newH != viewH) {
            createView(newW, newH);
        }

        zoomTrackW = Math.max(48, Math.min(110, width / 5));
        zoomTrackX = width - 56 - zoomTrackW;

        int topY = 4;
        addSmall(68, topY, 46, "MARK", button -> VBVWaypointScreen.openCreate(this), "Create a waypoint here");
        addSmall(118, topY, 40, "WPS", button -> VBVNav.open(new VBVWaypointsScreen(this)), "Waypoints");
        if (width >= 390) {
            addSmall(162, topY, 40, "EXP", button ->
                VBVWorldMap.exportBackup(VBVWorldMap.worldKey(Minecraft.getInstance())), "Export a map backup");
            addSmall(206, topY, 40, "IMP", button -> VBVWorldMap.importBackup(), "Import a map backup");
        }
        addSmall(width - 50, topY, 42, "BACK", button -> VBVNav.open(parent), "Close the map");

        int toolbarX = mapLeft + 6;
        int toolbarY = mapTop + 6;
        addSmall(toolbarX, toolbarY, 30, "ME", button -> recenterOnPlayer(), "Centre and follow the player");
        followButton = addSmall(toolbarX, toolbarY + 20, 64, follow ? "FOLLOW ON" : "FOLLOW OFF", button -> {
            follow = !follow;
            if (follow) recenterOnPlayer();
            else refreshToggleButtons();
        }, "Keep the map centred on the player");
        gridButton = addSmall(toolbarX, toolbarY + 40, 52, showGrid ? "GRID ON" : "GRID OFF", button -> {
            showGrid = !showGrid;
            refreshToggleButtons();
        }, "Show chunk boundaries");

        addSmall(width - 48, height - 22, 18, "-", button -> zoomBy(1.0D / 1.25D), "Zoom out");
        addSmall(width - 26, height - 22, 18, "+", button -> zoomBy(1.25D), "Zoom in");
    }

    private void createView(int w, int h) {
        releaseView();
        viewW = w;
        viewH = h;
        frame = new int[w * h];
        repaint = true;
        try {
            view = new DynamicTexture(() -> "VBV world map", w, h, false);
            Minecraft.getInstance().getTextureManager().register(VIEW_ID, view);
            viewError = "";
        } catch (Throwable t) {
            view = null;
            viewError = "Map view could not start: " + t;
            System.err.println("[VBV World Map] " + viewError);
        }
    }

    private void releaseView() {
        if (view != null) {
            // Releasing through the texture manager also closes the texture.
            Minecraft.getInstance().getTextureManager().release(VIEW_ID);
            view = null;
        }
    }

    @Override
    public void removed() {
        dragging = false;
        draggingZoom = false;
        releaseView();
        super.removed();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (inZoomTrack(event.x(), event.y())) {
            draggingZoom = true;
            setZoomFromMouse(event.x());
            return true;
        }
        if (inMap(event.x(), event.y())) {
            dragging = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggingZoom) {
            setZoomFromMouse(event.x());
            return true;
        }
        if (dragging) {
            follow = false;
            refreshToggleButtons();
            centerX -= dragX / scale;
            centerZ -= dragY / scale;
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = false;
        draggingZoom = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!inMap(mouseX, mouseY) || scrollY == 0.0D) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        double factor = Math.pow(WHEEL_STEP, Math.max(-4.0D, Math.min(4.0D, scrollY)));
        if (follow) {
            // Following the player: keep them centred instead of anchoring on the cursor.
            zoomBy(factor);
            return true;
        }
        // Keep the block under the cursor where it is, so zooming homes in on what you point at.
        double relX = mouseX - mapLeft - viewW / 2.0D;
        double relZ = mouseY - mapTop - viewH / 2.0D;
        double blockX = centerX + relX / scale;
        double blockZ = centerZ + relZ / scale;
        zoomBy(factor);
        centerX = blockX - relX / scale;
        centerZ = blockZ - relZ / scale;
        return true;
    }

    private double screenX(double blockX) {
        return mapLeft + viewW / 2.0D + (blockX - centerX) * scale;
    }

    private double screenZ(double blockZ) {
        return mapTop + viewH / 2.0D + (blockZ - centerZ) * scale;
    }

    /** Repaints the cached texture when anything that affects it changed. */
    private void paintIfNeeded(String world, Set<Long> explored) {
        if (view == null) {
            return;
        }
        int version = VBVWorldMap.dataVersion();
        if (!repaint && version == paintedVersion && scale == paintedScale && centerX == paintedCenterX
            && centerZ == paintedCenterZ && world.equals(paintedWorld)) {
            return;
        }
        try {
            paint(world, explored);
            view.upload();
            repaint = false;
            paintedVersion = version;
            paintedScale = scale;
            paintedCenterX = centerX;
            paintedCenterZ = centerZ;
            paintedWorld = world;
        } catch (Throwable t) {
            viewError = "Map view failed: " + t;
            System.err.println("[VBV World Map] " + viewError);
            releaseView();
        }
    }

    private void paint(String world, Set<Long> explored) {
        VBVMapPainter.paint(frame, viewW, viewH, centerX, centerZ, scale, UNEXPLORED, explored,
            key -> VBVWorldMap.terrain(world, key), columnSample);
        NativeImage pixels = view.getPixels();
        int index = 0;
        for (int y = 0; y < viewH; y++) {
            for (int x = 0; x < viewW; x++) {
                pixels.setPixelABGR(x, y, frame[index++]);
            }
        }
    }

    private static String worldLabel(String world) {
        String[] parts = world.split("\\|", 3);
        String dimension = parts.length > 2 ? parts[2] : "overworld";
        dimension = dimension.replace("minecraft:", "");
        if (!dimension.isEmpty()) {
            dimension = Character.toUpperCase(dimension.charAt(0)) + dimension.substring(1);
        }
        String source = parts.length > 0 && "server".equals(parts[0])
            ? (parts.length > 1 ? parts[1] : "Server") : "Singleplayer";
        return source + " - " + dimension;
    }

    private void drawChunkGrid(GuiGraphicsExtractor graphics) {
        if (!showGrid || 16.0D * scale < 12.0D) {
            return;
        }
        double halfBlocksX = viewW / (2.0D * scale);
        double halfBlocksZ = viewH / (2.0D * scale);
        double firstX = Math.ceil((centerX - halfBlocksX) / 16.0D) * 16.0D;
        for (double blockX = firstX; blockX <= centerX + halfBlocksX; blockX += 16.0D) {
            int x = (int) Math.round(screenX(blockX));
            if (x < mapLeft || x >= mapRight) {
                continue;
            }
            int color = (long) blockX % 128L == 0L ? 0x44FFFFFF : 0x22FFFFFF;
            graphics.fill(x, mapTop, x + 1, mapBottom, color);
        }
        double firstZ = Math.ceil((centerZ - halfBlocksZ) / 16.0D) * 16.0D;
        for (double blockZ = firstZ; blockZ <= centerZ + halfBlocksZ; blockZ += 16.0D) {
            int z = (int) Math.round(screenZ(blockZ));
            if (z < mapTop || z >= mapBottom) {
                continue;
            }
            int color = (long) blockZ % 128L == 0L ? 0x44FFFFFF : 0x22FFFFFF;
            graphics.fill(mapLeft, z, mapRight, z + 1, color);
        }
    }

    private void drawCompass(GuiGraphicsExtractor graphics) {
        int cx = mapRight - 24;
        int cy = mapTop + 22;
        graphics.fill(cx - 9, cy - 9, cx + 10, cy + 10, 0x90000000);
        graphics.outline(cx - 9, cy - 9, 19, 19, VBVTheme.accentAlpha(0x80));
        graphics.fill(cx - 1, cy - 8, cx + 1, cy + 2, 0xFFE8B44A);
        graphics.fill(cx - 4, cy - 4, cx + 5, cy - 2, 0xFFE8B44A);
        graphics.fill(cx - 2, cy - 2, cx + 3, cy, 0xFFF6D27A);
        graphics.text(font, "N", cx - font.width("N") / 2, cy - 19, 0xFFFFFFFF, true);
    }

    private void drawScaleBar(GuiGraphicsExtractor graphics) {
        int maxPixels = Math.min(120, Math.max(48, width / 4));
        int chosen = 8;
        for (int candidate : new int[] {8, 16, 32, 64, 128, 256, 512, 1024}) {
            if (candidate * scale <= maxPixels) {
                chosen = candidate;
            } else {
                break;
            }
        }
        int length = Math.max(4, (int) Math.round(chosen * scale));
        int x = mapLeft + 10;
        int y = mapBottom - 16;
        graphics.fill(x - 1, y - 1, x + length + 1, y + 5, 0x90000000);
        graphics.fill(x, y + 2, x + length, y + 3, 0xFFFFFFFF);
        graphics.fill(x, y, x + 1, y + 5, 0xFFFFFFFF);
        graphics.fill(x + length - 1, y, x + length, y + 5, 0xFFFFFFFF);
        graphics.text(font, chosen + " blocks", x, y - 9, 0xFFFFFFFF, true);
    }

    private void drawWaypoints(GuiGraphicsExtractor graphics, String world) {
        for (VBVWaypoint waypoint : VBVWorldMap.waypoints(world).values()) {
            int x = (int) Math.round(screenX(waypoint.x()));
            int z = (int) Math.round(screenZ(waypoint.z()));
            if (x < mapLeft || x > mapRight || z < mapTop + 14 || z > mapBottom - 8) {
                continue;
            }
            int color = 0xFFE8B44A;
            graphics.fill(x, z - 2, x + 3, z + 8, 0xB0000000);
            graphics.fill(x - 4, z - 12, x + 5, z - 3, 0xFF000000);
            graphics.fill(x - 3, z - 11, x + 4, z - 4, color);
            graphics.fill(x - 1, z - 9, x + 2, z - 6, 0xFF2A1C00);

            String name = waypoint.name();
            int labelX = Math.min(x + 7, mapRight - font.width(name) - 6);
            if (labelX > mapLeft && z - 14 > mapTop) {
                int textWidth = font.width(name);
                graphics.fill(labelX - 2, z - 14, labelX + textWidth + 3, z - 3, 0xB0101014);
                graphics.fill(labelX - 2, z - 14, labelX - 1, z - 3, color);
                graphics.text(font, name, labelX + 2, z - 12, 0xFFFFFFFF, false);
            }
        }
    }

    private void drawPlayer(GuiGraphicsExtractor graphics, Player player) {
        if (player == null) {
            return;
        }
        int px = (int) Math.round(screenX(player.getX()));
        int pz = (int) Math.round(screenZ(player.getZ()));
        if (px < mapLeft - 8 || px > mapRight + 8 || pz < mapTop - 8 || pz > mapBottom + 8) {
            return;
        }
        double yaw = Math.toRadians(player.getYRot());
        double dirX = -Math.sin(yaw);
        double dirZ = Math.cos(yaw);
        graphics.fill(px - 5, pz - 5, px + 6, pz + 6, 0x80000000);
        for (int i = 4; i <= 9; i++) {
            int nx = (int) Math.round(px + dirX * i);
            int nz = (int) Math.round(pz + dirZ * i);
            graphics.fill(nx - 1, nz - 1, nx + 2, nz + 2, 0xFFFFFFFF);
        }
        graphics.fill(px - 4, pz - 4, px + 5, pz + 5, 0xFFFFFFFF);
        graphics.fill(px - 3, pz - 3, px + 4, pz + 4, 0xFF3A74D8);
        graphics.fill(px, pz, px + 1, pz + 1, 0xFFFFFFFF);
    }

    private void drawChrome(GuiGraphicsExtractor graphics, String world, Set<Long> explored, Player player,
                            double mouseX, double mouseY) {
        graphics.fill(0, 0, width, TOP_BAR, 0xF00C0C10);
        graphics.fill(0, TOP_BAR - 1, width, TOP_BAR, VBVTheme.accentAlpha(0xB0));
        graphics.fill(0, height - BOTTOM_BAR, width, height, 0xF00C0C10);
        graphics.fill(0, height - BOTTOM_BAR, width, height - BOTTOM_BAR + 1, VBVTheme.accentAlpha(0xB0));

        graphics.text(font, "WORLD MAP", 8, 8, 0xFFFFFFFF, true);
        if (width >= 470) {
            String label = VBVUi.fit(font, worldLabel(world), 130);
            int labelX = width - 8 - 42 - 8 - font.width(label);
            if (labelX > 252) {
                graphics.text(font, label, labelX, 8, VBVUi.TEXT_DIM, false);
            }
        }

        // Floating map toolbar, Xaero-style.
        int toolbarX = mapLeft + 3;
        int toolbarY = mapTop + 3;
        graphics.fill(toolbarX, toolbarY, toolbarX + 75, toolbarY + 63, 0xA00A0A0E);
        graphics.outline(toolbarX, toolbarY, 75, 63, VBVTheme.accentAlpha(0x50));

        int blockX = (int) Math.floor(centerX);
        int blockZ = (int) Math.floor(centerZ);
        if (inMap(mouseX, mouseY)) {
            blockX = (int) Math.floor(centerX + (mouseX - mapLeft - viewW / 2.0D) / scale);
            blockZ = (int) Math.floor(centerZ + (mouseY - mapTop - viewH / 2.0D) / scale);
        } else if (player != null) {
            blockX = (int) Math.floor(player.getX());
            blockZ = (int) Math.floor(player.getZ());
        }
        String coords = "X " + blockX + "  Z " + blockZ;
        graphics.text(font, coords, 8, height - 19, 0xFFFFFFFF, true);

        String exploredText = explored.size() + " chunks explored";
        int exploredX = (width - font.width(exploredText)) / 2;
        if (exploredX > font.width(coords) + 22 && exploredX < zoomTrackX - 54) {
            graphics.text(font, exploredText, exploredX, height - 19, VBVUi.TEXT_DIM, false);
        }

        String zoom = String.format(Locale.ROOT, "%.2fx", scale);
        int zoomX = zoomTrackX - 6 - font.width(zoom);
        if (zoomX > 80) {
            graphics.text(font, zoom, zoomX, height - 19, VBVUi.TEXT_DIM, false);
        }
        int trackY = height - 14;
        graphics.fill(zoomTrackX, trackY, zoomTrackX + zoomTrackW, trackY + 2, 0xFF2A2A31);
        int thumbX = zoomTrackX + (int) Math.round(zoomT() * (zoomTrackW - 6));
        graphics.fill(zoomTrackX, trackY, Math.min(thumbX + 3, zoomTrackX + zoomTrackW), trackY + 2, VBVTheme.accent());
        graphics.fill(thumbX, height - 19, thumbX + 6, height - 9, 0xFFE9E2D0);
        graphics.outline(thumbX, height - 19, 6, 10, 0xFF000000);
    }

    private void drawHover(GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
        if (!inMap(mouseX, mouseY)) {
            return;
        }
        int blockX = (int) Math.floor(centerX + (mouseX - mapLeft - viewW / 2.0D) / scale);
        int blockZ = (int) Math.floor(centerZ + (mouseY - mapTop - viewH / 2.0D) / scale);
        String coords = "X " + blockX + "  Z " + blockZ;
        int boxWidth = font.width(coords) + 8;
        int boxX = (int) Math.min(mouseX + 12, mapRight - boxWidth - 2);
        int boxY = (int) mouseY + 12;
        if (boxY + 15 > mapBottom) {
            boxY = (int) mouseY - 20;
        }
        boxX = Math.max(mapLeft + 2, boxX);
        boxY = Math.max(mapTop + 2, boxY);
        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + 15, 0xE0101014);
        graphics.outline(boxX, boxY, boxWidth, 15, VBVTheme.accentAlpha(0x90));
        graphics.text(font, coords, boxX + 4, boxY + 4, 0xFFFFFFFF, false);
    }

    private void drawErrors(GuiGraphicsExtractor graphics) {
        int boxWidth = Math.min(width - 16, 430);
        int boxX = width / 2 - boxWidth / 2;
        if (!viewError.isEmpty()) {
            graphics.fill(boxX, mapTop + 8, boxX + boxWidth, mapTop + 28, 0xE0A02020);
            graphics.text(font, VBVUi.fit(font, viewError, boxWidth - 10), boxX + 5, mapTop + 14, 0xFFFFFFFF, false);
        }
        if (VBVWorldMap.storageWriteFailed()) {
            int y = mapTop + (viewError.isEmpty() ? 8 : 32);
            graphics.fill(boxX, y, boxX + boxWidth, y + 20, 0xE0A02020);
            graphics.text(font, VBVUi.fit(font, "Browser storage is full or blocked. Use EXP to back up the map.",
                boxWidth - 10), boxX + 5, y + 6, 0xFFFFFFFF, false);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xFF08080B);
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        String world = VBVWorldMap.worldKey(minecraft);
        Set<Long> explored = VBVWorldMap.explored(world);

        if (follow && player != null && !dragging) {
            double dx = player.getX() - centerX;
            double dz = player.getZ() - centerZ;
            // Only recenter once the player is visibly off-centre, which avoids a repaint every frame.
            // At high zoom the threshold is at least four blocks so walking does not repaint constantly.
            double threshold = Math.max(16.0D, 36.0D / (scale * scale));
            if (dx * dx + dz * dz > threshold) {
                centerX = player.getX();
                centerZ = player.getZ();
            }
        }

        paintIfNeeded(world, explored);
        if (view != null) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, VIEW_ID, mapLeft, mapTop, 0.0F, 0.0F, viewW, viewH, viewW, viewH);
        } else {
            graphics.fill(mapLeft, mapTop, mapRight, mapBottom, 0xFF181818);
        }
        graphics.outline(mapLeft - 1, mapTop - 1, viewW + 2, viewH + 2, VBVTheme.accentAlpha(0x60));

        drawChunkGrid(graphics);
        drawScaleBar(graphics);
        drawWaypoints(graphics, world);
        drawPlayer(graphics, player);
        drawCompass(graphics);
        drawChrome(graphics, world, explored, player, mouseX, mouseY);
        drawHover(graphics, mouseX, mouseY);
        drawErrors(graphics);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }
}
