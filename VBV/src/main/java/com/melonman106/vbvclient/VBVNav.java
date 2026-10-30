package com.melonman106.vbvclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Screen navigation compatibility layer. The generated Eagler source may
 * rename the vanilla screen classes, so apply_dungeons_ui.py resolves those
 * classes from the generated TitleScreen before compilation.
 */
public final class VBVNav {
    private VBVNav() {}

    public static void open(Screen screen) {
        Minecraft.getInstance().setScreen(screen);
    }

    // Build-time fallback bodies. apply_dungeons_ui.py replaces these with
    // direct constructors for the actual Eagler 26.2 screen classes.
    public static void openMultiplayer(Screen parent) {
        open(parent);
    }

    public static void openSingleplayer(Screen parent) {
        open(parent);
    }

    public static void openOptions(Screen parent) {
        open(parent);
    }
}
