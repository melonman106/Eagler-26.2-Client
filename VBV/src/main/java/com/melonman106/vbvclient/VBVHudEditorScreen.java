package com.melonman106.vbvclient;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Drag-and-drop positioning editor for the native VBV HUD widgets. */
public final class VBVHudEditorScreen extends Screen {
    private final Screen parent;
    private int selected = 0; // 0 Simple HUD, 1 Armor HUD, 2 BetterF3, 3 AppleSkin text
    private boolean dragging;

    public VBVHudEditorScreen(Screen parent) {
        super(Component.literal("HUD Editor"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.literal("Widget: Simple HUD"), b -> {
            selected = (selected + 1) % 5;
            updateWidgetButton(b);
        }).bounds(width / 2 - 150, height - 30, 150, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Reset selected"), b -> resetSelected())
            .bounds(width / 2 + 4, height - 30, 105, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> VBVNav.open(parent))
            .bounds(width / 2 + 115, height - 30, 65, 20).build());
    }

    private void updateWidgetButton(Button button) {
        String[] names = {"Simple HUD", "Armor HUD", "BetterF3", "AppleSkin text", "Minimap"};
        button.setMessage(Component.literal("Widget: " + names[selected]));
    }

    private void resetSelected() {
        if (selected == 0) {
            VBVSimpleHudConfig.hudXOffset = 0;
            VBVSimpleHudConfig.hudYOffset = 0;
        } else if (selected == 1) {
            VBVArmorHudConfig.offsetX = 0;
            VBVArmorHudConfig.offsetY = 0;
        } else if (selected == 2) {
            VBVExtraFeatures.debugHudX = 6;
            VBVExtraFeatures.debugHudY = 6;
        } else if (selected == 3) {
            VBVAppleSkin.x = 4;
            VBVAppleSkin.y = 4;
        } else {
            VBVMiniMap.offsetX = 0;
            VBVMiniMap.offsetY = 0;
        }
    }

    private String selectedName() {
        return switch (selected) {
            case 1 -> "Armor HUD";
            case 2 -> "BetterF3";
            case 3 -> "AppleSkin text";
            case 4 -> "Minimap";
            default -> "Simple HUD";
        };
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubleClick) {
        if (super.mouseClicked(click, doubleClick)) return true;
        double mx = click.x();
        double my = click.y();
        if (hitSelected(mx, my)) {
            dragging = true;
            return true;
        }
        return false;
    }

    private boolean hitSelected(double mx, double my) {
        int x, y, w, h;
        if (selected == 0) {
            x = 3 + VBVSimpleHudConfig.hudXOffset;
            y = 3 + VBVSimpleHudConfig.hudYOffset;
            w = 205; h = 120;
        } else if (selected == 1) {
            x = width / 2 - 145 + VBVArmorHudConfig.offsetX;
            y = height - 82 - VBVArmorHudConfig.offsetY;
            w = 100; h = 32;
        } else if (selected == 2) {
            x = VBVExtraFeatures.debugHudX;
            y = VBVExtraFeatures.debugHudY;
            w = 230; h = 70;
        } else if (selected == 3) {
            x = VBVAppleSkin.x;
            y = VBVAppleSkin.y;
            w = 210; h = 18;
        } else {
            x = width - 104 + VBVMiniMap.offsetX;
            y = 8 + VBVMiniMap.offsetY;
            w = 96; h = 96;
        }
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        dragging = false;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (!dragging) return super.mouseDragged(event, dragX, dragY);
        if (selected == 0) {
            VBVSimpleHudConfig.hudXOffset += (int) Math.round(dragX);
            VBVSimpleHudConfig.hudYOffset += (int) Math.round(dragY);
        } else if (selected == 1) {
            boolean negativeX = (VBVArmorHudConfig.anchor == VBVArmorHudConfig.ANCHOR_HOTBAR && VBVArmorHudConfig.side == VBVArmorHudConfig.SIDE_LEFT)
                || (VBVArmorHudConfig.anchor != VBVArmorHudConfig.ANCHOR_HOTBAR && VBVArmorHudConfig.side == VBVArmorHudConfig.SIDE_RIGHT);
            VBVArmorHudConfig.offsetX += (int) Math.round(negativeX ? -dragX : dragX);
            VBVArmorHudConfig.offsetY += (int) Math.round(
                VBVArmorHudConfig.anchor == VBVArmorHudConfig.ANCHOR_BOTTOM
                    || VBVArmorHudConfig.anchor == VBVArmorHudConfig.ANCHOR_HOTBAR ? -dragY : dragY);
        } else if (selected == 2) {
            VBVExtraFeatures.debugHudX = Math.max(0, VBVExtraFeatures.debugHudX + (int) Math.round(dragX));
            VBVExtraFeatures.debugHudY = Math.max(0, VBVExtraFeatures.debugHudY + (int) Math.round(dragY));
        } else if (selected == 3) {
            VBVAppleSkin.x = Math.max(0, VBVAppleSkin.x + (int) Math.round(dragX));
            VBVAppleSkin.y = Math.max(0, VBVAppleSkin.y + (int) Math.round(dragY));
        } else {
            VBVMiniMap.offsetX += (int) Math.round(dragX);
            VBVMiniMap.offsetY += (int) Math.round(dragY);
        }
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xE0101010);
        graphics.text(font, "HUD Editor — drag the selected widget to move it", width / 2 - 150, 8, 0xFFFFFFFF, true);
        graphics.text(font, "Selected: " + selectedName(), width / 2 - 150, 22, 0xFFAAAAAA, true);

        int sx = 3 + VBVSimpleHudConfig.hudXOffset;
        int sy = 3 + VBVSimpleHudConfig.hudYOffset;
        int sw = 205, sh = 58;
        graphics.fill(sx - 2, sy - 2, sx + sw, sy + sh, selected == 0 ? 0xAA3B6795 : 0x66333333);
        graphics.outline(sx - 2, sy - 2, sw + 2, sh + 2, selected == 0 ? 0xFF80B8FF : 0xFF777777);
        graphics.text(font, "FPS: 144", sx + 4, sy + 4, 0xFFFFFFFF, true);
        graphics.text(font, "XYZ: 120 64 -42", sx + 4, sy + 16, 0xFFFFFFFF, true);
        graphics.text(font, "Speed: 4.20 m/s", sx + 4, sy + 28, 0xFFFFFFFF, true);

        int ax = width / 2 - 145 + VBVArmorHudConfig.offsetX;
        int ay = height - 82 - VBVArmorHudConfig.offsetY;
        graphics.fill(ax - 2, ay - 2, ax + 94, ay + 27, selected == 1 ? 0xAA3B6795 : 0x66333333);
        for (int i = 0; i < 4; i++) {
            int x = ax + i * 22;
            graphics.fill(x, ay, x + 20, ay + 20, 0xFF171717);
            graphics.fill(x + 1, ay + 1, x + 19, ay + 19, 0xFFB0B0B0);
            graphics.fill(x + 3, ay + 3, x + 17, ay + 17, 0xFF303030);
        }
        graphics.text(font, "Armor HUD", ax + 1, ay + 20, 0xFFFFFFFF, true);

        int bx = VBVExtraFeatures.debugHudX, by = VBVExtraFeatures.debugHudY;
        graphics.fill(bx - 2, by - 2, bx + 218, by + 48, selected == 2 ? 0xAA3B6795 : 0x66333333);
        graphics.text(font, "BetterF3", bx + 4, by + 3, 0xFFFFFFFF, true);
        graphics.text(font, "XYZ: 120.00 / 64.00 / -42.00", bx + 4, by + 15, 0xFF55FFFF, true);
        graphics.text(font, "Yaw: 90.0  Pitch: 0.0  FPS: 144", bx + 4, by + 27, 0xFFFFFFFF, true);

        int cx = VBVAppleSkin.x, cy = VBVAppleSkin.y;
        graphics.fill(cx - 2, cy - 2, cx + 205, cy + 15, selected == 3 ? 0xAA3B6795 : 0x66333333);
        graphics.text(font, "Food: 20/20  Saturation: 12.0", cx + 2, cy + 2, 0xFFFFFFFF, true);

        int mx = width - 104 + VBVMiniMap.offsetX, my = 8 + VBVMiniMap.offsetY;
        graphics.fill(mx - 3, my - 3, mx + 99, my + 99, selected == 4 ? 0xAA3B6795 : 0x66333333);
        for (int row = 0; row < 12; row++) for (int col = 0; col < 12; col++) {
            int tone = ((row * 7 + col * 11) % 4 == 0) ? 0xFF527C45 : ((row + col) % 3 == 0 ? 0xFF777777 : 0xFF4C7441);
            graphics.fill(mx + col * 8, my + row * 8, mx + col * 8 + 8, my + row * 8 + 8, tone);
        }
        graphics.fill(mx + 44, my + 44, mx + 52, my + 52, 0xFFFFFFFF);
        graphics.text(font, "N", mx + 46, my + 3, 0xFFFFFFFF, true);
        graphics.text(font, "The saturation tint itself stays attached to the hunger bar, like AppleSkin.", 8, height - 48, 0xFFAAAAAA, true);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }
}
