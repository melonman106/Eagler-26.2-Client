package com.melonman106.vbvclient;

import org.teavm.jso.JSBody;

/**
 * Read-only view of the browser-side crash journal that {@code inject_error_overlay.js}
 * keeps in localStorage.
 *
 * <p>The diagnostic overlay already notices a launch that never reaches
 * {@code window.__loaded}. It now writes the last stage and the last few errors
 * to storage, so the run after a tab kill (a freeze the user closes, an out of
 * memory death) can say what the dead run was doing. Opening the page with
 * {@code ?safe} turns the expensive optional features off for that session and,
 * importantly, stops settings from being saved, so a safe-mode session cannot
 * overwrite a good configuration.</p>
 */
public final class VBVSafeMode {
    private VBVSafeMode() {}

    /** True when the page was opened with {@code ?safe}. */
    public static boolean requested() {
        try {
            return requestedJS();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** How many previous launches ended before the client reported itself loaded. */
    public static int previousFailures() {
        try {
            return Math.max(0, previousFailuresJS());
        } catch (Throwable ignored) {
            return 0;
        }
    }

    public static String describe() {
        StringBuilder out = new StringBuilder();
        out.append("Safe mode: ").append(requested() ? "ON (no settings are saved this session)" : "off").append('\n');
        int failures = previousFailures();
        if (failures > 0) {
            out.append("Previous launches that never finished loading: ").append(failures)
                .append(" (open the page with ?safe after a freeze)\n");
        }
        return out.toString();
    }

    @JSBody(script = "try { return window.__vbvSafeMode === true; } catch(e) { return false; }")
    private static native boolean requestedJS();

    @JSBody(script = "try { return Math.max(0, Math.round(Number(window.__vbvPreviousFailures)||0)); } catch(e) { return 0; }")
    private static native int previousFailuresJS();
}
