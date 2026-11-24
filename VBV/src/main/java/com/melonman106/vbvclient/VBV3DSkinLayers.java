package com.melonman106.vbvclient;

/**
 * 3D Skin Layers: gives the player's second skin layer real depth.
 *
 * <p>Vanilla builds the jacket, sleeves, pants and hat with a 0.25 px shell. The player
 * model reads {@link #overlayThickness()} while it is baked, so the chosen thickness is
 * applied on the next resource reload (the config screen has an Apply button).</p>
 */
public final class VBV3DSkinLayers {
    private static final float VANILLA_THICKNESS = 0.25F;
    private static final float[] THICKNESS = {0.5F, 0.75F, 1.0F};

    public static boolean enabled = true;
    public static int thickness;

    private VBV3DSkinLayers() {}

    /** Outer-layer growth handed to PlayerModel.createMesh, in model pixels. */
    public static float overlayThickness() {
        if (!enabled) return VANILLA_THICKNESS;
        return THICKNESS[Math.max(0, Math.min(THICKNESS.length - 1, thickness))];
    }

    public static void cycleThickness() {
        thickness = (thickness + 1) % THICKNESS.length;
    }

    public static String thicknessLabel() {
        return String.format(java.util.Locale.ROOT, "%.2f", overlayThickness()) + " px";
    }

    public static void loadSettings(boolean on, int level) {
        enabled = on;
        thickness = Math.max(0, Math.min(THICKNESS.length - 1, level));
    }
}
