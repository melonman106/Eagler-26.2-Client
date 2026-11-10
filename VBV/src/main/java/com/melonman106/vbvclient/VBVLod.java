package com.melonman106.vbvclient;

/**
 * Level-of-detail (LOD) distant terrain setting.
 *
 * <p>When LOD is on, terrain beyond the normal render distance is drawn as cheap, low-resolution
 * colour columns (see {@link VBVLodCache} and {@link VBVLodRenderer}) instead of full-detail chunk
 * meshes, so the visible range can reach far past what real chunks could cost. The level picks
 * how far the LOD reaches and how finely it is sampled.</p>
 *
 * <p>The level is persisted by {@link VBVConfigStore} under {@code lod.level}. It defaults to Off.</p>
 */
public final class VBVLod {
    public static final int OFF = 0;
    public static final int LOW = 1;
    public static final int MEDIUM = 2;
    public static final int FULL = 3;
    public static final String[] NAMES = {"Off", "Low", "Medium", "Full"};

    /** How far each level reaches, in chunks, measured from the camera. */
    private static final int[] RANGE_CHUNKS = {0, 32, 96, 256};

    /**
     * Per level, rings of {outerChunkDistance, cellSizeInBlocks}. Rings are listed from the
     * camera outward; cells get coarser with distance so the vertex count stays small.
     */
    private static final int[][][] TIERS = {
        {},
        {{16, 16}, {32, 32}},
        {{16, 8}, {40, 16}, {64, 32}, {96, 64}},
        {{12, 4}, {32, 8}, {72, 16}, {128, 32}, {256, 64}}
    };

    private static final String[] DETAIL = {
        "Distant terrain is not simplified; only real chunks are drawn.",
        "Short reach, coarse 16-32 block columns. Lightest on memory and GPU.",
        "Medium reach, 8-64 block columns. A good balance.",
        "Longest reach, finest columns (4 blocks near, 64 blocks far)."
    };

    private static int level = OFF;
    private static String failure = "";

    private VBVLod() {}

    public static int getLevel() {
        return level;
    }

    /** Used by VBVConfigStore when loading; out-of-range values fall back to Off. */
    public static void load(int value) {
        level = value >= OFF && value <= FULL ? value : OFF;
    }

    public static void setLevel(int value) {
        int next = Math.floorMod(value, NAMES.length);
        if (next != level) {
            level = next;
            // A fresh choice gets a fresh chance if the renderer had switched itself off.
            failure = "";
            VBVLodRenderer.resetAfterSettingChange();
        }
    }

    public static void cycle(int direction) {
        setLevel(level + direction);
    }

    public static boolean isActive() {
        return level != OFF && failure.isEmpty();
    }

    public static String name() {
        return NAMES[level];
    }

    public static String name(int value) {
        return NAMES[Math.floorMod(value, NAMES.length)];
    }

    public static String detail(int value) {
        return DETAIL[Math.floorMod(value, DETAIL.length)];
    }

    public static int rangeChunks() {
        return RANGE_CHUNKS[level];
    }

    public static int rangeChunks(int value) {
        return RANGE_CHUNKS[Math.floorMod(value, RANGE_CHUNKS.length)];
    }

    /** {outerChunkDistance, cellSizeInBlocks} rings for the current level, camera outward. */
    public static int[][] tiers() {
        return TIERS[level];
    }

    /** Where fog is fully opaque. The LOD fades out into the sky right here. */
    public static float fogEndBlocks() {
        return RANGE_CHUNKS[level] * 16.0F;
    }

    /** Fog begins well before the end so the far terrain dissolves gradually. */
    public static float fogStartBlocks() {
        return fogEndBlocks() * 0.55F;
    }

    /** Camera far plane needed so the LOD is not clipped; 0 when LOD is off. */
    public static float farPlaneBlocks() {
        return isActive() ? fogEndBlocks() * 1.1F + 64.0F : 0.0F;
    }

    /** Set by the renderer when it hits an unrecoverable error; LOD then stays off until changed. */
    static void fail(String message) {
        failure = message == null || message.isEmpty() ? "unknown error" : message;
    }

    public static String failure() {
        return failure;
    }
}
