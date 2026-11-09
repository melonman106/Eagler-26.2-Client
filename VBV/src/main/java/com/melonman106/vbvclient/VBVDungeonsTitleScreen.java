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
 * ember-lit night scene with drifting fireflies, the logo up top, and a short
 * left-hand menu (PLAY, SINGLEPLAYER, MODS, OPTIONS) with a glowing accent-colour
 * PLAY button. Hero, Party, Storage and resource packs live in the launcher
 * ({@link VBVLauncherScreen}), reached from the profile card or the LAUNCHER button.
 * The accent colour comes from {@link VBVTheme}.
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

    private static final int TEXT = VBVUi.TEXT;
    private static final int TEXT_DIM = VBVUi.TEXT_DIM;

    private static final int FIREFLIES = 26;
    private static final int SECONDARY_BUTTONS = 3;

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
        addMenuButton("PLAY", y, primaryH, VBVUi.Style.PRIMARY, b -> VBVNav.openMultiplayer(this));
        y += primaryH + gap;
        addMenuButton("SINGLEPLAYER", y, buttonH, VBVUi.Style.MENU, b -> VBVNav.openSingleplayer(this));
        y += buttonH + gap;
        addMenuButton("MODS", y, buttonH, VBVUi.Style.MENU, b -> VBVNav.open(new VBVModsScreen(this)));
        y += buttonH + gap;
        addMenuButton("OPTIONS", y, buttonH, VBVUi.Style.MENU, b -> VBVNav.openOptions(this));

        addRenderableWidget(new ProfileCard(this));
        int launcherW = 112;
        int launcherH = 22;
        addRenderableWidget(new LauncherButton(width - launcherW - 10, height - launcherH - 10, launcherW, launcherH,
            b -> VBVNav.open(new VBVLauncherScreen(this))));

        layoutWidth = width;
        layoutHeight = height;
    }

    private void addMenuButton(String label, int y, int h, VBVUi.Style style, Button.OnPress action) {
        addRenderableWidget(new VBVUi.ThemedButton(left, y, menuW, h, label, style, action));
    }

    /** Sizes the logo and menu so the whole block fits the current GUI size. */
    private void computeLayout() {
        left = Math.max(14, width / 16);
        menuW = Math.max(140, Math.min(240, Math.min(width / 4 + 40, width - left * 2)));

        int margin = 10;
        int footer = 16;
        int avail = height - margin - footer;

        int lh = Math.min(menuW + 20, LOGO_TEX_W) * LOGO_ART_H / LOGO_TEX_W;
        int bh = 26;
        int g = 5;
        int sub = 18;
        int headGap = 12;
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
        primaryH = bh + 12;
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
        return logoH + subtitleH + headGap + (buttonH + 12) + SECONDARY_BUTTONS * (buttonH + gap);
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
            graphics.fill(left, subtitleY + 4, left + 10, subtitleY + 5, VBVTheme.accent());
            graphics.text(font, "YOUR ADVENTURE STARTS HERE", left + 15, subtitleY, TEXT_DIM, true);
        }

        renderPartyPanel(graphics);

        // A small edition label anchors the lower edge without obscuring the panorama
        // (skipped on tiny GUI sizes where the menu already reaches the bottom).
        if (menuBottom <= height - 14) {
            graphics.text(font, "VBV EDITION  \u2022  26.2", left, height - 14, TEXT_DIM, false);
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
        int glow = VBVTheme.accentLight() & 0xFFFFFF;
        int halo0 = VBVTheme.accent() & 0xFFFFFF;
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
            graphics.fill(x - 1, y - 1, x + 3, y + 3, (halo << 24) | halo0);
            graphics.fill(x, y, x + 2, y + 2, (core << 24) | glow);
        }
    }

    private static double fract(double value) {
        return value - Math.floor(value);
    }

    private void renderPartyPanel(GuiGraphicsExtractor graphics) {
        String[] party = VBVParty.players();
        if (party.length == 0) return;
        int shown = Math.min(4, party.length);
        int py = 12 + 32;
        graphics.fill(width - 188, py - 4, width - 10, py + shown * 18 + 16, VBVTheme.panel(0xB0));
        graphics.fill(width - 188, py - 4, width - 186, py + shown * 18 + 16, VBVTheme.accent());
        graphics.text(font, "PARTY", width - 176, py, VBVTheme.accentLight(), true);
        py += 14;
        for (int i = 0; i < shown; i++) {
            graphics.text(font, party[i], width - 166, py, TEXT, false);
            py += 18;
        }
    }

    /** Skin head and name in the top-right corner; opens the launcher on the Hero tab. */
    private static final class ProfileCard extends Button {
        ProfileCard(VBVDungeonsTitleScreen screen) {
            super(screen.width - 10 - cardWidth(), 8, cardWidth(), 28, Component.literal("Hero"),
                b -> VBVNav.open(new VBVLauncherScreen(screen, VBVLauncherScreen.Tab.HERO)), DEFAULT_NARRATION);
        }

        private static int cardWidth() {
            Font font = Minecraft.getInstance().font;
            return 8 + 20 + 8 + font.width(profileName()) + 12;
        }

        private static String profileName() {
            String name = EaglerProfile.getName();
            return name == null || name.isEmpty() ? "Player" : name;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            Font font = Minecraft.getInstance().font;
            boolean lit = this.isHoveredOrFocused();
            int x = this.getX();
            int y = this.getY();
            int w = this.getWidth();
            int h = this.getHeight();
            graphics.fill(x, y, x + w, y + h, lit ? VBVTheme.panel(0xE8) : VBVTheme.panel(0xB0));
            graphics.fill(x, y + h - 1, x + w, y + h, lit ? VBVTheme.accentLight() : VBVTheme.accent());
            Identifier skin = EaglerProfile.getActiveSkinResourceLocation();
            graphics.blit(RenderPipelines.GUI_TEXTURED, skin, x + 6, y + 4, 8, 8, 20, 20, 8, 8, 64, 64);
            graphics.blit(RenderPipelines.GUI_TEXTURED, skin, x + 6, y + 4, 40, 8, 20, 20, 8, 8, 64, 64);
            graphics.text(font, profileName(), x + 34, y + (h - font.lineHeight) / 2 + 1,
                lit ? VBVTheme.accentLight() : 0xFFFFFFFF, true);
        }
    }

    /** Small launcher shortcut in the bottom-right corner. */
    private static final class LauncherButton extends Button {
        LauncherButton(int x, int y, int w, int h, Button.OnPress onPress) {
            super(x, y, w, h, Component.literal("LAUNCHER"), onPress, DEFAULT_NARRATION);
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            Font font = Minecraft.getInstance().font;
            boolean lit = this.isHoveredOrFocused();
            int x = this.getX();
            int y = this.getY();
            int w = this.getWidth();
            int h = this.getHeight();
            graphics.fill(x, y, x + w, y + h, lit ? VBVTheme.panel(0xF0) : VBVTheme.panel(0xC0));
            graphics.outline(x, y, w, h, lit ? VBVTheme.accentLight() : VBVTheme.accentAlpha(0xA0));
            VBVUi.gridIcon(graphics, x + 9, y + (h - 11) / 2, lit ? VBVTheme.accentLight() : VBVTheme.accent());
            graphics.text(font, "LAUNCHER", x + 26, y + (h - font.lineHeight) / 2 + 1,
                lit ? VBVTheme.accentLight() : TEXT, true);
        }
    }
}
