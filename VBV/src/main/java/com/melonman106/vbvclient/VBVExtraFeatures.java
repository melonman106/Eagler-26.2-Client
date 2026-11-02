package com.melonman106.vbvclient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
/** Native Eagler equivalents for selected client-side features. */
public final class VBVExtraFeatures {
    private static final KeyMapping GAMMA_KEY = KeyBindingHelper.registerKeyBinding(
        new KeyMapping("key.vbv.gamma", InputConstants.Type.KEYSYM, 71, KeyMapping.Category.MISC));
    private static final KeyMapping ZOOM_KEY = KeyBindingHelper.registerKeyBinding(
        new KeyMapping("key.vbv.zoom", InputConstants.Type.KEYSYM, 67, KeyMapping.Category.MISC));
    private static boolean gammaEnabled;
    private static long gammaStatusUntil;
    private static double previousGamma = 0.5D;
    private static double gammaLevel = 4.0D;
    private static boolean gammaFeatureEnabled = true;
    private static boolean zoomEnabled = true;
    private static boolean zooming;
    private static boolean zoomToggleMode;
    private static int zoomFov = 30;
    private static int previousFov = 70;
    private static boolean debugHudEnabled;
    static int debugHudX = 6;
    static int debugHudY = 6;
    private static boolean betterF3Enabled = true;
    private static boolean fKeyHeld;
    private VBVExtraFeatures() {}
    /** Force key mappings to register during client bootstrap, before the first key event. */
    public static void initializeKeyBindings() {
        // Static key mappings are registered by their constructors.
    }

    /** Poll registered mappings from the shared Fabric client-tick event. */
    public static void tick(Minecraft minecraft) {
        if (minecraft == null) return;
        if (minecraft.gui.screen() != null) {
            while (GAMMA_KEY.consumeClick()) { }
            while (ZOOM_KEY.consumeClick()) { }
            if (zooming) stopZoom(minecraft);
            return;
        }

        if (GAMMA_KEY.consumeClick() && gammaFeatureEnabled) toggleGamma(minecraft);

        if (!zoomEnabled) {
            while (ZOOM_KEY.consumeClick()) { }
            if (zooming) stopZoom(minecraft);
            return;
        }
        if (zoomToggleMode) {
            if (ZOOM_KEY.consumeClick()) {
                if (zooming) stopZoom(minecraft);
                else startZoom(minecraft);
            }
        } else if (ZOOM_KEY.isDown() && !zooming) {
            startZoom(minecraft);
        } else if (!ZOOM_KEY.isDown() && zooming) {
            stopZoom(minecraft);
        }
    }

    /** F3 modifier handling remains on the native keyboard event path. */
    public static void handleKey(int key, int action) {
        if (key == 70) {
            if (action == 1) fKeyHeld = true;
            else if (action == 0) fKeyHeld = false;
        }
        if (betterF3Enabled && action == 1 && (key == 292 || (key == 51 && fKeyHeld))) {
            debugHudEnabled = !debugHudEnabled;
        }
    }
    private static void toggleGamma(Minecraft minecraft) {
        if (!gammaEnabled) {
            previousGamma = minecraft.options.gamma().get();
            minecraft.options.gamma().set(1.0D);
            gammaEnabled = true;
        } else {
            minecraft.options.gamma().set(previousGamma);
            gammaEnabled = false;
        }
        gammaStatusUntil = System.currentTimeMillis() + 2200L;
    }
    /** Brief on-screen confirmation after pressing G. */
    public static void renderGammaStatus(GuiGraphicsExtractor graphics) {
        if (System.currentTimeMillis() > gammaStatusUntil) return;
        Minecraft minecraft = Minecraft.getInstance();
        String text = "Gamma: " + (gammaEnabled ? "ON" : "OFF");
        int x = graphics.guiWidth() / 2 - minecraft.font.width(text) / 2;
        int y = graphics.guiHeight() / 2 + 24;
        graphics.fill(x - 5, y - 3, x + minecraft.font.width(text) + 5, y + minecraft.font.lineHeight + 3, 0xA0000000);
        graphics.text(minecraft.font, text, x, y, gammaEnabled ? 0xFF80FF80 : 0xFFFF8080, true);
    }

