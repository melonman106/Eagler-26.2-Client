package com.melonman106.vbvclient.punchy;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** All animation clips loaded from the pack's {@code punchy/animations/*.animation.json}, by name. */
public final class PunchyLibrary {
    private final Map<String, PunchyClip> clips = new LinkedHashMap<>();
    /** Files that failed to parse, with a short reason (shown by diagnostics, never thrown). */
    private final Map<String, String> problems = new LinkedHashMap<>();

    /** Adds every animation of one file. A later file replaces an earlier clip of the same name. Returns how many were read. */
    public int add(String fileName, String json) {
        int added = 0;
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject() || !root.getAsJsonObject().has("animations")
                || !root.getAsJsonObject().get("animations").isJsonObject()) {
                problems.put(fileName, "no animations object");
                return 0;
            }
            JsonObject animations = root.getAsJsonObject().getAsJsonObject("animations");
            for (Map.Entry<String, JsonElement> entry : animations.entrySet()) {
                if (!entry.getValue().isJsonObject()) continue;
                clips.put(entry.getKey(), PunchyClip.parse(entry.getKey(), entry.getValue().getAsJsonObject()));
                added++;
            }
        } catch (RuntimeException e) {
            problems.put(fileName, String.valueOf(e.getMessage()));
        }
        return added;
    }

    public PunchyClip get(String name) {
        return name == null ? null : clips.get(name);
    }

    /**
     * Finds the clip for a rule's animation name and the hand using it. Packs ship mirrored pairs
     * ({@code use_bow_left}/{@code use_bow_right}); a rule just says {@code use_bow}. Tries the side-suffixed
     * name first, then the plain one.
     */
    public PunchyClip find(String base, boolean rightHand) {
        if (base == null) return null;
        PunchyClip sided = clips.get(base + (rightHand ? "_right" : "_left"));
        return sided != null ? sided : clips.get(base);
    }

    public int size() {
        return clips.size();
    }

    public Map<String, PunchyClip> all() {
        return Collections.unmodifiableMap(clips);
    }

    public Map<String, String> problems() {
        return Collections.unmodifiableMap(problems);
    }
}
