package com.melonman106.vbvclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Small Dungeons-style secondary menu for Mods and Options. */
public final class VBVDungeonsExtrasScreen extends Screen {
    private final Screen parent;

    public VBVDungeonsExtrasScreen(Screen parent) {
        super(Component.literal("Mods & Options"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int w = Math.min(300, Math.max(220, width / 3));
        int x = width / 2 - w / 2;
        int y = Math.max(80, height / 2 - 48);
        addRenderableWidget(Button.builder(Component.literal("Mods"), b ->
            VBVNav.open(new VBVModsScreen(this)))
            .bounds(x, y, w, 24).build());
        addRenderableWidget(Button.builder(Component.literal("Options"), b ->
            VBVNav.open(new OptionsScreen(this, Minecraft.getInstance().options)))
            .bounds(x, y + 29, w, 24).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> VBVNav.open(parent))
            .bounds(x, y + 58, w, 24).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.fill(0, 0, width, height, 0x50000000);
        String title = "MODS & OPTIONS";
        graphics.text(font, title, width / 2 - font.width(title) / 2, 36, 0xFFFFFFFF, true);
    }

    @Override
    public void onClose() {
        VBVNav.open(parent);
    }
}
