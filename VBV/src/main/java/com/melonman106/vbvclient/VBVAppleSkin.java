package com.melonman106.vbvclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;

/** Native AppleSkin-style saturation overlay drawn on the hunger bar. */
public final class VBVAppleSkin {
    private static boolean enabled = true;
    private static boolean showText = false;
    static int x = 4;
    static int y = 4;
    private VBVAppleSkin() {}
    public static boolean isEnabled() { return enabled; }
    public static void toggleEnabled() { enabled = !enabled; }
    public static boolean isShowText() { return showText; }
    public static void toggleShowText() { showText = !showText; }

    public static void render(GuiGraphicsExtractor graphics) {
        if (!enabled) return;
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) return;

        int foodLevel = Math.max(0, Math.min(20, player.getFoodData().getFoodLevel()));
        float saturation = Math.max(0.0F, Math.min(20.0F, player.getFoodData().getSaturationLevel()));
        int guiWidth = graphics.guiWidth();
        int guiHeight = graphics.guiHeight();
        int startX = guiWidth / 2 + 10;
        int top = guiHeight - 39;
        for (int i = 0; i < 10; i++) {
            int iconX = startX + 80 - i * 8;
            float amount = Math.max(0.0F, Math.min(1.0F, (saturation - i * 2.0F) / 2.0F));
            if (amount > 0.0F && i * 2 < foodLevel) {
                int alpha = 70 + Math.round(65.0F * amount);
                int color = (alpha << 24) | 0x00FFD34A;
                int right = Math.min(iconX + 8, iconX + 1 + Math.max(1, Math.round(6.0F * amount)));
                graphics.fill(iconX + 1, top + 1, right, top + 8, color);
            }
        }
        if (showText) {
            String line = String.format(java.util.Locale.ROOT, "Food: %d/20  Saturation: %.1f", foodLevel, saturation);
            graphics.text(minecraft.font, line, x, y, 0xFFFFFFFF, true);
        }
    }
}
