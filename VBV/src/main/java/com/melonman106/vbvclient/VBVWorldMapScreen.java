package com.melonman106.vbvclient;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
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

    private final Screen parent;
    private double centerX;
    private double centerZ;
    private double scale = 1.5D;

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
    private final int[] columnSample = VBVMapPainter.newColumnScratch();
    private int[] frame = new int[0];

    public VBVWorldMapScreen(Screen parent) {
        this(parent, Double.NaN, Double.NaN);
    }

    public VBVWorldMapScreen(Screen parent, double focusX, double focusZ) {
        super(Component.literal("Xaero's World Map"));
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

    private double panBlocks() {
        return Math.max(8.0D, 60.0D / scale);
    }

    private void recenterOnPlayer() {
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            centerX = player.getX();
            centerZ = player.getZ();
        }
    }

    private void zoomBy(double factor) {
        scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale * factor));
    }

    private boolean inMap(double x, double y) {
        return x >= mapLeft && x < mapRight && y >= mapTop && y < mapBottom;
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
        addRenderableWidget(Button.builder(Component.literal("Import Backup"), b -> VBVWorldMap.importBackup())
            .bounds(350, 8, 112, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> VBVNav.open(parent))
            .bounds(width - 108, 8, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("-"), b -> zoomBy(1.0D / 1.3D))
            .bounds(width / 2 - 50, height - 30, 40, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> zoomBy(1.3D))
            .bounds(width / 2 + 10, height - 30, 40, 20).build());
        addRenderableWidget(Button.builder(Component.literal("<"), b -> centerX -= panBlocks()).bounds(8, height - 30, 22, 20).build());
        addRenderableWidget(Button.builder(Component.literal("^"), b -> centerZ -= panBlocks()).bounds(32, height - 30, 22, 20).build());
        addRenderableWidget(Button.builder(Component.literal("v"), b -> centerZ += panBlocks()).bounds(56, height - 30, 22, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> centerX += panBlocks()).bounds(80, height - 30, 22, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Me"), b -> recenterOnPlayer()).bounds(106, height - 30, 30, 20).build());

        mapTop = 30;
        mapBottom = Math.max(mapTop + 16, height - 38);
        mapLeft = 4;
        mapRight = Math.max(mapLeft + 16, width - 4);
        int newW = mapRight - mapLeft;
        int newH = mapBottom - mapTop;
        if (view == null || newW != viewW || newH != viewH) {
            createView(newW, newH);
        }
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
        releaseView();
        super.removed();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
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
        if (dragging) {
            centerX -= dragX / scale;
            centerZ -= dragY / scale;
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!inMap(mouseX, mouseY) || scrollY == 0.0D) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        // Keep the block under the cursor where it is, so zooming homes in on what you point at.
        double relX = mouseX - mapLeft - viewW / 2.0D;
        double relZ = mouseY - mapTop - viewH / 2.0D;
        double blockX = centerX + relX / scale;
        double blockZ = centerZ + relZ / scale;
        zoomBy(Math.pow(WHEEL_STEP, Math.max(-4.0D, Math.min(4.0D, scrollY))));
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

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xFF101010);
        Minecraft minecraft = Minecraft.getInstance();
        String world = VBVWorldMap.worldKey(minecraft);
        Set<Long> explored = VBVWorldMap.explored(world);

        paintIfNeeded(world, explored);
        if (view != null) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, VIEW_ID, mapLeft, mapTop, 0.0F, 0.0F, viewW, viewH, viewW, viewH);
        } else {
            graphics.fill(mapLeft, mapTop, mapRight, mapBottom, 0xFF181818);
        }

        for (VBVWaypoint waypoint : VBVWorldMap.waypoints(world).values()) {
            int x = (int) Math.round(screenX(waypoint.x()));
            int z = (int) Math.round(screenZ(waypoint.z()));
            if (x < mapLeft + 4 || x > mapRight - 4 || z < mapTop + 2 || z > mapBottom) continue;
            graphics.fill(x - 3, z - 3, x + 4, z + 4, 0xFFE0B020);
            graphics.text(minecraft.font, waypoint.name(), x + 6, z - 4, 0xFFFFFFFF, true);
        }

        if (minecraft.player != null) {
            int px = (int) Math.round(screenX(minecraft.player.getX()));
            int pz = (int) Math.round(screenZ(minecraft.player.getZ()));
            if (px >= mapLeft + 4 && px <= mapRight - 4 && pz >= mapTop + 4 && pz <= mapBottom - 4) {
                graphics.fill(px - 4, pz - 4, px + 5, pz + 5, 0xFFFFFFFF);
                graphics.fill(px - 2, pz - 2, px + 3, pz + 3, 0xFF3A74D8);
            }
        }

        graphics.text(minecraft.font, "Explored chunks: " + explored.size(), 8, height - 48, 0xFFFFFFFF, true);
        graphics.text(minecraft.font, "Zoom: " + String.format(Locale.ROOT, "%.2fx", scale / 1.5D), 8, height - 60, 0xFFCCCCCC, true);
        graphics.text(minecraft.font, "Scroll to zoom · drag to move · explored chunks only", 8, mapTop + 2, 0xFFCCCCCC, true);
        if (inMap(mouseX, mouseY)) {
            int blockX = (int) Math.floor(centerX + (mouseX - mapLeft - viewW / 2.0D) / scale);
            int blockZ = (int) Math.floor(centerZ + (mouseY - mapTop - viewH / 2.0D) / scale);
            String coords = "X " + blockX + "  Z " + blockZ;
            graphics.text(minecraft.font, coords, width - 8 - minecraft.font.width(coords), height - 48, 0xFFFFFFFF, true);
        }
        if (!viewError.isEmpty()) {
            graphics.fill(4, mapTop + 16, Math.min(width - 4, 400), mapTop + 38, 0xE0A02020);
            graphics.text(minecraft.font, viewError.length() > 60 ? viewError.substring(0, 60) : viewError, 9, mapTop + 22, 0xFFFFFFFF, true);
        }
        if (VBVWorldMap.storageWriteFailed()) {
            graphics.fill(4, mapTop + 40, Math.min(width - 4, 360), mapTop + 62, 0xE0A02020);
            graphics.text(minecraft.font, "Browser storage is full or blocked. Click Export Backup!", 9, mapTop + 46, 0xFFFFFFFF, true);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }
}
