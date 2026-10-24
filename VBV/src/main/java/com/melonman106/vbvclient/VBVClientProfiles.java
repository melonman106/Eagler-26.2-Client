package com.melonman106.vbvclient;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Central profile registry for the Chromium-client-inspired rendering modes.
 *
 * This first stage intentionally keeps the selected profile in memory only.
 * Persistence and renderer/shader hooks will be added after the profile UI is
 * proven against the current 26.2 U2 build.
 */
public final class VBVClientProfiles {
    private static final List<VBVClientProfile> PROFILES =
        Collections.unmodifiableList(Arrays.asList(VBVClientProfile.values()));

    private static VBVClientProfile current = VBVClientProfile.VANILLA;

    private VBVClientProfiles() {}

    public static List<VBVClientProfile> getProfiles() {
        return PROFILES;
    }

    public static VBVClientProfile current() {
        return current;
    }

    public static void select(VBVClientProfile profile) {
        if (profile != null) current = profile;
    }

    public static void next() {
        VBVClientProfile[] values = VBVClientProfile.values();
        int next = (current.ordinal() + 1) % values.length;
        current = values[next];
    }
}
