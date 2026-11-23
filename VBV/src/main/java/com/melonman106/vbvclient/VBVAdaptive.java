package com.melonman106.vbvclient;

import net.minecraft.client.Minecraft;

/**
 * Automatic quality governor.
 *
 * <p>The one-click profile is a single decision made at one moment; this keeps making it. It watches
 * the smoothed FPS the meter already computes and, in two-second windows, lowers the render distance
 * while the frame rate stays low. Below 22 FPS it turns LOD off first, because the far-terrain mesh
 * can cost more than the chunks it hides on a weak CPU/GPU; below 15 FPS it applies the full
 * low-end profile after two windows. When the frame rate recovers it walks the render distance back
 * up, but only to where it started, so it never pushes the machine off the cliff it just climbed.</p>
 *
 * <p>Changes are rare, spaced at least eight seconds apart, and written to the config like any
 * other setting, so the player can always take over. It only acts in the world (never in a menu),
 * and it never touches a setting the player changed by hand while it was running.</p>
 */
public final class VBVAdaptive {
    private static final long WINDOW_MS = 2000L;
    private static final long COOLDOWN_MS = 8000L;
    private static final int BAD_FPS = 42;
    /**
     * "Good enough to give a chunk back". It has to be reachable on a 60 Hz screen, where the frame rate
     * can never exceed 60: with 70 here, a render distance that was lowered was never raised again.
     */
    private static final int GOOD_FPS = 56;
    /** A render distance that just caused a step down is not tried again for this long (stops a see-saw). */
    private static final long RETRY_MS = 120_000L;
    private static final int BAD_WINDOWS = 3;
    private static final int GOOD_WINDOWS = 6;
    /**
     * Below this the frame rate is not "a bit low", it is unplayable. The governor steps two chunks
     * at a time, drops LOD, and after two windows hands over to {@link VBVPerf#applyLowEndMode()}.
     */
    private static final int EMERGENCY_FPS = 15;
    private static final int EMERGENCY_WINDOWS = 2;
    /** The engine's web FAST preset starts at 4; below that only a genuinely tiny view is left. */
    private static final int MIN_RENDER_DISTANCE = 2;

    private static boolean enabled = true;
    private static int badWindows;
    private static int goodWindows;
    private static int originalRenderDistance = -1;
    /** The render distance this class set last; anything else means the player changed it by hand. */
    private static int lastSetDistance = -1;
    /** The render distance at which the frame rate was last too low, and when it may be tried again. */
    private static int badDistance = -1;
    private static long badUntil;
    private static long windowStarted;
    private static long nextChangeAt;
    private static int emergencyWindows;
    /** Smoothed FPS from the last window, used to choose how hard stepDown should cut. */
    private static int lastFps;
    private static String lastAction = "";
    private static String lastNote = "";

    private VBVAdaptive() {}

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        reset();
    }

    public static void toggle() {
        setEnabled(!enabled);
    }

    /** Forgets the counters and the remembered render distance; the current settings are unchanged. */
    public static void reset() {
        badWindows = 0;
        goodWindows = 0;
        originalRenderDistance = -1;
        lastSetDistance = -1;
        badDistance = -1;
        badUntil = 0L;
        windowStarted = 0L;
        nextChangeAt = 0L;
        emergencyWindows = 0;
        lastFps = 0;
        lastAction = "";
        lastNote = "";
    }

    /** Called from the FPS meter each frame with the smoothed FPS. */
    public static void tick(int fps) {
        if (!enabled || fps <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        if (windowStarted == 0L) {
            windowStarted = now;
        }
        if (now - windowStarted < WINDOW_MS) {
            return;
        }
        windowStarted = now;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null || mc.player == null || mc.level == null) {
            return;
        }
        lastFps = fps;
        // A machine that stays under 15 FPS after two windows is past what render-distance steps can
        // fix. Apply the full low-end profile once; the player can undo any part of it afterwards.
        if (fps < EMERGENCY_FPS) {
            if (++emergencyWindows >= EMERGENCY_WINDOWS && !VBVPerf.isLowEndMode()) {
                emergencyWindows = 0;
                VBVPerf.applyLowEndMode();
                return;
            }
        } else {
            emergencyWindows = 0;
        }
        if (lastSetDistance >= 0 && mc.options.renderDistance().get() != lastSetDistance) {
            // The player moved the slider themselves: that is their setting now, so stop managing it.
            reset();
            return;
        }
        if (fps < BAD_FPS) {
            badWindows++;
            goodWindows = 0;
        } else if (fps > GOOD_FPS) {
            goodWindows++;
            badWindows = Math.max(0, badWindows - 1);
        } else {
            badWindows = Math.max(0, badWindows - 1);
            goodWindows = 0;
        }
        if (now < nextChangeAt) {
            return;
        }
        if (badWindows >= BAD_WINDOWS) {
            badWindows = 0;
            stepDown(mc, now);
        } else if (goodWindows >= GOOD_WINDOWS) {
            goodWindows = 0;
            stepUp(mc, now);
        }
    }

    private static void stepDown(Minecraft mc, long now) {
        int renderDistance = mc.options.renderDistance().get();
        if (originalRenderDistance < 0) {
            originalRenderDistance = renderDistance;
        }
        // Far terrain is the first thing to give up when the frame rate is already very low: the
        // LOD mesh and its rebuilds can cost more than the chunks they hide on a weak CPU/GPU.
        if (lastFps < 22 && VBVLod.getLevel() != VBVLod.OFF) {
            VBVLod.setLevel(VBVLod.OFF);
            lastAction = "LOD off";
            nextChangeAt = now + COOLDOWN_MS;
            return;
        }
        if (renderDistance > MIN_RENDER_DISTANCE) {
            // Two chunks at a time below 25 FPS, so a 5 FPS machine reaches the floor in a few
            // windows instead of spending a minute at 12 -> 11 -> 10 ...
            int step = lastFps < 25 ? 2 : 1;
            int next = Math.max(MIN_RENDER_DISTANCE, renderDistance - step);
            mc.options.renderDistance().set(next);
            mc.options.save();
            lastSetDistance = next;
            badDistance = renderDistance;
            badUntil = now + RETRY_MS;
            lastAction = "render distance " + next;
            nextChangeAt = now + COOLDOWN_MS;
            return;
        }
        // At the floor, VBVPerf.applyLowEndMode() is the only thing left that can help.
    }

    private static void stepUp(Minecraft mc, long now) {
        if (originalRenderDistance < 0) {
            return;
        }
        int renderDistance = mc.options.renderDistance().get();
        if (renderDistance < originalRenderDistance) {
            if (badDistance >= 0 && now < badUntil && renderDistance + 1 >= badDistance) {
                return; // that distance was too heavy a moment ago
            }
            mc.options.renderDistance().set(renderDistance + 1);
            mc.options.save();
            lastSetDistance = renderDistance + 1;
            lastAction = "render distance " + (renderDistance + 1);
            nextChangeAt = now + COOLDOWN_MS;
        }
    }

    /** One line for the FPS meter, or null when there is nothing worth saying. */
    public static String reportLine() {
        if (!enabled) {
            return "Auto quality: off";
        }
        return lastAction.isEmpty() ? null : "Auto quality: " + lastAction;
    }

    /** Multi-line detail for the performance report. */
    public static String detail() {
        if (!enabled) {
            return "Auto quality: off";
        }
        return "Auto quality: on (target " + BAD_FPS + "-" + GOOD_FPS + " FPS, min render distance "
            + MIN_RENDER_DISTANCE + ")" + (lastAction.isEmpty() ? ", nothing changed yet" : ", last change " + lastAction);
    }
}
