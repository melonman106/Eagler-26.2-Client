package com.melonman106.vbvclient.mods;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/** Draws the HUD panels declared by imported mods, using only data from content.json. */
public final class ModHud {
    private static final int PANEL = 0xB8101820;
    private static final int ACCENT = 0xFF66D9FF;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int DIM = 0xFFB8D8E0;

    private ModHud() {}

    public static void render(GuiGraphicsExtractor graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.font == null) return;
        int stacked = 0;
        for (ModContent.HudDef def : ModContentBootstrap.huds()) {
            Holder<MobEffect> effect = ModContentBootstrap.effect(def.effect());
            MobEffectInstance instance = effect == null ? null : minecraft.player.getEffect(effect);
            if (instance == null && !def.alwaysShow()) continue;

            Font font = minecraft.font;
            int nearby = -1;
            if (def.nearbyEntity() != null && minecraft.level != null) {
                EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(def.nearbyEntity()));
                if (type != null) {
                    nearby = minecraft.level.getEntitiesOfClass(Entity.class,
                        minecraft.player.getBoundingBox().inflate(16.0D),
                        entity -> entity.isAlive() && entity.getType() == type).size();
                }
            }

            String timer = instance == null ? "Inactive" : duration(instance.getDuration());
            String nearbyLine = nearby < 0 ? "" : "Nearby: " + nearby;
            int textWidth = Math.max(font.width(def.title()), def.showTimer() ? font.width(timer) + 8 : 0);
            if (!nearbyLine.isEmpty()) textWidth = Math.max(textWidth, font.width(nearbyLine));
            int panelWidth = Math.max(132, 25 + textWidth + 8);
            int panelHeight = nearbyLine.isEmpty() ? 24 : 36;
            int x = (graphics.guiWidth() - panelWidth) / 2;
            int y = graphics.guiHeight() - 48 - panelHeight - stacked;
            if (y < 4) y = 4;
            stacked += panelHeight + 4;

            graphics.fill(x, y, x + panelWidth, y + panelHeight, PANEL);
            graphics.fill(x, y, x + 2, y + panelHeight, ACCENT);
            if (effect != null) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, Hud.getMobEffectSprite(effect), x + 5, y + 4, 16, 16);
            }
            graphics.text(font, def.title(), x + 25, y + 5, TEXT, true);
            if (def.showTimer()) {
                graphics.text(font, timer, x + panelWidth - 5 - font.width(timer), y + 5, DIM, false);
            }
            if (!nearbyLine.isEmpty()) {
                graphics.text(font, nearbyLine, x + 25, y + 17, DIM, false);
            }
        }
    }

    private static String duration(int ticks) {
        int seconds = Math.max(0, ticks) / 20;
        return (seconds / 60) + ":" + (seconds % 60 < 10 ? "0" : "") + (seconds % 60);
    }
}
