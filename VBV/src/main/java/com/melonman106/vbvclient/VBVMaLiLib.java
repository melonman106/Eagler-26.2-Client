package com.melonman106.vbvclient;

/**
 * Browser-safe MaLiLib compatibility core for native Eagler ports.
 * This does not load upstream Fabric classes, mixins, or binaries.
 */
public final class VBVMaLiLib {
    public static final String MOD_ID = "malilib";
    public static final String VERSION = "1.0.0-eagler";
    private static final String PREFIX = "vbv.malilib.";
    private static boolean initialized;

    private VBVMaLiLib() {}

    public static void initialize() {
        initialized = true;
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static String getString(String key, String fallback) {
        String value = VBVStorage.getText(PREFIX + safeKey(key));
        return value == null || value.isEmpty() ? fallback : value;
    }

    public static void putString(String key, String value) {
        VBVStorage.putText(PREFIX + safeKey(key), value == null ? "" : value);
    }

    public static boolean getBoolean(String key, boolean fallback) {
        String value = getString(key, "");
        return value.isEmpty() ? fallback : Boolean.parseBoolean(value);
    }

    public static void putBoolean(String key, boolean value) {
        putString(key, Boolean.toString(value));
    }

    public static int getInteger(String key, int fallback) {
        try {
            return Integer.parseInt(getString(key, ""));
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    public static void putInteger(String key, int value) {
        putString(key, Integer.toString(value));
    }

    private static String safeKey(String key) {
        if (key == null || key.isEmpty()) return "default";
        return key.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
