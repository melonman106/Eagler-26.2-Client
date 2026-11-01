package com.melonman106.vbvclient;

import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** VBV home screen with native Minecraft widgets and Dungeons-inspired layout. */
public final class VBVDungeonsTitleScreen extends TitleScreen {
    public VBVDungeonsTitleScreen() {
        super();
    }

    @Override
    protected void init() {
        super.init();
        clearWidgets();

        int left = Math.max(24, width / 24);
        int bottom = height - 44;
        // Keep the title menu close to Minecraft's standard 200x20 controls.
        int mainWidth = 200;
        int smallWidth = 96;
        int heightMain = 20;
        int heightSmall = 20;
        int gap = 6;
        int y = Math.max(56, height - 158);

        addNativeButton("PLAY", left, y, mainWidth, heightMain,
            b -> VBVNav.openMultiplayer(this));
        y += heightMain + gap;
        addNativeButton("SINGLEPLAYER", left, y, smallWidth, heightSmall,
            b -> VBVNav.openSingleplayer(this));
        addNativeButton("PARTY", left + smallWidth + gap, y, smallWidth, heightSmall,
            b -> VBVNav.open(new VBVFriendsScreen(this)));
        y += heightSmall + gap;
        addNativeButton("MODS", left, y, smallWidth, heightSmall,
            b -> VBVNav.open(new VBVModsScreen(this)));
        addNativeButton("OPTIONS", left + smallWidth + gap, y, smallWidth, heightSmall,
            b -> VBVNav.open(new VBVSettingsScreen(this)));

        // Storage is deliberately a normal native Minecraft button and exists
        // only on the home screen.
        addNativeButton("STORAGE", left, bottom, 112, 20,
            b -> VBVNav.open(new VBVStorageScreen(this)));
    }

    private void addNativeButton(String label, int x, int y, int w, int h, Button.OnPress action) {
        addRenderableWidget(Button.builder(Component.literal(label), action)
            .bounds(x, y, w, h).build());
    }
}
