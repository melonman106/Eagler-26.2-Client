package com.melonman106.vbvclient;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** VBV settings launcher. Each tab opens Minecraft's real configuration UI. */
public final class VBVSettingsScreen extends Screen {
    private final Screen parent;
    private static final String[] TABS = {"Video", "Language", "Controls", "Audio", "Accessibility", "Other"};
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
        for (String tab : TABS) {
            final String selected = tab;
            addRenderableWidget(Button.builder(Component.literal((activeTab.equals(tab) ? "■ " : "") + tab),
                b -> { activeTab = selected; rebuild(); })
                .bounds(x, 58, tabWidth, 24).build());
            x += tabWidth + gap;
        }

        int buttonW = Math.min(360, width - 40);
        int buttonX = width / 2 - buttonW / 2;
        addRenderableWidget(Button.builder(Component.literal("OPEN " + activeTab.toUpperCase() + " SETTINGS"),
            b -> VBVNav.openOptions(this)).bounds(buttonX, 132, buttonW, 26).build());
        addRenderableWidget(Button.builder(Component.literal("VBV CLIENT SETTINGS"),
            b -> VBVNav.open(new VBVModsScreen(this))).bounds(buttonX, 164, buttonW, 26).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> VBVNav.open(parent))
            .bounds(width / 2 - 100, height - 38, 200, 22).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xFF101010);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.text(font, "OPTIONS", 28, 28, 0xFFFFFFFF, true);
        graphics.text(font, "Minecraft's real settings are opened from the selected category.", 28, 40, 0xFFAAAAAA, false);
        String heading = activeTab.toUpperCase() + " SETTINGS";
        graphics.text(font, heading, width / 2 - font.width(heading) / 2, 105, 0xFFFFFFFF, true);
    }

    @Override
    public void onClose() { VBVNav.open(parent); }
}
