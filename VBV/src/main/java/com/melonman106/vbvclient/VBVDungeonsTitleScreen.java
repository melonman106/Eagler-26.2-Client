package com.melonman106.vbvclient;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

/** Minecraft Dungeons II-inspired VBV title screen using Minecraft's live panorama. */
public final class VBVDungeonsTitleScreen extends TitleScreen {
    public VBVDungeonsTitleScreen() {
        super();
    }

    @Override
    protected void init() {
        super.init();
        clearWidgets();

        int buttonWidth = Math.min(220, Math.max(180, width / 5));
        int x = 18;
        int buttonHeight = 24;
        int gap = 6;
        int y = height - (buttonHeight * 3 + gap * 2) - 24;

        addRenderableWidget(Button.builder(Component.literal("Play"), b ->
            VBVNav.openMultiplayer(this))
            .bounds(x, y, buttonWidth, buttonHeight).build());
        addRenderableWidget(Button.builder(Component.literal("Singleplayer"), b ->
            VBVNav.openSingleplayer(this))
            .bounds(x, y + buttonHeight + gap, buttonWidth, buttonHeight).build());
        addRenderableWidget(Button.builder(Component.literal("Mods & Options"), b ->
            VBVNav.open(new VBVDungeonsExtrasScreen(this)))
            .bounds(x, y + (buttonHeight + gap) * 2, buttonWidth, buttonHeight).build());
    }

    /*
     * TitleScreen's panorama renderer already draws Minecraft's registered
     * panorama_overlay.png after the six panorama faces. Keep that native
     * overlay in the background stratum, before Screen renders the widgets.
     * Do not blit a second copy after super.extractRenderState(): that would
     * cover the buttons and was the cause of the invisible controls.
     */
    @Override
    protected void extractPanorama(GuiGraphicsExtractor graphics, float partialTick) {
        super.extractPanorama(graphics, partialTick);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        String title = "MINECRAFT DUNGEONS II";
        graphics.text(font, title, width / 2 - font.width(title) / 2, 36, 0xFFFFFFFF, true);
    }
}
