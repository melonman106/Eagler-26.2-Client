package com.melonman106.vbvclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

/** VBV home screen with native Minecraft widgets and Dungeons-inspired layout. */
public final class VBVDungeonsTitleScreen extends TitleScreen {
    public VBVDungeonsTitleScreen() { super(); }

    @Override
    protected void init() {
        super.init();
        clearWidgets();

        int left = Math.max(24, width / 24);
        int mainWidth = 220;
        int smallWidth = 106;
        int heightMain = 20;
        int heightSmall = 20;
        int gap = 6;
        int y = Math.max(72, height - 178); // moved down slightly

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
        y += heightSmall + gap;
        addNativeButton("HERO", left, y, smallWidth, heightSmall,
            b -> VBVNav.open(new VBVProfileScreen(this)));

        // Native MC button in the bottom-right, never the injected web launcher.
        addNativeButton("STORAGE", width - 136, height - 30, 112, 20,
            b -> VBVNav.open(new VBVStorageScreen(this)));
    }

    private void addNativeButton(String label, int x, int y, int w, int h, Button.OnPress action) {
        addRenderableWidget(Button.builder(Component.literal(label), action).bounds(x, y, w, h).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        Minecraft mc = Minecraft.getInstance();
        String name = mc.getUser() != null ? mc.getUser().getName() : "Player";
        int x = width - Math.max(150, font.width(name) + 58);
        graphics.fill(x - 8, 10, width - 12, 42, 0x99000000);
        graphics.text(font, name, x + 34, 20, 0xFFFFFFFF, true);
        graphics.text(font, "PROFILE", x + 34, 31, 0xFFAAAAAA, false);
    }
}
