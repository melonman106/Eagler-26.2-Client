package com.melonman106.vbvclient;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * "Modded Settings", reached from the Options menu. Holds VBV's own client settings that do not
 * belong in vanilla's video or controls screens, starting with Level of Detail (LOD).
 */
public final class VBVModdedSettingsScreen extends Screen {
    private static final int TEXT_DIM = 0xFFB8B0A0;
    private static final int ERROR = 0xFFFF6B6B;

    private final Screen parent;
    private Button lodButton;
    private Button cullingButton;
    private Button meterButton;
    private Button cemButton;
    private Button cemAnimButton;
    private int lodDescY;

    public VBVModdedSettingsScreen(Screen parent) {
        super(Component.literal("Modded Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int cx = width / 2;
        int rowW = Math.min(280, width - 24);
        int left = cx - rowW / 2;
        boolean tight = height < 230;
        int gap = tight ? 2 : 4;
        int y = tight ? 24 : 34;

        lodButton = Button.builder(lodLabel(), button -> {
            VBVLod.cycle(1);
            VBVConfigStore.saveAll();
            button.setMessage(lodLabel());
        }).bounds(left, y, rowW, 20).build();
        addRenderableWidget(lodButton);
        y += 20 + gap;
        lodDescY = y;

        // The description block is drawn between the LOD button and the link buttons.
        int descLines = tight ? 3 : 5;
        y += descLines * (font.lineHeight + 1) + 6;

        // Two-column grid: links on the first rows, then the performance tools.
        int half = (rowW - 4) / 2;
        int right = left + half + 4;
        addRenderableWidget(Button.builder(Component.literal("Mod Settings..."),
            button -> VBVNav.open(new VBVModsScreen(this))).bounds(left, y, half, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Hand Position..."),
            button -> VBVNav.open(new VBVFirstPersonAnimationsConfigScreen(this))).bounds(right, y, half, 20).build());
        y += 20 + gap;
        addRenderableWidget(Button.builder(Component.literal("Menu Colour..."),
            button -> VBVNav.open(new VBVLauncherScreen(this, VBVLauncherScreen.Tab.APPEARANCE))).bounds(left, y, half, 20).build());
        cullingButton = Button.builder(cullingLabel(), button -> {
            VBVEntityCulling.toggle();
            VBVConfigStore.saveAll();
            button.setMessage(cullingLabel());
        }).bounds(right, y, half, 20).build();
        addRenderableWidget(cullingButton);
        y += 20 + gap;
        meterButton = Button.builder(meterLabel(), button -> {
            VBVPerf.toggleMeter();
            VBVConfigStore.saveAll();
            button.setMessage(meterLabel());
        }).bounds(left, y, half, 20).build();
        addRenderableWidget(meterButton);
        addRenderableWidget(Button.builder(Component.literal("Copy Perf Report"), button -> {
            VBVPerf.copyReport();
            button.setMessage(Component.literal("Copied!"));
        }).bounds(right, y, half, 20).build());
        y += 20 + gap;
        cemButton = Button.builder(cemLabel(), button -> {
            VBVCem.toggle();
            VBVConfigStore.saveAll();
            // Entity models are baked during a resource reload, so the change shows after F3+T.
            button.setMessage(Component.literal("Models " + (VBVCem.isEnabled() ? "ON" : "OFF") + " - press F3+T"));
        }).bounds(left, y, half, 20).build();
        addRenderableWidget(cemButton);
        // The animation formulas of a custom model apply immediately, no resource reload needed.
        cemAnimButton = Button.builder(cemAnimLabel(), button -> {
            VBVCem.toggleAnimations();
            VBVConfigStore.saveAll();
            button.setMessage(cemAnimLabel());
        }).bounds(right, y, half, 20).build();
        addRenderableWidget(cemAnimButton);

        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
            .bounds(cx - 100, height - 27, 200, 20).build());
    }

    private static Component cullingLabel() {
        return Component.literal("Entity Culling: " + (VBVEntityCulling.isEnabled() ? "ON" : "OFF"));
    }

    private static Component cemLabel() {
        return Component.literal("Custom Models: " + (VBVCem.isEnabled() ? "ON" : "OFF"));
    }

    private static Component cemAnimLabel() {
        return Component.literal("Model Animations: " + (VBVCem.isAnimationsEnabled() ? "ON" : "OFF"));
    }

    private static Component meterLabel() {
        return Component.literal("FPS Meter: " + (VBVPerf.isMeter() ? "ON" : "OFF"));
    }

    private static Component lodLabel() {
        return Component.literal("Level of Detail (LOD): " + VBVLod.name());
    }

    @Override
    public void onClose() {
        VBVConfigStore.saveAll();
        VBVNav.open(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        String heading = title.getString();
        graphics.text(font, heading, width / 2 - font.width(heading) / 2, height < 230 ? 8 : 14, VBVTheme.accentLight(), true);

        int rowW = Math.min(280, width - 24);
        int left = width / 2 - rowW / 2;
        int level = VBVLod.getLevel();
        List<String> lines = new ArrayList<>();
        String failure = VBVLod.failure();
        if (!failure.isEmpty()) {
            lines.addAll(wrap("LOD switched itself off after an error: " + failure + " Pick a level again to retry.", rowW));
        } else if (level == VBVLod.OFF) {
            lines.addAll(wrap(VBVLod.detail(level), rowW));
            lines.addAll(wrap("Turn it on to draw simplified terrain far past your render distance.", rowW));
        } else {
            lines.addAll(wrap(VBVLod.detail(level), rowW));
            lines.addAll(wrap("Reach " + VBVLod.rangeChunks() + " chunks. Draws beyond your Render Distance ("
                + minecraft.options.getEffectiveRenderDistance() + ").", rowW));
            lines.addAll(wrap("Remembered terrain: " + VBVLodCache.size() + " chunks (fills in as you explore).", rowW));
        }
        int maxLines = height < 230 ? 3 : 5;
        int color = failure.isEmpty() ? TEXT_DIM : ERROR;
        for (int i = 0; i < lines.size() && i < maxLines; i++) {
            graphics.text(font, lines.get(i), left, lodDescY + i * (font.lineHeight + 1), color, true);
        }
    }

    private List<String> wrap(String text, int maxWidth) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (font.width(candidate) > maxWidth && line.length() > 0) {
                out.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (line.length() > 0) {
            out.add(line.toString());
        }
        return out;
    }
}
