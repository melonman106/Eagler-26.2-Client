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
    private static boolean gammaStatusEnabled = true;
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
    /** Free camera hotkey; the camera itself lives in {@link VBVFreecam}. */
    private static final KeyMapping FREECAM_KEY = KeyBindingHelper.registerKeyBinding(
        new KeyMapping("key.vbv.freecam", InputConstants.Type.KEYSYM, 66, KeyMapping.Category.MISC));
    private VBVExtraFeatures() {}
    /** Force key mappings to register during client bootstrap, before the first key event. */
    public static void initializeKeyBindings() {
        // Static key mappings are registered by their constructors; apply the saved keys to them.
        applyConfiguredBindings();
    }

    /**
     * Pushes the keys chosen in Options &gt; Modded Settings &gt; Keybinds into the native key mappings.
     * {@code setKey} only changes the mapping itself, so the static dispatch table is rebuilt after it.
     */
    public static void applyConfiguredBindings() {
        ZOOM_KEY.setKey(InputConstants.Type.KEYSYM.getOrCreate(VBVKeybinds.code(VBVKeybinds.ZOOM)));
        GAMMA_KEY.setKey(InputConstants.Type.KEYSYM.getOrCreate(VBVKeybinds.code(VBVKeybinds.GAMMA)));
        FREECAM_KEY.setKey(InputConstants.Type.KEYSYM.getOrCreate(VBVKeybinds.code(VBVKeybinds.FREECAM)));
        KeyMapping.resetMapping();
    }

    /** Stop hold-to-zoom immediately when any screen is initialized. */
    public static void onScreenOpened() {
        Minecraft minecraft = Minecraft.getInstance();
        if (zooming) stopZoom(minecraft);
        ZOOM_KEY.setDown(false);
        while (ZOOM_KEY.consumeClick()) { }
        while (GAMMA_KEY.consumeClick()) { }
        while (FREECAM_KEY.consumeClick()) { }
    }

    /** Poll registered mappings from the shared Fabric client-tick event. */
    public static void tick(Minecraft minecraft) {
        if (minecraft == null) return;
        if (minecraft.gui.screen() != null) {
            while (GAMMA_KEY.consumeClick()) { }
            while (ZOOM_KEY.consumeClick()) { }
            while (FREECAM_KEY.consumeClick()) { }
            if (zooming) stopZoom(minecraft);
            return;
        }

        // Handled before the zoom branch, which returns early when zoom is switched off.
        if (FREECAM_KEY.consumeClick()) VBVFreecam.toggle(minecraft);

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
            VBVConfigStore.saveAll();
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
        if (!gammaStatusEnabled || System.currentTimeMillis() > gammaStatusUntil) return;
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
    public static void loadSettings(boolean gammaFeature, double gammaStrength, boolean gammaStatus,
                                    boolean zoom, boolean zoomToggle, int fov,
                                    boolean betterF3, boolean debugOverlay,
                                    boolean showPerformance, boolean showWorld, boolean showPlayer,
                                    boolean compact, boolean showTarget, boolean showSystem) {
        gammaFeatureEnabled = gammaFeature; gammaLevel = Math.max(1.0D, Math.min(8.0D, gammaStrength));
        gammaStatusEnabled = gammaStatus; zoomEnabled = zoom; zoomToggleMode = zoomToggle;
        zoomFov = Math.max(20, Math.min(50, fov)); betterF3Enabled = betterF3;
        debugHudEnabled = debugOverlay;
        VBVBetterF3.load(showPerformance, showWorld, showPlayer, showTarget, showSystem, compact);
    }
    public static boolean isGammaStatusEnabled() { return gammaStatusEnabled; }
    public static void toggleGammaStatus() { gammaStatusEnabled = !gammaStatusEnabled; }
    public static boolean debugShowPerformance() { return VBVBetterF3.showPerformance(); }
    public static boolean debugShowWorld() { return VBVBetterF3.showWorld(); }
    public static boolean debugShowPlayer() { return VBVBetterF3.showPlayer(); }
    public static boolean debugShowTarget() { return VBVBetterF3.showTarget(); }
    public static boolean debugShowSystem() { return VBVBetterF3.showSystem(); }
    public static boolean debugCompact() { return VBVBetterF3.isCompact(); }
    public static void toggleDebugShowPerformance() { VBVBetterF3.togglePerformance(); }
    public static void toggleDebugShowWorld() { VBVBetterF3.toggleWorld(); }
    public static void toggleDebugShowPlayer() { VBVBetterF3.togglePlayer(); }
    public static void toggleDebugShowTarget() { VBVBetterF3.toggleTarget(); }
    public static void toggleDebugShowSystem() { VBVBetterF3.toggleSystem(); }
    public static void toggleDebugCompact() { VBVBetterF3.toggleCompact(); }
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
    public static void setDebugHudEnabled(boolean enabled) { debugHudEnabled = enabled; }
    /** Full replacement for vanilla F3, called from DebugScreenOverlay rather than Hud. */
    public static void renderDebug(GuiGraphicsExtractor graphics) {
        if (!betterF3Enabled || !debugHudEnabled) return;
        VBVBetterF3.render(graphics);
    }
}
