package com.melonman106.vbvclient;

import net.minecraft.client.gui.screens.Screen;
import net.lax1dude.eaglercraft.v1_8.profile.EaglerProfileScreen26;

/** Routes the Hero entry to Eaglercraft's real profile editor. */
public final class VBVProfileScreen {
    private VBVProfileScreen() {}

    public static void open(Screen parent) {
        net.minecraft.client.Minecraft.getInstance().setScreen(new EaglerProfileScreen26(parent));
    }
}
