package com.melonman106.vbvclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Shared, theme-aware drawing helpers and widgets for the VBV title screen and launcher. */
public final class VBVUi {
    public static final int TEXT = 0xFFE9E2D0;
    public static final int TEXT_DIM = 0xFFB8B0A0;
    public static final int TEXT_DISABLED = 0xFF706860;

    private VBVUi() {}

    public enum Style {
        /** Big solid accent slab (PLAY). */
        PRIMARY,
        /** Dark menu bar with an accent edge. */
        MENU,
        /** Sidebar tab; highlighted while selected. */
        TAB,
        /** Compact action button. */
        SMALL,
        /** Colour swatch square (appearance tab). */
        SWATCH
    }

    /** Flat, dark, accent-edged button used throughout VBV's own menus. */
    public static final class ThemedButton extends Button {
        private final Style style;
        private boolean selected;
        private int swatchColor = 0xFFFFFFFF;

        public ThemedButton(int x, int y, int w, int h, String label, Style style, Button.OnPress onPress) {
            super(x, y, w, h, Component.literal(label), onPress, DEFAULT_NARRATION);
            this.style = style;
        }

        public ThemedButton selected(boolean value) {
            this.selected = value;
            return this;
        }

        public void setSelected(boolean value) {
            this.selected = value;
        }

        public ThemedButton swatch(int argb) {
            this.swatchColor = argb;
            return this;
        }

        public void setLabel(String label) {
            setMessage(Component.literal(label));
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            Font font = Minecraft.getInstance().font;
            String label = getMessage().getString();
            boolean lit = this.isHoveredOrFocused() && this.active;
            int x = this.getX();
            int y = this.getY();
            int w = this.getWidth();
            int h = this.getHeight();
            int accent = VBVTheme.accent();
            int accentLight = VBVTheme.accentLight();

            switch (style) {
                case PRIMARY -> {
                    int top = lit ? VBVTheme.mix(accent, 0xFFFFFFFF, 0.45F) : VBVTheme.mix(accent, 0xFFFFFFFF, 0.22F);
                    int bottom = lit ? VBVTheme.mix(accent, 0xFF000000, 0.10F) : VBVTheme.mix(accent, 0xFF000000, 0.22F);
                    graphics.fillGradient(x, y, x + w, y + h, top, bottom);
                    graphics.outline(x, y, w, h, VBVTheme.accentDark());
                    float scale = h >= 30 ? 1.5F : 1.0F;
                    int textW = (int) (font.width(label) * scale);
                    int textH = (int) (font.lineHeight * scale);
                    graphics.pose().pushMatrix();
                    graphics.pose().translate(x + 14, y + (h - textH) / 2.0F + 0.5F);
                    graphics.pose().scale(scale, scale);
                    graphics.text(font, label, 0, 0, VBVTheme.onAccent(), false);
                    graphics.pose().popMatrix();
                    if (lit && w > textW + 40) {
                        chevron(graphics, x + w - 20, y + (h - 9) / 2, VBVTheme.onAccent());
                    }
                }
                case TAB -> {
                    int fill = selected ? VBVTheme.accentAlpha(0x38) : (lit ? 0xB02A2A30 : 0x90101014);
                    graphics.fill(x, y, x + w, y + h, fill);
                    graphics.fill(x, y, x + (selected ? 3 : 2), y + h, selected ? accentLight : VBVTheme.accentAlpha(0x70));
                    int color = !this.active ? TEXT_DISABLED : (selected || lit ? accentLight : TEXT);
                    graphics.text(font, fit(font, label, w - 14), x + 9, y + (h - font.lineHeight) / 2 + 1, color, true);
                }
                case SWATCH -> {
                    graphics.fill(x, y, x + w, y + h, swatchColor);
                    int border = selected ? 0xFFFFFFFF : (lit ? 0xFFD0D0D0 : 0xFF000000);
                    graphics.outline(x, y, w, h, border);
                    if (selected) {
                        graphics.outline(x + 1, y + 1, w - 2, h - 2, 0xFF000000);
                    }
                }
                case SMALL -> {
                    graphics.fill(x, y, x + w, y + h, lit ? VBVTheme.accentAlpha(0x50) : VBVTheme.panel(0xD0));
                    graphics.outline(x, y, w, h, lit ? accentLight : VBVTheme.accentAlpha(0x90));
                    int color = !this.active ? TEXT_DISABLED : (lit ? accentLight : TEXT);
                    String shown = fit(font, label, w - 8);
                    graphics.text(font, shown, x + (w - font.width(shown)) / 2, y + (h - font.lineHeight) / 2 + 1, color, true);
                }
                default -> {
                    graphics.fill(x, y, x + w, y + h, lit ? VBVTheme.panel(0xE8) : VBVTheme.panel(0xB0));
                    int textX = x + 12;
                    if (lit) {
                        graphics.fill(x, y, x + 3, y + h, accentLight);
                        graphics.outline(x, y, w, h, VBVTheme.accentAlpha(0x80));
                        textX += 3;
                        if (w > font.width(label) + 44) {
                            chevron(graphics, x + w - 18, y + (h - 9) / 2, accentLight);
                        }
                    } else {
                        graphics.fill(x, y, x + 2, y + h, VBVTheme.accentAlpha(0x80));
                    }
                    int color = !this.active ? TEXT_DISABLED : (lit ? accentLight : TEXT);
                    graphics.text(font, label, textX, y + (h - font.lineHeight) / 2 + 1, color, true);
                }
            }
        }
    }

