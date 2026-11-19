package com.melonman106.vbvclient;

import net.minecraft.client.Minecraft;

/**
 * Heap watchdog.
 *
 * <p>The client runs from a single HTML page, so the browser caps its heap (about 2 GB in Chrome).
 * When that cap is reached the tab dies without a message and the session is lost, which is the
 * worst failure this client can have on an 8 GB machine. This class checks the heap every couple of
 * seconds and, while it is nearly full, drops the far half of the remembered LOD terrain: that is
 * the largest cache the client owns and the cheapest one to lose, because it fills back in as the
 * player explores. Nothing is dropped while there is room, and one shed per half minute at most,
 * so it cannot turn into a loop of constant rebuilding.</p>
 *
 * <p>{@code Runtime.maxMemory()} is not meaningful on every WebAssembly runtime, so a value that
 * looks like "unlimited" is treated as unknown and the check falls back to the size of the caches
 * themselves.</p>
 */
public final class VBVMemory {
    private static final long CHECK_MS = 2000L;
    private static final long SHED_COOLDOWN_MS = 30_000L;
    /** Fraction of the heap cap that counts as pressure. */
    private static final float PRESSURE = 0.82F;
    /** Fallback trigger when the runtime cannot report a heap cap: this many LOD chunks is a lot. */
    private static final int LOD_CHUNKS_FALLBACK = 45_000;

    private static boolean enabled = true;
    private static long lastCheckMs;
    private static long lastShedMs;
    private static int usedMb;
    private static int maxMb;
    private static float pressure = -1.0F;
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
        try {
            Runtime runtime = Runtime.getRuntime();
            long max = runtime.maxMemory();
            long used = runtime.totalMemory() - runtime.freeMemory();
            usedMb = (int) (used / (1024L * 1024L));
            // A real cap is a few hundred MB to a few GB; anything else means "not reported".
            if (max > 0L && max < (1L << 42)) {
                maxMb = (int) (max / (1024L * 1024L));
                pressure = (float) used / (float) max;
            } else {
                maxMb = 0;
            }
        } catch (Throwable ignored) {
            pressure = -1.0F;
            maxMb = 0;
        }
        boolean low = pressure >= PRESSURE;
        boolean fallbackLow = pressure < 0.0F && VBVLodCache.size() > LOD_CHUNKS_FALLBACK;
        if ((low || fallbackLow) && now - lastShedMs >= SHED_COOLDOWN_MS) {
            lastShedMs = now;
            int centerChunkX = 0;
            int centerChunkZ = 0;
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.player != null) {
                centerChunkX = (int) Math.floor(mc.player.getX()) >> 4;
                centerChunkZ = (int) Math.floor(mc.player.getZ()) >> 4;
            }
            int kept = VBVLodCache.shed(1, 2, centerChunkX, centerChunkZ);
            note = "Low memory: dropped far LOD, kept " + kept + " chunks";
        } else if (!low && !fallbackLow) {
            note = "";
        }
    }

    /** One line for the FPS meter, or null while everything is normal. */
    public static String pressureNote() {
        return note.isEmpty() ? null : note;
    }

    /** Multi-line detail for the performance report. */
    public static String reportLine() {
        StringBuilder out = new StringBuilder("Memory: ");
        if (maxMb > 0) {
            out.append(usedMb).append(" / ").append(maxMb).append(" MB (").append(Math.round(pressure * 100.0F)).append("%)");
        } else {
            out.append(usedMb).append(" MB used (heap cap not reported by this runtime)");
        }
        out.append(" | watchdog ").append(enabled ? "ON" : "OFF");
        if (!note.isEmpty()) {
            out.append(" | ").append(note);
        }
        return out.toString();
    }
}
