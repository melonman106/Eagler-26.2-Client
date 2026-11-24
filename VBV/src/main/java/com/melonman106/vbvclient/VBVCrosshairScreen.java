package com.melonman106.vbvclient;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Pixel editor and appearance controls for the custom crosshair. */
public final class VBVCrosshairScreen extends Screen {
    private static final int GRID = 15;
    private static final int PIXELS = GRID * GRID;

    private final Screen parent;
    private int cell;
    private int gridX;
    private int gridY;
    private int gridSize;
    private int previewX;
    private int previewY;
    private int previewCell;
    private boolean showPreview;
    private boolean painting;
    private boolean paintValue;
    private int lastCell = -1;
    private VBVUi.ThemedButton enableButton;
    private VBVUi.ThemedButton scaleButton;
    private VBVUi.ThemedButton colorButton;

    public VBVCrosshairScreen(Screen parent) {
        super(Component.literal("Custom Crosshair"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        // Leave room for the title, one control row and the Back button at every GUI size.
        int reserved = 34 + 8 + 18 + 8 + 22;
        cell = Math.max(7, Math.min(11, (height - reserved) / GRID));
        gridSize = GRID * cell;
        gridY = 34;

        previewCell = cell >= 9 ? 4 : 3;
        int previewSize = GRID * previewCell;
        showPreview = width >= gridSize + 24 + previewSize + 24 && height >= 260;
        int groupWidth = gridSize + (showPreview ? 20 + previewSize : 0);
        gridX = width / 2 - groupWidth / 2;
        previewX = gridX + gridSize + 20;
        previewY = gridY + (gridSize - previewSize) / 2;

        int controlsY = gridY + gridSize + 10;
        int controlsWidth = Math.min(width - 16, 360);
        int controlsX = width / 2 - controlsWidth / 2;
        int gap = 3;
        int buttonWidth = Math.max(30, (controlsWidth - gap * 4) / 5);

        enableButton = addSmall(controlsX, controlsY, buttonWidth, "Enable", button -> {
            VBVCrosshairConfig.enabled = !VBVCrosshairConfig.enabled;
            VBVConfigStore.saveAll();
            refreshLabels();
        }, "Show the custom crosshair in game");
        scaleButton = addSmall(controlsX + buttonWidth + gap, controlsY, buttonWidth, "Size", button -> {
            VBVCrosshairConfig.cycleSize();
            VBVConfigStore.saveAll();
            refreshLabels();
        }, "Pixel scale: click to cycle 1x-4x");
        colorButton = addSmall(controlsX + (buttonWidth + gap) * 2, controlsY, buttonWidth, "Color", button -> {
            VBVCrosshairConfig.cycleColor();
            VBVConfigStore.saveAll();
            refreshLabels();
        }, "Cycle the crosshair colour");
        addSmall(controlsX + (buttonWidth + gap) * 3, controlsY, buttonWidth, "Reset", button -> {
            VBVCrosshairConfig.defaultCrosshair();
            VBVConfigStore.saveAll();
            refreshLabels();
        }, "Restore the default crosshair pattern");
        addSmall(controlsX + (buttonWidth + gap) * 4, controlsY, buttonWidth, "Clear", button -> {
            VBVCrosshairConfig.clear();
            VBVConfigStore.saveAll();
            refreshLabels();
        }, "Remove every painted pixel");

        addSmall(width / 2 - 50, height - 24, 100, "Back", button -> VBVNav.open(parent), "Save and go back");
        refreshLabels();
    }

    private VBVUi.ThemedButton addSmall(int x, int y, int w, String label, Button.OnPress press, String tooltip) {
        VBVUi.ThemedButton button = new VBVUi.ThemedButton(x, y, w, 18, label, VBVUi.Style.SMALL, press);
        button.setTooltip(Tooltip.create(Component.literal(tooltip)));
        addRenderableWidget(button);
        return button;
    }

    private void refreshLabels() {
        if (enableButton != null) enableButton.setLabel(VBVCrosshairConfig.enabled ? "Enabled" : "Enable");
        if (scaleButton != null) scaleButton.setLabel("Size " + VBVCrosshairConfig.size);
        if (colorButton != null) colorButton.setLabel(colorLabel());
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

    private int cellAt(double mouseX, double mouseY) {
        if (mouseX < gridX || mouseX >= gridX + gridSize || mouseY < gridY || mouseY >= gridY + gridSize) return -1;
        int x = (int) ((mouseX - gridX) / cell);
        int y = (int) ((mouseY - gridY) / cell);
        if (x < 0 || x >= GRID || y < 0 || y >= GRID) return -1;
        return y * GRID + x;
    }

    private void paintLine(int from, int to) {
        int x0 = from % GRID, y0 = from / GRID;
        int x1 = to % GRID, y1 = to / GRID;
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int i = 0; i <= steps; i++) {
            int x = steps == 0 ? x1 : Math.round(x0 + (x1 - x0) * (float) i / steps);
            int y = steps == 0 ? y1 : Math.round(y0 + (y1 - y0) * (float) i / steps);
            if (x >= 0 && x < GRID && y >= 0 && y < GRID) VBVCrosshairConfig.pixels[y * GRID + x] = paintValue;
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        int index = cellAt(event.x(), event.y());
        if (index >= 0 && index < PIXELS) {
            paintValue = !VBVCrosshairConfig.pixels[index];
            VBVCrosshairConfig.pixels[index] = paintValue;
            painting = true;
            lastCell = index;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (painting) {
            int index = cellAt(event.x(), event.y());
            if (index >= 0 && index != lastCell) {
                paintLine(lastCell, index);
                lastCell = index;
            }
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (painting) {
            painting = false;
            lastCell = -1;
            VBVConfigStore.saveAll();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public void removed() {
        VBVConfigStore.saveAll();
        super.removed();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {

        String title = "CUSTOM CROSSHAIR";
        graphics.text(font, title, width / 2 - font.width(title) / 2, 11, 0xFFFFFFFF, true);
        String subtitle = "Click or drag to paint  ·  15 x 15  ·  " + (VBVCrosshairConfig.enabled ? "enabled" : "disabled");
        graphics.text(font, subtitle, width / 2 - font.width(subtitle) / 2, 23, VBVUi.TEXT_DIM, false);

        // Grid backing: the one-pixel gutters become the grid lines.
        graphics.fill(gridX - 3, gridY - 3, gridX + gridSize + 3, gridY + gridSize + 3, 0xFF050507);
        graphics.outline(gridX - 3, gridY - 3, gridSize + 6, gridSize + 6, VBVTheme.accentAlpha(0x70));
        graphics.fill(gridX, gridY, gridX + gridSize, gridY + gridSize, 0xFF2B2B33);

        int hover = cellAt(mouseX, mouseY);
        int color = VBVCrosshairConfig.color;
        for (int y = 0; y < GRID; y++) {
            for (int x = 0; x < GRID; x++) {
                int index = y * GRID + x;
                int px = gridX + x * cell;
                int py = gridY + y * cell;
                graphics.fill(px, py, px + cell - 1, py + cell - 1,
                    VBVCrosshairConfig.pixels[index] ? color : 0xFF19191F);
                if (VBVCrosshairConfig.pixels[index]) {
                    graphics.fill(px, py, px + cell - 1, py + 1, 0x40FFFFFF);
                }
                if (index == hover) {
                    graphics.outline(px - 1, py - 1, cell + 1, cell + 1, 0xFFFFFFFF);
                }
            }
        }
        // Centre guides help line the pattern up with the middle of the screen.
        int mid = gridSize / 2;
        graphics.fill(gridX + mid, gridY, gridX + mid + 1, gridY + gridSize, 0x28FFFFFF);
        graphics.fill(gridX, gridY + mid, gridX + gridSize, gridY + mid + 1, 0x28FFFFFF);

        if (showPreview) {
            int previewSize = GRID * previewCell;
            graphics.fill(previewX - 4, previewY - 4, previewX + previewSize + 4, previewY + previewSize + 4, 0xC0101014);
            graphics.outline(previewX - 4, previewY - 4, previewSize + 8, previewSize + 8, VBVTheme.accentAlpha(0x80));
            graphics.text(font, "PREVIEW", previewX, previewY - 15, VBVUi.TEXT_DIM, false);
            for (int y = 0; y < GRID; y++) {
                for (int x = 0; x < GRID; x++) {
                    if (!VBVCrosshairConfig.pixels[y * GRID + x]) continue;
                    int px = previewX + x * previewCell;
                    int py = previewY + y * previewCell;
                    graphics.fill(px, py, px + previewCell, py + previewCell, color);
                }
            }
            String info = VBVCrosshairConfig.size + "x  |  " + colorLabel();
            graphics.text(font, info, previewX, previewY + previewSize + 8, VBVUi.TEXT_DIM, false);
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }
}
