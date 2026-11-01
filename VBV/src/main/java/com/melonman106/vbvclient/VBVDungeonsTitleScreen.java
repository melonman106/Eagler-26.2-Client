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
        int y = Math.max(70, height - 190);

        // These home-screen entries are intentionally visual-only for now.
        addTextOnlyButton("Play", x, y, buttonWidth, buttonHeight, b -> {});
        addTextOnlyButton("Friends", x, y + buttonHeight + gap, buttonWidth, buttonHeight, b -> {});
        addTextOnlyButton("Profile", x, y + (buttonHeight + gap) * 2, buttonWidth, buttonHeight, b -> {});
        addTextOnlyButton("Mods", x, y + (buttonHeight + gap) * 3, buttonWidth, buttonHeight, b -> {});

        // Bottom utility buttons use the normal Minecraft Button widget so they
        // match the native Eagler/Minecraft UI rather than being custom text.
        int utilityY = height - 42;
        addTextOnlyButton("Settings", x, utilityY, 100, 24, b ->
            VBVNav.open(new VBVSettingsScreen(this)));
        addTextOnlyButton("Storage", x + 106, utilityY, 100, 24, b ->
            VBVNav.open(new VBVStorageScreen(this)));
    }

    private void addTextOnlyButton(String label, int x, int y, int w, int h, Button.OnPress action) {
        Button button = Button.builder(Component.literal(label), action)
            .bounds(x, y, w, h).build();
        // Keep the native widget for keyboard/mouse focus and hit detection,
        // but draw only the label ourselves so no grey button plate appears.
        button.setAlpha(0.0F);
        addRenderableWidget(button);
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

        int buttonWidth = Math.min(220, Math.max(180, width / 5));
        int x = 18;
        int buttonHeight = 24;
        int gap = 6;
        int y = Math.max(70, height - 190);
        drawMenuLabel(graphics, "PLAY", x, y, buttonWidth, buttonHeight, mouseX, mouseY);
        drawMenuLabel(graphics, "FRIENDS", x, y + buttonHeight + gap, buttonWidth, buttonHeight, mouseX, mouseY);
        drawMenuLabel(graphics, "PROFILE", x, y + (buttonHeight + gap) * 2, buttonWidth, buttonHeight, mouseX, mouseY);
        drawMenuLabel(graphics, "MODS", x, y + (buttonHeight + gap) * 3, buttonWidth, buttonHeight, mouseX, mouseY);
        int utilityY = height - 42;
        drawMenuLabel(graphics, "SETTINGS", x, utilityY, 100, 24, mouseX, mouseY);
        drawMenuLabel(graphics, "STORAGE", x + 106, utilityY, 100, 24, mouseX, mouseY);
    }

    private void drawMenuLabel(GuiGraphicsExtractor graphics, String label, int x, int y,
            int w, int h, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        int color = hovered ? 0xFFFFFFFF : 0xFF808080;
        graphics.text(font, label, x + 2, y + (h - 8) / 2, color, true);
    }
}
