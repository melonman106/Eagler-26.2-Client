package com.melonman106.vbvclient;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * Native first-person swing animation layer for Eaglercraft.
 *
 * This deliberately does not add the old constant side-to-side idle motion.
 * Instead it exaggerates the actual attack swing so the held item and the
 * rendered arm/hand follow a noticeable forward/back and rotational arc.
 * Vanilla item-use poses (eat, drink, block, draw, charge, etc.) are left alone.
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

        // Never interfere with vanilla use animations.
        if (player.isUsingItem()) return;

        float side = hand == InteractionHand.MAIN_HAND ? 1.0F : -1.0F;
        float swing = Mth.sin(Mth.sqrt(Mth.clamp(attack, 0.0F, 1.0F)) * (float)Math.PI);
        float swingArc = Mth.sin(Mth.clamp(attack, 0.0F, 1.0F) * (float)Math.PI);
        float returnArc = Mth.sin(Mth.sqrt(Mth.clamp(attack, 0.0F, 1.0F)) * (float)(Math.PI * 2.0D));

        if (attack <= 0.0F) return;

        // Pull the hand/item across the body, then forward, producing a clear
        // punch/swing arc rather than the previous idle wobble.
        poseStack.translate(
            side * swing * 0.18F,
            -Math.abs(swing) * 0.10F - swingArc * 0.035F,
            -swing * 0.16F
        );

        // Large rotations make the hand visibly roll through the swing.
        poseStack.mulPose(Axis.YP.rotationDegrees(side * (28.0F * swingArc + 8.0F * returnArc)));
        poseStack.mulPose(Axis.XP.rotationDegrees(-34.0F * swing + 9.0F * swingArc));
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * (14.0F * swing + 7.0F * returnArc)));
    }
}
