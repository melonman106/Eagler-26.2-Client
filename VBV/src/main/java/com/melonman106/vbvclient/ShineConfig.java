package com.melonman106.vbvclient;

/**
 * Eagler/WebGL-native Shine configuration state.
 *
 * This is a clean-room configuration layer: the upstream Shine source is
 * All Rights Reserved, so its source is not copied into this project.
 */
public final class ShineConfig {
    private static boolean enabled = true;
    private static boolean bloom = true;
    private static boolean coloredLighting = true;
    private static boolean rimLighting = true;
    private static boolean disabledShading = false;
    private static float bloomStrength = 1.0f;
    private static float rimStrength = 0.65f;
    private static float lightRadius = 12.0f;
    private static int quality = 1;

    private ShineConfig() {}

    public static boolean enabled() { return enabled; }
    public static boolean bloom() { return enabled && bloom; }
    public static boolean coloredLighting() { return enabled && coloredLighting; }
    public static boolean rimLighting() { return enabled && rimLighting; }
    public static boolean disabledShading() { return enabled && disabledShading; }
    public static float bloomStrength() { return bloomStrength; }
    public static float rimStrength() { return rimStrength; }
    public static float lightRadius() { return lightRadius; }
    public static int quality() { return quality; }

    public static void setEnabled(boolean value) { enabled = value; }
    public static void toggleEnabled() { enabled = !enabled; }
    public static void toggleBloom() { bloom = !bloom; }
    public static void toggleColoredLighting() { coloredLighting = !coloredLighting; }
    public static void toggleRimLighting() { rimLighting = !rimLighting; }
    public static void toggleDisabledShading() { disabledShading = !disabledShading; }

    public static void cycleBloomStrength() {
        bloomStrength = next(bloomStrength, 0.25f, 2.5f, 0.25f);
    }

    public static void cycleRimStrength() {
        rimStrength = next(rimStrength, 0.25f, 1.5f, 0.25f);
    }

    public static void cycleLightRadius() {
        lightRadius = next(lightRadius, 4.0f, 24.0f, 4.0f);
    }

    public static void cycleQuality() {
        quality = (quality + 1) % 3;
    }

    public static void applyPreset(String preset) {
        if ("all".equals(preset)) {
            enabled = true;
            bloom = true;
            coloredLighting = true;
            rimLighting = true;
            disabledShading = false;
            bloomStrength = 1.0f;
            rimStrength = 0.65f;
            lightRadius = 12.0f;
            quality = 1;
        } else if ("bloom".equals(preset)) {
            enabled = true;
            bloom = true;
            coloredLighting = false;
            rimLighting = false;
            disabledShading = false;
            bloomStrength = 1.0f;
            quality = 1;
        } else if ("performance".equals(preset)) {
            enabled = true;
            bloom = true;
            coloredLighting = false;
            rimLighting = false;
            disabledShading = false;
            bloomStrength = 0.75f;
            lightRadius = 8.0f;
            quality = 0;
        }
    }

    private static float next(float value, float min, float max, float step) {
        float next = value + step;
        return next > max + 0.001f ? min : next;
    }

    /** Relative effect scale for the selected performance/quality preset. */
    public static float qualityFactor() {
        return quality == 0 ? 0.65f : quality == 1 ? 0.82f : 1.0f;
    }

    public static String qualityName() {
        return quality == 0 ? "Low" : quality == 1 ? "Medium" : "High";
    }
}
