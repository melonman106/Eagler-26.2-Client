package com.melonman106.vbvclient;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
/** Native configuration pages for VBV's client-side feature ports. */
public final class VBVFeatureConfigScreen extends Screen {
    private final Screen parent;
    private final String modId;
    private int nextY = 42;
    public VBVFeatureConfigScreen(Screen parent, String modId, String title) {
        super(Component.literal(title + " Config")); this.parent = parent; this.modId = modId;
    }
    @Override protected void init() {
        super.init();
        if ("gamma-utils".equals(modId)) {
            option(() -> "Gamma feature: " + onOff(VBVExtraFeatures.isGammaFeatureEnabled()), VBVExtraFeatures::toggleGammaFeature);
            option(() -> "Brightness strength: " + (int) VBVExtraFeatures.gammaLevel(), VBVExtraFeatures::cycleGammaLevel);
        } else if ("zoomify".equals(modId)) {
            option(() -> "Zoom: " + onOff(VBVExtraFeatures.isZoomEnabled()), VBVExtraFeatures::toggleZoomEnabled);
            option(() -> "Zoom FOV: " + VBVExtraFeatures.zoomFov(), VBVExtraFeatures::cycleZoomFov);
            option(() -> "Mode: " + (VBVExtraFeatures.isZoomToggleMode() ? "Toggle" : "Hold"), VBVExtraFeatures::toggleZoomMode);
        } else if ("betterf3".equals(modId)) {
            option(() -> "BetterF3: " + onOff(VBVExtraFeatures.isBetterF3Enabled()), VBVExtraFeatures::toggleBetterF3Enabled);
            option(() -> "Show overlay: " + onOff(VBVExtraFeatures.isDebugHudEnabled()), VBVExtraFeatures::toggleDebugHud);
        } else if ("mouse-tweaks".equals(modId)) {
            option(() -> "Wheel tweak: " + onOff(VBVMouseTweaks.isWheelTweakEnabled()), () -> VBVMouseTweaks.setWheelTweakEnabled(!VBVMouseTweaks.isWheelTweakEnabled()));
            option(() -> "Quick move: " + onOff(VBVMouseTweaks.isQuickMoveEnabled()), () -> VBVMouseTweaks.setQuickMoveEnabled(!VBVMouseTweaks.isQuickMoveEnabled()));
            option(() -> "Drag evenly: " + onOff(VBVMouseTweaks.isDragEvenlyEnabled()), () -> VBVMouseTweaks.setDragEvenlyEnabled(!VBVMouseTweaks.isDragEvenlyEnabled()));
        } else if ("appleskin".equals(modId)) {
            option(() -> "Food HUD: " + onOff(VBVAppleSkin.isEnabled()), VBVAppleSkin::toggleEnabled);
        }
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> VBVNav.open(parent)).bounds(width / 2 - 50, height - 30, 100, 20).build());
    }
    private void option(Supplier<String> text, Runnable action) {
        addRenderableWidget(Button.builder(Component.literal(text.get()), button -> { action.run(); button.setMessage(Component.literal(text.get())); }).bounds(width / 2 - 130, nextY, 260, 22).build());
        nextY += 27;
    }
    private static String onOff(boolean value) { return value ? "ON" : "OFF"; }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xFF151515);
        String heading = title.getString();
        graphics.text(font, heading, width / 2 - font.width(heading) / 2, 12, 0xFFFFFFFF, true);
        String note = "Changes apply immediately; keybinds shown are fixed in this build.";
        graphics.text(font, note, width / 2 - font.width(note) / 2, height - 48, 0xFFAAAAAA, true);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }
}
