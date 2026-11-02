package com.melonman106.vbvclient;

/** Runtime settings consumed by the native Eagler Shine rendering hooks. */
public final class ShineConfig {
    private static boolean enabled = true;
    private static boolean bloom = true;
    private static boolean coloredLighting = true;
    private static boolean rimLighting = true;
    private static boolean disabledShading;
    private static int quality = 2;
    private static int lightRadius = 64;
    private static float bloomStrength = 1.0F;
    private static float rimStrength = 1.0F;

    private ShineConfig() {}

    public static boolean enabled() { return enabled; }
    public static boolean bloom() { return enabled && bloom; }
    public static boolean coloredLighting() { return enabled && coloredLighting; }
    public static boolean rimLighting() { return enabled && rimLighting; }
    public static boolean disabledShading() { return enabled && disabledShading; }
    public static float qualityFactor() { return quality * 0.5F; }
    public static int lightRadius() { return lightRadius; }
    public static float bloomStrength() { return bloomStrength; }
    public static float rimStrength() { return rimStrength; }

    public static void toggleEnabled() { enabled = !enabled; }
    public static void toggleBloom() { bloom = !bloom; }
    public static void toggleColoredLighting() { coloredLighting = !coloredLighting; }
    public static void toggleRimLighting() { rimLighting = !rimLighting; }
    public static void toggleDisabledShading() { disabledShading = !disabledShading; }
    public static String qualityLabel() { return switch (quality) { case 1 -> "Low"; case 3 -> "High"; default -> "Medium"; }; }
    public static void cycleQuality() { quality = quality == 1 ? 2 : quality == 2 ? 3 : 1; }
    public static void cycleLightRadius() { lightRadius = lightRadius >= 96 ? 32 : lightRadius + 32; }
    public static void cycleBloomStrength() { bloomStrength = bloomStrength >= 1.5F ? 0.5F : bloomStrength + 0.5F; }
    public static void cycleRimStrength() { rimStrength = rimStrength >= 1.5F ? 0.5F : rimStrength + 0.5F; }
}
