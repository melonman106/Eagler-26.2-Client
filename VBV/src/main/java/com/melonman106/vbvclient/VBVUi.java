package com.melonman106.vbvclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Shared drawing helpers and widgets for the VBV menus.
 *
 * <p>Buttons use Minecraft's own widget sprites and panels use the vanilla list background,
 * so every VBV screen follows the active resource pack and matches the vanilla UI.</p>
 */
public final class VBVUi {
    public static final int TEXT = 0xFFFFFFFF;
    public static final int TEXT_DIM = 0xFFA0A0A0;
    public static final int TEXT_DISABLED = 0xFF707070;

    /** Vanilla menu textures, resolved through the active resource pack. */
    private static final Identifier MENU_LIST_BACKGROUND =
        Identifier.withDefaultNamespace("textures/gui/menu_list_background.png");
    private static final Identifier INWORLD_LIST_BACKGROUND =
        Identifier.withDefaultNamespace("textures/gui/inworld_menu_list_background.png");
    private static final Identifier HEADER_SEPARATOR =
        Identifier.withDefaultNamespace("textures/gui/header_separator.png");
    private static final Identifier INWORLD_HEADER_SEPARATOR =
        Identifier.withDefaultNamespace("textures/gui/inworld_header_separator.png");
    private static final Identifier FOOTER_SEPARATOR =
        Identifier.withDefaultNamespace("textures/gui/footer_separator.png");
    private static final Identifier INWORLD_FOOTER_SEPARATOR =
        Identifier.withDefaultNamespace("textures/gui/inworld_footer_separator.png");

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

    /**
     * Button that keeps VBV's sizing helpers but draws exactly like a vanilla Minecraft button:
     * the same sprites, hover/focus highlight, disabled state and centred label from the
     * resource pack.
     */
    public static final class ThemedButton extends Button {
        private final Style style;
        private boolean selected;
        private int swatchColor = 0xFFFFFFFF;

        public ThemedButton(int x, int y, int w, int h, String label, Style style, Button.OnPress onPress) {
            super(x, y, w, h, Component.literal(label), onPress, DEFAULT_NARRATION);
            this.style = style;
            if (style == Style.TAB) {
                // The selected tab keeps the vanilla "highlighted" sprite so it reads as active.
                setOverrideRenderHighlightedSprite(() -> this.selected || this.isHoveredOrFocused());
            }
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
            extractDefaultSprite(graphics);
            if (style == Style.SWATCH) {
                int x = getX();
                int y = getY();
                int w = getWidth();
                int h = getHeight();
                graphics.fill(x + 3, y + 3, x + w - 3, y + h - 3, swatchColor);
                graphics.outline(x + 2, y + 2, w - 4, h - 4, selected ? 0xFFFFFFFF : 0xFF000000);
                return;
            }
            extractDefaultLabel(graphics.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE));
        }
    }

    /**
     * Square, texture-backed corner button. It uses the vanilla button sprite as its frame, so it
     * matches the other menus, with the icon drawn on top and the label exposed as a tooltip.
     */
    public static final class IconButton extends Button {
        private final Identifier texture;

        public IconButton(int x, int y, int size, Identifier texture, String tooltip, Button.OnPress onPress) {
            super(x, y, size, size, Component.literal(tooltip), onPress, DEFAULT_NARRATION);
            this.texture = texture;
            setTooltip(Tooltip.create(Component.literal(tooltip)));
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            extractDefaultSprite(graphics);
            int x = getX();
            int y = getY();
            int size = getWidth();
            graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x + 2, y + 2, 0, 0,
                    size - 4, size - 4, 64, 64, 64, 64);
        }
    }

    /** Width of {@code text} drawn by {@link #trackedText} with {@code tracking} extra pixels between letters. */
    public static int trackedWidth(Font font, String text, int tracking) {
        return Math.max(0, font.width(text) + Math.max(0, text.length() - 1) * tracking);
    }

    /** Draws wide-spaced letters (the look of a sci-fi menu) and returns the width used. */
    public static int trackedText(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int color,
                                  int tracking, boolean shadow) {
        int cx = x;
        for (int i = 0; i < text.length(); i++) {
            String ch = text.substring(i, i + 1);
            graphics.text(font, ch, cx, y, color, shadow);
            cx += font.width(ch) + tracking;
        }
        return cx - x - tracking;
    }

    /**
     * A translucent bar whose right end is cut on a slant (the highlight behind the selected menu
     * item). The top edge is {@code w} wide, the bottom edge {@code slant} pixels shorter.
     */
    public static void slantedBar(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int slant, int color) {
        for (int row = 0; row < h; row++) {
            int inset = h <= 1 ? 0 : row * slant / (h - 1);
            graphics.fill(x, y + row, x + w - inset, y + row + 1, color);
        }
    }

    /**
     * Like {@link #slantedBar} but mirrored: the bar's left end is cut on a slant (the top edge starts
     * {@code slant} pixels further left than the bottom one), for items hugging the right screen edge.
     */
    public static void slantedBarFromRight(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int slant, int color) {
        for (int row = 0; row < h; row++) {
            int inset = h <= 1 ? 0 : (h - 1 - row) * slant / (h - 1);
            graphics.fill(x + inset, y + row, x + w, y + row + 1, color);
        }
    }

    /** A small right-pointing chevron, 5 wide and 9 tall. */
    public static void chevron(GuiGraphicsExtractor graphics, int x, int y, int color) {
        for (int k = 0; k < 5; k++) {
            graphics.fill(x + k, y + k, x + k + 2, y + k + 1, color);
            graphics.fill(x + k, y + 8 - k, x + k + 2, y + 9 - k, color);
        }
    }

    private static boolean inWorld() {
        return Minecraft.getInstance().level != null;
    }

    /** Tiled vanilla list background, used for cards and other flat panels. */
    public static void panelBackground(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) return;
        Identifier texture = inWorld() ? INWORLD_LIST_BACKGROUND : MENU_LIST_BACKGROUND;
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0, 0, w, h, 32, 32);
    }

    /** Vanilla list panel: tiled list background framed by the vanilla header and footer separators. */
    public static void panel(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) return;
        boolean inWorld = inWorld();
        panelBackground(graphics, x, y, w, h);
        graphics.blit(RenderPipelines.GUI_TEXTURED, inWorld ? INWORLD_HEADER_SEPARATOR : HEADER_SEPARATOR,
                x, y - 2, 0, 0, w, 2, 32, 2);
        graphics.blit(RenderPipelines.GUI_TEXTURED, inWorld ? INWORLD_FOOTER_SEPARATOR : FOOTER_SEPARATOR,
                x, y + h, 0, 0, w, 2, 32, 2);
    }

    /** Horizontal progress bar; percent is clamped to 0-100. */
    public static void progressBar(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int percent) {
        int p = Math.max(0, Math.min(100, percent));
        graphics.fill(x, y, x + w, y + h, 0xFF0A0A0C);
        graphics.fill(x, y, x + (w * p) / 100, y + h, VBVTheme.accent());
        graphics.outline(x, y, w, h, 0xFF373737);
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
