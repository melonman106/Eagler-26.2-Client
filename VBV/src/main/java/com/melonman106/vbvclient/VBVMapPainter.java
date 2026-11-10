package com.melonman106.vbvclient;

import java.util.Arrays;
import java.util.Set;
import java.util.function.LongFunction;

/**
 * Paints explored-chunk terrain into an ABGR pixel array for the world map. It depends on no game
 * classes so the pixel mapping can be tested on its own.
 *
 * <p>Each output pixel samples the block under its centre, so neighbouring chunks tile without
 * gaps or overlaps at any zoom, including fractional ones.</p>
 */
final class VBVMapPainter {
    static final int SAMPLES = 16;
    static final double MAX_SCALE = 12.0D;

    private VBVMapPainter() {}

    /** The scratch array {@link #paint} needs; sized for the largest zoom. */
    static int[] newColumnScratch() {
        return new int[(int) (SAMPLES * MAX_SCALE) + 8];
    }

    static int abgr(int argb) {
        return (argb & 0xFF00FF00) | ((argb >> 16) & 0xFF) | ((argb & 0xFF) << 16);
    }

    /**
     * @param out         w*h pixels, row-major, ABGR; fully overwritten
     * @param centerX     world block X at the middle of the view
     * @param centerZ     world block Z at the middle of the view
     * @param scale       pixels per block, at most {@link #MAX_SCALE}
     * @param explored    keys (see {@link VBVWorldMap#pack}) of explored chunks
     * @param terrain     colour samples (SAMPLES*SAMPLES, ARGB) for a chunk key, or null
     * @param scratch     from {@link #newColumnScratch()}
     * @return how many chunks were drawn
     */
    static int paint(int[] out, int w, int h, double centerX, double centerZ, double scale,
                     int background, Set<Long> explored, LongFunction<int[]> terrain, int[] scratch) {
        Arrays.fill(out, 0, w * h, background);
        double halfW = w / 2.0D;
        double halfH = h / 2.0D;
        int minChunkX = (int) Math.floor((centerX - halfW / scale) / 16.0D) - 1;
        int maxChunkX = (int) Math.floor((centerX + halfW / scale) / 16.0D) + 1;
        int minChunkZ = (int) Math.floor((centerZ - halfH / scale) / 16.0D) - 1;
        int maxChunkZ = (int) Math.floor((centerZ + halfH / scale) / 16.0D) + 1;
        long visible = (long) (maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1);
        int drawn = 0;

        if (visible > explored.size()) {
            // Zoomed far out: walking the explored set is cheaper than probing every visible cell.
            for (long key : explored) {
                int chunkX = VBVWorldMap.chunkX(key);
                int chunkZ = VBVWorldMap.chunkZ(key);
                if (chunkX >= minChunkX && chunkX <= maxChunkX && chunkZ >= minChunkZ && chunkZ <= maxChunkZ
                    && paintChunk(out, w, h, centerX, centerZ, scale, terrain.apply(key), chunkX, chunkZ, scratch)) {
                    drawn++;
                }
            }
        } else {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                    long key = VBVWorldMap.pack(chunkX, chunkZ);
                    if (explored.contains(key)
                        && paintChunk(out, w, h, centerX, centerZ, scale, terrain.apply(key), chunkX, chunkZ, scratch)) {
                        drawn++;
                    }
                }
            }
        }
        return drawn;
    }

    /** Pixel position of the boundary before chunk {@code chunk} along one axis. */
    private static double edge(int extent, double center, double scale, int chunk) {
        return extent / 2.0D + (chunk * 16.0D - center) * scale;
    }

    private static boolean paintChunk(int[] out, int w, int h, double centerX, double centerZ, double scale,
                                      int[] colors, int chunkX, int chunkZ, int[] columnSample) {
        if (colors == null || colors.length < SAMPLES * SAMPLES) {
            return false;
        }
        // Each chunk edge is computed by the same expression for both chunks that share it, so
        // rounding can never leave a one-pixel gap or overlap between neighbours.
        double left = edge(w, centerX, scale, chunkX);
        double right = edge(w, centerX, scale, chunkX + 1);
        double top = edge(h, centerZ, scale, chunkZ);
        double bottom = edge(h, centerZ, scale, chunkZ + 1);
        // A pixel belongs to the chunk when its centre (index + 0.5) falls inside [left, right).
        int x0 = Math.max(0, (int) Math.ceil(left - 0.5D));
        int x1 = Math.min(w, (int) Math.ceil(right - 0.5D));
        int y0 = Math.max(0, (int) Math.ceil(top - 0.5D));
        int y1 = Math.min(h, (int) Math.ceil(bottom - 0.5D));
        if (x0 >= x1 || y0 >= y1 || x1 - x0 > columnSample.length) {
            return false;
        }
        for (int px = x0; px < x1; px++) {
            int sx = (int) Math.floor((px + 0.5D - left) / scale);
            columnSample[px - x0] = sx < 0 ? 0 : Math.min(SAMPLES - 1, sx);
        }
        for (int py = y0; py < y1; py++) {
            int sz = (int) Math.floor((py + 0.5D - top) / scale);
            int row = (sz < 0 ? 0 : Math.min(SAMPLES - 1, sz)) * SAMPLES;
            int base = py * w;
            for (int px = x0; px < x1; px++) {
                out[base + px] = abgr(colors[row + columnSample[px - x0]]);
            }
        }
        return true;
    }
}
