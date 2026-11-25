package com.melonman106.vbvclient.punchy;

/**
 * Minimal 4x4 matrix helpers on row-major {@code float[16]} arrays (column vectors: {@code p' = M * p}).
 * Kept here so the rig maths needs no game or JOML classes and can be tested on a plain JVM.
 */
public final class Mat4 {
    private Mat4() {}

    public static float[] identity() {
        return new float[]{1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
    }

    public static float[] translation(float x, float y, float z) {
        float[] m = identity();
        m[3] = x;
        m[7] = y;
        m[11] = z;
        return m;
    }

    public static float[] scaling(float x, float y, float z) {
        float[] m = identity();
        m[0] = x;
        m[5] = y;
        m[10] = z;
        return m;
    }

    /** Standard right-handed rotation about X (degrees): y' = y cos - z sin, z' = y sin + z cos. */
    public static float[] rotationX(float degrees) {
        float r = (float) Math.toRadians(degrees);
        float c = (float) Math.cos(r);
        float s = (float) Math.sin(r);
        return new float[]{1, 0, 0, 0, 0, c, -s, 0, 0, s, c, 0, 0, 0, 0, 1};
    }

    /** Standard right-handed rotation about Y (degrees): x' = x cos + z sin, z' = -x sin + z cos. */
    public static float[] rotationY(float degrees) {
        float r = (float) Math.toRadians(degrees);
        float c = (float) Math.cos(r);
        float s = (float) Math.sin(r);
        return new float[]{c, 0, s, 0, 0, 1, 0, 0, -s, 0, c, 0, 0, 0, 0, 1};
    }

    /** Standard right-handed rotation about Z (degrees): x' = x cos - y sin, y' = x sin + y cos. */
    public static float[] rotationZ(float degrees) {
        float r = (float) Math.toRadians(degrees);
        float c = (float) Math.cos(r);
        float s = (float) Math.sin(r);
        return new float[]{c, -s, 0, 0, s, c, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
    }

    /** Java's {@code Quaternionf.rotationZYX(z, y, x)}: Rz * Ry * Rx, so X is applied to a vector first. */
    public static float[] rotationZYX(float xDeg, float yDeg, float zDeg) {
        return mul(rotationZ(zDeg), mul(rotationY(yDeg), rotationX(xDeg)));
    }

    public static float[] mul(float[] a, float[] b) {
        float[] out = new float[16];
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 4; col++) {
                float sum = 0.0F;
                for (int k = 0; k < 4; k++) sum += a[row * 4 + k] * b[k * 4 + col];
                out[row * 4 + col] = sum;
            }
        }
        return out;
    }

    /** Transforms a point (w = 1). */
    public static float[] apply(float[] m, float x, float y, float z) {
        return new float[]{
            m[0] * x + m[1] * y + m[2] * z + m[3],
            m[4] * x + m[5] * y + m[6] * z + m[7],
            m[8] * x + m[9] * y + m[10] * z + m[11]};
    }

    /** Transforms a direction (w = 0). */
    public static float[] applyDirection(float[] m, float x, float y, float z) {
        return new float[]{
            m[0] * x + m[1] * y + m[2] * z,
            m[4] * x + m[5] * y + m[6] * z,
            m[8] * x + m[9] * y + m[10] * z};
    }

    /** Determinant of the upper-left 3x3 (negative means the matrix mirrors). */
    public static float determinant3(float[] m) {
        return m[0] * (m[5] * m[10] - m[6] * m[9])
            - m[1] * (m[4] * m[10] - m[6] * m[8])
            + m[2] * (m[4] * m[9] - m[5] * m[8]);
    }
}
