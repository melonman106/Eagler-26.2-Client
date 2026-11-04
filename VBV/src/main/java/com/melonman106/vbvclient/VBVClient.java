package com.melonman106.vbvclient;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;

/** Native Eagler client mod registry and shared Fabric-shaped event wiring. */
public final class VBVClient {
    public static final String MOD_ID = "viabackportvisuals-client";
    public static final String VERSION = "1.5.3";
    private static final Map<String, VBVModInfo> MODS = new LinkedHashMap<>();
    private static boolean callbacksRegistered;

    private VBVClient() {}

    public static synchronized void init() {
        if (!MODS.isEmpty()) return;\n        VBVConfigStore.loadAll();

        reg(MOD_ID, "ViaBackportVisuals Client", VERSION, "melonman106",
            "A native Eaglercraft 26.2 client enhancement suite with performance, interface, rendering and quality-of-life features.",
            "vbvclient:textures/mods/viabackportvisuals-client.png");
        reg("ukus-armor-hud", "Uku's Armor HUD", "1.5.3", "Uku3lig",
            "Displays equipped armor pieces and their remaining durability directly on the HUD.", "vbvclient:textures/mods/ukus-armor-hud.png");
        reg("simple-hud-enhanced", "Simple HUD Enhanced", "1.5.3", "SoRadGaming",
            "A configurable information HUD for coordinates, direction, FPS, server tick rate, velocity, light level, ping, biome, world time, day count, chunk position, active effects and movement statistics.",
            "vbvclient:textures/mods/simple-hud-enhanced.png");
        reg("mouse-tweaks", "Mouse Tweaks", "1.5.3", "YaLTeR",
            "Inventory interaction helpers for wheel-based slot movement, quick item transfers and evenly distributed drag operations.", "vbvclient:textures/mods/mouse-tweaks.png");
        reg("appleskin", "AppleSkin", "1.5.3", "Eagler client port",
            "Adds food-level, saturation and hunger-preview information to the in-game HUD using the AppleSkin-style icon atlas.", "vbvclient:textures/mods/appleskin.png");
        reg("gamma-utils", "Gamma Utils", "1.5.3", "Sjouwer",
            "Provides adjustable gamma and brightness controls for clearer visibility in dark environments.", "vbvclient:textures/mods/gamma-utils.png");
        reg("zoomify", "Zoomify", "1.5.3", "Xander",
            "Adds configurable hold or toggle zoom with adjustable field of view.", "vbvclient:textures/mods/zoomify.png");
        reg("betterf3", "BetterF3", "1.5.3", "TreyRuffy",
            "Reorganizes technical debug information into a clearer, configurable on-screen layout.", "vbvclient:textures/mods/betterf3.png");
        reg("vbv-minimap", "VBV Minimap", "1.5.3", "melonman106",
            "Displays a compact map of cached nearby terrain and the player position, with a low-overhead rendering mode.", "vbvclient:textures/mods/vbv-minimap.png");
        reg("jade", "Jade", "1.5.3", "Snownee",
            "Shows contextual information about the block or entity under the crosshair, including optional coordinates.", "vbvclient:textures/mods/jade.png");
        reg("shulkerboxtooltip", "ShulkerBoxTooltip", "1.5.3", "MisterPeModder",
            "Previews the contents of shulker boxes in a 9×3 inventory grid when Shift-hovering a box in a container.", "vbvclient:textures/mods/shulkerboxtooltip.jpeg");
        reg("custom-crosshair", "Custom Crosshair", "1.5.3", "melonman106",
            "Draw your own 15x15 crosshair.", "vbvclient:textures/mods/viabackportvisuals-client.png");
        reg("shine", "Shine", "1.5.3", "VBV",
            "Experimental visual enhancement controls for bloom, colored lighting, rim lighting, shading and effect quality.",
            "vbvclient:textures/mods/shine.png");

        registerFabricCallbacks();
        VBVGeneratedPackMods.register();
    }

    private static void registerFabricCallbacks() {
        if (callbacksRegistered) return;
        callbacksRegistered = true;

        // Register Gamma Utils and Zoomify through Fabric's native keybinding API.
        VBVExtraFeatures.initializeKeyBindings();

        // Zoomify restores the player's normal FOV as soon as a screen opens.
        ScreenEvents.AFTER_INIT.register((screen, width, height) -> VBVExtraFeatures.onScreenOpened());

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
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            VBVExtraFeatures.tick(client);
            VBVWorldMap.tick();
        });
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
