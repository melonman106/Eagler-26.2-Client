package com.melonman106.vbvclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Native Hero/profile editor. */
public final class VBVProfileScreen extends Screen {
    private final Screen parent;
    private EditBox nameBox;

    public VBVProfileScreen(Screen parent) {
        super(Component.literal("Hero Profile"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        String current = Minecraft.getInstance().getUser() != null ? Minecraft.getInstance().getUser().getName() : "Player";
        nameBox = new EditBox(font, width / 2 - 120, 92, 240, 20, Component.literal("Player name"));
        nameBox.setValue(current);
        addRenderableWidget(nameBox);
        addRenderableWidget(Button.builder(Component.literal("SAVE PROFILE"), b -> save())
            .bounds(width / 2 - 100, 126, 200, 20).build());
        addRenderableWidget(Button.builder(Component.literal("SKIN / CAPE SETTINGS"), b -> VBVNav.openOptions(this))
            .bounds(width / 2 - 100, 151, 200, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> VBVNav.open(parent))
            .bounds(width / 2 - 100, height - 34, 200, 20).build());
    }

    private void save() {
        String name = nameBox.getValue().trim();
        if (!name.isEmpty()) VBVProfile.saveName(name);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xFF101010);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        String title = "HERO PROFILE";
        graphics.text(font, title, width / 2 - font.width(title) / 2, 38, 0xFFFFFFFF, true);
        graphics.text(font, "Edit the player profile used by the VBV home and party UI.",
            width / 2 - 145, 62, 0xFFAAAAAA, false);
    }

    @Override
    public void onClose() { VBVNav.open(parent); }
}
