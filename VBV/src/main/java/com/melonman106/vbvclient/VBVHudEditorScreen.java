package com.melonman106.vbvclient;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Drag-and-drop editor for the VBV minimap only. */
public final class VBVHudEditorScreen extends Screen {
    private final Screen parent;
    private boolean dragging;
    private boolean editJade;

    public VBVHudEditorScreen(Screen parent) { this(parent, false); }

    public VBVHudEditorScreen(Screen parent, boolean editJade) {
        super(Component.literal("HUD Position Editor"));
        this.parent = parent;
        this.editJade = editJade;
    }

    @Override protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.literal(editLabel()), b -> {
            editJade = !editJade;
            b.setMessage(Component.literal(editLabel()));
        }).bounds(8, 8, 160, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Reset position"), b -> {
            if (editJade) { VBVJadeHud.offsetX = 0; VBVJadeHud.offsetY = 0; }
            else { VBVMiniMap.offsetX = 0; VBVMiniMap.offsetY = 0; }
        }).bounds(width / 2 - 105, height - 30, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> VBVNav.open(parent))
            .bounds(width / 2 + 5, height - 30, 100, 20).build());
    }

    private String editLabel() { return "Edit: " + (editJade ? "Jade overlay" : "Minimap"); }
    private int mapX() { return editJade ? width / 2 - 90 + VBVJadeHud.offsetX : width - 64 - 8 + VBVMiniMap.offsetX; }
    private int mapY() { return 8 + (editJade ? VBVJadeHud.offsetY : VBVMiniMap.offsetY); }

    @Override public boolean mouseClicked(MouseButtonEvent click, boolean doubleClick) {
        if (super.mouseClicked(click, doubleClick)) return true;
        int w = editJade ? 180 : 64;
        int h = editJade ? 46 : 64;
        if (click.x() >= mapX() - 4 && click.x() <= mapX() + w
            && click.y() >= mapY() - 4 && click.y() <= mapY() + h) {
            dragging = true;
            return true;
        }
        return false;
    }

    @Override public boolean mouseReleased(MouseButtonEvent click) {
        dragging = false;
        return super.mouseReleased(click);
    }

    @Override public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (!dragging) return super.mouseDragged(event, dragX, dragY);
        if (editJade) {
            VBVJadeHud.offsetX = Math.max(-width / 2 + 90, Math.min(width / 2 - 90, VBVJadeHud.offsetX + (int)Math.round(dragX)));
            VBVJadeHud.offsetY = Math.max(-8, Math.min(height - 50, VBVJadeHud.offsetY + (int)Math.round(dragY)));
        } else {
            VBVMiniMap.offsetX = Math.max(-width + 104, Math.min(width - 104, VBVMiniMap.offsetX + (int)Math.round(dragX)));
            VBVMiniMap.offsetY = Math.max(-height + 40, Math.min(height - 120, VBVMiniMap.offsetY + (int)Math.round(dragY)));
        }
        return true;
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xE0101010);
        graphics.text(font, "HUD Position Editor", width / 2 - 65, 8, 0xFFFFFFFF, true);
        graphics.text(font, "Choose Minimap or Jade above, then drag the preview.", 8, 32, 0xFFCCCCCC, true);
        int x = mapX(), y = mapY();
        if (editJade) {
            graphics.fill(x - 3, y - 3, x + 183, y + 45, 0xCC111111);
            graphics.fill(x, y, x + 180, y + 42, 0xE0101010);
            graphics.text(font, "Stone", x + 8, y + 5, 0xFFFFFFFF, true);
            graphics.text(font, "XYZ: 120, 64, -32", x + 8, y + 18, 0xFFCCCCCC, false);
        } else {
            graphics.fill(x - 3, y - 3, x + 67, y + 67, 0xCC111111);
            graphics.fill(x - 1, y - 1, x + 65, y + 65, 0xFFB0B0B0);
            for (int py = 0; py < 64; py += 4) for (int px = 0; px < 64; px += 4) {
                int r = 45 + (px * 3 + py) % 70, g = 75 + (py * 2 + px) % 100, b = 35 + (px + py * 3) % 50;
                graphics.fill(x + px, y + py, x + px + 4, y + py + 4, 0xFF000000 | (r << 16) | (g << 8) | b);
            }
            graphics.fill(x + 29, y + 29, x + 36, y + 36, 0xFFFFFFFF);
            graphics.fill(x + 31, y + 31, x + 34, y + 34, 0xFF3B83E8);
            graphics.text(font, "N", x + 29, y + 3, 0xFFFFFFFF, true);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }
}
