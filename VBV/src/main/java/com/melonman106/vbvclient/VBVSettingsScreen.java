package com.melonman106.vbvclient;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Tabbed VBV settings screen. */
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
        int margin = 18;
        int gap = 4;
        int available = Math.max(120, width - margin * 2 - gap * (TABS.length - 1));
        int tabWidth = available / TABS.length;
        int x = margin;
        int tabY = 54;
        for (String tab : TABS) {
            final String selected = tab;
            int w = tabWidth;
            addRenderableWidget(Button.builder(
                Component.literal((activeTab.equals(tab) ? "> " : "") + tab),
                b -> { activeTab = selected; rebuild(); }
            ).bounds(x, tabY, w, 22).build());
            x += tabWidth + gap;
        }

        int contentX = Math.max(24, width / 2 - 190);
        int contentW = Math.min(380, width - contentX * 2);
        int y = 100;

        if ("Video".equals(activeTab)) {
            addInfo("Video and rendering options", contentX, y, contentW); y += 30;
            addToggle("Animation Arms", VBVFirstPersonAnimations.isEnabled(),
                contentX, y, contentW, VBVFirstPersonAnimations::setEnabled);
        } else if ("Language".equals(activeTab)) {
            addInfo("Language selection is managed by the game language menu.", contentX, y, contentW);
        } else if ("Controls".equals(activeTab)) {
            addInfo("Keyboard, mouse and key bindings are managed by the game controls menu.", contentX, y, contentW);
        } else if ("Audio".equals(activeTab)) {
            addInfo("Audio levels are managed by the game sound settings.", contentX, y, contentW);
        } else if ("Accessibility".equals(activeTab)) {
            addInfo("Accessibility options", contentX, y, contentW);
        } else {
            addInfo("Additional VBV client settings", contentX, y, contentW);
        }

        addRenderableWidget(Button.builder(Component.literal("Back"), b -> VBVNav.open(parent))
            .bounds(width / 2 - 100, height - 38, 200, 22).build());
    }

    private void addInfo(String text, int x, int y, int w) {
        addRenderableWidget(Button.builder(Component.literal(text), b -> {})
            .bounds(x, y, w, 24).build());
    }

    private void addToggle(String label, boolean value, int x, int y, int w,
                           java.util.function.Consumer<Boolean> setter) {
        addRenderableWidget(Button.builder(
            Component.literal(label + ": " + (value ? "ON" : "OFF")),
            b -> { setter.accept(!value); rebuild(); }
        ).bounds(x, y, w, 24).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.fill(0, 0, width, height, 0x50000000);
        graphics.text(font, "OPTIONS", 20, 24, 0xFFFFFFFF, true);
        graphics.fill(18, 43, width - 18, 44, 0x80FFFFFF);
    }

    @Override
    public void onClose() {
        VBVNav.open(parent);
    }
}
