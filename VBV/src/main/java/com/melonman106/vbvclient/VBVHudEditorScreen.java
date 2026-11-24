package com.melonman106.vbvclient;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Drag-and-drop editor for the modded HUD overlays.
 *
 * <p>The button at the top cycles through the overlays that can be moved: the Jade target tooltip,
 * the Simple HUD information block and the FPS meter. Each one is drawn in its real size and
 * position, with the accent colour as a border, and dragging it stores an offset that the overlay
 * itself adds to its normal anchor. Offsets are saved when the mouse is released, so a mistake
 * only costs one drag.</p>
 */
public final class VBVHudEditorScreen extends Screen {
    private static final String[] TARGETS = { "Jade overlay", "Simple HUD", "FPS meter", "Potion HUD" };
    private static final int JADE = 0, SIMPLE = 1, METER = 2, POTION = 3;
    private final Screen parent;
    private int target;
    private boolean dragging;
    private int grabX;
    private int grabY;

    public VBVHudEditorScreen(Screen parent) {
        this(parent, JADE);
    }

    public VBVHudEditorScreen(Screen parent, int target) {
        super(Component.literal("HUD Position Editor"));
        this.parent = parent;
        this.target = Math.max(0, Math.min(TARGETS.length - 1, target));
    }

    @Override protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.literal(editLabel()), b -> {
            target = (target + 1) % TARGETS.length;
            b.setMessage(Component.literal(editLabel()));
        }).bounds(8, 8, 200, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Reset position"), b -> {
            setOffset(0, 0);
            VBVConfigStore.saveAll();
        }).bounds(width / 2 - 105, height - 30, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> VBVNav.open(parent))
            .bounds(width / 2 + 5, height - 30, 100, 20).build());
    }

    private String editLabel() { return "Edit: " + TARGETS[target]; }

    /** Where the overlay's own anchor puts it before the saved offset is added. */
    private int originX() {
        if (target == SIMPLE) return 6;
        if (target == POTION) return width - VBVPotionHud.lastWidth - 6;
        return width / 2 - 90;
    }

    private int originY() {
        if (target == SIMPLE) return 6;
        if (target == POTION) return 54;
        return target == METER ? 2 : 8;
    }

    private int offsetX() {
        if (target == SIMPLE) return VBVSimpleHudConfig.hudXOffset;
        if (target == POTION) return VBVPotionHud.offsetX;
        return target == METER ? VBVPerf.offsetX : VBVJadeHud.offsetX;
    }

    private int offsetY() {
        if (target == SIMPLE) return VBVSimpleHudConfig.hudYOffset;
        if (target == POTION) return VBVPotionHud.offsetY;
        return target == METER ? VBVPerf.offsetY : VBVJadeHud.offsetY;
    }

    private void setOffset(int x, int y) {
        if (target == SIMPLE) {
            VBVSimpleHudConfig.hudXOffset = x;
            VBVSimpleHudConfig.hudYOffset = y;
        } else if (target == METER) {
            VBVPerf.offsetX = x;
            VBVPerf.offsetY = y;
        } else if (target == POTION) {
            VBVPotionHud.offsetX = x;
            VBVPotionHud.offsetY = y;
        } else {
            VBVJadeHud.offsetX = x;
            VBVJadeHud.offsetY = y;
        }
    }

    private int boxW() {
        if (target == SIMPLE) return Math.max(205, Math.round(230 * VBVSimpleHudConfig.hudScale));
        if (target == POTION) return Math.max(96, VBVPotionHud.lastWidth);
        return 184;
    }

    private int boxH() {
        if (target == SIMPLE) return 60;
        if (target == POTION) return Math.max(32, VBVPotionHud.lastHeight);
        return target == METER ? 62 : 43;
    }

    private int boxX() { return originX() + offsetX(); }
    private int boxY() { return originY() + offsetY(); }

    private int clampX(int value) {
        int min = 20 - (originX() + boxW());
        int max = Math.max(min, width - 20 - originX());
        return Math.max(min, Math.min(max, value));
    }

    private int clampY(int value) {
        int min = 2 - originY();
        int max = Math.max(min, height - 24 - originY());
        return Math.max(min, Math.min(max, value));
    }

    @Override public boolean mouseClicked(MouseButtonEvent click, boolean doubleClick) {
        if (super.mouseClicked(click, doubleClick)) return true;
        if (click.x() >= boxX() - 4 && click.x() <= boxX() + boxW()
            && click.y() >= boxY() - 4 && click.y() <= boxY() + boxH()) {
            dragging = true;
            grabX = (int) Math.round(click.x()) - boxX();
            grabY = (int) Math.round(click.y()) - boxY();
            return true;
        }
        return false;
    }

    @Override public boolean mouseReleased(MouseButtonEvent click) {
        if (dragging) {
            dragging = false;
            VBVConfigStore.saveAll();
        }
        return super.mouseReleased(click);
    }

    @Override public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (!dragging) return super.mouseDragged(event, dragX, dragY);
        // Absolute placement rather than an accumulated delta: the overlay follows the pointer even
        // when a frame is dropped, and cannot drift away from it.
        int x = (int) Math.round(event.x()) - grabX - originX();
        int y = (int) Math.round(event.y()) - grabY - originY();
        setOffset(clampX(x), clampY(y));
        return true;
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fillGradient(0, 0, width, height, -1072689136, -804253680);
        String heading = "HUD Position Editor";
        graphics.text(font, heading, width / 2 - font.width(heading) / 2, height - 46, 0xFFAAAAAA, true);
        graphics.text(font, "Drag the outlined box. The top-left button switches overlay.", 8, 32, 0xFFCCCCCC, true);
        int x = boxX(), y = boxY(), w = boxW(), h = boxH();
        int accent = VBVTheme.accent();
        graphics.fill(x - 3, y - 3, x + w + 3, y + h + 3, 0xCC111111);
        graphics.fill(x, y, x + w, y + h, 0xE0101010);
        if (target == JADE) {
            graphics.text(font, "Stone", x + 8, y + 5, 0xFFFFFFFF, true);
            graphics.text(font, "Hardness: 1.5", x + 8, y + 18, 0xFFCCCCCC, false);
            graphics.text(font, "XYZ: 120, 64, -32", x + 8, y + 30, 0xFFAAAAAA, false);
        } else if (target == POTION) {
            String[] names = { "Speed II", "Fire Resistance", "Poison" };
            String[] times = { "1:32", "0:08", "0:21" };
            for (int i = 0; i < names.length; i++) {
                int row = y + 4 + i * 16;
                graphics.fill(x + 5, row + 2, x + 17, row + 14, i == 2 ? 0x804A1010 : 0x804A4A10);
                graphics.text(font, names[i], x + 21, row + 3, i == 2 ? 0xFFFF8C8C : 0xFFFFFFFF, false);
                graphics.text(font, times[i], x + w - 6 - font.width(times[i]), row + 3, 0xFFB8B0A0, false);
            }
        } else if (target == SIMPLE) {
            String[] lines = { "FPS: 60", "XYZ: 120 64 -32", "Facing: north", "Chunk: 7 -2" };
            for (int i = 0; i < lines.length; i++) {
                graphics.text(font, lines[i], x + 4, y + 4 + i * (font.lineHeight + 1), 0xFFFFFFFF, true);
            }
        } else {
            graphics.text(font, "60 FPS   16.7 ms   worst 21 ms", x + 4, y + 4, 0xFF8CFF8C, true);
            graphics.text(font, "Spikes (30s): 0 over 50ms, 0 over 100ms", x + 4, y + 4 + font.lineHeight + 1, 0xFFB8B0A0, true);
            graphics.text(font, "Chunks 1.2/0.4/6.1 ms", x + 4, y + 4 + (font.lineHeight + 1) * 2, accent, true);
            for (int i = 0; i < 40; i++) {
                int bh = 2 + (i * 7) % 10;
                graphics.fill(x + 70 + i * 2, y + h - 6 - bh, x + 71 + i * 2, y + h - 6, 0xFF70E070);
            }
        }
        // Outline last so it stays visible over the preview content.
        graphics.fill(x - 1, y - 1, x + w + 1, y, accent);
        graphics.fill(x - 1, y + h, x + w + 1, y + h + 1, accent);
        graphics.fill(x - 1, y, x, y + h, accent);
        graphics.fill(x + w, y, x + w + 1, y + h, accent);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }
}
