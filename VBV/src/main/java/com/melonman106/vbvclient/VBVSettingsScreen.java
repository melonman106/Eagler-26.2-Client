package com.melonman106.vbvclient;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Dungeons-style breadcrumb settings area for the VBV home screen. */
public final class VBVSettingsScreen extends Screen {
    private final Screen parent;
    private final List<String> breadcrumb = new ArrayList<>();
    private String page = "root";

    private boolean highContrast;
    private boolean reducedMotion;
    private boolean largeText;
    private boolean subtitles;
    private boolean narrator;

    public VBVSettingsScreen(Screen parent) {
        super(Component.literal("Settings"));
        this.parent = parent;
        breadcrumb.add("Settings");
    }

    @Override
    protected void init() {
        super.init();
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        int w = Math.min(430, Math.max(280, width / 2));
        int x = width / 2 - w / 2;
        int y = 78;
        int gap = 31;

        if (page.equals("root")) {
            add("Language", x, y, w, () -> openPage("Language", "language")); y += gap;
            add("Accessibility", x, y, w, () -> openPage("Accessibility", "accessibility")); y += gap;
            add("Video", x, y, w, () -> openPage("Video", "video")); y += gap;
            add("Audio", x, y, w, () -> openPage("Audio", "audio")); y += gap;
            add("Controls", x, y, w, () -> openPage("Controls", "controls")); y += gap;
            add("Gameplay", x, y, w, () -> openPage("Gameplay", "gameplay")); y += gap;
            add("Interface", x, y, w, () -> openPage("Interface", "interface")); y += gap;
            add("Resource Packs", x, y, w, () -> openPage("Resource Packs", "packs")); y += gap;
            add("Account & Privacy", x, y, w, () -> openPage("Account & Privacy", "account")); y += gap;
            add("Storage", x, y, w, () -> openPage("Storage", "storage")); y += gap;
            add("About", x, y, w, () -> openPage("About", "about"));
            addBack(x, Math.min(height - 42, y + gap + 8), w);
            return;
        }

        if (page.equals("language")) {
            add("English (US)", x, y, w, () -> {}); y += gap;
            add("Language Packs", x, y, w, () -> {});
        } else if (page.equals("accessibility")) {
            addToggle("High Contrast", highContrast, x, y, w, v -> highContrast = v); y += gap;
            addToggle("Reduced Motion", reducedMotion, x, y, w, v -> reducedMotion = v); y += gap;
            addToggle("Large Text", largeText, x, y, w, v -> largeText = v); y += gap;
            addToggle("Subtitles", subtitles, x, y, w, v -> subtitles = v); y += gap;
            addToggle("Narrator", narrator, x, y, w, v -> narrator = v);
        } else {
            String[] options = switch (page) {
                case "video" -> new String[]{"Graphics", "Brightness", "UI Scale", "View Distance", "Animations"};
                case "audio" -> new String[]{"Master Volume", "Music", "Sound Effects", "Weather", "Players"};
                case "controls" -> new String[]{"Keyboard & Mouse", "Touch Controls", "Controller", "Keybinds"};
                case "gameplay" -> new String[]{"Difficulty", "Chat", "Auto-Jump", "Hints", "Notifications"};
                case "interface" -> new String[]{"HUD", "HUD Editor", "Crosshair", "Coordinates", "Status Effects"};
                case "packs" -> new String[]{"Active Resource Packs", "Available Packs", "Pack Settings"};
                case "account" -> new String[]{"Player Name", "Privacy", "Online Features", "Party & Friends"};
                case "storage" -> new String[]{"World Storage", "Import / Export", "Clear Cache"};
                default -> new String[]{"Eaglercraft 26.2", "VBV Client", "Credits", "Open Source Notices"};
            };
            for (String option : options) {
                add(option, x, y, w, () -> {});
                y += gap;
            }
        }
        addBack(x, Math.min(height - 42, y + 8), w);
    }

    private void add(String label, int x, int y, int w, Runnable action) {
        addRenderableWidget(Button.builder(Component.literal(label), b -> action.run())
            .bounds(x, y, w, 24).build());
    }

    private interface ToggleAction { void set(boolean value); }

    private void addToggle(String label, boolean value, int x, int y, int w, ToggleAction action) {
        String text = label + ": " + (value ? "ON" : "OFF");
        add(text, x, y, w, () -> {
            action.set(!value);
            rebuild();
        });
    }

    private void addBack(int x, int y, int w) {
        add("Back", x, y, w, () -> {
            if (page.equals("root")) {
                VBVNav.open(parent);
            } else {
                page = "root";
                breadcrumb.clear();
                breadcrumb.add("Settings");
                rebuild();
            }
        });
    }

    private void openPage(String name, String id) {
        page = id;
        breadcrumb.clear();
        breadcrumb.add("Settings");
        breadcrumb.add(name);
        rebuild();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.fill(0, 0, width, height, 0x50000000);
        String crumbs = String.join("  >  ", breadcrumb).toUpperCase();
        graphics.text(font, crumbs, 28, 28, 0xFFFFFFFF, true);
        graphics.fill(28, 50, width - 28, 51, 0x80FFFFFF);
    }

    @Override
    public void onClose() {
        VBVNav.open(parent);
    }
}
