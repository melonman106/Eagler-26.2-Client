package com.melonman106.vbvclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

/**
 * Native AppleSkin port: a saturation overlay on the hunger bar and, like the original, a flashing
 * preview of how much hunger and saturation the food in your hand will restore.
 */
public final class VBVAppleSkin {
    private static boolean enabled = true;
    private static boolean showText = false;
    private static boolean showFoodText = false;
    private static boolean showSaturationText = false;
    private static boolean foodPreview = true;
    private static final Identifier FOOD_FULL = Identifier.withDefaultNamespace("hud/food_full");
    private static final Identifier FOOD_HALF = Identifier.withDefaultNamespace("hud/food_half");
    private static final Identifier ICONS = Identifier.parse("appleskin:textures/icons.png");
    static int x = 4;
    static int y = 4;

    private VBVAppleSkin() {}
    public static boolean isEnabled() { return enabled; }
    public static void toggleEnabled() { enabled = !enabled; }
    public static boolean isFoodPreview() { return foodPreview; }
    public static void setFoodPreview(boolean value) { foodPreview = value; }
    public static void toggleFoodPreview() { foodPreview = !foodPreview; }
    public static boolean isShowText() { return showText; }
    public static void toggleShowText() { showText = !showText; showFoodText = showText; showSaturationText = showText; }
    public static boolean isShowFoodText() { return showFoodText; }
    public static boolean isShowSaturationText() { return showSaturationText; }
    public static void toggleFoodText() { showFoodText = !showFoodText; showText = showFoodText || showSaturationText; }
    public static void toggleSaturationText() { showSaturationText = !showSaturationText; showText = showFoodText || showSaturationText; }
    public static void loadSettings(boolean active, boolean foodText, boolean saturationText, int xOffset, int yOffset) {
        enabled = active; showFoodText = foodText; showSaturationText = saturationText;
        showText = foodText || saturationText; x = xOffset; y = yOffset;
    }

    /** Food the player is holding (main hand first, then off hand), or null. */
    private static FoodProperties heldFood(LocalPlayer player) {
        ItemStack main = player.getMainHandItem();
        FoodProperties food = main.isEmpty() ? null : main.get(DataComponents.FOOD);
        if (food != null) return food;
        ItemStack off = player.getOffhandItem();
        return off.isEmpty() ? null : off.get(DataComponents.FOOD);
    }

    public static void render(GuiGraphicsExtractor graphics) {
        if (!enabled) return;
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) return;

        int foodLevel = Math.max(0, Math.min(20, player.getFoodData().getFoodLevel()));
        float saturation = Math.max(0.0F, Math.min(20.0F, player.getFoodData().getSaturationLevel()));
        int guiWidth = graphics.guiWidth();
        int guiHeight = graphics.guiHeight();
        int top = guiHeight - 39;

        // Current saturation on top of the vanilla hunger bar.
        drawSaturation(graphics, guiWidth, top, foodLevel, saturation, null, -1);

        // Held-food preview: flash the shanks the food would fill, and the saturation it would add.
        FoodProperties held = foodPreview ? heldFood(player) : null;
        if (held != null && (foodLevel < 20 || held.canAlwaysEat())) {
            int afterFood = Math.min(20, foodLevel + Math.max(0, held.nutrition()));
            float afterSaturation = Math.min((float) afterFood, saturation + Math.max(0.0F, held.saturation()));
            float pulse = 0.35F + 0.4F * (0.5F + 0.5F * (float) Math.sin(System.currentTimeMillis() / 180.0D));
            for (int i = 0; i < 10; i++) {
                int before = Math.max(0, Math.min(2, foodLevel - i * 2));
                int after = Math.max(0, Math.min(2, afterFood - i * 2));
                if (after <= before) continue;
                int shankX = guiWidth / 2 + 82 - i * 8;
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, after >= 2 ? FOOD_FULL : FOOD_HALF,
                    shankX, top, 9, 9, pulse);
            }
            drawSaturation(graphics, guiWidth, top, afterFood, afterSaturation, Float.valueOf(saturation), pulse);
        }

        if (showFoodText || showSaturationText) {
            String line = (showFoodText ? "Food: " + foodLevel + "/20" : "")
                + (showFoodText && showSaturationText ? "  " : "")
                + (showSaturationText ? String.format(java.util.Locale.ROOT, "Saturation: %.1f", saturation) : "");
            graphics.text(minecraft.font, line, x, y, 0xFFFFFFFF, true);
        }
    }

    /**
     * Draws the saturation overlay. With {@code onlyIncreaseOver == null} every covered shank is drawn
     * opaque; otherwise only shanks whose saturation grows past that value are drawn, at {@code alpha}.
     */
    private static void drawSaturation(GuiGraphicsExtractor graphics, int guiWidth, int top, int foodLevel,
                                       float saturation, Float onlyIncreaseOver, float alpha) {
        // Match vanilla's exact hunger-bar x positions:
        // xRight = guiWidth / 2 + 91, then xo = xRight - i * 8 - 9.
        int color = alpha < 0.0F ? -1 : (Math.max(0, Math.min(255, Math.round(alpha * 255.0F))) << 24) | 0xFFFFFF;
        for (int i = 0; i < 10; i++) {
            if (i * 2 >= foodLevel) continue;
            float amount = Math.max(0.0F, Math.min(1.0F, (saturation - i * 2.0F) / 2.0F));
            if (amount <= 0.0F) continue;
            if (onlyIncreaseOver != null) {
                float before = Math.max(0.0F, Math.min(1.0F, (onlyIncreaseOver - i * 2.0F) / 2.0F));
                if (amount <= before) continue;
            }
            int frame = Math.min(3, Math.max(0, (int) Math.ceil(amount * 4.0F) - 1));
            int iconX = guiWidth / 2 + 82 - i * 8;
            // Draw the full 9x9 atlas cell (one pixel larger than the previous 8x8 icon).
            graphics.blit(RenderPipelines.GUI_TEXTURED, ICONS, iconX, top + 1, frame * 9, 0, 9, 9, 256, 256, color);
        }
    }
}
