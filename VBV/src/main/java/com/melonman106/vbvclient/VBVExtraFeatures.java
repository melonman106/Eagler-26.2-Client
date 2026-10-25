package com.melonman106.vbvclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;

/**
 * Small native equivalents for selected client-side features from Gamma Utils,
 * Zoomify, and BetterF3. This is a feature port, not the original Fabric mods.
 */
public final class VBVExtraFeatures {
    private static boolean gammaEnabled;
    private static double previousGamma = 0.5D;
    private static boolean zooming;
    private static int previousFov = 70;
    private static boolean debugHudEnabled;
    private static boolean fKeyHeld;

    private VBVExtraFeatures() {}

    /**
     * KeyboardHandler passes GLFW-style key codes and actions (1=press, 0=release).
     * G toggles the vanilla brightness setting, C zooms while held, and BetterF3
     * toggles with F3 or F+3 for Chromebook keyboards without a dedicated F3 key.
     * Vanilla gamma is capped at 1.0, so this is not true
     * shader-level fullbright.
     */
    public static void handleKey(int key, int action) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return;

        if (key == 70) { // F: track for the Chromebook-friendly F+3 shortcut
            fKeyHeld = action == 1;
        }

        if (key == 71 && action == 1) { // G: Gamma Utils-style brightness toggle
            if (!gammaEnabled) {
                previousGamma = minecraft.options.gamma().get();
                minecraft.options.gamma().set(1.0D);
                gammaEnabled = true;
            } else {
                minecraft.options.gamma().set(previousGamma);
                gammaEnabled = false;
            }
        } else if (key == 67) { // C: hold to zoom, restore FOV on release
            if (action == 1 && !zooming) {
                previousFov = minecraft.options.fov().get();
                minecraft.options.fov().set(30);
                zooming = true;
            } else if (action == 0 && zooming) {
                minecraft.options.fov().set(previousFov);
                zooming = false;
            }
        } else if (action == 1 && (key == 292 || (key == 51 && fKeyHeld))) {
            // GLFW_KEY_F3 = 292; GLFW_KEY_3 = 51. F+3 is the Chromebook alternative.
            debugHudEnabled = !debugHudEnabled;
        }
    }

    public static void renderDebug(GuiGraphicsExtractor graphics) {
        if (!debugHudEnabled) return;
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) return;

        int x = 6;
        int y = 6;
        int lineHeight = minecraft.font.lineHeight + 2;
        graphics.fill(2, 2, 188, 2 + lineHeight * 4 + 4, 0x90000000);
        graphics.text(minecraft.font, "BetterF3", x, y, 0xFFFFFFFF, true);
        graphics.text(minecraft.font,
            String.format(java.util.Locale.ROOT, "XYZ: %.2f / %.2f / %.2f",
                player.getX(), player.getY(), player.getZ()),
            x, y + lineHeight, 0xFF55FFFF, true);
        graphics.text(minecraft.font,
            String.format(java.util.Locale.ROOT, "Yaw: %.1f  Pitch: %.1f",
                player.getYRot(), player.getXRot()),
            x, y + lineHeight * 2, 0xFFFFFFFF, true);
        graphics.text(minecraft.font, "FPS: " + minecraft.getFps(),
            x, y + lineHeight * 3, 0xFFAAAAAA, true);
    }
}
