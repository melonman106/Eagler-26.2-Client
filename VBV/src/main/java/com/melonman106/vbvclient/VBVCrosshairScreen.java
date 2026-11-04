package com.melonman106.vbvclient;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Pixel editor and appearance controls for the custom crosshair. */
public final class VBVCrosshairScreen extends Screen {
    private final Screen parent;

    public VBVCrosshairScreen(Screen parent) {
        super(Component.literal("Custom Crosshair"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int cell = 18;
        int left = width / 2 - 135;
        int top = 55;
        for (int y = 0; y < 15; y++) {
            for (int x = 0; x < 15; x++) {
                final int index = y * 15 + x;
                addRenderableWidget(Button.builder(Component.literal(VBVCrosshairConfig.pixels[index] ? "■" : "·"), button -> {
                    VBVCrosshairConfig.pixels[index] = !VBVCrosshairConfig.pixels[index];
                    VBVConfigStore.saveAll();
                    button.setMessage(Component.literal(VBVCrosshairConfig.pixels[index] ? "■" : "·"));
                }).bounds(left + x * cell, top + y * cell, 17, 17).build());
            }
        }
        int controlsY = top + 15 * cell + 8;
        addRenderableWidget(Button.builder(Component.literal("Enable: " + (VBVCrosshairConfig.enabled ? "ON" : "OFF")), button -> {
            VBVCrosshairConfig.enabled = !VBVCrosshairConfig.enabled;
            VBVConfigStore.saveAll();
            button.setMessage(Component.literal("Enable: " + (VBVCrosshairConfig.enabled ? "ON" : "OFF")));
        }).bounds(width / 2 - 100, controlsY, 200, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Scale: " + VBVCrosshairConfig.size + "x"), button -> {
            VBVCrosshairConfig.cycleSize();
            VBVConfigStore.saveAll();
            button.setMessage(Component.literal("Scale: " + VBVCrosshairConfig.size + "x"));
        }).bounds(width / 2 - 100, controlsY + 26, 95, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Color: " + colorLabel()), button -> {
            VBVCrosshairConfig.cycleColor();
            VBVConfigStore.saveAll();
            button.setMessage(Component.literal("Color: " + colorLabel()));
        }).bounds(width / 2 + 5, controlsY + 26, 95, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Default"), button -> {
            VBVCrosshairConfig.defaultCrosshair();
            VBVConfigStore.saveAll();
            VBVNav.open(new VBVCrosshairScreen(parent));
        }).bounds(width / 2 - 100, controlsY + 52, 95, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Clear"), button -> {
            VBVCrosshairConfig.clear();
            VBVConfigStore.saveAll();
            VBVNav.open(new VBVCrosshairScreen(parent));
        }).bounds(width / 2 + 5, controlsY + 52, 95, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), button -> VBVNav.open(parent))
            .bounds(width / 2 - 100, height - 30, 200, 20).build());
    }

    private static String colorLabel() {
        return switch (VBVCrosshairConfig.color) {
            case 0xFF55FF55 -> "Green";
            case 0xFFFF5555 -> "Red";
            case 0xFF55FFFF -> "Cyan";
            case 0xFFFFFF55 -> "Yellow";
            default -> "White";
        };
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xFF101010);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        String title = "CUSTOM CROSSHAIR";
        graphics.text(font, title, width / 2 - font.width(title) / 2, 25, 0xFFFFFFFF, true);
        graphics.text(font, "Edit the 15×15 pattern, scale and color.", width / 2 - 112, 42, 0xFFAAAAAA, false);
    }

    @Override
    public void onClose() {
        VBVConfigStore.saveAll();
        VBVNav.open(parent);
    }
}
