package com.melonman106.vbvclient;

/**
 * Rendering/client profile groundwork inspired by the Chromium Eagler client.
 *
 * The profile is deliberately metadata-only for the first integration step.
 * Actual renderer/shader switches will be wired separately so changing the
 * profile cannot accidentally alter the existing VBV/WASM build yet.
 */
public enum VBVClientProfile {
    VANILLA("Vanilla", "Default Eagler rendering and UI."),
    PERFORMANCE("Performance", "Reduced visual overhead for lower-end devices."),
    SHADERS("Shaders", "Shader-capable rendering profile; renderer integration pending.");

    private final String displayName;
    private final String description;

    VBVClientProfile(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }
}