    /** A small right-pointing chevron, 5 wide and 9 tall. */
    public static void chevron(GuiGraphicsExtractor graphics, int x, int y, int color) {
        for (int k = 0; k < 5; k++) {
            graphics.fill(x + k, y + k, x + k + 2, y + k + 1, color);
            graphics.fill(x + k, y + 8 - k, x + k + 2, y + 9 - k, color);
        }
    }

    /** Dark themed panel with a thin accent line along the top edge. */
    public static void panel(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, VBVTheme.panel(0xC0));
        graphics.fill(x, y, x + w, y + 1, VBVTheme.accentAlpha(0xB0));
    }

    /** Horizontal progress bar; percent is clamped to 0-100. */
    public static void progressBar(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int percent) {
        int p = Math.max(0, Math.min(100, percent));
        graphics.fill(x, y, x + w, y + h, 0xFF0A0A0C);
        graphics.fill(x, y, x + (w * p) / 100, y + h, VBVTheme.accent());
        graphics.outline(x, y, w, h, VBVTheme.accentDark());
    }

    /** Draws the face and hat layer of a player skin as a size x size square. */
    public static void head(GuiGraphicsExtractor graphics, Identifier skin, int x, int y, int size) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, skin, x, y, 8, 8, size, size, 8, 8, 64, 64);
        graphics.blit(RenderPipelines.GUI_TEXTURED, skin, x, y, 40, 8, size, size, 8, 8, 64, 64);
    }

    /** A 3x3 grid glyph used as the launcher icon. */
    public static void gridIcon(GuiGraphicsExtractor graphics, int x, int y, int color) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                graphics.fill(x + col * 4, y + row * 4, x + col * 4 + 3, y + row * 4 + 3, color);
            }
        }
    }

    /** Truncates text with "..." so it fits maxWidth pixels. */
    public static String fit(Font font, String text, int maxWidth) {
        if (text == null) return "";
        if (maxWidth <= 0) return "";
        if (font.width(text) <= maxWidth) return text;
        String ellipsis = "...";
        int limit = maxWidth - font.width(ellipsis);
        if (limit <= 0) return "";
        int end = text.length();
        while (end > 0 && font.width(text.substring(0, end)) > limit) end--;
        return text.substring(0, end).stripTrailing() + ellipsis;
    }
}
