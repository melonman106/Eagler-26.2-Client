package com.melonman106.vbvclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * VBV loading screen. It replaces the red Mojang logo and plain progress bar that
 * {@code LoadingOverlay} shows while the resources reload, which is the long wait at start-up.
 *
 * <p>Layout: a dark backdrop with a faint accent glow, the VBV wordmark, a segmented progress bar
 * with a light sweeping across it (so you can see the game is alive even while the percentage is
 * not moving), the name of the loading step that is running, the step count, the elapsed time, and a
 * rotating tip. It only uses a few dozen flat rectangles and short text per frame, so it does not
 * compete with the loading work on a low-power machine.</p>
 *
 * {@code patch/apply_dungeons_home.py} hooks {@link #render} into {@code LoadingOverlay} and points
 * the overlay's background colour at {@link #BACKGROUND}.
 */
public final class VBVBootScreen {
    /** Backdrop colour used by the loading overlay (replaces Mojang red / black). */
    public static final int BACKGROUND = 0xFF08070D;

    private static final String[] TIPS = {
        "Pick your menu backdrop in Extras > Panoramas.",
        "Resource Packs has a Modrinth button for installing packs.",
        "Change the menu colour in Extras > Appearance.",
        "Options > Modded Settings has LOD and animation range controls.",
        "Your skin, cape and name live in Extras > Hero.",
    };
    private static final long TIP_MS = 6000L;

    private static long startedAt = -1L;

    private VBVBootScreen() {}

    /**
     * Draws one frame.
     *
     * @param progress   smoothed reload progress, 0..1
     * @param fadeIn     0..1, how visible the screen is while it fades in
     * @param fadeOut    0..1, how far the hand-over to the menu has gone
     * @param reloadDone true once every resource step has finished
     */
    public static void render(GuiGraphicsExtractor graphics, int width, int height, float progress,
                              float fadeIn, float fadeOut, boolean reloadDone) {
        Font font = Minecraft.getInstance().font;
        long now = System.currentTimeMillis();
        if (startedAt < 0L) {
            startedAt = now;
        }
        long t = now - startedAt;
        float visible = clamp01(fadeIn) * (1.0F - clamp01(fadeOut));
        int accent = VBVTheme.accent();
        int accentLight = VBVTheme.accentLight();

        // ---- backdrop: a soft accent glow rising from the bottom, shading at the top edge
        graphics.fillGradient(0, height / 2, width, height, 0x00000000, fade(VBVTheme.accentAlpha(0x34), visible));
        graphics.fillGradient(0, 0, width, Math.max(1, height / 4), fade(0x88000000, visible), 0x00000000);

        // ---- wordmark
        int scale = height >= 300 ? 4 : (height >= 220 ? 3 : 2);
        int tracking = 4;
        String mark = "VBV";
        int markW = VBVUi.trackedWidth(font, mark, tracking) * scale;
        int markH = font.lineHeight * scale;
        int markX = (width - markW) / 2;
        int markY = (int) (height * 0.26F);
        int pulse = (int) (0x2C + 0x1C * Math.sin(t / 700.0D));
        int glow = fade(VBVTheme.accentAlpha(pulse), visible);
        for (int i = 0; i < 4; i++) {
            int dx = i == 0 ? -scale : (i == 1 ? scale : 0);
            int dy = i == 2 ? -scale : (i == 3 ? scale : 0);
            drawScaled(graphics, font, mark, markX + dx, markY + dy, scale, tracking, glow, false);
        }
        drawScaled(graphics, font, mark, markX, markY, scale, tracking, fade(0xFFFFFFFF, visible), true);

        // ---- accent rule and edition line
        int ruleY = markY + markH + 6;
        int ruleW = Math.max(36, markW / 2);
        graphics.fill((width - ruleW) / 2, ruleY, (width + ruleW) / 2, ruleY + 1, fade(accent, visible));
        String edition = "EAGLER 26.2 EDITION";
        int edW = VBVUi.trackedWidth(font, edition, 2);
        VBVUi.trackedText(graphics, font, edition, (width - edW) / 2, ruleY + 8, fade(VBVUi.TEXT_DIM, visible), 2, true);

        // ---- segmented progress bar
        int pitch = 6;
        int segments = Math.max(20, Math.min(54, (int) (width * 0.55F) / pitch));
        int barW = segments * pitch - 1;
        int barX = (width - barW) / 2;
        int barY = (int) (height * 0.64F);
        float filled = clamp01(progress) * segments;
        int whole = (int) filled;
        float part = filled - whole;
        int sweep = (int) ((t / 38L) % (segments + 10)) - 5;
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
            // A short light band travels along the bar so it never looks frozen.
            int d = i - sweep;
            if (d >= 0 && d < 4) {
                int lift = (4 - d) * 0x18;
                color = brighten(color, lift, i < whole || (i == whole && part > 0.0F));
            }
            graphics.fill(x, barY, x + pitch - 1, barY + 7, fade(color, visible));
        }

        // ---- percent (big, right) and the running step (left), both above the bar
        int percent = Math.max(0, Math.min(100, Math.round(progress * 100.0F)));
        String percentText = percent + "%";
        int pw = font.width(percentText) * 2;
        graphics.pose().pushMatrix();
        graphics.pose().translate(barX + barW - pw, barY - 22);
        graphics.pose().scale(2.0F, 2.0F);
        graphics.text(font, percentText, 0, 0, fade(0xFFFFFFFF, visible), true);
        graphics.pose().popMatrix();

        String stage = reloadDone ? "READY" : VBVBoot.currentStage();
        if (stage.isEmpty()) {
            stage = "STARTING UP";
        }
        String dots = reloadDone ? "" : ".".repeat((int) ((t / 400L) % 4L));
        String label = stage + dots;
        graphics.text(font, "NOW LOADING", barX, barY - 22, fade(accentLight, visible), true);
        VBVUi.trackedText(graphics, font, VBVUi.fit(font, label, Math.max(40, barW - pw - 12)), barX, barY - 11,
            fade(0xFFFFFFFF, visible), 1, true);

        // ---- step count and elapsed time, below the bar
        int total = VBVBoot.stepsTotal();
        String steps = total > 0 ? "STEP " + Math.min(total, VBVBoot.stepsDone() + (reloadDone ? 0 : 1)) + " OF " + total : "";
        graphics.text(font, steps, barX, barY + 13, fade(VBVUi.TEXT_DIM, visible), false);
        long seconds = VBVBoot.elapsedMs() / 1000L;
        String elapsed = (seconds / 60L) + ":" + (seconds % 60L < 10L ? "0" : "") + (seconds % 60L);
        graphics.text(font, elapsed, barX + barW - font.width(elapsed), barY + 13, fade(VBVUi.TEXT_DIM, visible), false);

        // ---- rotating tip and version
        String tip = "TIP  " + TIPS[(int) ((t / TIP_MS) % TIPS.length)];
        int tipW = font.width(tip);
        graphics.text(font, tip, (width - tipW) / 2, height - 26, fade(VBVUi.TEXT_DIM, visible), false);
        String version = "v" + VBVClient.VERSION;
        graphics.text(font, version, width - 8 - font.width(version), height - 12, fade(VBVUi.TEXT_DISABLED, visible), false);
    }

    private static void drawScaled(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int scale,
                                   int tracking, int color, boolean shadow) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);
        VBVUi.trackedText(graphics, font, text, 0, 0, color, tracking, shadow);
        graphics.pose().popMatrix();
    }

    private static float clamp01(float v) {
        return v < 0.0F ? 0.0F : Math.min(1.0F, v);
    }

    /** Multiplies the alpha of an ARGB colour by {@code amount} (kept at least 4 so text is never forced opaque). */
    private static int fade(int argb, float amount) {
        int a = (int) (((argb >>> 24) & 0xFF) * clamp01(amount));
        return (Math.max(a, 4) << 24) | (argb & 0xFFFFFF);
    }

    private static int mix(int from, int to, float amount) {
        float k = clamp01(amount);
        int r = (int) (((from >> 16) & 0xFF) * (1.0F - k) + ((to >> 16) & 0xFF) * k);
        int g = (int) (((from >> 8) & 0xFF) * (1.0F - k) + ((to >> 8) & 0xFF) * k);
        int b = (int) ((from & 0xFF) * (1.0F - k) + (to & 0xFF) * k);
        return (r << 16) | (g << 8) | b;
    }

    /** Adds a white lift: a filled segment gets lighter, an empty one gets a faint glint. */
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
