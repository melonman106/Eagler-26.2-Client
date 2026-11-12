package com.melonman106.vbvclient;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.lax1dude.eaglercraft.v1_8.EagRuntime;

/** Durable browser-side persistence for the native VBV feature configuration. */
public final class VBVConfigStore {
    private static final String STORAGE_KEY = "vbv_feature_config_v2";
    private static final Map<String, String> VALUES = new HashMap<>();
    private static boolean loaded;
    private VBVConfigStore() {}

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        try {
            byte[] bytes = EagRuntime.getStorage(STORAGE_KEY);
            if (bytes == null || bytes.length == 0) return;
            String raw = new String(bytes, StandardCharsets.UTF_8);
            for (String line : raw.split("\\n")) {
                int split = line.indexOf('=');
                if (split > 0) VALUES.put(line.substring(0, split), line.substring(split + 1));
            }
        } catch (Throwable ignored) {
        }
    }

    private static String read(String key, String fallback) {
        ensureLoaded();
        return VALUES.getOrDefault(key, fallback);
    }

    /** Accepts primitive config values and serializes them consistently. */
    private static void write(String key, Object value) {
        ensureLoaded();
        VALUES.put(key, String.valueOf(value));
    }

    private static void flush() {
        ensureLoaded();
        try {
            StringBuilder raw = new StringBuilder();
            for (Map.Entry<String, String> entry : VALUES.entrySet()) {
                raw.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
            }
            EagRuntime.setStorage(STORAGE_KEY, raw.toString().getBytes(StandardCharsets.UTF_8));
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
        VBVArmorHudConfig.enabled = bool("armor.enabled", true);
        VBVArmorHudConfig.anchor = integer("armor.anchor", 4, 0, 4);
        VBVArmorHudConfig.side = integer("armor.side", 0, 0, 1);
        VBVArmorHudConfig.offsetX = integer("armor.x", 0, -2000, 2000);
        VBVArmorHudConfig.offsetY = integer("armor.y", 8, -2000, 2000);
        VBVArmorHudConfig.style = integer("armor.style", 0, 0, 3);
        VBVArmorHudConfig.orientation = integer("armor.orientation", 0, 0, 1);
        VBVArmorHudConfig.widgetShown = integer("armor.widgetShown", 0, 0, 3);
        VBVArmorHudConfig.offhandBehavior = integer("armor.offhandBehavior", 1, 0, 2);
        VBVArmorHudConfig.durabilityDisplay = integer("armor.durabilityDisplay", 0, 0, 2);
        VBVArmorHudConfig.offHandDurability = bool("armor.offHandDurability", true);
        VBVArmorHudConfig.mainHandDurability = bool("armor.mainHandDurability", false);
        VBVArmorHudConfig.reversed = bool("armor.reversed", false);
        VBVArmorHudConfig.iconsShown = bool("armor.iconsShown", true);
        VBVArmorHudConfig.warningShown = bool("armor.warningShown", true);
        VBVArmorHudConfig.minDurabilityValue = integer("armor.minDurabilityValue", 20, 0, 100000);
        VBVArmorHudConfig.minDurabilityPercent = integer("armor.minDurabilityPercent", 10, 0, 100);
        VBVArmorHudConfig.warningBobIntensity = integer("armor.warningBobIntensity", 3, 0, 10);
        VBVSimpleHudConfig.load(read("simpleHud", ""));
        VBVMiniMap.loadSettings(
            bool("minimap.enabled", false), integer("minimap.x", 0, -2000, 2000),
            integer("minimap.y", 0, -2000, 2000), integer("minimap.size", 64, 48, 96),
            integer("minimap.radius", 4, 3, 5), bool("minimap.frame", true),
            bool("minimap.marker", true), bool("minimap.north", true));

        VBVAppleSkin.loadSettings(
            bool("appleskin.enabled", true), bool("appleskin.foodText", false),
            bool("appleskin.saturationText", false), integer("appleskin.x", 4, -2000, 2000),
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
            bool("crosshair.enabled", false), integer("crosshair.size", 2, 1, 4),
            integer("crosshair.color", 0xFFFFFFFF, Integer.MIN_VALUE, Integer.MAX_VALUE), crosshairPattern);

        VBVFirstPersonAnimations.setEnabled(bool("firstPerson.enabled", true));
        VBVFirstPersonAnimations.setOffsets(
            integer("firstPerson.x", VBVFirstPersonAnimations.DEFAULT_X, VBVFirstPersonAnimations.OFFSET_MIN, VBVFirstPersonAnimations.OFFSET_MAX),
            integer("firstPerson.y", VBVFirstPersonAnimations.DEFAULT_Y, VBVFirstPersonAnimations.OFFSET_MIN, VBVFirstPersonAnimations.OFFSET_MAX),
            integer("firstPerson.z", VBVFirstPersonAnimations.DEFAULT_Z, VBVFirstPersonAnimations.OFFSET_MIN, VBVFirstPersonAnimations.OFFSET_MAX));
        VBVShulkerViewer.enabled = bool("shulker.enabled", true);
        VBVAppleSkin.setFoodPreview(bool("appleskin.preview", true));
        VBVTheme.load(integer("theme.preset", 0, 0, VBVTheme.CUSTOM_INDEX),
            integer("theme.custom", 0xA66BFF, 0, 0xFFFFFF));
        VBVLod.load(integer("lod.level", VBVLod.OFF, VBVLod.OFF, VBVLod.FULL));
        VBVEntityCulling.setEnabled(bool("entityCulling.enabled", true));
        VBVPerf.setMeter(bool("perf.meter", false));
        VBVCem.setEnabled(bool("cem.enabled", true));

        VBVExtraFeatures.loadSettings(
            bool("gamma.enabled", true), decimal("gamma.strength", 4.0D, 1.0D, 8.0D),
            bool("gamma.status", true), bool("zoom.enabled", true),
            bool("zoom.toggle", false), integer("zoom.fov", 30, 20, 50),
            bool("betterF3.enabled", true), bool("betterF3.overlay", false),
            bool("betterF3.performance", true), bool("betterF3.world", true),
            bool("betterF3.player", true), bool("betterF3.compact", false));
    }

    public static void saveAll() {
        write("armor.enabled", VBVArmorHudConfig.enabled);
        write("armor.anchor", VBVArmorHudConfig.anchor); write("armor.side", VBVArmorHudConfig.side);
        write("armor.x", VBVArmorHudConfig.offsetX); write("armor.y", VBVArmorHudConfig.offsetY);
        write("armor.style", VBVArmorHudConfig.style); write("armor.orientation", VBVArmorHudConfig.orientation);
        write("armor.widgetShown", VBVArmorHudConfig.widgetShown);
        write("armor.offhandBehavior", VBVArmorHudConfig.offhandBehavior);
        write("armor.durabilityDisplay", VBVArmorHudConfig.durabilityDisplay);
        write("armor.offHandDurability", VBVArmorHudConfig.offHandDurability);
        write("armor.mainHandDurability", VBVArmorHudConfig.mainHandDurability);
        write("armor.reversed", VBVArmorHudConfig.reversed); write("armor.iconsShown", VBVArmorHudConfig.iconsShown);
        write("armor.warningShown", VBVArmorHudConfig.warningShown);
        write("armor.minDurabilityValue", VBVArmorHudConfig.minDurabilityValue);
        write("armor.minDurabilityPercent", VBVArmorHudConfig.minDurabilityPercent);
        write("armor.warningBobIntensity", VBVArmorHudConfig.warningBobIntensity);
        write("simpleHud", VBVSimpleHudConfig.serialize());
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
        write("firstPerson.x", VBVFirstPersonAnimations.getOffsetX());
        write("firstPerson.y", VBVFirstPersonAnimations.getOffsetY());
        write("firstPerson.z", VBVFirstPersonAnimations.getOffsetZ());
        write("shulker.enabled", VBVShulkerViewer.enabled);
        write("appleskin.preview", VBVAppleSkin.isFoodPreview());
        write("theme.preset", VBVTheme.getPreset());
        write("theme.custom", VBVTheme.getCustomRgb());
        write("lod.level", VBVLod.getLevel());
        write("entityCulling.enabled", VBVEntityCulling.isEnabled());
        write("perf.meter", VBVPerf.isMeter());
        write("cem.enabled", VBVCem.isEnabled());

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
        flush();
    }
}