package com.melonman106.vbvclient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
/** Native Eagler equivalents for selected client-side features. */
public final class VBVExtraFeatures {
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
    private static boolean betterF3Enabled = true;
    private static boolean fKeyHeld;
    private VBVExtraFeatures() {}
    public static void handleKey(int key, int action) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return;
        if (key == 70) fKeyHeld = action == 1;
        if (key == 71 && action == 1 && gammaFeatureEnabled) toggleGamma(minecraft);
        if (key == 67 && zoomEnabled) {
            if (zoomToggleMode) {
                if (action == 1) { if (zooming) stopZoom(minecraft); else startZoom(minecraft); }
            } else if (action == 1 && !zooming) startZoom(minecraft);
            else if (action == 0 && zooming) stopZoom(minecraft);
        } else if (key == 67 && !zoomEnabled && zooming) stopZoom(minecraft);
        if (betterF3Enabled && action == 1 && (key == 292 || (key == 51 && fKeyHeld))) debugHudEnabled = !debugHudEnabled;
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
    public static void cycleZoomFov() { zoomFov = zoomFov == 30 ? 20 : zoomFov == 20 ? 10 : 30; if (zooming) Minecraft.getInstance().options.fov().set(zoomFov); }
    public static boolean isBetterF3Enabled() { return betterF3Enabled; }
    public static boolean isDebugHudEnabled() { return debugHudEnabled; }
    public static void toggleBetterF3Enabled() { betterF3Enabled = !betterF3Enabled; if (!betterF3Enabled) debugHudEnabled = false; }
    public static void toggleDebugHud() { debugHudEnabled = !debugHudEnabled; }
    public static void renderDebug(GuiGraphicsExtractor graphics) {
        if (!betterF3Enabled || !debugHudEnabled) return;
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) return;
        int x = 6, y = 6, lineHeight = minecraft.font.lineHeight + 2;
        graphics.fill(2, 2, 220, 2 + lineHeight * 4 + 4, 0x90000000);
        graphics.text(minecraft.font, "BetterF3", x, y, 0xFFFFFFFF, true);
        graphics.text(minecraft.font, String.format(java.util.Locale.ROOT, "XYZ: %.2f / %.2f / %.2f", player.getX(), player.getY(), player.getZ()), x, y + lineHeight, 0xFF55FFFF, true);
        graphics.text(minecraft.font, String.format(java.util.Locale.ROOT, "Yaw: %.1f  Pitch: %.1f", player.getYRot(), player.getXRot()), x, y + lineHeight * 2, 0xFFFFFFFF, true);
        graphics.text(minecraft.font, "FPS: " + minecraft.getFps(), x, y + lineHeight * 3, 0xFFAAAAAA, true);
    }
}
