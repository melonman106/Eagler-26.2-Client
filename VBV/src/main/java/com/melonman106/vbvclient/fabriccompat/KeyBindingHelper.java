package com.melonman106.vbvclient.fabriccompat;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Registration and dispatch primitives for the Eagler keyboard bridge. */
public final class KeyBindingHelper {
    private static final Map<String, KeyBinding> BINDINGS = new LinkedHashMap<>();
    private KeyBindingHelper() {}

    public static synchronized KeyBinding registerKeyBinding(KeyBinding binding) {
        if (binding == null) throw new IllegalArgumentException("Key binding must not be null");
        BINDINGS.put(binding.getTranslationKey(), binding);
        return binding;
    }

    public static synchronized Collection<KeyBinding> getAllKeyBindings() {
        return Collections.unmodifiableCollection(BINDINGS.values());
    }

    /** Called by a keyboard hook when a browser keydown matches a registered code. */
    public static synchronized boolean onKeyPressed(int keyCode) {
        boolean matched = false;
        for (KeyBinding binding : BINDINGS.values()) {
            if (binding.getDefaultKeyCode() == keyCode) {
                binding.setPressed(true);
                matched = true;
            }
        }
        return matched;
    }

    /** Called by a keyboard hook on keyup to clear matching held bindings. */
    public static synchronized void onKeyReleased(int keyCode) {
        for (KeyBinding binding : BINDINGS.values()) {
            if (binding.getDefaultKeyCode() == keyCode) binding.setPressed(false);
        }
    }
}
