package com.melonman106.vbvclient;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * VBV main menu, laid out like a sci-fi launcher: the animated panorama behind a left-aligned
 * wordmark and a compact list of wide-spaced capitals (PLAY, SINGLEPLAYER, MODS, OPTIONS), with
 * EXTRAS tucked into the bottom-right corner. The selected row gets a light highlight bar with a
 * slanted edge. Hover or the up/down keys move the selection; Enter or a click opens it.
 *
 * The first time the menu is reached after launch, {@link VBVSplashScreen} ("press any button to
 * begin") is shown on top of the same panorama and opens this screen once a key is pressed.
 * Hero, Party, Storage, Panoramas and Appearance live in {@link VBVLauncherScreen}, the Extras screen.
 *
 * The panorama itself is drawn by TitleScreen; everything here is layered on top of it. Layout is
 * computed in one place ({@link #computeLayout()}) and shrinks to fit short GUI heights.
 */
public final class VBVDungeonsTitleScreen extends TitleScreen {
    /** PLAY, SINGLEPLAYER, MODS and OPTIONS form the list; the fifth item is EXTRAS in the bottom-right corner. */
    private static final int LIST_COUNT = 4;
    private static final int ITEM_COUNT = 5;
    private static final int EXTRAS = 4;

    private int layoutWidth = -1;
    private int layoutHeight = -1;

    // Computed by computeLayout().
    private int left;
    private int menuW;
    private int rowH;
    private int gap;
    private float markScale;
    private int markY;
    private int markH;
    private boolean showSubtitle;
    private int subtitleY;
    private int menuY;
    private int extrasX;
    private int extrasY;
    private int extrasW;

    private final ZenithItem[] items = new ZenithItem[ITEM_COUNT];
    private final Runnable[] actions = new Runnable[ITEM_COUNT];
    private int selected;
    private int lastMouseX = -1;
    private int lastMouseY = -1;

    public VBVDungeonsTitleScreen() { super(); }

    @Override protected void init() {
        VBVClient.init();
        VBVBoot.menuReached();
        if (!VBVSplashScreen.isDone()) {
            // First visit this launch: show "press any button to begin" first. Pressing a button
            // opens a fresh copy of this screen.
            VBVSplashScreen.show(this);
            return;
        }
        super.init();
        VBVParty.initialize();
        clearWidgets();
        computeLayout();

        // First renderable: the vignette sits above the panorama but underneath every row.
        addRenderableOnly((graphics, mouseX, mouseY, a) -> renderAtmosphere(graphics));

        String[] labels = { "PLAY", "SINGLEPLAYER", "MODS", "OPTIONS", "EXTRAS" };
        actions[0] = () -> VBVNav.openMultiplayer(this);
        actions[1] = () -> VBVNav.openSingleplayer(this);
        actions[2] = () -> VBVNav.open(new VBVModsScreen(this));
        actions[3] = () -> VBVNav.openOptions(this);
        actions[4] = () -> VBVNav.open(new VBVLauncherScreen(this));

        int y = menuY;
        for (int i = 0; i < ITEM_COUNT; i++) {
            final int index = i;
            boolean corner = i == EXTRAS;
            items[i] = corner
                ? new ZenithItem(extrasX, extrasY, extrasW, rowH, labels[i], true, b -> {
                    selected = index;
                    actions[index].run();
                })
                : new ZenithItem(left, y, menuW, rowH, labels[i], false, b -> {
                    selected = index;
                    actions[index].run();
                });
            addRenderableWidget(items[i]);
            if (!corner) y += rowH + gap;
        }
        selected = Math.max(0, Math.min(ITEM_COUNT - 1, selected));

        layoutWidth = width;
        layoutHeight = height;
    }

    /** Sizes the wordmark and list so the whole block fits the current GUI size. */
    private void computeLayout() {
        // Compact column hugging the left edge; the text stays at normal size.
        left = Math.max(10, width / 40);
        int needed = VBVUi.trackedWidth(font, "SINGLEPLAYER", 2) + 28;
        menuW = Math.min(needed, Math.max(80, width - left - 10));

        int bottomMargin = 10;
        int top = 10;
        int avail = height - top - bottomMargin;

        float ms = height >= 300 ? 3.0F : 2.0F;
        int rh = font.lineHeight + 9;
        int g = 2;
        boolean sub = true;
        for (int guard = 0; guard < 64 && blockHeight(ms, sub, rh, g) > avail; guard++) {
            if (rh > 14) {
                rh--;
            } else if (sub) {
                sub = false;
            } else if (g > 1) {
                g--;
            } else {
                break;
            }
        }

        markScale = ms;
        rowH = rh;
        gap = g;
        showSubtitle = sub;
        markH = (int) (font.lineHeight * ms);
        int total = blockHeight(ms, sub, rh, g);
        markY = top + Math.max(0, (avail - total) / 2);
        subtitleY = markY + markH + 7;
        menuY = markY + markH + (sub ? 7 + font.lineHeight : 0) + 12;

        // EXTRAS sits alone in the bottom-right corner.
        extrasW = VBVUi.trackedWidth(font, "EXTRAS", 2) + 28;
        extrasX = width - extrasW - 10;
        extrasY = height - rh - 10;
    }

    private int blockHeight(float markScale, boolean subtitle, int rowH, int gap) {
        return (int) (font.lineHeight * markScale) + (subtitle ? 7 + font.lineHeight : 0) + 12
            + LIST_COUNT * (rowH + gap);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == 265) { // up
            selected = (selected + ITEM_COUNT - 1) % ITEM_COUNT;
            return true;
        }
        if (key == 264) { // down
            selected = (selected + 1) % ITEM_COUNT;
            return true;
        }
        if ((key == 257 || key == 335 || key == 32) && actions[selected] != null) { // enter, keypad enter, space
            actions[selected].run();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int currentWidth = graphics.guiWidth();
        int currentHeight = graphics.guiHeight();
        if (currentWidth != layoutWidth || currentHeight != layoutHeight) {
            // Browser fullscreen can resize the canvas without a normal window event.
            // Rebuild all bounds against the new GUI dimensions before drawing.
            this.init(currentWidth, currentHeight);
            if (layoutWidth != currentWidth) {
                return; // the splash screen took over; nothing to draw for this screen
            }
        }

        // The mouse moves the selection only when it actually moves, so the arrow keys still work
        // while the pointer rests over a row.
        if (mouseX != lastMouseX || mouseY != lastMouseY) {
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            for (int i = 0; i < ITEM_COUNT; i++) {
                if (items[i] != null && items[i].isMouseOver(mouseX, mouseY)) {
                    selected = i;
                    break;
                }
            }
        }
        for (int i = 0; i < ITEM_COUNT; i++) {
            if (items[i] != null) items[i].selectedRow = i == selected;
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        // Wordmark: big wide-spaced letters, an accent rule and the edition line.
        int tracking = 3;
        graphics.pose().pushMatrix();
        graphics.pose().translate(left, markY);
        graphics.pose().scale(markScale, markScale);
        VBVUi.trackedText(graphics, font, "VBV", 0, 0, 0xFFFFFFFF, tracking, true);
        graphics.pose().popMatrix();
        if (showSubtitle) {
            graphics.fill(left, subtitleY + 4, left + 10, subtitleY + 5, VBVTheme.accent());
            VBVUi.trackedText(graphics, font, "EAGLER 26.2 EDITION", left + 15, subtitleY, VBVUi.TEXT_DIM, 1, true);
        }

        renderPartyPanel(graphics);
    }

    /** Left-hand vignette and top/bottom shading so the menu reads on any panorama. */
    private void renderAtmosphere(GuiGraphicsExtractor graphics) {
        int w = graphics.guiWidth();
        int h = graphics.guiHeight();

        // Horizontal fade: darkest at the left edge, gone by the end of the menu column.
        int vigW = Math.min(w, left + menuW + w / 6);
        int steps = Math.min(48, Math.max(1, vigW));
        for (int i = 0; i < steps; i++) {
            float t = 1.0F - (float) i / steps;
            int alpha = (int) (0xB8 * t * t);
            if (alpha <= 0) continue;
            int x0 = i * vigW / steps;
            int x1 = (i + 1) * vigW / steps;
            if (x1 > x0) graphics.fill(x0, 0, x1, h, alpha << 24);
        }
        graphics.fillGradient(0, 0, w, Math.min(h, 40), 0x99000000, 0x00000000);
        graphics.fillGradient(0, Math.max(0, h - 52), w, h, 0x00000000, 0xB0000000);
    }

    private void renderPartyPanel(GuiGraphicsExtractor graphics) {
        String[] party = VBVParty.players();
        if (party.length == 0) return;
        int shown = Math.min(4, party.length);
        int py = 12;
        graphics.fill(width - 188, py - 4, width - 10, py + shown * 18 + 16, VBVTheme.panel(0xB0));
        graphics.fill(width - 188, py - 4, width - 186, py + shown * 18 + 16, VBVTheme.accent());
        graphics.text(font, "PARTY", width - 176, py, VBVTheme.accentLight(), true);
        py += 14;
        for (int i = 0; i < shown; i++) {
            graphics.text(font, party[i], width - 166, py, VBVUi.TEXT, false);
            py += 18;
        }
    }

    /**
     * One menu row: plain wide-spaced text; the selected row slides in a light bar with a slanted
     * edge, and its text turns dark. The corner item (EXTRAS) is right-aligned and its bar slants the
     * other way so it points into the screen.
     */
    private final class ZenithItem extends Button {
        boolean selectedRow;
        private final boolean corner;
        private float glow;

        ZenithItem(int x, int y, int w, int h, String label, boolean corner, Button.OnPress onPress) {
            super(x, y, w, h, Component.literal(label), onPress, DEFAULT_NARRATION);
            this.corner = corner;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            Font f = VBVDungeonsTitleScreen.this.font;
            int x = this.getX();
            int y = this.getY();
            int w = this.getWidth();
            int h = this.getHeight();

            // Ease toward the target so the bar slides in instead of snapping.
            glow += ((selectedRow ? 1.0F : 0.0F) - glow) * 0.4F;
            if (glow < 0.01F) glow = 0.0F;

            if (glow > 0.0F) {
                int bar = VBVTheme.mix(0xFFFFFFFF, VBVTheme.accentLight(), 0.22F);
                int alpha = (int) (0xB4 * glow);
                int barW = (int) (w * (0.7F + 0.3F * glow));
                int color = (alpha << 24) | (bar & 0xFFFFFF);
                if (corner) {
                    VBVUi.slantedBarFromRight(graphics, x + w - barW, y, barW, h, h / 2, color);
                    graphics.fill(x + w - 3, y, x + w, y + h, VBVTheme.accent());
                } else {
                    VBVUi.slantedBar(graphics, x, y, barW, h, h / 2, color);
                    graphics.fill(x, y, x + 3, y + h, VBVTheme.accent());
                }
            }

            boolean lit = glow > 0.5F;
            int color = lit ? 0xFF14110A : 0xFFFFFFFF;
            String text = getMessage().getString();
            int textW = VBVUi.trackedWidth(f, text, 2);
            int textX = corner ? x + w - 14 - textW : x + 14;
            int textY = y + (h - f.lineHeight) / 2 + 1;
            VBVUi.trackedText(graphics, f, text, textX, textY, color, 2, !lit);
        }
    }
}
