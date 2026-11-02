package com.melonman106.vbvclient;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

/** Native Eagler client mod registry and shared Fabric-shaped event wiring. */
public final class VBVClient {
    public static final String MOD_ID = "viabackportvisuals-client";
    public static final String VERSION = "1.0.0-26.2";
    private static final Map<String, VBVModInfo> MODS = new LinkedHashMap<>();
    private static boolean callbacksRegistered;

    private VBVClient() {}

    public static synchronized void init() {
        if (!MODS.isEmpty()) return;

        reg(MOD_ID, "ViaBackportVisuals Client", VERSION, "melonman106",
            "Native client-side mod suite for Eaglercraft 26.2 u2.",
            "vbvclient:textures/mods/viabackportvisuals-client.png");
        reg("ukus-armor-hud", "Uku's Armor HUD", "Eagler port 1.0.0", "Uku3lig",
            "Armor slots and durability.", "vbvclient:textures/mods/ukus-armor-hud.png");
        reg("simple-hud-enhanced", "Simple HUD Enhanced", "Native Eagler port 1.0.0", "SoRadGaming",
            "Coordinates, directions, FPS, TPS, velocity, light, ping, biome, time, day, chunk/sub-chunk, effects and movement.",
            "vbvclient:textures/mods/simple-hud-enhanced.png");
        reg("mouse-tweaks", "Mouse Tweaks", "Native Eagler port 1.0.0", "YaLTeR",
            "Browser-safe inventory helpers.", "vbvclient:textures/mods/mouse-tweaks.png");
        reg("appleskin", "AppleSkin", "Eagler port 1.0.0", "Eagler client port",
            "Food and hunger information.", "vbvclient:textures/mods/appleskin.png");
        reg("gamma-utils", "Gamma Utils", "Eagler port 1.0.0", "Sjouwer",
            "Brightness toggle.", "vbvclient:textures/mods/gamma-utils.png");
        reg("zoomify", "Zoomify", "Eagler port 1.0.0", "Xander",
            "Zoom controls.", "vbvclient:textures/mods/zoomify.png");
        reg("betterf3", "BetterF3", "Eagler port 1.0.0", "TreyRuffy",
            "Debug information.", "vbvclient:textures/mods/betterf3.png");
        reg("vbv-minimap", "VBV Minimap", "Native Eagler port 1.0.0", "melonman106",
            "Nearby terrain minimap.", "vbvclient:textures/mods/vbv-minimap.png");
        reg("jade", "Jade", "Eagler port 1.0.0", "Snownee",
            "Targeted block/entity tooltip.", "vbvclient:textures/mods/jade.png");
        reg("shulkerboxtooltip", "ShulkerBoxTooltip", "Native Eagler port 1.0.0", "MisterPeModder",
            "Shulker box preview.", "vbvclient:textures/mods/shulkerboxtooltip.jpeg");
        reg("custom-crosshair", "Custom Crosshair", "Native Eagler port 1.0.0", "melonman106",
            "Draw your own 15x15 crosshair.", "vbvclient:textures/mods/viabackportvisuals-client.png");

        registerFabricCallbacks();
        VBVGeneratedPackMods.register();
    }

    private static void registerFabricCallbacks() {
        if (callbacksRegistered) return;
        callbacksRegistered = true;

        // Shared post-vanilla HUD phase for the native HUD ports.
        HudRenderCallback.EVENT.register((client, graphics, tickCounter) -> {
            VBVSimpleHud.render(graphics);
            VBVArmorHud.render(graphics);
            VBVExtraFeatures.renderGammaStatus(graphics);
            VBVAppleSkin.render(graphics);
            VBVMiniMap.render(graphics);
            VBVJadeHud.render(graphics);
            VBVCrosshair.render(graphics);
        });

        // The explored map records the local player's current chunk at end tick.
        ClientTickEvents.END_CLIENT_TICK.register(client -> VBVWorldMap.tick());
    }

    private static void reg(String id, String name, String version, String authors,
                            String description, String icon) {
        registerMod(new VBVModInfo(id, name, version, authors, description, icon, false));
    }

    public static void registerMod(VBVModInfo info) {
        MODS.put(info.id(), info);
        HerzVBVBridge.register(info);
    }

    public static Collection<VBVModInfo> getMods() {
        return Collections.unmodifiableCollection(MODS.values());
    }
}
