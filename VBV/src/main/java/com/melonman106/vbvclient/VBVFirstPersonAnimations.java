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
    /** Offsets are stored in hundredths of a block. Positive Z moves the item toward the camera. */
    public static final int OFFSET_MIN = -40;
    public static final int OFFSET_MAX = 40;
    public static final int DEFAULT_X = 2;
    public static final int DEFAULT_Y = 6;
    public static final int DEFAULT_Z = 8;

    private static boolean enabled = true;
    private static int offsetX = DEFAULT_X;
    private static int offsetY = DEFAULT_Y;
    private static int offsetZ = DEFAULT_Z;

    private VBVFirstPersonAnimations() {}

    private static int clamp(int value) {
        return Math.max(OFFSET_MIN, Math.min(OFFSET_MAX, value));
    }

    public static int getOffsetX() { return offsetX; }
    public static int getOffsetY() { return offsetY; }
    public static int getOffsetZ() { return offsetZ; }

    public static void setOffsets(int x, int y, int z) {
        offsetX = clamp(x);
        offsetY = clamp(y);
        offsetZ = clamp(z);
    }

    public static void resetOffsets() {
        setOffsets(DEFAULT_X, DEFAULT_Y, DEFAULT_Z);
    }

    /** Sideways shift in blocks (mirrored for the left hand by the caller). */
    public static float blocksX() { return offsetX / 100.0F; }
    /** Upward shift in blocks. */
    public static float blocksY() { return offsetY / 100.0F; }
    /** Shift toward the camera in blocks (vanilla uses negative Z for "away"). */
    public static float blocksZ() { return offsetZ / 100.0F; }

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
