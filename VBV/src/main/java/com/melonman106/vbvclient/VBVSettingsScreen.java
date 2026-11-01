package com.melonman106.vbvclient;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Clean VBV options hub that routes settings back to Minecraft's real menus. */
public final class VBVSettingsScreen extends Screen {
    private final Screen parent;
    private static final String[] TABS = {
        "Video", "Language", "Controls", "Audio", "Accessibility", "Other"
    };
    private String activeTab = "Video";

    public VBVSettingsScreen(Screen parent) {
        super(Component.literal("Options"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        rebuild();
    }

    private void rebuild() {
        clearWidgets();

        int margin = Math.max(18, width / 40);
        int gap = 5;
        int available = Math.max(240, width - margin * 2 - gap * (TABS.length - 1));
        int tabWidth = available / TABS.length;
        int x = margin;
        int tabY = 58;

        for (String tab : TABS) {
            final String selected = tab;
            int w = tabWidth;
            addRenderableWidget(Button.builder(
                Component.literal((activeTab.equals(tab) ? "■ " : "") + tab),
                b -> { activeTab = selected; rebuild(); }
            ).bounds(x, tabY, w, 24).build());
            x += tabWidth + gap;
        }

        int panelW = Math.min(620, width - margin * 2);
        int panelX = width / 2 - panelW / 2;
        int panelY = 102;
        int buttonW = Math.min(360, panelW - 40);
        int buttonX = width / 2 - buttonW / 2;

        String heading;
        String description;
        String action;
        boolean opensGameMenu = true;

        switch (activeTab) {
            case "Video" -> {
                heading = "VIDEO SETTINGS";
                description = "Graphics, render distance, particles, clouds and other visual options.";
                action = "OPEN VIDEO / OPTIONS";
            }
            case "Controls" -> {
                heading = "CONTROLS";
                description = "Key bindings, mouse settings and controller/input options.";
                action = "OPEN CONTROLS";
            }
            case "Audio" -> {
                heading = "AUDIO SETTINGS";
                description = "Music, sound effects and individual audio categories.";
                action = "OPEN SOUND SETTINGS";
            }
            case "Accessibility" -> {
                heading = "ACCESSIBILITY";
                description = "Accessibility, narration, text and visual assistance options.";
                action = "OPEN ACCESSIBILITY";
            }
            case "Other" -> {
                heading = "MINECRAFT OPTIONS";
                description = "Open Minecraft's complete options menu for settings not provided by VBV.";
                action = "OPEN MINECRAFT OPTIONS";
            }
            default -> {
                heading = "LANGUAGE";
                description = "Language selection is intentionally handled by Minecraft's in-game language menu.";
                action = "OPEN MINECRAFT OPTIONS";
            }
        }

        addRenderableWidget(Button.builder(Component.literal(action), b -> {
            if (opensGameMenu) VBVNav.openOptions(this);
        }).bounds(buttonX, panelY + 76, buttonW, 26).build());

        addRenderableWidget(Button.builder(Component.literal("Back"), b -> VBVNav.open(parent))
            .bounds(width / 2 - 100, height - 38, 200, 22).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        graphics.fill(0, 0, width, height, 0x70000000);
        graphics.fill(18, 18, width - 18, 46, 0xB0101010);
        graphics.text(font, "VBV OPTIONS", 30, 28, 0xFFFFFFFF, true);
        graphics.fill(18, 47, width - 18, 48, 0x70FFFFFF);

        int panelW = Math.min(620, width - Math.max(18, width / 40) * 2);
        int panelX = width / 2 - panelW / 2;
        int panelY = 92;
        graphics.fill(panelX, panelY, panelX + panelW, height - 52, 0xB0181818);
        graphics.fill(panelX + 1, panelY + 1, panelX + panelW - 1, panelY + 2, 0x40FFFFFF);

        String heading = activeTab.toUpperCase();
        String description = switch (activeTab) {
            case "Video" -> "Graphics, render distance, particles, clouds and other visual options.";
            case "Controls" -> "Key bindings, mouse settings and controller/input options.";
            case "Audio" -> "Music, sound effects and individual audio categories.";
            case "Accessibility" -> "Accessibility, narration, text and visual assistance options.";
            case "Other" -> "Open Minecraft's complete options menu for settings not provided by VBV.";
            default -> "Language selection is handled by Minecraft's in-game language menu.";
        };
        graphics.text(font, heading, panelX + 22, panelY + 20, 0xFFFFFFFF, true);
        graphics.text(font, description, panelX + 22, panelY + 46, 0xFFD0D0D0, false);
    }

    @Override
    public void onClose() {
        VBVNav.open(parent);
    }
}
