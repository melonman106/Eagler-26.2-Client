package com.melonman106.vbvclient.punchy;

/**
 * An animation offset for one bone: Euler rotation in degrees, translation in model pixels and a scale, all
 * <em>added to</em> the bone's rest pose (rotation and position) or multiplied into it (scale).
 */
public final class BonePose {
    public final float[] rot = new float[3];
    public final float[] pos = new float[3];
    public final float[] scale = {1.0F, 1.0F, 1.0F};

    public BonePose reset() {
        rot[0] = rot[1] = rot[2] = 0.0F;
        pos[0] = pos[1] = pos[2] = 0.0F;
        scale[0] = scale[1] = scale[2] = 1.0F;
        return this;
    }

    public BonePose set(BonePose other) {
        System.arraycopy(other.rot, 0, rot, 0, 3);
        System.arraycopy(other.pos, 0, pos, 0, 3);
        System.arraycopy(other.scale, 0, scale, 0, 3);
        return this;
    }

    /** this = this + (other - this) * f, per component. */
    public BonePose lerpTo(BonePose other, float f) {
        for (int i = 0; i < 3; i++) {
            rot[i] += (other.rot[i] - rot[i]) * f;
            pos[i] += (other.pos[i] - pos[i]) * f;
            scale[i] += (other.scale[i] - scale[i]) * f;
        }
        return this;
    }

    public boolean isIdentity() {
        for (int i = 0; i < 3; i++) {
            if (rot[i] != 0.0F || pos[i] != 0.0F || scale[i] != 1.0F) return false;
        }
        return true;
    }
}
