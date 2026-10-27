package com.melonman106.vbvclient;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;

/**
 * Lightweight native first-person item animation layer for Eaglercraft.
 * Toggle with V. It adds subtle idle movement and a little extra swing response
 * without replacing vanilla item-use poses or relying on Punchy! code/assets.
 */
public final class VBVFirstPersonAnimations {
    private static boolean enabled = true;

    private VBVFirstPersonAnimations() {}

    public static boolean isEnabled() {
        return enabled;
    }

    public static void toggle() {
        enabled = !enabled;
    }

    public static void apply(PoseStack poseStack, AbstractClientPlayer player,
                             float partialTick, float attack, InteractionHand hand,
                             ItemStack itemStack) {
        if (!enabled || player == null || itemStack == null || itemStack.isEmpty()) return;
        // Keep vanilla eating, drinking, blocking, drawing and charging poses intact.
        if (player.isUsingItem()) return;

        float time = player.tickCount + partialTick;
        float side = hand == InteractionHand.MAIN_HAND ? 1.0F : -1.0F;
        float walk = Mth.sin(time * 0.22F);
        float idle = Mth.sin(time * 0.075F);
        poseStack.translate(side * idle * 0.004F, Math.abs(walk) * 0.008F + idle * 0.003F, 0.0F);
        poseStack.mulPose(Axis.XP.rotationDegrees(idle * 1.1F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * walk * 0.65F));

        if (attack > 0.0F) {
            float swing = Mth.sin(Mth.sqrt(attack) * (float)Math.PI);
            poseStack.translate(side * swing * 0.018F, -swing * 0.012F, 0.0F);
            poseStack.mulPose(Axis.YP.rotationDegrees(side * swing * 3.5F));
            poseStack.mulPose(Axis.XP.rotationDegrees(-swing * 2.5F));
        }
    }
}
