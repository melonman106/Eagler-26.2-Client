package com.melonman106.vbvclient;

import net.lax1dude.eaglercraft.v1_8.profile.EaglerProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * VBV home screen styled after the Minecraft Dungeons main menu: a dark,
 * ember-lit night scene with drifting fireflies, the logo up top, and one
 * tall left-hand menu with a glowing gold PLAY button.
 *
 * The animated panorama is drawn by TitleScreen itself; everything here is
 * layered on top of it. Layout is computed in one place ({@link #computeLayout()})
 * so the buttons and the artwork can never drift apart, and it shrinks to fit
 * short GUI heights instead of running off the bottom of the screen.
 */
public final class VBVDungeonsTitleScreen extends TitleScreen {
    private static final Identifier LOGO = Identifier.parse("minecraft:textures/gui/title/minecraft.png");
    // The logo texture is 256x64 but only the top 44 rows hold artwork.
    private static final int LOGO_TEX_W = 256;
    private static final int LOGO_TEX_H = 64;
    private static final int LOGO_ART_H = 44;

    private static final int GOLD = 0xFFE8B44A;
    private static final int GOLD_LIGHT = 0xFFFFD36B;
    private static final int TEXT = 0xFFE9E2D0;
    private static final int TEXT_DIM = 0xFFB8B0A0;
    private static final int PANEL = 0xB0100C08;
    private static final int PANEL_HOVER = 0xE02A200E;

    private static final int FIREFLIES = 26;
    private static final int SECONDARY_BUTTONS = 6;

    private int layoutWidth = -1;
    private int layoutHeight = -1;

    // Computed by computeLayout().
    private int left;
    private int menuW;
    private int logoW;
    private int logoH;
    private int logoY;
    private int subtitleY;
    private int menuY;
    private int menuBottom;
    private int primaryH;
    private int buttonH;
    private int gap;
    private boolean showSubtitle;

    public VBVDungeonsTitleScreen() { super(); }

    @Override protected void init() {
        super.init();
        VBVClient.init();
        VBVParty.initialize();
        EaglerProfile.readIfNeeded();
        clearWidgets();
        computeLayout();

        // First renderable: the vignette and fireflies sit above the panorama
        // but underneath every button.
        addRenderableOnly((graphics, mouseX, mouseY, a) -> renderAtmosphere(graphics));

        int y = menuY;
        addDungeonsButton("PLAY", y, primaryH, true, b -> VBVNav.openMultiplayer(this));
        y += primaryH + gap;
        addDungeonsButton("SINGLEPLAYER", y, buttonH, false, b -> VBVNav.openSingleplayer(this));
        y += buttonH + gap;
        addDungeonsButton("PARTY", y, buttonH, false, b -> VBVNav.open(new VBVPartyScreen(this)));
        y += buttonH + gap;
        addDungeonsButton("HERO", y, buttonH, false,
            b -> VBVNav.open(new net.lax1dude.eaglercraft.v1_8.profile.EaglerProfileScreen26(this)));
        y += buttonH + gap;
        addDungeonsButton("MODS", y, buttonH, false, b -> VBVNav.open(new VBVModsScreen(this)));
        y += buttonH + gap;
        addDungeonsButton("OPTIONS", y, buttonH, false, b -> VBVNav.openOptions(this));
        y += buttonH + gap;
        addDungeonsButton("STORAGE", y, buttonH, false, b -> VBVNav.open(new VBVStorageScreen(this)));
        layoutWidth = width;
        layoutHeight = height;
    }

    private void addDungeonsButton(String label, int y, int h, boolean primary, Button.OnPress action) {
        addRenderableWidget(new DungeonsButton(left, y, menuW, h, label, primary, action));
    }

    /** Sizes the logo and menu so the whole block fits the current GUI size. */
    private void computeLayout() {
        left = Math.max(14, width / 16);
        menuW = Math.max(150, Math.min(300, Math.min(width * 3 / 10 + 40, width - left * 2)));

        int margin = 10;
        int footer = 16;
        int avail = height - margin - footer;

        int lh = Math.min(menuW, LOGO_TEX_W) * LOGO_ART_H / LOGO_TEX_W;
        int bh = 24;
        int g = 4;
        int sub = 18;
        int headGap = 10;
        // Shrink in order of least visual damage until the block fits.
        for (int guard = 0; guard < 64 && blockHeight(lh, sub, headGap, bh, g) > avail; guard++) {
            if (bh > 16) {
                bh--;
            } else if (lh > 24) {
                lh = Math.max(24, lh - 4);
            } else if (sub > 0) {
                sub = 0;
            } else if (g > 2) {
                g--;
            } else {
                break;
            }
        }

        buttonH = bh;
        primaryH = bh + 10;
        gap = g;
        logoH = lh;
        logoW = lh * LOGO_TEX_W / LOGO_ART_H;
        showSubtitle = sub > 0;
        int total = blockHeight(lh, sub, headGap, bh, g);
        logoY = margin + Math.max(0, (avail - total) / 2);
        subtitleY = logoY + logoH + 5;
        menuY = logoY + logoH + sub + headGap;
        menuBottom = menuY + primaryH + SECONDARY_BUTTONS * (buttonH + gap);
    }

    private static int blockHeight(int logoH, int subtitleH, int headGap, int buttonH, int gap) {
        return logoH + subtitleH + headGap + (buttonH + 10) + SECONDARY_BUTTONS * (buttonH + gap);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int currentWidth = graphics.guiWidth();
        int currentHeight = graphics.guiHeight();
        if (currentWidth != layoutWidth || currentHeight != layoutHeight) {
            // Browser fullscreen can resize the canvas without a normal window event.
            // Rebuild all button bounds against the new GUI dimensions before drawing.
            this.init(currentWidth, currentHeight);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        EaglerProfile.readIfNeeded();

        // Logo, drawn at its true aspect ratio (only the top 44 texture rows are artwork).
        graphics.blit(RenderPipelines.GUI_TEXTURED, LOGO, left, logoY,
            0.0F, 0.0F, logoW, logoH, LOGO_TEX_W, LOGO_ART_H, LOGO_TEX_W, LOGO_TEX_H);
        if (showSubtitle) {
            graphics.fill(left, subtitleY + 4, left + 10, subtitleY + 5, GOLD);
            graphics.text(font, "YOUR ADVENTURE STARTS HERE", left + 15, subtitleY, TEXT_DIM, true);
        }

        renderProfileCard(graphics);
        renderPartyPanel(graphics);

        // A small edition label anchors the lower edge without obscuring the panorama
        // (skipped on tiny GUI sizes where the menu already reaches the bottom).
        if (menuBottom <= height - 14) {
            graphics.text(font, "VBV EDITION  •  26.2", left, height - 14, TEXT_DIM, false);
        }
    }

    /** Left-hand vignette, top/bottom shading and a handful of drifting fireflies. */
    private void renderAtmosphere(GuiGraphicsExtractor graphics) {
        int w = graphics.guiWidth();
        int h = graphics.guiHeight();

        // Horizontal fade: darkest at the left edge, gone by the end of the menu column.
        int vigW = Math.min(w, left + menuW + w / 5);
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

        // Fireflies, like the Creeper Woods camp: slow drifting pinpoints that pulse.
        long now = System.currentTimeMillis();
        double time = now / 1000.0;
        for (int i = 0; i < FIREFLIES; i++) {
            double seed = i * 12.9898;
            double baseX = fract(Math.sin(seed) * 43758.5453) * w;
            double baseY = h * (0.25 + 0.7 * fract(Math.sin(seed * 1.37) * 24634.6345));
            int x = (int) (baseX + Math.sin(time * 0.6 + seed) * 16.0);
            int y = (int) (baseY + Math.cos(time * 0.43 + seed * 1.7) * 11.0);
            double pulse = 0.5 + 0.5 * Math.sin(time * 1.7 + seed * 3.0);
            int core = (int) (50 + pulse * 170);
            int halo = (int) (8 + pulse * 28);
            graphics.fill(x - 1, y - 1, x + 3, y + 3, (halo << 24) | 0xB8E04A);
            graphics.fill(x, y, x + 2, y + 2, (core << 24) | 0xEFFF8A);
        }
    }

    private static double fract(double value) {
        return value - Math.floor(value);
    }

    private void renderProfileCard(GuiGraphicsExtractor graphics) {
        String name = EaglerProfile.getName();
        if (name == null || name.isEmpty()) name = "Player";
        Identifier skin = EaglerProfile.getActiveSkinResourceLocation();
        int nameWidth = font.width(name);
        int textX = width - 16 - nameWidth;
        int headX = textX - 25;
        int headY = 12;
        graphics.fill(headX - 5, headY - 4, width - 10, headY + 25, PANEL);
        graphics.fill(headX - 5, headY + 24, width - 10, headY + 25, GOLD);
        graphics.blit(RenderPipelines.GUI_TEXTURED, skin, headX, headY, 8, 8, 20, 20, 8, 8, 64, 64);
        graphics.blit(RenderPipelines.GUI_TEXTURED, skin, headX, headY, 40, 8, 20, 20, 8, 8, 64, 64);
        graphics.text(font, name, textX, headY + 6, 0xFFFFFFFF, true);
    }

    private void renderPartyPanel(GuiGraphicsExtractor graphics) {
        String[] party = VBVParty.players();
        if (party.length == 0) return;
        int shown = Math.min(4, party.length);
        int py = 12 + 28;
        graphics.fill(width - 188, py - 4, width - 10, py + shown * 18 + 16, PANEL);
        graphics.fill(width - 188, py - 4, width - 186, py + shown * 18 + 16, GOLD);
        graphics.text(font, "PARTY", width - 176, py, GOLD_LIGHT, true);
        py += 14;
        for (int i = 0; i < shown; i++) {
            graphics.text(font, party[i], width - 166, py, TEXT, false);
            py += 18;
        }
    }

    /** Flat, dark menu bar with a gold accent; PLAY is a solid glowing gold slab. */
    private static final class DungeonsButton extends Button {
        private final String label;
        private final boolean primary;

        DungeonsButton(int x, int y, int w, int h, String label, boolean primary, Button.OnPress onPress) {
            super(x, y, w, h, Component.literal(label), onPress, DEFAULT_NARRATION);
            this.label = label;
            this.primary = primary;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            Font font = Minecraft.getInstance().font;
            boolean lit = this.isHoveredOrFocused() && this.active;
            int x = this.getX();
            int y = this.getY();
            int w = this.getWidth();
            int h = this.getHeight();

            if (primary) {
                graphics.fillGradient(x, y, x + w, y + h, lit ? 0xFFFFDC7A : 0xFFF4C75A, lit ? 0xFFE8A230 : 0xFFD08F24);
                graphics.outline(x, y, w, h, 0xFF5A3A08);
                float scale = h >= 30 ? 1.5F : 1.0F;
                int textW = (int) (font.width(label) * scale);
                int textH = (int) (font.lineHeight * scale);
                graphics.pose().pushMatrix();
                graphics.pose().translate(x + 14, y + (h - textH) / 2.0F + 0.5F);
                graphics.pose().scale(scale, scale);
                graphics.text(font, label, 0, 0, 0xFF2B1A04, false);
                graphics.pose().popMatrix();
                if (lit && w > textW + 40) {
                    drawChevron(graphics, x + w - 20, y + (h - 9) / 2, 0xFF2B1A04);
                }
                return;
            }

            graphics.fill(x, y, x + w, y + h, lit ? PANEL_HOVER : PANEL);
            int textX = x + 12;
            if (lit) {
                graphics.fill(x, y, x + 3, y + h, GOLD_LIGHT);
                graphics.outline(x, y, w, h, 0x80E8B44A);
                textX += 3;
                if (w > font.width(label) + 44) {
                    drawChevron(graphics, x + w - 18, y + (h - 9) / 2, GOLD_LIGHT);
                }
            } else {
                graphics.fill(x, y, x + 2, y + h, 0x80E8B44A);
            }
            int color = !this.active ? 0xFF706860 : (lit ? GOLD_LIGHT : TEXT);
            graphics.text(font, label, textX, y + (h - font.lineHeight) / 2 + 1, color, true);
        }

        /** A small right-pointing chevron, 5 wide and 9 tall. */
        private static void drawChevron(GuiGraphicsExtractor graphics, int x, int y, int color) {
            for (int k = 0; k < 5; k++) {
                graphics.fill(x + k, y + k, x + k + 2, y + k + 1, color);
                graphics.fill(x + k, y + 8 - k, x + k + 2, y + 9 - k, color);
            }
        }
    }
}
