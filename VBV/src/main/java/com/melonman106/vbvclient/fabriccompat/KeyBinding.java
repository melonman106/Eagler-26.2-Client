package com.melonman106.vbvclient.fabriccompat;

/**
 * Logical keybinding descriptor. Actual browser key dispatch must be connected
 * by a client patch; registering a descriptor alone does not intercept keys.
 */
public final class KeyBinding {
    private final String translationKey;
    private final String category;
    private final int keyCode;
    private boolean down;
    private int presses;

    public KeyBinding(String translationKey, String category, int keyCode) {
        if (translationKey == null || translationKey.isBlank()) throw new IllegalArgumentException("Translation key is required");
        this.translationKey = translationKey;
        this.category = category == null ? "key.categories.misc" : category;
        this.keyCode = keyCode;
    }

    public String getTranslationKey() { return translationKey; }
    public String getCategory() { return category; }
    public int getDefaultKeyCode() { return keyCode; }
    public boolean isPressed() {
        if (presses <= 0) return false;
        presses--;
        return true;
    }
    public boolean isDown() { return down; }
    void setPressed(boolean pressed) {
        if (pressed && !down) presses++;
        down = pressed;
    }
}
