package com.melonman106.vbvclient;

import java.nio.charset.StandardCharsets;
import net.lax1dude.eaglercraft.v1_8.EagRuntime;

/** Persistent runtime settings consumed by the native Eagler Shine rendering hooks. */
public final class ShineConfig {
    private static final String STORAGE_KEY = "vbv_shine";
    private static boolean enabled = true;
    private static boolean bloom = true;
    private static boolean coloredLighting = true;
    private static boolean rimLighting = true;
    private static boolean disabledShading;
    private static int quality = 2;
    private static int lightRadius = 64;
    private static float bloomStrength = 1.0F;
    private static float rimStrength = 1.0F;

    static { load(); }
    private ShineConfig() {}

    private static void load() {
        try {
            byte[] stored = EagRuntime.getStorage(STORAGE_KEY);
            if (stored == null || stored.length == 0) return;
            String[] v = new String(stored, StandardCharsets.UTF_8).split(",", -1);
            if (v.length != 9) return;
            enabled = Boolean.parseBoolean(v[0]);
            bloom = Boolean.parseBoolean(v[1]);
            coloredLighting = Boolean.parseBoolean(v[2]);
            rimLighting = Boolean.parseBoolean(v[3]);
            disabledShading = Boolean.parseBoolean(v[4]);
            quality = clamp(Integer.parseInt(v[5]), 1, 3);
            lightRadius = clamp(Integer.parseInt(v[6]), 32, 96);
            bloomStrength = clamp(Float.parseFloat(v[7]), 0.5F, 2.0F);
            rimStrength = clamp(Float.parseFloat(v[8]), 0.5F, 2.0F);
        } catch (Throwable ignored) {
            // Invalid/old settings safely fall back to defaults.
        }
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    private static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }

    private static void save() {
        try {
            String data = enabled + "," + bloom + "," + coloredLighting + "," + rimLighting + ","
                + disabledShading + "," + quality + "," + lightRadius + "," + bloomStrength + "," + rimStrength;
            EagRuntime.setStorage(STORAGE_KEY, data.getBytes(StandardCharsets.UTF_8));
        } catch (Throwable ignored) {
        }
    }

    public static boolean enabled() { return enabled; }
    public static boolean bloom() { return enabled && bloom; }
    public static boolean coloredLighting() { return enabled && coloredLighting; }
    public static boolean rimLighting() { return enabled && rimLighting; }
    public static boolean disabledShading() { return enabled && disabledShading; }
    public static float qualityFactor() { return quality * 0.5F; }
    public static int lightRadius() { return lightRadius; }
    public static float bloomStrength() { return bloomStrength; }
    public static float rimStrength() { return rimStrength; }

    public static void toggleEnabled() { enabled = !enabled; save(); }
    public static void toggleBloom() { bloom = !bloom; save(); }
    public static void toggleColoredLighting() { coloredLighting = !coloredLighting; save(); }
    public static void toggleRimLighting() { rimLighting = !rimLighting; save(); }
    public static void toggleDisabledShading() { disabledShading = !disabledShading; save(); }
    public static String qualityLabel() { return switch (quality) { case 1 -> "Low"; case 3 -> "High"; default -> "Medium"; }; }
    public static void cycleQuality() { quality = quality == 1 ? 2 : quality == 2 ? 3 : 1; save(); }
    public static void cycleLightRadius() { lightRadius = lightRadius >= 96 ? 32 : lightRadius + 32; save(); }
    public static void cycleBloomStrength() { bloomStrength = bloomStrength >= 2.0F ? 0.5F : bloomStrength + 0.5F; save(); }
    public static void cycleRimStrength() { rimStrength = rimStrength >= 2.0F ? 0.5F : rimStrength + 0.5F; save(); }
}
