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

    public VBVHudEditorScreen(Screen parent) {
        super(Component.literal("Minimap Position Editor"));
        this.parent = parent;
    }

    @Override protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.literal("Reset position"), b -> {
            VBVMiniMap.offsetX = 0;
            VBVMiniMap.offsetY = 0;
        }).bounds(width / 2 - 105, height - 30, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> VBVNav.open(parent))
            .bounds(width / 2 + 5, height - 30, 100, 20).build());
    }

    private int mapX() { return width - 96 - 8 + VBVMiniMap.offsetX; }
    private int mapY() { return 8 + VBVMiniMap.offsetY; }

    @Override public boolean mouseClicked(MouseButtonEvent click, boolean doubleClick) {
        if (super.mouseClicked(click, doubleClick)) return true;
        if (click.x() >= mapX() - 4 && click.x() <= mapX() + 100
            && click.y() >= mapY() - 4 && click.y() <= mapY() + 100) {
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
        VBVMiniMap.offsetX = Math.max(-width + 104, Math.min(width - 104, VBVMiniMap.offsetX + (int)Math.round(dragX)));
        VBVMiniMap.offsetY = Math.max(-height + 40, Math.min(height - 120, VBVMiniMap.offsetY + (int)Math.round(dragY)));
        return true;
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xE0101010);
        graphics.text(font, "Minimap Position Editor", width / 2 - 80, 8, 0xFFFFFFFF, true);
        graphics.text(font, "Drag the minimap to move it. This editor only changes the minimap.", 8, 24, 0xFFCCCCCC, true);
        int x = mapX(), y = mapY();
        graphics.fill(x - 3, y - 3, x + 99, y + 99, 0xCC111111);
        graphics.fill(x - 1, y - 1, x + 97, y + 97, 0xFFB0B0B0);
        for (int py = 0; py < 96; py += 2) {
            for (int px = 0; px < 96; px += 2) {
                int r = 45 + (px * 3 + py) % 70;
                int g = 75 + (py * 2 + px) % 100;
                int b = 35 + (px + py * 3) % 50;
                graphics.fill(x + px, y + py, x + px + 2, y + py + 2, 0xFF000000 | (r << 16) | (g << 8) | b);
            }
        }
        graphics.fill(x + 45, y + 45, x + 52, y + 52, 0xFFFFFFFF);
        graphics.fill(x + 47, y + 47, x + 50, y + 50, 0xFF3B83E8);
        graphics.text(font, "N", x + 45, y + 3, 0xFFFFFFFF, true);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }
}
