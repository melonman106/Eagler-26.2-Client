package com.melonman106.vbvclient;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.lax1dude.eaglercraft.v1_8.EagRuntime;

/**
 * Start-up timer. It records when the resource reload starts and ends, and how long each reload
 * listener (textures, models, fonts, sounds, ...) took, so a slow start can be blamed on a specific
 * step instead of guessed at. Everything is printed to the browser console with a {@code [VBV boot]}
 * prefix, shown on the "press any button" screen, and added to the performance report.
 *
 * <p>Listeners finish one after another (each waits for the previous one), so the gap between two
 * consecutive finish times is the time that listener needed.</p>
 */
public final class VBVBoot {
    private static final String LOG = "[VBV boot] ";
    private static final List<String> NAMES = new ArrayList<>();
    private static final List<Long> STEPS = new ArrayList<>();
    private static long reloadStart = -1L;
    private static long reloadEnd = -1L;
    private static long lastListenerEnd = -1L;
    private static long menuAt = -1L;
    private static int listeners;

    private VBVBoot() {}

    private static long now() {
        try {
            return EagRuntime.steadyTimeMillis();
        } catch (Throwable t) {
            return System.currentTimeMillis();
        }
    }

    /** The resource reload begins (called from {@code LoadingOverlay}'s constructor). */
    public static synchronized void reloadStarted() {
        if (reloadStart >= 0L) {
            return;
        }
        reloadStart = now();
        lastListenerEnd = reloadStart;
        System.out.println(LOG + "resource reload started, " + reloadStart + " ms after the browser clock started");
    }

    /** A reload listener's pending result; called once per listener when the reload is set up. */
    public static synchronized void track(Object listener, CompletableFuture<?> done) {
        if (reloadStart < 0L) {
            reloadStarted();
        }
        final int index = ++listeners;
        String label;
        try {
            label = listener.getClass().getName();
            int dot = label.lastIndexOf('.');
            if (dot >= 0) {
                label = label.substring(dot + 1);
            }
        } catch (Throwable t) {
            label = "listener";
        }
        final String name = index + ". " + label;
        done.thenRun(() -> finished(name));
    }

    private static synchronized void finished(String name) {
        long t = now();
        long step = Math.max(0L, t - lastListenerEnd);
        lastListenerEnd = t;
        NAMES.add(name);
        STEPS.add(step);
        System.out.println(LOG + name + ": " + step + " ms");
    }

    /** All listeners are done (called just before the loading overlay hands over). */
    public static synchronized void reloadFinished() {
        if (reloadEnd >= 0L) {
            return;
        }
        reloadEnd = now();
        System.out.println(LOG + "resource reload finished in " + (reloadEnd - Math.max(0L, reloadStart)) + " ms");
    }

    /** The first menu screen is up. */
    public static synchronized void menuReached() {
        if (menuAt >= 0L) {
            return;
        }
        menuAt = now();
        System.out.println(LOG + "first menu shown at " + menuAt + " ms (browser clock)");
    }

    public static synchronized boolean hasData() {
        return reloadEnd >= 0L;
    }

    /** One short line: how long the reload took and what the slowest step was. */
    public static synchronized String headline() {
        if (reloadEnd < 0L) {
            return "";
        }
        long total = reloadEnd - Math.max(0L, reloadStart);
        String slow = "";
        long best = 0L;
        for (int i = 0; i < STEPS.size(); i++) {
            if (STEPS.get(i) > best) {
                best = STEPS.get(i);
                slow = NAMES.get(i);
            }
        }
        StringBuilder out = new StringBuilder("Resources loaded in ").append(seconds(total));
        if (!slow.isEmpty() && best > 0L) {
            out.append(" (slowest: ").append(slow.substring(slow.indexOf(' ') + 1)).append(' ').append(seconds(best)).append(')');
        }
        return out.toString();
    }

    /** Multi-line breakdown for the performance report. */
    public static synchronized String report() {
        StringBuilder out = new StringBuilder();
        if (reloadStart < 0L) {
            return "Start-up: no timing recorded.\n";
        }
        out.append("Start-up: page clock at reload start ").append(seconds(reloadStart));
        if (reloadEnd >= 0L) {
            out.append(", reload took ").append(seconds(reloadEnd - reloadStart));
        }
        if (menuAt >= 0L) {
            out.append(", first menu at ").append(seconds(menuAt));
        }
        out.append('\n');
        for (int i = 0; i < NAMES.size(); i++) {
            if (STEPS.get(i) >= 50L) {
                out.append("  ").append(NAMES.get(i)).append(": ").append(seconds(STEPS.get(i))).append('\n');
            }
        }
        return out.toString();
    }

    private static String seconds(long ms) {
        long tenths = Math.round(ms / 100.0D);
        return (tenths / 10) + "." + (tenths % 10) + " s";
    }
}
