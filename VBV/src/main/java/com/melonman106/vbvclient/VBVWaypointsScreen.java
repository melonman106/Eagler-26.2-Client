package com.melonman106.vbvclient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class VBVWaypointsScreen extends Screen {
    private final Screen parent;
    private int scroll;

    public VBVWaypointsScreen(Screen parent) {
        super(Component.literal("Waypoints"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        Minecraft minecraft = Minecraft.getInstance();
        String world = VBVWorldMap.worldKey(minecraft);
        List<VBVWaypoint> list = new ArrayList<>(VBVWorldMap.waypoints(world).values());
        list.sort(Comparator.comparing(VBVWaypoint::name, String.CASE_INSENSITIVE_ORDER));

        int visible = Math.max(1, (height - 105) / 26);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, list.size() - visible)));
        int end = Math.min(list.size(), scroll + visible);
        for (int i = scroll; i < end; i++) {
            VBVWaypoint selected = list.get(i);
            int y = 44 + (i - scroll) * 26;
            addRenderableWidget(Button.builder(
                Component.literal(selected.name() + "  [" + selected.x() + ", " + selected.y() + ", " + selected.z() + "]"),
                button -> VBVNav.open(new VBVWaypointDetailsScreen(this, selected))
            ).bounds(12, y, Math.max(1, Math.min(width - 24, 520)), 22).build());
        }

        addRenderableWidget(Button.builder(Component.literal("Back"), button -> VBVNav.open(parent))
            .bounds(width / 2 - 50, height - 30, 100, 20).build());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0 || mouseY < 40 || mouseY > height - 36) return false;
        Minecraft minecraft = Minecraft.getInstance();
        List<VBVWaypoint> list = new ArrayList<>(VBVWorldMap.waypoints(VBVWorldMap.worldKey(minecraft)).values());
        int visible = Math.max(1, (height - 105) / 26);
        int next = Math.max(0, Math.min(Math.max(0, list.size() - visible), scroll + (scrollY < 0 ? 1 : -1)));
        if (next == scroll) return false;
        scroll = next;
        clearWidgets();
        init();
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xFF151515);
        graphics.text(font, title.getString(), width / 2 - font.width(title.getString()) / 2, 14, 0xFFFFFFFF, true);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }
}
