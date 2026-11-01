package com.melonman106.vbvclient;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Friends/party entry point for the future VBV party service. */
public final class VBVFriendsScreen extends Screen {
    private final Screen parent;
    public VBVFriendsScreen(Screen parent) {
        super(Component.literal("Party"));
        this.parent = parent;
    }
    @Override
    protected void init() {
        super.init();
        int w = Math.min(360, Math.max(240, width - 32));
        int x = width / 2 - w / 2;
        addRenderableWidget(Button.builder(Component.literal("Friends & Party"), b -> {})
            .bounds(x, height / 2 - 36, w, 24).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> VBVNav.open(parent))
            .bounds(x, height / 2 + 4, w, 24).build());
    }
    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x90000000);
        String heading = "PARTY";
        graphics.text(font, heading, width / 2 - font.width(heading) / 2, 28, 0xFFFFFFFF, true);
        String note = "Friends and party invites will appear here.";
        graphics.text(font, note, width / 2 - font.width(note) / 2, 56, 0xFFB8B8B8, true);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }
    @Override
    public void onClose() { VBVNav.open(parent); }
}
