package com.melonman106.vbvclient;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * The first thing shown once the game has finished loading: the animated panorama, the VBV wordmark
 * and a pulsing "PRESS ANY BUTTON TO BEGIN". Any key or mouse button opens the main menu. It is shown
 * once per launch (the main menu opens it before it builds itself), so returning to the title screen
 * later goes straight to the menu.
 */
public final class VBVSplashScreen extends Screen {
    private static boolean done;

    private final Screen next;
    private final long openedAt = System.currentTimeMillis();

    private VBVSplashScreen(Screen next) {
        super(Component.literal("VBV"));
        this.next = next;
    }

    public static boolean isDone() {
        return done;
    }

    /** Shows the splash; {@code next} (the main menu) opens when the player presses something. */
    public static void show(Screen next) {
        VBVNav.open(new VBVSplashScreen(next));
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        begin();
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        begin();
        return true;
    }

    private void begin() {
        // Ignore input for a moment so a key still held from the loading screen does not skip the splash.
        if (System.currentTimeMillis() - openedAt < 350L) {
            return;
        }
        done = true;
        VBVNav.open(next);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int w = graphics.guiWidth();
        int h = graphics.guiHeight();
        long now = System.currentTimeMillis();

        // Soft shading so the text reads on any panorama.
        graphics.fillGradient(0, 0, w, h / 3, 0x99000000, 0x00000000);
        graphics.fillGradient(0, h * 2 / 3, w, h, 0x00000000, 0xB8000000);

        float fade = Math.min(1.0F, (now - openedAt) / 700.0F);

        // Wordmark: big, wide-spaced letters with an accent rule and the edition line below.
        float scale = Math.max(2.0F, Math.min(5.0F, h / 90.0F));
        String mark = "VBV";
        int tracking = Math.max(3, (int) (scale * 3.0F));
        int markW = (int) (VBVUi.trackedWidth(font, mark, tracking) * scale);
        int markH = (int) (font.lineHeight * scale);
        int markX = (w - markW) / 2;
        int markY = h / 2 - markH - Math.max(4, h / 40);
        graphics.pose().pushMatrix();
        graphics.pose().translate(markX, markY);
        graphics.pose().scale(scale, scale);
        VBVUi.trackedText(graphics, font, mark, 0, 0, withAlpha(0xFFFFFFFF, fade), tracking, true);
        graphics.pose().popMatrix();

        int ruleW = Math.max(40, markW / 2);
        graphics.fill((w - ruleW) / 2, markY + markH + 6, (w + ruleW) / 2, markY + markH + 7, withAlpha(VBVTheme.accent(), fade));
        String edition = "EAGLER 26.2 EDITION";
        int edW = VBVUi.trackedWidth(font, edition, 2);
        VBVUi.trackedText(graphics, font, edition, (w - edW) / 2, markY + markH + 13, withAlpha(VBVUi.TEXT_DIM, fade), 2, true);

        // Pulsing prompt near the bottom.
        double pulse = 0.55 + 0.45 * Math.sin(now / 420.0);
        int alpha = (int) (Math.max(0.0F, Math.min(1.0F, fade)) * (90 + pulse * 165));
        String prompt = "PRESS ANY BUTTON TO BEGIN";
        int pw = VBVUi.trackedWidth(font, prompt, 2);
        VBVUi.trackedText(graphics, font, prompt, (w - pw) / 2, h - Math.max(34, h / 7), (alpha << 24) | 0xFFFFFF, 2, true);

        // Small print: how long loading took, and the version.
        String boot = VBVBoot.headline();
        if (!boot.isEmpty()) {
            graphics.text(font, boot, 8, h - 12, VBVUi.TEXT_DISABLED, false);
        }
        String version = "v" + VBVClient.VERSION;
        graphics.text(font, version, w - 8 - font.width(version), h - 12, VBVUi.TEXT_DISABLED, false);
    }

    private static int withAlpha(int argb, float fade) {
        int a = (int) (((argb >>> 24) & 0xFF) * Math.max(0.0F, Math.min(1.0F, fade)));
        return (a << 24) | (argb & 0xFFFFFF);
    }
}
