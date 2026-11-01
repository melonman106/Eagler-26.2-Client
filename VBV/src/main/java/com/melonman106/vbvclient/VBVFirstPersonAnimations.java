package com.melonman106.vbvclient;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * Compatibility stub for the removed first-person animation experiment.
 * The renderer may still reference this class in older generated patches,
 * but it must never modify the player's arm/item pose.
 */
public final class VBVFirstPersonAnimations {
    private static boolean enabled = false;

    private VBVFirstPersonAnimations() {}

    public static boolean isEnabled() {
        return false;
    }

    public static void toggle() {
        enabled = false;
    }

    public static void setEnabled(boolean value) {
        enabled = false;
    }

    public static void apply(PoseStack poseStack, AbstractClientPlayer player,
                             float partialTick, float attack, InteractionHand hand,
                             ItemStack itemStack) {
        // Intentionally empty: first-person arm animation is disabled.
    }
}
