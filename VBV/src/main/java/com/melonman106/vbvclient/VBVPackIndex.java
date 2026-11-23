package com.melonman106.vbvclient;

import org.teavm.jso.JSBody;

/**
 * The resource-pack merge index written at build time.
 *
 * <p>Eighteen packs are merged into one resource tree and the last pack copied
 * wins every contested path. That used to be visible only in the CI log, which
 * is no help when a texture or a model looks wrong in the game. The build now
 * writes a short text summary (how many files each pack contributed, and which
 * paths more than one pack supplies) and the overlay embeds it as
 * {@code window.__vbvPackIndexText}; Diagnostics prints it.</p>
 */
public final class VBVPackIndex {
    private static final int MAX_LINES = 60;

    private VBVPackIndex() {}

    /** The build index as report lines, or an empty string when there is none. */
    public static String report() {
        String text;
        try {
            text = rawJS();
        } catch (Throwable ignored) {
            return "";
        }
        if (text == null) {
            return "";
        }
        text = text.replace("\r", "").trim();
        if (text.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder("Resource packs (build index)\n");
        String[] lines = text.split("\n");
        int shown = Math.min(lines.length, MAX_LINES);
        for (int i = 0; i < shown; i++) {
            out.append(lines[i]).append('\n');
        }
        if (lines.length > shown) {
            out.append("(").append(lines.length - shown).append(" more lines in the build log)\n");
        }
        return out.toString();
    }

    @JSBody(script = "try { return (typeof window.__vbvPackIndexText === 'string') ? window.__vbvPackIndexText : ''; } catch(e) { return ''; }")
    private static native String rawJS();
}
