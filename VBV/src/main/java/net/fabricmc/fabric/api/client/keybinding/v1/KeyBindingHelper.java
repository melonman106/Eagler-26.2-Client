package net.fabricmc.fabric.api.client.keybinding.v1;

import net.minecraft.client.KeyMapping;

/** Fabric-shaped keybinding registration for the Eagler client. */
public final class KeyBindingHelper {
    private KeyBindingHelper() {}

    /**
     * KeyMapping registers itself with the native Minecraft key registry when
     * constructed. This method mirrors Fabric's registration entry point.
     */
    public static KeyMapping registerKeyBinding(KeyMapping keyBinding) {
        if (keyBinding == null) throw new IllegalArgumentException("Key binding must not be null");
        return keyBinding;
    }
}
