package com.melonman106.vbvclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The VBV look for the screens shown while a world or the integrated server starts up.
 *
 * <p>Before this, opening a world dropped you onto the vanilla busy screen (tiled dirt background,
 * one status line) and, on the single-thread path, the vanilla {@code LevelLoadingScreen} with its
 * green progress bar - neither of which matched the dark, gold boot screen and main menu.</p>
 *
 * <p>Both screens now draw the same parts as the boot screen: the {@code #08070D} backdrop with the
 * accent glow, the VBV wordmark and edition line, and a segmented bar with a light that keeps
 * travelling along it. That last part matters on a slow machine: when the percentage sits still for
 * several seconds because one generation step is running, the bar still moves, so the screen never
 * looks frozen.</p>
 */
public final class VBVLoadingTheme {
    /** Same backdrop as the boot screen, so the hand-off between them is seamless. */
    public static final int BACKGROUND = VBVBootScreen.BACKGROUND;

    private VBVLoadingTheme() {}

    /** Dark backdrop with the accent glow rising from the bottom and a shade along the top edge. */
    public static void backdrop(GuiGraphicsExtractor graphics, int width, int height) {
        graphics.fill(0, 0, width, height, BACKGROUND);
        graphics.fillGradient(0, height / 2, width, height, 0x00000000, VBVTheme.accentAlpha(0x34));
        graphics.fillGradient(0, 0, width, Math.max(1, height / 4), 0x88000000, 0x00000000);
    }

    /**
     * Wordmark, accent rule, edition line, segmented bar, percentage, the running step and how long
     * the screen has been up.
     *
     * @param progress 0..1, or a negative value when the game has not reported a percentage yet
     * @param stage    the step being run, such as "Preparing spawn area"
     * @param elapsed  milliseconds since the screen opened
     */
    public static void shell(GuiGraphicsExtractor graphics, int width, int height, float progress,
                             String stage, long elapsedMs) {
        shell(graphics, width, height, progress, stage, elapsedMs, true);
    }

    /**
     * Same shell, with {@code labels} off when the caller already draws its own status and timer
     * (the integrated-server busy screen does, through its widgets).
     */
    public static void shell(GuiGraphicsExtractor graphics, int width, int height, float progress,
                             String stage, long elapsedMs, boolean labels) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.font == null) {
            return;
        }
        Font font = minecraft.font;
        long now = System.currentTimeMillis();
        int accent = VBVTheme.accent();
        int accentLight = VBVTheme.accentLight();

        // ---- wordmark
        int scale = height >= 320 ? 3 : 2;
        int tracking = 4;
        String mark = "VBV";
        int markW = VBVUi.trackedWidth(font, mark, tracking) * scale;
        int markH = font.lineHeight * scale;
        int markX = (width - markW) / 2;
        int markY = (int) (height * 0.28F);
        graphics.pose().pushMatrix();
        graphics.pose().translate(markX, markY);
        graphics.pose().scale(scale, scale);
        VBVUi.trackedText(graphics, font, mark, 0, 0, 0xFFFFFFFF, tracking, true);
        graphics.pose().popMatrix();

        int ruleY = markY + markH + 6;
        int ruleW = Math.max(36, markW / 2);
        graphics.fill((width - ruleW) / 2, ruleY, (width + ruleW) / 2, ruleY + 1, accent);
        String edition = "EAGLER 26.2 EDITION";
        int edW = VBVUi.trackedWidth(font, edition, 2);
        VBVUi.trackedText(graphics, font, edition, (width - edW) / 2, ruleY + 8, VBVUi.TEXT_DIM, 2, true);

        // ---- segmented bar with a travelling highlight
        int known = progress < 0.0F ? 0 : Math.max(0, Math.min(100, Math.round(progress * 100.0F)));
        int pitch = 6;
        int segments = Math.max(20, Math.min(54, (int) (width * 0.55F) / pitch));
        int barW = segments * pitch - 1;
        int barX = (width - barW) / 2;
        int barY = (int) (height * 0.68F);
        float filled = (known / 100.0F) * segments;
        int whole = (int) filled;
        float part = filled - whole;
        int sweep = (int) ((now / 38L) % (segments + 10)) - 5;
        for (int i = 0; i < segments; i++) {
            int x = barX + i * pitch;
            int color;
            if (i < whole) {
                color = mix(accent, accentLight, (float) i / segments) | 0xFF000000;
            } else if (i == whole && whole < segments) {
                int a = 0x30 + (int) (0xB0 * part);
                color = (a << 24) | (accentLight & 0xFFFFFF);
            } else {
                color = 0x26FFFFFF;
            }
            int d = i - sweep;
            if (d >= 0 && d < 4) {
                int lift = (4 - d) * 0x18;
                color = brighten(color, lift, i < whole || (i == whole && part > 0.0F));
            }
            graphics.fill(x, barY, x + pitch - 1, barY + 7, color);
        }

        if (!labels) {
            return;
        }

        // ---- percentage on the right, the running step on the left
        String percentText = known + "%";
        int pw = font.width(percentText) * 2;
        graphics.pose().pushMatrix();
        graphics.pose().translate(barX + barW - pw, barY - 22);
        graphics.pose().scale(2.0F, 2.0F);
        graphics.text(font, percentText, 0, 0, 0xFFFFFFFF, true);
        graphics.pose().popMatrix();

        graphics.text(font, "NOW LOADING", barX, barY - 22, accentLight, true);
        String dots = ".".repeat((int) ((now / 400L) % 4L));
        String label = (stage == null || stage.isEmpty() ? "STARTING UP" : stage) + dots;
        VBVUi.trackedText(graphics, font, VBVUi.fit(font, label, Math.max(40, barW - pw - 12)),
            barX, barY - 11, 0xFFFFFFFF, 1, true);

        // ---- elapsed time underneath, so a long step is visibly still counting
        long seconds = Math.max(0L, elapsedMs) / 1000L;
        String elapsed = (seconds / 60L) + ":" + (seconds % 60L < 10L ? "0" : "") + (seconds % 60L);
        graphics.text(font, elapsed, barX, barY + 13, VBVUi.TEXT_DIM, false);
        String version = "v" + VBVClient.VERSION;
        graphics.text(font, version, width - 8 - font.width(version), height - 12, VBVUi.TEXT_DISABLED, false);
    }

    private static int mix(int from, int to, float amount) {
        float k = Math.max(0.0F, Math.min(1.0F, amount));
        int r = (int) (((from >> 16) & 0xFF) * (1.0F - k) + ((to >> 16) & 0xFF) * k);
        int g = (int) (((from >> 8) & 0xFF) * (1.0F - k) + ((to >> 8) & 0xFF) * k);
        int b = (int) ((from & 0xFF) * (1.0F - k) + (to & 0xFF) * k);
        return (r << 16) | (g << 8) | b;
    }

    private static int brighten(int argb, int lift, boolean filled) {
        if (!filled) {
            int a = Math.min(0xFF, ((argb >>> 24) & 0xFF) + lift);
            return (a << 24) | (argb & 0xFFFFFF);
        }
        int r = Math.min(0xFF, ((argb >> 16) & 0xFF) + lift);
        int g = Math.min(0xFF, ((argb >> 8) & 0xFF) + lift);
        int b = Math.min(0xFF, (argb & 0xFF) + lift);
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }
}
