package com.melonman106.vbvclient;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * Potion HUD: a compact list of the active status effects with their icon, amplifier and remaining
 * time. It is drawn in the shared post-vanilla HUD phase, so it works in every world and server.
 *
 * <p>The panel hugs the right edge below the vanilla effect icons; the saved offset moves it from
 * there and the HUD Editor previews the same position. The list is rebuilt at most ten times a second
 * because sorting and the effect lookups do not need to run on every frame.</p>
 */
public final class VBVPotionHud {
    public static boolean enabled = true;
    public static boolean showTimer = true;
    public static boolean showAmplifier = true;
    public static boolean compact = false;
    public static int offsetX = 0;
    public static int offsetY = 0;

    /** Last drawn size, used by the HUD Editor preview. */
    public static int lastWidth = 132;
    public static int lastHeight = 54;

    private static final List<MobEffectInstance> SORTED = new ArrayList<>();
    private static final List<String> NAMES = new ArrayList<>();
    private static final List<String> TIMES = new ArrayList<>();
    private static long builtAt;
    private static int builtCount = -1;

    private VBVPotionHud() {}

    public static void render(GuiGraphicsExtractor graphics) {
        if (!enabled) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) return;
        var effects = minecraft.player.getActiveEffects();
        if (effects.isEmpty()) return;

        long now = System.currentTimeMillis();
        if (now - builtAt > 100L || now < builtAt || effects.size() != builtCount) {
            builtAt = now;
            builtCount = effects.size();
            rebuild(effects);
        }
        if (SORTED.isEmpty()) return;

        Font font = minecraft.font;
        int rowH = compact ? 14 : 18;
        int icon = compact ? 12 : 16;
        int nameWidth = 0;
        int timeWidth = 0;
        for (int i = 0; i < NAMES.size(); i++) {
            nameWidth = Math.max(nameWidth, font.width(NAMES.get(i)));
            timeWidth = Math.max(timeWidth, font.width(TIMES.get(i)));
        }
        int padding = 5;
        int panelW = Math.max(86, Math.min(220, padding * 2 + icon + 4 + nameWidth + (timeWidth > 0 ? 8 + timeWidth : 0)));
        int panelH = padding * 2 + SORTED.size() * rowH - (compact ? 2 : 3);
        int x = graphics.guiWidth() - panelW - 6 + offsetX;
        int y = 54 + offsetY;
        if (y + panelH > graphics.guiHeight() - 4) y = Math.max(4, graphics.guiHeight() - panelH - 4);
        lastWidth = panelW;
        lastHeight = panelH;

        graphics.fill(x, y, x + panelW, y + panelH, VBVTheme.panel(0xA8));
        graphics.fill(x, y, x + 2, y + panelH, VBVTheme.accentAlpha(0xB0));

        int rowY = y + padding;
        for (int i = 0; i < SORTED.size(); i++) {
            MobEffectInstance effect = SORTED.get(i);
            boolean good = effect.getEffect().value().isBeneficial();
            int color = good ? VBVUi.TEXT : 0xFFFF8C8C;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, Hud.getMobEffectSprite(effect.getEffect()),
                x + padding, rowY + (rowH - icon) / 2, icon, icon);
            graphics.text(font, VBVUi.fit(font, NAMES.get(i), nameWidth + 1), x + padding + icon + 4,
                rowY + (rowH - font.lineHeight) / 2 + 1, color, false);
            if (!TIMES.get(i).isEmpty()) {
                graphics.text(font, TIMES.get(i), x + panelW - padding - font.width(TIMES.get(i)),
                    rowY + (rowH - font.lineHeight) / 2 + 1, VBVUi.TEXT_DIM, false);
            }
            rowY += rowH;
        }
    }

    private static void rebuild(java.util.Collection<MobEffectInstance> effects) {
        SORTED.clear();
        NAMES.clear();
        TIMES.clear();
        SORTED.addAll(effects);
        // Beneficial effects first, then by name; the amplifier breaks ties so the strongest shows first.
        SORTED.sort((a, b) -> {
            boolean aGood = a.getEffect().value().isBeneficial();
            boolean bGood = b.getEffect().value().isBeneficial();
            if (aGood != bGood) return aGood ? -1 : 1;
            int byName = effectName(a).compareToIgnoreCase(effectName(b));
            if (byName != 0) return byName;
            return Integer.compare(b.getAmplifier(), a.getAmplifier());
        });
        for (MobEffectInstance effect : SORTED) {
            NAMES.add(effectName(effect) + (showAmplifier ? amplifier(effect.getAmplifier()) : ""));
            TIMES.add(showTimer ? duration(effect.getDuration()) : "");
        }
    }

    private static String effectName(MobEffectInstance effect) {
        try {
            return effect.getEffect().value().getDisplayName().getString();
        } catch (RuntimeException ignored) {
            return "Effect";
        }
    }

    /** III for amplifier 2, empty for the base level. */
    static String amplifier(int level) {
        if (level <= 0) return "";
        int value = Math.min(10, level);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value; i++) out.append('I');
        return " " + out;
    }

    static String duration(int ticks) {
        if (ticks < 0) return "∞";
        int seconds = ticks / 20;
        if (seconds >= 3600) return String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60);
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }
}
