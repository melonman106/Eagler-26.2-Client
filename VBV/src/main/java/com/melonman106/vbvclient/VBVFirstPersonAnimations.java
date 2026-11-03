package com.melonman106.vbvclient;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * Controls the additional first-person player-arm layer. The generated
 * ItemInHandRenderer uses vanilla's own renderPlayerArm method, preserving
 * the exact skin model, sleeve visibility, lighting and arm swing transforms.
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

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    /** Compatibility method for older generated hooks; no pose changes here. */
    public static void apply(PoseStack poseStack, AbstractClientPlayer player,
                             float partialTick, float attack, InteractionHand hand,
                             ItemStack itemStack) {
        // The arm is submitted directly by ItemInHandRenderer.
    }
}
