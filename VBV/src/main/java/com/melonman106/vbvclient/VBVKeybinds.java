package com.melonman106.vbvclient;

import net.minecraft.client.input.KeyEvent;

/**
 * The configurable VBV client hotkeys.
 *
 * <p>Every VBV hotkey is a plain GLFW key code kept in {@link VBVConfigStore}, so the player can
 * change it in Options &gt; Modded Settings &gt; Keybinds... The Mods and World Map keys are read by the
 * generated {@code KeyboardHandler} hook; the Zoom and Gamma keys are pushed into their native
 * {@code KeyMapping} objects by {@link VBVExtraFeatures#applyConfiguredBindings()}.</p>
 */
public final class VBVKeybinds {
    public static final int MODS = 0;
    public static final int MAP = 1;
    public static final int ZOOM = 2;
    public static final int GAMMA = 3;
    public static final int SORT = 4;
    public static final int VOICE = 5;
    public static final int COUNT = 6;

    public static final int DEFAULT_MODS = 86;   // V
    public static final int DEFAULT_MAP = 77;    // M
    public static final int DEFAULT_ZOOM = 67;   // C
    public static final int DEFAULT_GAMMA = 71;  // G
    public static final int DEFAULT_SORT = 82;   // R
    public static final int DEFAULT_VOICE = 66;  // B

    /** Range of key codes the keybind screen accepts (GLFW keyboard codes). */
    public static final int MIN_CODE = 32;
    public static final int MAX_CODE = 348;

    private static final String[] NAMES = { "Mods menu", "World map", "Zoom", "Gamma", "Sort inventory", "Voice mute" };
    private static final int[] DEFAULTS = { DEFAULT_MODS, DEFAULT_MAP, DEFAULT_ZOOM, DEFAULT_GAMMA, DEFAULT_SORT, DEFAULT_VOICE };
    private static final int[] CODES = DEFAULTS.clone();

    private VBVKeybinds() {}

    /** Restores every key from saved settings, falling back to the default for anything invalid. */
    public static void load(int modsCode, int mapCode, int zoomCode, int gammaCode, int sortCode, int voiceCode) {
        CODES[MODS] = valid(modsCode, DEFAULT_MODS);
        CODES[MAP] = valid(mapCode, DEFAULT_MAP);
        CODES[ZOOM] = valid(zoomCode, DEFAULT_ZOOM);
        CODES[GAMMA] = valid(gammaCode, DEFAULT_GAMMA);
        CODES[SORT] = valid(sortCode, DEFAULT_SORT);
        CODES[VOICE] = valid(voiceCode, DEFAULT_VOICE);
    }

    public static int code(int action) {
        return CODES[checked(action)];
    }

    public static void set(int action, int keyCode) {
        CODES[checked(action)] = valid(keyCode, DEFAULTS[checked(action)]);
    }

    public static void resetAll() {
        System.arraycopy(DEFAULTS, 0, CODES, 0, CODES.length);
    }

    /** Display name of an action, such as "World map". */
    public static String name(int action) {
        return NAMES[checked(action)];
    }

    /** "Mods menu: V", used by the keybind screen and the settings button. */
    public static String label(int action) {
        return NAMES[checked(action)] + ": " + keyName(code(action));
    }

    /** True when the event is the configured Mods-menu key. */
    public static boolean mods(KeyEvent event) {
        return matches(MODS, event);
    }

    /** True when the event is the configured world-map key. */
    public static boolean map(KeyEvent event) {
        return matches(MAP, event);
    }

    public static boolean matches(int action, KeyEvent event) {
        return event != null && event.key() == CODES[checked(action)];
    }

    /** Readable name for a GLFW key code, short enough to fit in a button. */
    public static String keyName(int keyCode) {
        if (keyCode >= 65 && keyCode <= 90) return String.valueOf((char) keyCode);
        if (keyCode >= 48 && keyCode <= 57) return String.valueOf((char) keyCode);
        if (keyCode >= 290 && keyCode <= 313) return "F" + (keyCode - 289);
        switch (keyCode) {
            case 32: return "Space";
            case 39: return "Apostrophe";
            case 44: return "Comma";
            case 45: return "Minus";
            case 46: return "Period";
            case 47: return "Slash";
            case 59: return "Semicolon";
            case 61: return "Equals";
            case 91: return "Left Bracket";
            case 92: return "Backslash";
            case 93: return "Right Bracket";
            case 96: return "Grave";
            case 257: return "Enter";
            case 258: return "Tab";
            case 259: return "Backspace";
            case 261: return "Delete";
            case 262: return "Right";
            case 263: return "Left";
            case 264: return "Down";
            case 265: return "Up";
            case 266: return "Page Up";
            case 267: return "Page Down";
            case 268: return "Home";
            case 269: return "End";
            case 280: return "Caps Lock";
            case 340: return "Left Shift";
            case 341: return "Left Ctrl";
            case 342: return "Left Alt";
            case 344: return "Right Shift";
            case 345: return "Right Ctrl";
            case 346: return "Right Alt";
            default: return "Key " + keyCode;
        }
    }

    private static int checked(int action) {
        if (action < 0 || action >= CODES.length) {
            throw new IllegalArgumentException("Unknown VBV keybind action: " + action);
        }
        return action;
    }

    private static int valid(int keyCode, int fallback) {
        return keyCode >= MIN_CODE && keyCode <= MAX_CODE ? keyCode : fallback;
    }
}
