package com.melonman106.vbvclient;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Lists, applies and deletes persisted per-server client profiles. */
public final class VBVServerProfilesScreen extends Screen {
    private final Screen parent;
    private String[] profiles = new String[0];

    public VBVServerProfilesScreen(Screen parent) {
        super(Component.literal("Server Profiles"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        refresh();
        addRenderableWidget(Button.builder(Component.literal("NEW PROFILE"),
            button -> VBVNav.open(new VBVStorageProfileScreen(this)))
            .bounds(width / 2 - 155, height - 34, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("REFRESH"),
            button -> { refresh(); rebuildWidgets(); })
            .bounds(width / 2 - 50, height - 34, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("BACK"),
            button -> VBVNav.open(parent))
            .bounds(width / 2 + 55, height - 34, 100, 20).build());

        int visible = Math.min(profiles.length, 10);
        for (int i = 0; i < visible; i++) {
            String[] fields = profiles[i].split("\\t", -1);
            if (fields.length < 3) continue;
            String name = fields[0];
            String address = fields[1];
            String distance = fields[2];
            int y = 76 + i * 42;
            addRenderableWidget(Button.builder(Component.literal("APPLY"),
                button -> VBVStorage.applyProfile(address))
                .bounds(width / 2 + 85, y, 65, 20).build());
            addRenderableWidget(Button.builder(Component.literal("DELETE"),
                button -> { VBVStorage.deleteProfile(address); rebuildWidgets(); })
                .bounds(width / 2 + 155, y, 65, 20).build());
            addRenderableWidget(Button.builder(Component.literal(name + "  •  " + address + "  •  " + distance + " chunks"),
                button -> VBVStorage.applyProfile(address))
                .bounds(width / 2 - 160, y, 238, 20).build());
        }
    }

    private void refresh() {
        profiles = VBVStorage.profiles();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xFF101010);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        String title = "SERVER PROFILES";
        graphics.text(font, title, width / 2 - font.width(title) / 2, 28, 0xFFFFFFFF, true);
        graphics.text(font, "Profiles are saved locally and auto-applied when you join the matching server.",
            width / 2 - 205, 52, 0xFFAAAAAA, false);
        if (profiles.length == 0) {
            String empty = "No server profiles saved yet.";
            graphics.text(font, empty, width / 2 - font.width(empty) / 2, 100, 0xFF888888, false);
        }
    }

    @Override
    public void onClose() {
        VBVNav.open(parent);
    }
}
