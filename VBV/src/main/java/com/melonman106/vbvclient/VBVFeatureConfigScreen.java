package com.melonman106.vbvclient;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
/** Native configuration pages for VBV's client-side feature ports. */
public final class VBVFeatureConfigScreen extends Screen {
    private final Screen parent;
    private final String modId;
    private record Entry(Supplier<String> text, Runnable action, boolean save) {}
    private final List<Entry> entries = new ArrayList<>();
    public VBVFeatureConfigScreen(Screen parent, String modId, String title) {
        super(Component.literal(title + " Config")); this.parent = parent; this.modId = modId;
    }
    @Override protected void init() {
        super.init();
        entries.clear();
        if ("gamma-utils".equals(modId)) {
            option(() -> "Gamma feature: " + onOff(VBVExtraFeatures.isGammaFeatureEnabled()), VBVExtraFeatures::toggleGammaFeature);
            option(() -> "Brightness strength: " + (int) VBVExtraFeatures.gammaLevel(), VBVExtraFeatures::cycleGammaLevel);
            option(() -> "Status notification: " + onOff(VBVExtraFeatures.isGammaStatusEnabled()), VBVExtraFeatures::toggleGammaStatus);
        } else if ("zoomify".equals(modId)) {
            option(() -> "Zoom: " + onOff(VBVExtraFeatures.isZoomEnabled()), VBVExtraFeatures::toggleZoomEnabled);
            option(() -> "Zoom FOV: " + VBVExtraFeatures.zoomFov(), VBVExtraFeatures::cycleZoomFov);
            option(() -> "Mode: " + (VBVExtraFeatures.isZoomToggleMode() ? "Toggle" : "Hold"), VBVExtraFeatures::toggleZoomMode);
        } else if ("betterf3".equals(modId)) {
            option(() -> "BetterF3: " + onOff(VBVExtraFeatures.isBetterF3Enabled()), VBVExtraFeatures::toggleBetterF3Enabled);
            option(() -> "Show overlay: " + onOff(VBVExtraFeatures.isDebugHudEnabled()), VBVExtraFeatures::toggleDebugHud);
            option(() -> "Performance section: " + onOff(VBVExtraFeatures.debugShowPerformance()), VBVExtraFeatures::toggleDebugShowPerformance);
            option(() -> "World section: " + onOff(VBVExtraFeatures.debugShowWorld()), VBVExtraFeatures::toggleDebugShowWorld);
            option(() -> "Player section: " + onOff(VBVExtraFeatures.debugShowPlayer()), VBVExtraFeatures::toggleDebugShowPlayer);
            option(() -> "Compact layout: " + onOff(VBVExtraFeatures.debugCompact()), VBVExtraFeatures::toggleDebugCompact);
        } else if ("mouse-tweaks".equals(modId)) {
            option(() -> "Wheel tweak: " + onOff(VBVMouseTweaks.isWheelTweakEnabled()), () -> VBVMouseTweaks.setWheelTweakEnabled(!VBVMouseTweaks.isWheelTweakEnabled()));
            option(() -> "Quick move: " + onOff(VBVMouseTweaks.isQuickMoveEnabled()), () -> VBVMouseTweaks.setQuickMoveEnabled(!VBVMouseTweaks.isQuickMoveEnabled()));
            option(() -> "Drag evenly: " + onOff(VBVMouseTweaks.isDragEvenlyEnabled()), () -> VBVMouseTweaks.setDragEvenlyEnabled(!VBVMouseTweaks.isDragEvenlyEnabled()));
        } else if ("vbv-minimap".equals(modId)) {
            option(() -> "Minimap: " + onOff(VBVMiniMap.enabled), () -> VBVMiniMap.enabled = !VBVMiniMap.enabled);
            option(() -> "Map size: " + VBVMiniMap.size + "px", VBVMiniMap::cycleSize);
            option(() -> "Scan radius: " + VBVMiniMap.scanRadius + " chunks", VBVMiniMap::cycleScanRadius);
            option(() -> "Frame: " + onOff(VBVMiniMap.showFrame), () -> VBVMiniMap.showFrame = !VBVMiniMap.showFrame);
            option(() -> "Player marker: " + onOff(VBVMiniMap.showPlayerMarker), () -> VBVMiniMap.showPlayerMarker = !VBVMiniMap.showPlayerMarker);
            option(() -> "North indicator: " + onOff(VBVMiniMap.showNorth), () -> VBVMiniMap.showNorth = !VBVMiniMap.showNorth);
            link("Edit minimap position", () -> VBVNav.open(new VBVHudEditorScreen(this)));
        } else if ("jade".equals(modId)) {
            option(() -> "Jade overlay: " + onOff(VBVJadeHud.enabled), () -> VBVJadeHud.enabled = !VBVJadeHud.enabled);
            option(() -> "Show coordinates: " + onOff(VBVJadeHud.showCoordinates), () -> VBVJadeHud.showCoordinates = !VBVJadeHud.showCoordinates);
            option(() -> "Entity health: " + onOff(VBVJadeHud.showEntityHealth), () -> VBVJadeHud.showEntityHealth = !VBVJadeHud.showEntityHealth);
            option(() -> "Block hardness: " + onOff(VBVJadeHud.showBlockHardness), () -> VBVJadeHud.showBlockHardness = !VBVJadeHud.showBlockHardness);
            option(() -> "Target name: " + onOff(VBVJadeHud.showTargetName), () -> VBVJadeHud.showTargetName = !VBVJadeHud.showTargetName);
            link("Edit Jade position", () -> VBVNav.open(new VBVHudEditorScreen(this, true)));
        } else if ("shulkerboxtooltip".equals(modId)) {
            option(() -> "Shulker preview: " + onOff(VBVShulkerViewer.enabled), VBVShulkerViewer::toggle);
        } else if ("appleskin".equals(modId)) {
            option(() -> "Saturation overlay: " + onOff(VBVAppleSkin.isEnabled()), VBVAppleSkin::toggleEnabled);
            option(() -> "Held food preview: " + onOff(VBVAppleSkin.isFoodPreview()), VBVAppleSkin::toggleFoodPreview);
            option(() -> "Food / saturation text: " + onOff(VBVAppleSkin.isShowText()), VBVAppleSkin::toggleShowText);
            option(() -> "Food level text: " + onOff(VBVAppleSkin.isShowFoodText()), VBVAppleSkin::toggleFoodText);
            option(() -> "Saturation text: " + onOff(VBVAppleSkin.isShowSaturationText()), VBVAppleSkin::toggleSaturationText);
        } else if ("shine".equals(modId)) {
            option(() -> "VBV shader effects: " + onOff(ShineConfig.enabled()), ShineConfig::toggleEnabled);
            option(() -> "Bloom (optional): " + onOff(ShineConfig.bloom()), ShineConfig::toggleBloom);
            option(() -> "Colored lighting (optional): " + onOff(ShineConfig.coloredLighting()), ShineConfig::toggleColoredLighting);
            option(() -> "Rim lighting (optional): " + onOff(ShineConfig.rimLighting()), ShineConfig::toggleRimLighting);
            option(() -> "Disable vanilla shading: " + onOff(ShineConfig.disabledShading()), ShineConfig::toggleDisabledShading);
            option(() -> "Quality: " + ShineConfig.qualityLabel(), ShineConfig::cycleQuality);
            option(() -> "Light radius: " + ShineConfig.lightRadius(), ShineConfig::cycleLightRadius);
            option(() -> "Bloom strength: " + ShineConfig.bloomStrength(), ShineConfig::cycleBloomStrength);
            option(() -> "Rim strength: " + ShineConfig.rimStrength(), ShineConfig::cycleRimStrength);
        }
        layoutEntries();
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> VBVNav.open(parent)).bounds(width / 2 - 50, height - 30, 100, 20).build());
    }
    private void option(Supplier<String> text, Runnable action) {
        entries.add(new Entry(text, action, true));
    }
    private void link(String label, Runnable action) {
        entries.add(new Entry(() -> label, action, false));
    }
    /** Lays the options out to fit the current GUI height, switching to two columns when needed. */
    private void layoutEntries() {
        int n = entries.size();
        if (n == 0) return;
        int top = 36;
        int avail = Math.max(24, height - 56 - top);
        boolean twoColumns = n * 27 > avail && width >= 330 && n > 1;
        int perColumn = twoColumns ? (n + 1) / 2 : n;
        int rowH = Math.max(22, Math.min(27, avail / perColumn));
        int buttonH = Math.min(22, rowH - 3);
        int colW = twoColumns ? Math.min(250, (width - 30) / 2) : 260;
        int totalW = twoColumns ? colW * 2 + 10 : colW;
        int left = width / 2 - totalW / 2;
        for (int i = 0; i < n; i++) {
            Entry entry = entries.get(i);
            int col = twoColumns ? i / perColumn : 0;
            int row = twoColumns ? i % perColumn : i;
            int x = left + col * (colW + 10);
            int y = top + row * rowH;
            addRenderableWidget(Button.builder(Component.literal(entry.text().get()), button -> {
                entry.action().run();
                if (entry.save()) VBVConfigStore.saveAll();
                button.setMessage(Component.literal(entry.text().get()));
            }).bounds(x, y, colW, buttonH).build());
        }
    }
    private static String onOff(boolean value) { return value ? "ON" : "OFF"; }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xFF151515);
        String heading = title.getString();
        graphics.text(font, heading, width / 2 - font.width(heading) / 2, 12, VBVTheme.accentLight(), true);
        graphics.fill(width / 2 - 20, 24, width / 2 + 20, 25, VBVTheme.accent());
        String note = "Changes apply immediately; keybinds shown are fixed in this build.";
        if (height >= 200) graphics.text(font, note, width / 2 - font.width(note) / 2, height - 44, 0xFFAAAAAA, true);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }
}
