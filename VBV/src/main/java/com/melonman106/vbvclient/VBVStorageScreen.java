package com.melonman106.vbvclient;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Native entry point for the browser-backed Eaglercraft Storage Manager. */
public final class VBVStorageScreen extends Screen {
    private final Screen parent;

    public VBVStorageScreen(Screen parent) {
        super(Component.literal("Eaglercraft Storage"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int w = Math.min(320, Math.max(220, width - 32));
        int x = width / 2 - w / 2;
        addRenderableWidget(Button.builder(Component.literal("Open Storage Manager"), b -> VBVStorage.openManager())
            .bounds(x, height / 2 - 24, w, 24).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> VBVNav.open(parent))
            .bounds(x, height / 2 + 8, w, 24).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xFF151515);
        String heading = "EAGLERCRAFT STORAGE";
        graphics.text(font, heading, width / 2 - font.width(heading) / 2, 34, 0xFFFFFFFF, true);
        String note = "Choose or reconnect a computer folder to manage worlds, maps, packs and profiles.";
        graphics.text(font, note, Math.max(8, width / 2 - font.width(note) / 2), 58, 0xFFBBBBBB, true);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }
}
