package com.melonman106.vbvclient;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.SelectWorldScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Minecraft Dungeons II-inspired VBV title screen using the native panorama. */
public final class VBVDungeonsTitleScreen extends TitleScreen {
    private static final Identifier OVERLAY = Identifier.parse("vbvclient:textures/panorama/panorama_overlay.png");

    public VBVDungeonsTitleScreen() {
        super();
    }

    @Override
    protected void init() {
        super.init();
        clearWidgets();

        int buttonWidth = Math.min(300, Math.max(220, width / 3));
        int x = width / 2 - buttonWidth / 2;
        int y = Math.max(110, height / 2 - 48);
        int h = 24;
        int gap = 5;

        addRenderableWidget(Button.builder(Component.literal("Play"), b ->
            VBVNav.open(new JoinMultiplayerScreen(this)))
            .bounds(x, y, buttonWidth, h).build());
        addRenderableWidget(Button.builder(Component.literal("Singleplayer"), b ->
            VBVNav.open(new SelectWorldScreen(this)))
            .bounds(x, y + h + gap, buttonWidth, h).build());
        addRenderableWidget(Button.builder(Component.literal("Mods & Options"), b ->
            VBVNav.open(new VBVDungeonsExtrasScreen(this)))
            .bounds(x, y + (h + gap) * 2, buttonWidth, h).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        try {
            graphics.blit(RenderPipelines.GUI_TEXTURED, OVERLAY, 0, 0, 0, 0,
                width, height, width, height, width, height);
        } catch (RuntimeException ignored) {
            // Optional overlay: keep the native live panorama when it is absent.
        }
        String title = "MINECRAFT DUNGEONS II";
        graphics.text(font, title, width / 2 - font.width(title) / 2, 36, 0xFFFFFFFF, true);
    }
}
