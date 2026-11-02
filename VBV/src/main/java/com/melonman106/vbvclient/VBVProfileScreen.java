package com.melonman106.vbvclient;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.lax1dude.eaglercraft.v1_8.profile.EaglerProfileScreen26;

/** Compatibility wrapper that opens Eagler's full username/skin/cape editor. */
public final class VBVProfileScreen extends Screen {
    private final Screen parent;

    public VBVProfileScreen(Screen parent) {
        super(Component.literal("Hero Profile"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        VBVNav.open(new EaglerProfileScreen26(parent));
    }
}
