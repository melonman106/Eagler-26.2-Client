package com.melonman106.vbvclient;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemStack;

/**
 * Adds the local player's skin-textured arm alongside vanilla first-person
 * item rendering. Item transforms and use/swing animations remain vanilla.
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

    /**
     * Draws only the arm mesh at the current item pose. The caller deliberately
     * invokes this after vanilla has applied the item's transforms and before
     * the item itself is submitted, so swings, placing, blocking and use
     * animations stay synchronized without replacing vanilla item rendering.
     */
    public static void renderHeldArm(PoseStack poseStack,
                                    SubmitNodeCollector submitNodeCollector,
                                    int lightCoords,
                                    AbstractClientPlayer player,
                                    HumanoidArm arm,
                                    EntityRenderDispatcher entityRenderDispatcher) {
        if (!enabled || player == null || player.isInvisible()) {
            return;
        }

        AvatarRenderer<AbstractClientPlayer> avatarRenderer =
            entityRenderDispatcher.getPlayerRenderer(player);
        Identifier skinTexture = player.getSkin().body().texturePath();

        if (arm == HumanoidArm.RIGHT) {
            avatarRenderer.renderRightHand(
                poseStack, submitNodeCollector, lightCoords, skinTexture,
                player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE)
            );
        } else {
            avatarRenderer.renderLeftHand(
                poseStack, submitNodeCollector, lightCoords, skinTexture,
                player.isModelPartShown(PlayerModelPart.LEFT_SLEEVE)
            );
        }
    }

    /** Kept for compatibility with any older generated renderer hooks. */
    public static void apply(PoseStack poseStack, AbstractClientPlayer player,
                             float partialTick, float attack, InteractionHand hand,
                             ItemStack itemStack) {
        // The arm is now rendered directly by ItemInHandRenderer.
    }
}
