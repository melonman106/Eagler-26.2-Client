package com.melonman106.vbvclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;

/**
 * Lightweight native AppleSkin-style information overlay for the browser client.
 * It exposes the live food and saturation values without relying on
 * Fabric events or a Java mod loader.
 */
public final class VBVAppleSkin {
    private static boolean enabled = true;
    private VBVAppleSkin() {}
    public static boolean isEnabled() { return enabled; }
    public static void toggleEnabled() { enabled = !enabled; }

    public static void render(GuiGraphicsExtractor graphics) {
        if (!enabled) return;
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }

        var food = player.getFoodData();
        int foodLevel = food.getFoodLevel();
        float saturation = food.getSaturationLevel();
        String line = String.format(java.util.Locale.ROOT,
            "Food: %d/20  Saturation: %.1f",
            foodLevel, saturation);
        int y = minecraft.getWindow().getGuiScaledHeight() - 46;
        graphics.text(minecraft.font, line, 4, y, 0xFFFFFFFF, true);
    }
}
