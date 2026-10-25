package com.melonman106.vbvclient;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Native Eagler configuration screen for the Shine visual-effects port. */
public final class ShineConfigScreen extends Screen {
    private final Screen parent;
    private int nextY = 40;

    public ShineConfigScreen(Screen parent) {
        super(Component.literal("Shine Config"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        option(() -> "Shine: " + onOff(ShineConfig.enabled()), ShineConfig::toggleEnabled);
        option(() -> "Selective Bloom: " + onOff(ShineConfig.bloom()), ShineConfig::toggleBloom);
        option(() -> "Colored Lighting: " + onOff(ShineConfig.coloredLighting()), ShineConfig::toggleColoredLighting);
        option(() -> "Rim Lighting: " + onOff(ShineConfig.rimLighting()), ShineConfig::toggleRimLighting);
        option(() -> "Disabled Shading: " + onOff(ShineConfig.disabledShading()), ShineConfig::toggleDisabledShading);
        option(() -> "Bloom Strength: " + String.format(java.util.Locale.ROOT, "%.2f", ShineConfig.bloomStrength()), ShineConfig::cycleBloomStrength);
        option(() -> "Rim Strength: " + String.format(java.util.Locale.ROOT, "%.2f", ShineConfig.rimStrength()), ShineConfig::cycleRimStrength);
        option(() -> "Light Radius: " + (int) ShineConfig.lightRadius(), ShineConfig::cycleLightRadius);
        option(() -> "Quality: " + ShineConfig.qualityName(), ShineConfig::cycleQuality);

        addRenderableWidget(Button.builder(Component.literal("All Features"), b -> {
            ShineConfig.applyPreset("all");
            rebuildOptions();
        }).bounds(width / 2 - 130, nextY, 125, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Bloom Only"), b -> {
            ShineConfig.applyPreset("bloom");
            rebuildOptions();
        }).bounds(width / 2 + 5, nextY, 125, 20).build());
        nextY += 27;

        addRenderableWidget(Button.builder(Component.literal("Performance"), b -> {
            ShineConfig.applyPreset("performance");
            rebuildOptions();
        }).bounds(width / 2 - 130, nextY, 260, 20).build());
        nextY += 27;

        addRenderableWidget(Button.builder(Component.literal("Done"), b -> VBVNav.open(parent))
            .bounds(width / 2 - 50, height - 28, 100, 20).build());
    }

    private void option(java.util.function.Supplier<String> text, Runnable action) {
        addRenderableWidget(Button.builder(Component.literal(text.get()), b -> {
            action.run();
            b.setMessage(Component.literal(text.get()));
        }).bounds(width / 2 - 130, nextY, 260, 20).build());
        nextY += 23;
    }

    private void rebuildOptions() {
        clearWidgets();
        nextY = 40;
        init();
    }

    private static String onOff(boolean value) {
        return value ? "ON" : "OFF";
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xFF101216);
        String heading = "Shine — Visual Effects";
        graphics.text(font, heading, width / 2 - font.width(heading) / 2, 10, 0xFFFFFFFF, true);
        String note = "Native Eagler/WebGL port • changes apply immediately";
        graphics.text(font, note, width / 2 - font.width(note) / 2, 25, 0xFFB8C0CC, false);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }
}
