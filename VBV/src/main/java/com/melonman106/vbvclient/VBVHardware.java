package com.melonman106.vbvclient;

import org.teavm.jso.JSBody;

/**
 * What the browser is willing to say about the machine, read once on first run.
 *
 * <p>Chrome caps {@code navigator.deviceMemory} at 8, so a value of 8 means
 * "8 GB or more" rather than exactly 8. The client targets a 4-core N100 with
 * 8 GB, which reports 4 cores, so the first-run profile is chosen from the core
 * count as well as the memory hint. The measurement is only a starting point:
 * every value it sets is an ordinary VBV setting the player can change
 * afterwards, and the result is written once so a later choice is never
 * overwritten.</p>
 */
public final class VBVHardware {
    private VBVHardware() {}

    /** Browser memory hint in GB, or 0 when the browser does not expose it. */
    public static int memoryGb() {
        try {
            return Math.max(0, memoryJS());
        } catch (Throwable ignored) {
            return 0;
        }
    }

    /** Logical cores, or 0 when the browser does not expose them. */
    public static int cores() {
        try {
            return Math.max(0, coresJS());
        } catch (Throwable ignored) {
            return 0;
        }
    }

    /**
     * A deliberately conservative low-end decision: 4 GB or less of reported
     * memory, or 4 logical cores or fewer. Both are heuristics, not benchmarks.
     */
    public static boolean lowEnd() {
        int memory = memoryGb();
        int cores = cores();
        return (memory > 0 && memory <= 4) || (cores > 0 && cores <= 4);
    }

    public static String describe() {
        int memory = memoryGb();
        int cores = cores();
        String profile = lowEnd() ? "low-end defaults" : "defaults";
        return "Hardware: " + (memory > 0 ? memory + " GB reported" : "memory not reported")
            + ", " + (cores > 0 ? cores + " cores" : "cores not reported")
            + " -> " + profile + '\n';
    }

    @JSBody(script = "try { return Math.round(Number(navigator.deviceMemory)||0); } catch(e) { return 0; }")
    private static native int memoryJS();

    @JSBody(script = "try { return Math.round(Number(navigator.hardwareConcurrency)||0); } catch(e) { return 0; }")
    private static native int coresJS();
}
