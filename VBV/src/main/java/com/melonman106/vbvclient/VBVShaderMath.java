package com.melonman106.vbvclient;

/**
 * Pure maths behind the VBV shader presets. It touches no game classes so it can be run and
 * checked on a plain JVM. Conventions match the game: yaw 0 faces south (+z), positive pitch looks
 * down, and the sun is drawn at +Y rotated by the sun angle about X and then -90 degrees about Y.
 */
final class VBVShaderMath {
    private VBVShaderMath() {}

    /** World-space direction toward the sun for a sun angle in radians: (-sin a, cos a, 0). */
    static float[] sunWorld(float sunAngleRadians) {
        return new float[] { -(float) Math.sin(sunAngleRadians), (float) Math.cos(sunAngleRadians), 0.0F };
    }

    /**
     * Rotates a world direction into view space (x right, y up, z toward the viewer) for a camera
     * with the given yaw and pitch in degrees.
     */
    static float[] toView(float[] world, float yawDegrees, float pitchDegrees) {
        double yaw = Math.toRadians(yawDegrees);
        double pitch = Math.toRadians(pitchDegrees);
        double cp = Math.cos(pitch);
        double[] forward = { -Math.sin(yaw) * cp, -Math.sin(pitch), Math.cos(yaw) * cp };
        double[] right = { -Math.cos(yaw), 0.0, -Math.sin(yaw) };
        // up = (-forward) x right
        double[] back = { -forward[0], -forward[1], -forward[2] };
        double[] up = {
            back[1] * right[2] - back[2] * right[1],
            back[2] * right[0] - back[0] * right[2],
            back[0] * right[1] - back[1] * right[0]
        };
        return new float[] {
            (float) (world[0] * right[0] + world[1] * right[1] + world[2] * right[2]),
            (float) (world[0] * up[0] + world[1] * up[1] + world[2] * up[2]),
            (float) (world[0] * back[0] + world[1] * back[1] + world[2] * back[2])
        };
    }

    static float clamp01(float value) {
        return value < 0.0F ? 0.0F : Math.min(value, 1.0F);
    }

    static float smoothstep(float edge0, float edge1, float x) {
        float t = clamp01((x - edge0) / (edge1 - edge0));
        return t * t * (3.0F - 2.0F * t);
    }

    /** Maps 0..1 to a byte value 0..255, clamping. */
    static int toByte(float value) {
        return Math.round(clamp01(value) * 255.0F);
    }

    /** Packs four 0..1 values into the ABGR int NativeImage.setPixelABGR expects (bytes R,G,B,A in memory). */
    static int packAbgr(float r, float g, float b, float a) {
        return (toByte(a) << 24) | (toByte(b) << 16) | (toByte(g) << 8) | toByte(r);
    }
}
