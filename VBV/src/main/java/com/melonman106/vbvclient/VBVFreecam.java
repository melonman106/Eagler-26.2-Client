package com.melonman106.vbvclient;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * Spectator-style free camera.
 *
 * <p>While it is on, the camera is detached from the player and flown around with the normal
 * movement keys, while the player stays exactly where they were: movement input is cleared for the
 * player, interaction is suppressed (no attacks, no block use, so no chests open), and the mouse
 * turns the camera instead of the player. Turning it off puts the camera straight back on the
 * player, who never moved.</p>
 *
 * <p>The generated-source hooks that make this work are in {@code Camera.alignWithEntity} (place
 * and rotation), {@code MouseHandler.turnPlayer} (look), {@code KeyboardInput.tick} (freeze the
 * player), three guards in {@code Minecraft} (no attack/use) and one in {@code ItemInHandRenderer}
 * (the held item is drawn at the camera, so it is hidden).</p>
 */
public final class VBVFreecam {
    /** Blocks per tick at walking speed; sprint multiplies it. */
    private static final double BASE_SPEED = 0.55D;
    private static final double SPRINT_MULTIPLIER = 4.0D;
    /** Keeps the camera inside the buildable range even in short dimensions. */
    private static final int MIN_Y_OFFSET = 64;
    private static final int MAX_Y_OFFSET = 384;

    private static boolean active;
    private static double posX;
    private static double posY;
    private static double posZ;
    private static float yRot;
    private static float xRot;

    private VBVFreecam() {}

    public static boolean isActive() {
        return active;
    }

    /** Turns the free camera on from where the player is looking, or off again. */
    public static void toggle(Minecraft minecraft) {
        if (active) {
            active = false;
            return;
        }
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            return;
        }
        Vec3 eye = minecraft.player.getEyePosition();
        posX = eye.x;
        posY = eye.y;
        posZ = eye.z;
        yRot = minecraft.player.getYRot();
        xRot = minecraft.player.getXRot();
        active = true;
    }

    /** Drops the camera back on the player; harmless when it is already off. */
    public static void stop() {
        active = false;
    }

    public static float yRot() {
        return yRot;
    }

    public static float xRot() {
        return xRot;
    }

    public static double posX() {
        return posX;
    }

    public static double posY() {
        return posY;
    }

    public static double posZ() {
        return posZ;
    }

    /** Ground-plane forward direction of the free camera, for the LOD's behind-camera culling. */
    public static double forwardX() {
        return -Math.sin(Math.toRadians(yRot));
    }

    public static double forwardZ() {
        return Math.cos(Math.toRadians(yRot));
    }

    /** Mouse look: the same deltas the player would have received. */
    public static void look(double deltaYaw, double deltaPitch) {
        if (!active) {
            return;
        }
        yRot += (float) deltaYaw;
        xRot = (float) Math.max(-90.0D, Math.min(90.0D, xRot + (float) deltaPitch));
    }

    /** Called once per client tick: reads the movement keys and flies the camera. */
    public static void tick(Minecraft minecraft) {
        if (!active) {
            return;
        }
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            // Leaving the world must never leave a camera floating in the void.
            active = false;
            return;
        }
        if (minecraft.gui.screen() != null) {
            // Menus should not fly the camera around; the keys belong to the screen.
            return;
        }
        double forward = (minecraft.options.keyUp.isDown() ? 1.0D : 0.0D)
            - (minecraft.options.keyDown.isDown() ? 1.0D : 0.0D);
        double strafe = (minecraft.options.keyRight.isDown() ? 1.0D : 0.0D)
            - (minecraft.options.keyLeft.isDown() ? 1.0D : 0.0D);
        double vertical = (minecraft.options.keyJump.isDown() ? 1.0D : 0.0D)
            - (minecraft.options.keyShift.isDown() ? 1.0D : 0.0D);
        if (forward == 0.0D && strafe == 0.0D && vertical == 0.0D) {
            return;
        }
        double speed = BASE_SPEED * (minecraft.options.keySprint.isDown() ? SPRINT_MULTIPLIER : 1.0D);
        double yawRadians = Math.toRadians(yRot);
        double pitchRadians = Math.toRadians(xRot);
        double cosPitch = Math.cos(pitchRadians);
        // Forward from yaw/pitch, right on the ground plane, up straight up.
        double fx = -Math.sin(yawRadians) * cosPitch;
        double fy = -Math.sin(pitchRadians);
        double fz = Math.cos(yawRadians) * cosPitch;
        double rx = -Math.cos(yawRadians);
        double rz = -Math.sin(yawRadians);
        double dx = fx * forward + rx * strafe;
        double dy = fy * forward + vertical;
        double dz = fz * forward + rz * strafe;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length > 1.0E-4D) {
            dx = dx / length * speed;
            dy = dy / length * speed;
            dz = dz / length * speed;
        }
        posX += dx;
        posY += dy;
        posZ += dz;
        double minY = minecraft.level.getMinY() - MIN_Y_OFFSET;
        double maxY = minecraft.level.getMinY() + MAX_Y_OFFSET;
        posY = Math.max(minY, Math.min(maxY, posY));
    }

    /** One line for the FPS meter, or null when the free camera is off. */
    public static String hudLine() {
        if (!active) {
            return null;
        }
        return String.format(java.util.Locale.ROOT, "Freecam: %.1f %.1f %.1f (player stays put)", posX, posY, posZ);
    }
}
