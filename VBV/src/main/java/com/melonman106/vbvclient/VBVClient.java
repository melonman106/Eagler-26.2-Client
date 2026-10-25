package com.melonman106.vbvclient;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class VBVClient {
    public static final String MOD_ID = "viabackportvisuals-client";
    public static final String VERSION = "1.0.0-26.2";
    private static final String BUILTIN_MOD_ICON = "minecraft:textures/misc/unknown_pack.png";

    private static final Map<String, VBVModInfo> MODS = new LinkedHashMap<>();

    private VBVClient() {}

    public static void init() {
        if (!MODS.isEmpty()) return;

        registerMod(new VBVModInfo(
            MOD_ID,
            "ViaBackportVisuals Client",
            VERSION,
            "melonman106",
            "Native client-side mod suite for Eaglercraft 26.2 u1.",
            BUILTIN_MOD_ICON,
            false
        ));

        registerMod(new VBVModInfo(
            "ukus-armor-hud",
            "Uku's Armor HUD",
            "Eagler port 1.0.0",
            "Uku3lig / Eagler client port",
            "Four vertical armor slots beside the hotbar showing helmet, chestplate, leggings, boots, and remaining durability.",
            BUILTIN_MOD_ICON,
            false
        ));

        registerMod(new VBVModInfo(
            "simple-hud-enhanced",
            "Simple HUD Enhanced",
            "Native Eagler port 1.0.0",
            "SoRadGaming / Eagler client port",
            "Pick-and-choose HUD with FPS, coordinates, speed, light, time, player, health, hunger, movement, effects, and equipment information.",
            BUILTIN_MOD_ICON,
            false
        ));

        registerMod(new VBVModInfo(
            "mouse-tweaks",
            "Mouse Tweaks",
            "Native Eagler port 1.0.0",
            "YaLTeR / Eagler client port",
            "Inventory mouse improvements: wheel-based item selection and native hooks for quick-move and drag-evenly behavior.",
            BUILTIN_MOD_ICON,
            false
        ));

        registerMod(new VBVModInfo(
            "appleskin",
            "AppleSkin",
            "Eagler port 1.0.0",
            "Eagler client port",
            "Food, hunger, saturation, and exhaustion information in the client HUD.",
            BUILTIN_MOD_ICON,
            false
        ));

        registerMod(new VBVModInfo(
            "gamma-utils",
            "Gamma Utils (native port)",
            "Eagler port 1.0.0",
            "Sjouwer / native Eagler port",
            "Press G to toggle maximum vanilla brightness. Vanilla gamma is capped, so this is not full shader-level fullbright.",
            BUILTIN_MOD_ICON,
            false
        ));

        registerMod(new VBVModInfo(
            "zoomify",
            "Zoomify (native port)",
            "Eagler port 1.0.0",
            "Xander / native Eagler port",
            "Hold C to zoom in using the field-of-view setting. This initial port uses a fixed zoom level without Zoomify's full configuration or animation system.",
            BUILTIN_MOD_ICON,
            false
        ));

        registerMod(new VBVModInfo(
            "betterf3",
            "BetterF3 (native port)",
            "Eagler port 1.0.0",
            "TreyRuffy / native Eagler port",
            "Press F3 or F+3 (Chromebook shortcut) to toggle a compact debug panel showing coordinates, yaw, pitch, and FPS. This is a lightweight native implementation, not the full configurable BetterF3 layout.",
            BUILTIN_MOD_ICON,
            false
        ));

        registerMod(new VBVModInfo(
            "vbv-minimap",
            "VBV Minimap",
            "Native Eagler port 1.0.0",
            "melonman106",
            "Detailed explored-terrain minimap with terrain shading and a player marker.",
            BUILTIN_MOD_ICON,
            false
        ));

        VBVGeneratedPackMods.register();
        HerzVBVBridge.initialize();
    }

    public static void registerMod(VBVModInfo info) {
        MODS.put(info.id(), info);
        HerzVBVBridge.register(info);
    }

    public static Collection<VBVModInfo> getMods() {
        return Collections.unmodifiableCollection(MODS.values());
    }
}
