package com.melonman106.vbvclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;

/** Mods-screen and world-map navigation hotkeys on the native keyboard event bridge. */
public final class VBVKeybinds {
    private static final int KEY_V = 86;
    private static final int KEY_M = 77;

    private VBVKeybinds() {}

    /** Returns true when a VBV hotkey consumed the event. */
    public static boolean handle(KeyEvent event) {
        if (event == null || event.hasControlDown() || event.hasAltDown() || event.hasShiftDown()) {
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Screen screen = minecraft.gui.screen();
        if (screen != null || minecraft.player == null) {
            return false;
        }

        if (event.key() == KEY_V) {
            VBVClient.init();
            HerzVBVBridge.initialize();
            VBVNav.open(new VBVModsScreen(null));
            return true;
        }

        if (event.key() == KEY_M) {
            VBVClient.init();
            HerzVBVBridge.initialize();
            VBVNav.open(new VBVWorldMapScreen(null));
            return true;
        }

        return false;
    }
}
