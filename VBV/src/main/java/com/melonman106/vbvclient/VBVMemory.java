package com.melonman106.vbvclient;

import net.lax1dude.eaglercraft.v1_8.minecraft.EaglerHosted;
import net.minecraft.client.Minecraft;

/**
 * Heap watchdog and heap audit.
 *
 * <p>The client runs from a single HTML page, so the browser caps its heap. When that cap is
 * reached the tab dies without a message and the session is lost, which is the worst failure this
 * client can have on an 8 GB machine.</p>
 *
 * <p>The number that matters is the browser's own limit, not {@code Runtime.maxMemory()}: TeaVM
 * constant-folds that to a meaningless donor value, so the watchdog asks the Eagler runtime for the
 * real figures first ({@code EaglerHosted.webHeapStats}, wired to Chromium's
 * {@code performance.memory}) and only falls back to {@code Runtime} when the browser does not
 * expose them. Measuring against the real limit is what lets the client use the whole heap before
 * it sheds anything, instead of behaving as if a much smaller cap applied.</p>
 *
 * <p>While the heap is nearly full the watchdog drops the far half of the remembered LOD terrain:
 * it is the largest cache the client owns and the cheapest one to lose, because it fills back in as
 * the player explores. Nothing is dropped while there is room, and one shed per half minute at
 * most, so it cannot turn into a loop of constant rebuilding.</p>
 */
public final class VBVMemory {
    private static final long CHECK_MS = 2000L;
    private static final long SHED_COOLDOWN_MS = 30_000L;
    /**
     * Fraction of the heap cap that counts as pressure. Chrome allows this tab about 2 GB, so this
     * is ~1.55 GB: early enough that a big texture or world-map write still has room to land.
     */
    private static final float PRESSURE = 0.78F;
    /** Also shed when this little heap is left, even if the fraction still looks healthy. */
    private static final long MIN_HEADROOM = 256L * 1024L * 1024L;
    /** Sheds in a row before LOD is dropped altogether; a full tab dies with no message at all. */
    private static final int SHEDS_BEFORE_LOD_OFF = 3;
    /** Fallback trigger when the runtime cannot report a heap cap: this many LOD chunks is a lot. */
    private static final int LOD_CHUNKS_FALLBACK = 45_000;

    private static boolean enabled = true;
    private static long lastCheckMs;
    private static long lastShedMs;
    private static int usedMb;
    private static int maxMb;
    private static float pressure = -1.0F;
    private static boolean browserStats;
    private static int sheds;
    private static String note = "";

