package com.melonman106.vbvclient;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Native Minecraft-style storage area with tabbed sections. */
public final class VBVStorageScreen extends Screen {
    private final Screen parent;
    private int tab = 0;

    private static final String[] TABS = {
        "Overview", "Worlds", "Resource Packs", "Datapacks", "Profiles", "Backups"
    };

    public VBVStorageScreen(Screen parent) {
        super(Component.literal("Storage"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        rebuild();
    }

    private void rebuild() {
        clearWidgets();

        int tabWidth = Math.min(112, Math.max(78, (width - 36) / TABS.length));
        int total = tabWidth * TABS.length + 5 * (TABS.length - 1);
        int left = width / 2 - total / 2;
        for (int i = 0; i < TABS.length; i++) {
            final int selected = i;
            addRenderableWidget(Button.builder(Component.literal(TABS[i]), b -> {
                tab = selected;
                rebuild();
            }).bounds(left + i * (tabWidth + 5), 55, tabWidth, 20).build());
        }

        int x = width / 2 - 100;
        if (tab == 0) {
            addButton("Choose Storage Folder", x, 112, b -> VBVStorage.chooseFolder());
            addButton("Reconnect Existing Folder", x, 137, b -> VBVStorage.reconnect());
        } else if (tab == 1) {
            addButton("Open Worlds", x, 112, b -> showNotice("World storage", "World save handling remains in Minecraft's native save system."));
            addButton("Back", x, 178, b -> VBVNav.open(parent));
        } else if (tab == 2) {
            addButton("Resource Packs", x, 112, b -> showNotice("Resource Packs", "Global and server resource-pack files are managed by the storage bridge."));
            addButton("Back", x, 178, b -> VBVNav.open(parent));
        } else if (tab == 3) {
            addButton("Datapacks", x, 112, b -> showNotice("Datapacks", "Global and server datapack files are managed by the storage bridge."));
            addButton("Back", x, 178, b -> VBVNav.open(parent));
        } else if (tab == 4) {
            addButton("Server Profiles", x, 112, b -> showNotice("Server Profiles", "Server profiles are stored in the selected Eaglercraft folder."));
            addButton("Back", x, 178, b -> VBVNav.open(parent));
        } else {
            addButton("World Map Backups", x, 112, b -> showNotice("Backups", "World-map backups are stored separately from your Minecraft worlds."));
            addButton("Clear Cache", x, 137, b -> showNotice("Cache", "Temporary cache cleanup is available from the storage bridge."));
            addButton("Back", x, 178, b -> VBVNav.open(parent));
        }

        addRenderableWidget(Button.builder(Component.literal("Done"), b -> VBVNav.open(parent))
            .bounds(width / 2 - 100, height - 34, 200, 20).build());
    }

    private void addButton(String label, int x, int y, Button.OnPress action) {
        addRenderableWidget(Button.builder(Component.literal(label), action)
            .bounds(x, y, 200, 20).build());
    }

    private void showNotice(String title, String message) {
        VBVNav.open(new VBVStorageInfoScreen(this, title, message));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xFF101010);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        String heading = "STORAGE";
        graphics.text(font, heading, width / 2 - font.width(heading) / 2, 28, 0xFFFFFFFF, true);

        boolean connected = VBVStorage.connected();
        String status = connected
            ? "Connected: " + VBVStorage.folderName()
            : "Browser storage active — no computer folder connected";
        graphics.text(font, status, width / 2 - font.width(status) / 2, 88,
            connected ? 0xFF55FF55 : 0xFFFFD27A, false);

        String tabTitle = TABS[tab].toUpperCase();
        graphics.text(font, tabTitle, width / 2 - font.width(tabTitle) / 2, 101, 0xFFFFFFFF, true);

        if (tab == 0) {
            String detail = "Use a computer folder for large worlds, maps, packs, datapacks and backups.";
            graphics.text(font, detail, width / 2 - font.width(detail) / 2, 160, 0xFFB0B0B0, false);
            String detail2 = "IndexedDB remains the browser fallback.";
            graphics.text(font, detail2, width / 2 - font.width(detail2) / 2, 176, 0xFF888888, false);
        }
    }

    @Override
    public void onClose() {
        VBVNav.open(parent);
    }
}
