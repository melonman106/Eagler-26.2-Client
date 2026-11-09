package com.melonman106.vbvclient;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Configuration screen for the native VBV first-person animation layer.
 *
 * <p>Besides the on/off switch it lets the player nudge where held items sit:
 * X (sideways), Y (up/down) and Z (toward/away from the camera) in hundredths
 * of a block. Positive Z moves the item closer to the camera, positive Y moves
 * it up. Changes apply immediately and are saved.</p>
 */
public final class VBVFirstPersonAnimationsConfigScreen extends Screen {
    private final Screen parent;

    public VBVFirstPersonAnimationsConfigScreen(Screen parent) {
        super(Component.literal("First-Person Animations Config"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int rowH = Math.max(22, Math.min(28, (height - 100) / 5));
        int y = Math.max(34, height / 2 - rowH * 3);

        Button toggle = Button.builder(stateLabel(), button -> {
            VBVFirstPersonAnimations.toggle();
            VBVConfigStore.saveAll();
            button.setMessage(stateLabel());
        }).bounds(width / 2 - 130, y, 260, 20).build();
        addRenderableWidget(toggle);
        y += rowH;

        addAxisRow(y, "Side (X)", 0); y += rowH;
        addAxisRow(y, "Height (Y)", 1); y += rowH;
        addAxisRow(y, "Depth (Z)", 2); y += rowH;

        addRenderableWidget(Button.builder(Component.literal("Reset hand position"), button -> {
            VBVFirstPersonAnimations.resetOffsets();
            VBVConfigStore.saveAll();
            VBVNav.open(new VBVFirstPersonAnimationsConfigScreen(parent));
        }).bounds(width / 2 - 130, y, 260, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Done"), button -> VBVNav.open(parent))
            .bounds(width / 2 - 50, height - 28, 100, 20).build());
    }

    private void addAxisRow(int y, String label, int axis) {
        int cx = width / 2;
        Button value = Button.builder(axisLabel(label, axis), button -> { })
            .bounds(cx - 70, y, 140, 20).build();
        value.active = false;
        addRenderableWidget(value);
        addStep(cx - 130, y, "-5", axis, -5, label, value);
        addStep(cx - 100, y, "-1", axis, -1, label, value);
        addStep(cx + 70, y, "+1", axis, 1, label, value);
        addStep(cx + 100, y, "+5", axis, 5, label, value);
    }

    private void addStep(int x, int y, String text, int axis, int delta, String label, Button value) {
        addRenderableWidget(Button.builder(Component.literal(text), button -> {
            int px = VBVFirstPersonAnimations.getOffsetX();
            int py = VBVFirstPersonAnimations.getOffsetY();
            int pz = VBVFirstPersonAnimations.getOffsetZ();
            if (axis == 0) px += delta; else if (axis == 1) py += delta; else pz += delta;
            VBVFirstPersonAnimations.setOffsets(px, py, pz);
            VBVConfigStore.saveAll();
            value.setMessage(axisLabel(label, axis));
        }).bounds(x, y, 30, 20).build());
    }

    private static Component axisLabel(String label, int axis) {
        int v = axis == 0 ? VBVFirstPersonAnimations.getOffsetX()
            : axis == 1 ? VBVFirstPersonAnimations.getOffsetY() : VBVFirstPersonAnimations.getOffsetZ();
        return Component.literal(label + ": " + (v > 0 ? "+" : "") + v);
    }

    private static Component stateLabel() {
        return Component.literal("Animations: " + (VBVFirstPersonAnimations.isEnabled() ? "ON" : "OFF"));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xFF151515);
        String heading = title.getString();
        graphics.text(font, heading, width / 2 - font.width(heading) / 2, 12, 0xFFFFFFFF, true);
        String note = "Units are 1/100 block. Depth + = closer to you, Height + = higher.";
        graphics.text(font, note, width / 2 - font.width(note) / 2, height - 44, 0xFFAAAAAA, true);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }
}