    private VBVMemory() {}

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        note = "";
    }

    public static void toggle() {
        setEnabled(!enabled);
    }

    /** Heap in use, in MB, from the last check. */
    public static int usedMb() {
        return usedMb;
    }

    /** Heap limit in MB, or 0 when the runtime does not report one. */
    public static int maxMb() {
        return maxMb;
    }

    /** True when the figures above come from the browser's own heap counters. */
    public static boolean usingBrowserStats() {
        return browserStats;
    }

    /** Called from the FPS meter; cheap because it only does work every couple of seconds. */
    public static void tick() {
        if (!enabled) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastCheckMs < CHECK_MS) {
            return;
        }
        lastCheckMs = now;
        pressure = -1.0F;
        long used = 0L;
        long max = 0L;
        browserStats = false;
        try {
            long[] stats = webHeapStats();
            if (stats != null) {
                used = stats[0];
                max = stats[2];
                browserStats = true;
            }
        } catch (Throwable ignored) {
            browserStats = false;
        }
        if (!browserStats) {
            try {
                Runtime runtime = Runtime.getRuntime();
                max = runtime.maxMemory();
                used = runtime.totalMemory() - runtime.freeMemory();
                // A real cap is a few hundred MB to a few GB; anything else means "not reported".
                if (max <= 0L || max >= (1L << 42)) {
                    max = 0L;
                }
            } catch (Throwable ignored) {
                max = 0L;
            }
        }
        usedMb = (int) (used / (1024L * 1024L));
        maxMb = (int) (max / (1024L * 1024L));
        if (max > 0L) {
            pressure = (float) used / (float) max;
        }
        boolean low = pressure >= PRESSURE;
        boolean headroom = browserStats && max > 0L && (max - used) < MIN_HEADROOM;
        boolean fallbackLow = pressure < 0.0F && VBVLodCache.size() > LOD_CHUNKS_FALLBACK;
        boolean canFree = VBVLod.getLevel() != VBVLod.OFF || VBVLodCache.size() > 0;
        if ((low || headroom || fallbackLow) && canFree) {
            if (now - lastShedMs >= SHED_COOLDOWN_MS) {
                lastShedMs = now;
                int centerChunkX = 0;
                int centerChunkZ = 0;
                Minecraft mc = Minecraft.getInstance();
                if (mc != null && mc.player != null) {
                    centerChunkX = (int) Math.floor(mc.player.getX()) >> 4;
                    centerChunkZ = (int) Math.floor(mc.player.getZ()) >> 4;
                }
                // Halving the cache buys a little room; if that keeps not being enough, give up the
                // far terrain entirely rather than let the tab be killed with the session in it.
                if (sheds >= SHEDS_BEFORE_LOD_OFF && VBVLod.getLevel() != VBVLod.OFF) {
                    VBVLodCache.clear();
                    VBVLod.setLevel(VBVLod.OFF);
                    note = "Low memory: LOD turned off";
                } else {
                    sheds++;
                    int kept = VBVLodCache.shed(1, 2, centerChunkX, centerChunkZ);
                    note = "Low memory: dropped far LOD, kept " + kept + " chunks";
                }
            }
        } else {
            sheds = 0;
            note = "";
        }
    }

    /** One line for the FPS meter, or null while everything is normal. */
    public static String pressureNote() {
        return note.isEmpty() ? null : note;
    }

    /**
     * The browser's own heap counters as {used, total, limit}, or null when this runtime does not
     * expose them (only Chromium does). Never throws.
     */
    private static long[] webHeapStats() {
        try {
            java.util.function.Supplier<long[]> supplier = EaglerHosted.webHeapStats;
            long[] stats = supplier == null ? null : supplier.get();
            if (stats != null && stats.length >= 3 && stats[2] > 0L) {
                return stats;
            }
        } catch (Throwable ignored) {
            // A browser that blocks performance.memory simply reports nothing.
        }
        return null;
    }

    /** One line for the performance report. */
    public static String reportLine() {
        StringBuilder out = new StringBuilder("Memory: ");
        if (maxMb > 0) {
            out.append(usedMb).append(" / ").append(maxMb).append(" MB (")
                .append(Math.round(pressure * 100.0F)).append("%, ")
                .append(Math.max(0, maxMb - usedMb)).append(" MB free)");
        } else {
            out.append(usedMb).append(" MB used (heap cap not reported by this runtime)");
        }
        out.append(" | from ").append(browserStats ? "browser heap" : "java runtime");
        out.append(" | watchdog ").append(enabled ? "ON" : "OFF");
        if (!note.isEmpty()) {
            out.append(" | ").append(note);
        }
        return out.toString();
    }

    /**
     * Multi-line heap audit for the Diagnostics screen: the heap itself, then the sizes of the
     * caches the client owns, so it is clear what is actually worth shrinking. The per-chunk LOD
     * figure is the real array footprint of one remembered chunk (16 columns of height and colour).
     */
    public static String audit() {
        StringBuilder out = new StringBuilder();
        out.append(reportLine()).append('\n');
        int lodChunks = VBVLodCache.size();
        long lodBytes = VBVLodCache.estimatedBytes();
        out.append("Heap audit: LOD cache ").append(lodChunks).append(" chunks (~").append(kb(lodBytes)).append(')');
        if (VBVLodCache.persistedChunks() > 0) {
            out.append(", ").append(VBVLodCache.persistedChunks()).append(" restored from storage");
        }
        out.append('\n');
        int clips = VBVPanoramaOverlay.count();
        int frames = 0;
        for (int i = 0; i < clips; i++) {
            frames += VBVPanoramaOverlay.frameCount(i);
        }
        out.append("Heap audit: panorama ").append(clips).append(" clips, ").append(frames)
            .append(" frames in the pack (decoded one clip at a time)\n");
        out.append("Heap audit: last frame ").append(VBVEntityCulling.lastEntitiesSeen()).append(" entities, ")
            .append(VBVEntityCulling.lastBlockEntitiesSeen()).append(" block entities\n");
        return out.toString();
    }

    private static String kb(long bytes) {
        if (bytes >= 1024L * 1024L) {
            return (bytes / (1024L * 1024L)) + " MB";
        }
        return Math.max(1L, bytes / 1024L) + " KB";
    }
}