    /** Lightmap hook bypasses the vanilla 0..1 slider validation. */
    public static float gammaOverride(float vanillaGamma) { return gammaEnabled ? (float) gammaLevel : vanillaGamma; }
    private static void startZoom(Minecraft minecraft) {
        if (zooming) return;
        previousFov = minecraft.options.fov().get();
        minecraft.options.fov().set(zoomFov);
        zooming = true;
    }
    private static void stopZoom(Minecraft minecraft) {
        if (!zooming) return;
        minecraft.options.fov().set(previousFov);
        zooming = false;
    }
    public static boolean isGammaFeatureEnabled() { return gammaFeatureEnabled; }
    public static double gammaLevel() { return gammaLevel; }
    public static void toggleGammaFeature() {
        gammaFeatureEnabled = !gammaFeatureEnabled;
        if (!gammaFeatureEnabled && gammaEnabled) toggleGamma(Minecraft.getInstance());
    }
    public static void cycleGammaLevel() { gammaLevel = gammaLevel < 1.5D ? 2.0D : gammaLevel < 3.0D ? 4.0D : gammaLevel < 6.0D ? 8.0D : 1.0D; }
    public static boolean isZoomEnabled() { return zoomEnabled; }
    public static boolean isZoomToggleMode() { return zoomToggleMode; }
    public static int zoomFov() { return zoomFov; }
    public static void toggleZoomEnabled() { zoomEnabled = !zoomEnabled; if (!zoomEnabled && zooming) stopZoom(Minecraft.getInstance()); }
    public static void toggleZoomMode() { zoomToggleMode = !zoomToggleMode; }
    public static void cycleZoomFov() { zoomFov = zoomFov == 30 ? 35 : zoomFov == 35 ? 40 : 30; if (zooming) Minecraft.getInstance().options.fov().set(zoomFov); }
    public static boolean isBetterF3Enabled() { return betterF3Enabled; }
    public static boolean isDebugHudEnabled() { return debugHudEnabled; }
    public static void toggleBetterF3Enabled() { betterF3Enabled = !betterF3Enabled; if (!betterF3Enabled) debugHudEnabled = false; }
    public static void toggleDebugHud() { debugHudEnabled = !debugHudEnabled; }
    /** Full replacement for vanilla F3, called from DebugScreenOverlay rather than Hud. */
    public static void renderDebug(GuiGraphicsExtractor graphics) {
        if (!betterF3Enabled || !debugHudEnabled) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;
        BlockPos pos = player.blockPosition();
        int cx = Math.floorDiv(pos.getX(), 16), cz = Math.floorDiv(pos.getZ(), 16);
        Direction facing = Direction.fromYRot(player.getYRot());
        String biome = mc.level.getBiome(pos).unwrapKey().map(key -> key.identifier().toString()).orElse("unknown");
        String block = mc.level.getBlockState(pos).getBlock().getName().getString();
        net.minecraft.world.phys.Vec3 velocity = player.getDeltaMovement();
        Runtime runtime = Runtime.getRuntime();
        long usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L);
        long maxMb = runtime.maxMemory() / (1024L * 1024L);
        long time = mc.level.getOverworldClockTime();
        int renderedEntities = 0;
        for (Object ignored : mc.level.entitiesForRendering()) renderedEntities++;

