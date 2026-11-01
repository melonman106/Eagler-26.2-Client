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
        int mainWidth = Math.min(300, Math.max(230, width / 4));
        int smallWidth = Math.max(110, (mainWidth - 8) / 2);
        int heightMain = 34;
        int heightSmall = 26;
        int gap = 7;
        int y = Math.max(56, height - 190);

        addNativeButton("PLAY", left, y, mainWidth, heightMain,
            b -> VBVNav.openMultiplayer(this));
        y += heightMain + gap;
        addNativeButton("SINGLEPLAYER", left, y, smallWidth, heightSmall,
            b -> VBVNav.openSingleplayer(this));
        addNativeButton("PARTY", left + smallWidth + 8, y, smallWidth, heightSmall,
            b -> VBVNav.open(new VBVFriendsScreen(this)));
        y += heightSmall + gap;
        addNativeButton("MODS", left, y, smallWidth, heightSmall,
            b -> VBVNav.open(new VBVModsScreen(this)));
        addNativeButton("OPTIONS", left + smallWidth + 8, y, smallWidth, heightSmall,
            b -> VBVNav.openOptions(this));

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
