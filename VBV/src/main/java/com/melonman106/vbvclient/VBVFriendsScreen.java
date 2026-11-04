package com.melonman106.vbvclient;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Compatibility route for the previous Friends screen; opens the network party manager. */
public final class VBVFriendsScreen extends Screen {
    private final Screen parent;

    public VBVFriendsScreen(Screen parent) {
        super(Component.literal("Party"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        VBVNav.open(new VBVPartyScreen(parent));
    }

    @Override
    public void onClose() {
        VBVNav.open(parent);
    }
}