        java.util.List<String> left = new java.util.ArrayList<>();
        left.add("Minecraft 26.2 | VBV BetterF3");
        left.add(String.format(java.util.Locale.ROOT, "XYZ: %.3f / %.3f / %.3f", player.getX(), player.getY(), player.getZ()));
        left.add("Block: " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
        left.add("Chunk: " + cx + " " + Math.floorDiv(pos.getY(), 16) + " " + cz);
        left.add(String.format(java.util.Locale.ROOT, "Facing: %s | yaw %.1f | pitch %.1f", facing.getName(), player.getYRot(), player.getXRot()));
        left.add(String.format(java.util.Locale.ROOT, "Velocity: %.3f / %.3f / %.3f", velocity.x, velocity.y, velocity.z));
        left.add(String.format(java.util.Locale.ROOT, "Horizontal speed: %.3f blocks/s", Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z) * 20.0D));
        left.add("Dimension: " + mc.level.dimension());
        left.add("Biome: " + biome);
        left.add("Block at feet: " + block);
        left.add("Light level: " + mc.level.getMaxLocalRawBrightness(pos));
        left.add("World time: day " + Math.floorDiv(time, 24000L) + ", tick " + Math.floorMod(time, 24000L));
        left.add("Local server: " + (mc.isLocalServer() ? "yes" : "no"));

        java.util.List<String> right = new java.util.ArrayList<>();
        right.add("Performance");
        right.add("FPS: " + mc.getFps());
        right.add(String.format(java.util.Locale.ROOT, "Memory: %d / %d MB", usedMb, maxMb));
        right.add("Runtime: " + System.getProperty("java.version", "browser"));
        right.add("Render distance: " + mc.options.renderDistance().get() + " chunks");
        right.add("Simulation distance: " + mc.options.simulationDistance().get() + " chunks");
        right.add("Rendered entities: " + renderedEntities);
        right.add("Connection: " + (mc.getCurrentServer() == null ? "Singleplayer" : mc.getCurrentServer().ip));
        right.add("On ground: " + player.onGround());
        right.add("Sprinting: " + player.isSprinting());
        right.add("Sneaking: " + player.isCrouching());
        right.add(String.format(java.util.Locale.ROOT, "Fall distance: %.2f", player.fallDistance));
        right.add("Local brightness: " + mc.level.getMaxLocalRawBrightness(pos));
        right.add(String.format(java.util.Locale.ROOT, "Memory use: %.1f%%", maxMb <= 0 ? 0.0D : usedMb * 100.0D / maxMb));
        right.add("Player: " + player.getName().getString());
        right.add(String.format(java.util.Locale.ROOT, "Health: %.1f / %.1f", player.getHealth(), player.getMaxHealth()));
        right.add("Food: " + player.getFoodData().getFoodLevel() + " / 20");
        right.add("Armor: " + player.getArmorValue());
        right.add("Selected hotbar slot: " + (player.getInventory().getSelectedSlot() + 1));
        right.add("Game mode: " + (mc.gameMode == null ? "unknown" : mc.gameMode.getPlayerMode().getName()));

        drawDebugColumn(graphics, mc, left, 4, 4);
        int rightWidth = Math.max(170, graphics.guiWidth() / 2 - 8);
        drawDebugColumn(graphics, mc, right, graphics.guiWidth() - rightWidth - 4, 4);
    }

    private static void drawDebugColumn(GuiGraphicsExtractor graphics, Minecraft mc, java.util.List<String> lines, int x, int y) {
        int lineHeight = mc.font.lineHeight + 2;
        int maxWidth = Math.max(120, graphics.guiWidth() / 2 - 14);
        int visible = Math.min(lines.size(), Math.max(1, (graphics.guiHeight() - 12) / lineHeight));
        graphics.fill(x, y, x + maxWidth + 8, y + visible * lineHeight + 6, 0xB0101010);
        for (int i = 0; i < visible; i++) {
            String line = lines.get(i);
            if (mc.font.width(line) > maxWidth) {
                line = mc.font.plainSubstrByWidth(line, Math.max(0, maxWidth - mc.font.width("..."))) + "...";
            }
            int color = i == 0 ? 0xFFFFFF55 : (i == 1 ? 0xFF55FFFF : 0xFFE0E0E0);
            graphics.text(mc.font, line, x + 4, y + 3 + i * lineHeight, color, true);
        }
    }

}
