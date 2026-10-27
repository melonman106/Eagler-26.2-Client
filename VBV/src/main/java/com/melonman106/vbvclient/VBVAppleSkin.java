package com.melonman106.vbvclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** Native AppleSkin-style saturation overlay using the AppleSkin icons atlas. */
public final class VBVAppleSkin {
    private static boolean enabled = true;
    private static boolean showText = false;
    private static final Identifier ICONS = Identifier.parse("appleskin:textures/icons.png");
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

        // AppleSkin's 256x256 icons atlas uses four 9x9 partial saturation frames
        // at U=0,9,18,27. The resource is supplied by Unique Dark - Lite when present.
        for (int i = 0; i < 10; i++) {
            if (i * 2 >= foodLevel) continue;
            float amount = Math.max(0.0F, Math.min(1.0F, (saturation - i * 2.0F) / 2.0F));
            if (amount <= 0.0F) continue;
            int frame = Math.min(3, Math.max(0, (int)Math.ceil(amount * 4.0F) - 1));
            int iconX = guiWidth / 2 + 91 - i * 8;
            graphics.blit(RenderPipelines.GUI_TEXTURED, ICONS, iconX, top - 1,
                frame * 9, 0, 9, 9, 256, 256);
        }

        if (showText) {
            String line = String.format(java.util.Locale.ROOT,
                "Food: %d/20  Saturation: %.1f", foodLevel, saturation);
            graphics.text(minecraft.font, line, x, y, 0xFFFFFFFF, true);
        }
    }
}
