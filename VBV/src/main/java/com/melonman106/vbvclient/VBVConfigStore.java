package com.melonman106.vbvclient;

import java.nio.charset.StandardCharsets;
import net.lax1dude.eaglercraft.v1_8.EagRuntime;

/** Durable browser-side persistence for the native VBV feature configuration. */
public final class VBVConfigStore {
    private static final String PREFIX = "vbv_feature_config_v2.";
    private VBVConfigStore() {}

    private static String read(String key, String fallback) {
        try {
            byte[] bytes = EagRuntime.getStorage(PREFIX + key);
            return bytes == null || bytes.length == 0 ? fallback : new String(bytes, StandardCharsets.UTF_8);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static void write(String key, String value) {
        try {
            EagRuntime.setStorage(PREFIX + key, String.valueOf(value).getBytes(StandardCharsets.UTF_8));
        } catch (Throwable ignored) {
        }
    }

    private static boolean bool(String key, boolean fallback) {
        String value = read(key, Boolean.toString(fallback));
        return "true".equalsIgnoreCase(value) || (!"false".equalsIgnoreCase(value) && fallback);
    }

    private static int integer(String key, int fallback, int min, int max) {
        try { return Math.max(min, Math.min(max, Integer.parseInt(read(key, Integer.toString(fallback))))); }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static double decimal(String key, double fallback, double min, double max) {
        try { return Math.max(min, Math.min(max, Double.parseDouble(read(key, Double.toString(fallback))))); }
        catch (RuntimeException ignored) { return fallback; }
    }

    public static void loadAll() {
        VBVMiniMap.loadSettings(
            bool("minimap.enabled", false), integer("minimap.x", 0, -2000, 2000),
            integer("minimap.y", 0, -2000, 2000), integer("minimap.size", 64, 48, 96),
            integer("minimap.radius", 4, 3, 5), bool("minimap.frame", true),
            bool("minimap.marker", true), bool("minimap.north", true));

        VBVAppleSkin.loadSettings(
            bool("appleskin.enabled", true), bool("appleskin.foodText", true),
            bool("appleskin.saturationText", true), integer("appleskin.x", 4, -2000, 2000),
            integer("appleskin.y", 4, -2000, 2000));

        VBVJadeHud.loadSettings(
            bool("jade.enabled", true), bool("jade.coordinates", true),
            bool("jade.health", true), bool("jade.hardness", true),
            bool("jade.name", true), integer("jade.x", 0, -2000, 2000),
            integer("jade.y", 0, -2000, 2000));

        VBVMouseTweaks.loadSettings(
            bool("mouseTweaks.wheel", true), bool("mouseTweaks.quickMove", true),
            bool("mouseTweaks.dragEvenly", true));

        String crosshairPattern = read("crosshair.pattern", "");
        VBVCrosshairConfig.loadSettings(
            bool("crosshair.enabled", false), integer("crosshair.size", 15, 5, 31),
            integer("crosshair.color", 0xFFFFFFFF, 0, 0xFFFFFFFF), crosshairPattern);

        VBVFirstPersonAnimations.setEnabled(bool("firstPerson.enabled", true));
        VBVShulkerViewer.enabled = bool("shulker.enabled", true);

        VBVExtraFeatures.loadSettings(
            bool("gamma.enabled", true), decimal("gamma.strength", 4.0D, 1.0D, 8.0D),
            bool("gamma.status", true), bool("zoom.enabled", true),
            bool("zoom.toggle", false), integer("zoom.fov", 30, 20, 50),
            bool("betterF3.enabled", true), bool("betterF3.overlay", false),
            bool("betterF3.performance", true), bool("betterF3.world", true),
            bool("betterF3.player", true), bool("betterF3.compact", false));
    }

    public static void saveAll() {
        write("minimap.enabled", VBVMiniMap.enabled);
        write("minimap.x", VBVMiniMap.offsetX); write("minimap.y", VBVMiniMap.offsetY);
        write("minimap.size", VBVMiniMap.size); write("minimap.radius", VBVMiniMap.scanRadius);
        write("minimap.frame", VBVMiniMap.showFrame); write("minimap.marker", VBVMiniMap.showPlayerMarker);
        write("minimap.north", VBVMiniMap.showNorth);

        write("appleskin.enabled", VBVAppleSkin.isEnabled());
        write("appleskin.foodText", VBVAppleSkin.isShowFoodText());
        write("appleskin.saturationText", VBVAppleSkin.isShowSaturationText());
        write("appleskin.x", VBVAppleSkin.x); write("appleskin.y", VBVAppleSkin.y);

        write("jade.enabled", VBVJadeHud.enabled); write("jade.coordinates", VBVJadeHud.showCoordinates);
        write("jade.health", VBVJadeHud.showEntityHealth); write("jade.hardness", VBVJadeHud.showBlockHardness);
        write("jade.name", VBVJadeHud.showTargetName); write("jade.x", VBVJadeHud.offsetX);
        write("jade.y", VBVJadeHud.offsetY);

        write("mouseTweaks.wheel", VBVMouseTweaks.isWheelTweakEnabled());
        write("mouseTweaks.quickMove", VBVMouseTweaks.isQuickMoveEnabled());
        write("mouseTweaks.dragEvenly", VBVMouseTweaks.isDragEvenlyEnabled());

        write("crosshair.enabled", VBVCrosshairConfig.enabled);
        write("crosshair.size", VBVCrosshairConfig.size); write("crosshair.color", VBVCrosshairConfig.color);
        StringBuilder pattern = new StringBuilder(225);
        for (boolean pixel : VBVCrosshairConfig.pixels) pattern.append(pixel ? '1' : '0');
        write("crosshair.pattern", pattern.toString());

        write("firstPerson.enabled", VBVFirstPersonAnimations.isEnabled());
        write("shulker.enabled", VBVShulkerViewer.enabled);

        write("gamma.enabled", VBVExtraFeatures.isGammaFeatureEnabled());
        write("gamma.strength", VBVExtraFeatures.gammaLevel());
        write("gamma.status", VBVExtraFeatures.isGammaStatusEnabled());
        write("zoom.enabled", VBVExtraFeatures.isZoomEnabled());
        write("zoom.toggle", VBVExtraFeatures.isZoomToggleMode());
        write("zoom.fov", VBVExtraFeatures.zoomFov());
        write("betterF3.enabled", VBVExtraFeatures.isBetterF3Enabled());
        write("betterF3.overlay", VBVExtraFeatures.isDebugHudEnabled());
        write("betterF3.performance", VBVExtraFeatures.debugShowPerformance());
        write("betterF3.world", VBVExtraFeatures.debugShowWorld());
        write("betterF3.player", VBVExtraFeatures.debugShowPlayer());
        write("betterF3.compact", VBVExtraFeatures.debugCompact());
    }
}
