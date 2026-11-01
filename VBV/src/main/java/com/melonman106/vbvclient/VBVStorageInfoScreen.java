package com.melonman106.vbvclient;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Simple native Minecraft information page used by the storage tabs. */
public final class VBVStorageInfoScreen extends Screen {
    private final Screen parent;
    private final String message;

    public VBVStorageInfoScreen(Screen parent, String title, String message) {
        super(Component.literal(title));
        this.parent = parent;
        this.message = message;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> VBVNav.open(parent))
            .bounds(width / 2 - 100, height - 34, 200, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xFF101010);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        String title = getTitle().getString().toUpperCase();
        graphics.text(font, title, width / 2 - font.width(title) / 2, 34, 0xFFFFFFFF, true);
        graphics.text(font, message, width / 2 - font.width(message) / 2, 78, 0xFFB0B0B0, false);
    }

    @Override
    public void onClose() {
        VBVNav.open(parent);
    }
}
