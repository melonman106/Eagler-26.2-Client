package com.melonman106.vbvclient;

/**
 * Menu colour theme. The accent replaces the old hard-coded yellow everywhere VBV draws
 * its own menus. The choice is stored by {@link VBVConfigStore} ("theme.preset" and
 * "theme.custom") so it survives reloads.
 */
public final class VBVTheme {
    public static final String[] NAMES = {
        "Gold", "Purple", "Crimson", "Blue", "Green", "Orange", "Teal", "Pink", "Custom"
    };
    private static final int[] PRESETS = {
        0xE8B44A, // Gold (default, the original look)
        0xA66BFF, // Purple
        0xE5484D, // Crimson
        0x3E8BFF, // Blue
        0x3FBF6A, // Green
        0xFF8A3D, // Orange
        0x2EC4B6, // Teal
        0xFF5FA2  // Pink
    };
    public static final int CUSTOM_INDEX = PRESETS.length;

    private static int preset = 0;
    private static int custom = 0xA66BFF;

    private VBVTheme() {}

    public static int presetCount() { return NAMES.length; }
    public static int getPreset() { return preset; }
    public static int getCustomRgb() { return custom; }
    public static String presetName() { return NAMES[preset]; }
    public static String presetName(int index) { return NAMES[Math.floorMod(index, NAMES.length)]; }

    /** Used by VBVConfigStore when loading; out-of-range values fall back to Gold. */
    public static void load(int presetIndex, int customRgb) {
        preset = presetIndex >= 0 && presetIndex < NAMES.length ? presetIndex : 0;
        custom = customRgb & 0xFFFFFF;
    }

    public static void setPreset(int index) {
        preset = Math.floorMod(index, NAMES.length);
    }

    public static void cyclePreset(int direction) {
        setPreset(preset + direction);
    }

    /** Adjusts one channel (0=R, 1=G, 2=B) of the custom colour and selects it. */
    public static void nudgeCustom(int channel, int delta) {
        int shift = 16 - 8 * channel;
        int value = Math.max(0, Math.min(255, ((custom >> shift) & 0xFF) + delta));
        custom = (custom & ~(0xFF << shift)) | (value << shift);
        preset = CUSTOM_INDEX;
    }

    public static int rgbOf(int index) {
        return index == CUSTOM_INDEX ? custom : PRESETS[Math.floorMod(index, PRESETS.length)];
    }

    /** Opaque ARGB accent colour. */
    public static int accent() {
        return 0xFF000000 | rgbOf(preset);
    }

    /** Brighter variant for highlights and hovered text. */
    public static int accentLight() {
        return mix(accent(), 0xFFFFFFFF, 0.35F);
    }

    /** Darker variant for borders and shadows. */
    public static int accentDark() {
        return mix(accent(), 0xFF000000, 0.55F);
    }

    /** Accent with the given alpha (0-255), for translucent fills and glows. */
    public static int accentAlpha(int alpha) {
        return ((alpha & 0xFF) << 24) | (rgbOf(preset) & 0xFFFFFF);
    }

    /** Dark panel tinted slightly toward the accent so menus feel themed. */
    public static int panel(int alpha) {
        int base = mix(0xFF0B0B10, accent(), 0.10F);
        return ((alpha & 0xFF) << 24) | (base & 0xFFFFFF);
    }

    /** Readable text colour on top of the accent (black or white). */
    public static int onAccent() {
        int rgb = rgbOf(preset);
        int luma = (((rgb >> 16) & 0xFF) * 299 + ((rgb >> 8) & 0xFF) * 587 + (rgb & 0xFF) * 114) / 1000;
        return luma > 150 ? 0xFF14110A : 0xFFFFFFFF;
    }

    public static int mix(int from, int to, float amount) {
        int a = (int)(((from >>> 24) & 0xFF) * (1.0F - amount) + ((to >>> 24) & 0xFF) * amount);
        int r = (int)(((from >> 16) & 0xFF) * (1.0F - amount) + ((to >> 16) & 0xFF) * amount);
        int g = (int)(((from >> 8) & 0xFF) * (1.0F - amount) + ((to >> 8) & 0xFF) * amount);
        int b = (int)((from & 0xFF) * (1.0F - amount) + (to & 0xFF) * amount);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
