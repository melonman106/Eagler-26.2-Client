package com.melonman106.vbvclient;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * Options &gt; Modded Settings &gt; Keybinds...: rebinds every VBV client hotkey.
 *
 * <p>Click a row, press the key you want, and the change is applied and written to the VBV settings
 * immediately. Escape cancels the key that is being captured (a second Escape closes the screen).
 * If two actions end up on the same key a warning is shown above the buttons; both still fire.</p>
 */
public final class VBVKeybindConfigScreen extends Screen {
    private static final int TEXT_DIM = 0xFFB8B0A0;
    private static final int WARN = 0xFFFFC46B;

    private final Screen parent;
    private final Button[] rows = new Button[VBVKeybinds.COUNT];
    private int listening = -1;

    public VBVKeybindConfigScreen(Screen parent) {
        super(Component.literal("VBV Keybinds"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        listening = -1;
        int rowW = Math.min(280, width - 24);
        int left = width / 2 - rowW / 2;
        int btnH = height < 250 ? 18 : 20;
        int gap = height < 250 ? 2 : 4;
        int y = 42;
        for (int action = 0; action < VBVKeybinds.COUNT; action++) {
            final int index = action;
            rows[action] = Button.builder(label(action), button -> beginListening(index))
                .bounds(left, y, rowW, btnH).build();
            addRenderableWidget(rows[action]);
            y += btnH + gap;
        }
        int half = (rowW - 4) / 2;
        addRenderableWidget(Button.builder(Component.literal("Reset to Defaults"), button -> {
            VBVKeybinds.resetAll();
            applyAndSave();
            refreshLabels();
        }).bounds(left, y + gap, half, btnH).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
            .bounds(left + half + 4, y + gap, half, btnH).build());
    }

    private void beginListening(int action) {
        listening = action;
        refreshLabels();
    }

    private Component label(int action) {
        if (listening == action) {
            return Component.literal(VBVKeybinds.name(action) + ": press a key...");
        }
        return Component.literal(VBVKeybinds.label(action));
    }

    private void refreshLabels() {
        for (int action = 0; action < VBVKeybinds.COUNT; action++) {
            if (rows[action] != null) rows[action].setMessage(label(action));
        }
    }

    private static void applyAndSave() {
        VBVExtraFeatures.applyConfiguredBindings();
        VBVConfigStore.saveAll();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (listening >= 0) {
            int action = listening;
            listening = -1;
            // Escape cancels the capture instead of closing the screen.
            if (!event.isEscape()) {
                int keyCode = event.key();
                if (keyCode >= VBVKeybinds.MIN_CODE && keyCode <= VBVKeybinds.MAX_CODE) {
                    VBVKeybinds.set(action, keyCode);
                    applyAndSave();
                }
            }
            refreshLabels();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        listening = -1;
        VBVConfigStore.saveAll();
        VBVNav.open(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        String heading = title.getString();
        graphics.text(font, heading, width / 2 - font.width(heading) / 2, 16, VBVTheme.accentLight(), true);
        String hint = listening >= 0
            ? "Press the key you want, or Escape to cancel."
            : "Click a key, then press the new one. Escape closes this menu.";
        graphics.text(font, hint, width / 2 - font.width(hint) / 2, 28, TEXT_DIM, true);
        String conflict = conflictWarning();
        if (!conflict.isEmpty() && height >= 190) {
            graphics.text(font, conflict, width / 2 - font.width(conflict) / 2, height - 44, WARN, true);
        }
    }

    private static String conflictWarning() {
        for (int a = 0; a < VBVKeybinds.COUNT; a++) {
            for (int b = a + 1; b < VBVKeybinds.COUNT; b++) {
                if (VBVKeybinds.code(a) == VBVKeybinds.code(b)) {
                    return VBVKeybinds.name(a) + " and " + VBVKeybinds.name(b) + " share a key";
                }
            }
        }
        return "";
    }
}
