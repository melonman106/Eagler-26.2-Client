package com.melonman106.vbvclient;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Configuration screen for the native VBV first-person animation layer. */
public final class VBVFirstPersonAnimationsConfigScreen extends Screen {
    private final Screen parent;

    public VBVFirstPersonAnimationsConfigScreen(Screen parent) {
        super(Component.literal("First-Person Animations Config"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(stateLabel(), button -> {
            VBVFirstPersonAnimations.toggle();
            button.setMessage(stateLabel());
        }).bounds(width / 2 - 100, height / 2 - 10, 200, 22).build());

        addRenderableWidget(Button.builder(Component.literal("Back"), button -> VBVNav.open(parent))
            .bounds(width / 2 - 100, height / 2 + 24, 200, 20).build());
    }

    private static Component stateLabel() {
        return Component.literal("Animations: " + (VBVFirstPersonAnimations.isEnabled() ? "ON" : "OFF"));
    }
}
