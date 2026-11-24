package com.melonman106.vbvclient;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Modrinth update checker: compares every remembered installed pack with the newest published
 * version of its project and offers a one-click update.
 *
 * <p>The registry is written by {@link VBVModrinth} whenever a download finishes. The check runs in
 * the browser bridge (one request per installed project) and the result is polled here; updates
 * found are also pushed into the main-menu notification centre.</p>
 */
public final class VBVModrinthUpdatesScreen extends Screen {
    private static final int ROW_HEIGHT = 40;
    private static final int MIN_ROWS = 2;
    private static final int MAX_ROWS = 6;

    private final Screen parent;
    private final List<Row> rows = new ArrayList<>();
    private int scroll;
    private String lastStatus = "";
    private static int lastNotified;

    private record Row(String projectId, String title, String installed, String latest) {}

    public VBVModrinthUpdatesScreen(Screen parent) {
        super(Component.literal("Modrinth Updates"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        VBVClient.init();
        VBVModrinth.initialize();
        VBVModrinth.checkUpdates();
        reload();
        rebuild();
    }

    private void reload() {
        rows.clear();
        for (String line : VBVModrinth.updateRows()) {
            String[] f = line.split("\t", -1);
            if (f.length < 4 || f[0].isEmpty()) continue;
            rows.add(new Row(f[0], f[1], f[2], f[3]));
        }
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rows.size() - visibleRows())));
    }

    /** Rows that fit between the header and the footer buttons at the current GUI size. */
    private int visibleRows() {
        return Math.max(MIN_ROWS, Math.min(MAX_ROWS, (height - 110) / ROW_HEIGHT));
    }

    private void rebuild() {
        clearWidgets();
        addRenderableWidget(Button.builder(Component.literal("CHECK AGAIN"), button -> {
            VBVModrinth.checkUpdates();
            lastStatus = "";
        }).bounds(width / 2 - 150, height - 28, 110, 20).build());
        addRenderableWidget(Button.builder(Component.literal("BACK"), button -> VBVNav.open(parent))
            .bounds(width / 2 - 34, height - 28, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("BROWSE PACKS"), button -> VBVNav.open(new VBVModrinthScreen(parent)))
            .bounds(width / 2 + 72, height - 28, 110, 20).build());

        int shown = Math.min(visibleRows(), Math.max(0, rows.size() - scroll));
        for (int i = 0; i < shown; i++) {
            Row row = rows.get(scroll + i);
            int y = 58 + i * ROW_HEIGHT;
            VBVUi.ThemedButton install = new VBVUi.ThemedButton(width - 116, y + 8, 100, 20, "INSTALL UPDATE",
                VBVUi.Style.SMALL, button -> {
                    if (VBVModrinth.installBusy()) return;
                    VBVModrinth.install(row.projectId(), row.title());
                    VBVNotifications.push("Installing Modrinth update", row.title() + " " + row.latest(), null);
                });
            install.active = !VBVModrinth.installBusy();
            addRenderableWidget(install);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (VBVModrinth.packReady()) {
            VBVModrinth.drainPacks();
            VBVModrinth.finishInstallIfNeeded();
            VBVNotifications.push("Modrinth update downloaded",
                "Open Options > Resource Packs to finish importing it.", null);
            // The downloaded update is now in the importer; check again for the new registry row.
            VBVModrinth.checkUpdates();
            return;
        }
        VBVModrinth.finishInstallIfNeeded();
        String status = VBVModrinth.updatesStatus();
        if (!status.equals(lastStatus)) {
            lastStatus = status;
            reload();
            rebuild();
            if (rows.isEmpty()) {
                // Nothing to announce now, so a later update is announced again.
                lastNotified = 0;
            } else if (rows.size() != lastNotified) {
                lastNotified = rows.size();
                VBVNotifications.push("Modrinth updates available",
                    rows.size() + (rows.size() == 1 ? " installed pack can" : " installed packs can") + " be updated.",
                    rows.size() == 1 ? "https://modrinth.com/resourcepack/" + rows.get(0).projectId() : null);
            }
        }
    }

    private boolean updatesBusy() {
        return VBVModrinth.updatesBusy();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int direction = scrollY > 0 ? -1 : scrollY < 0 ? 1 : 0;
        if (direction == 0) return false;
        int next = Math.max(0, Math.min(Math.max(0, rows.size() - visibleRows()), scroll + direction));
        if (next == scroll) return true;
        scroll = next;
        rebuild();
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        String heading = "MODRINTH UPDATES";
        graphics.text(font, heading, width / 2 - font.width(heading) / 2, 14, 0xFFFFFFFF, true);
        String status = updatesBusy() ? "Checking Modrinth..." : VBVModrinth.updatesStatus();
        if (status.isEmpty()) status = VBVModrinth.installedCount() == 0
            ? "No Modrinth packs installed yet" : "Ready to check installed packs";
        graphics.text(font, VBVUi.fit(font, status, width - 24), 12, 32, VBVUi.TEXT_DIM, false);
        if (VBVModrinth.installBusy() || VBVModrinth.installProgress() >= 0) {
            String progress = VBVModrinth.installStatus();
            graphics.text(font, VBVUi.fit(font, progress, width - 150), 12, height - 42, VBVUi.TEXT, false);
            VBVUi.progressBar(graphics, width - 132, height - 42, 120, 8, VBVModrinth.installProgress());
        }

        int shown = Math.min(visibleRows(), Math.max(0, rows.size() - scroll));
        if (rows.isEmpty()) {
            String hint = updatesBusy() ? "Asking Modrinth for the latest versions..."
                : "Every remembered pack is on its newest version.";
            graphics.text(font, hint, width / 2 - font.width(hint) / 2, height / 2 - 10, VBVUi.TEXT_DIM, false);
        }
        for (int i = 0; i < shown; i++) {
            Row row = rows.get(scroll + i);
            int y = 58 + i * ROW_HEIGHT;
            graphics.fill(8, y - 2, width - 8, y + ROW_HEIGHT - 6, 0x60101010);
            graphics.fill(8, y - 2, 10, y + ROW_HEIGHT - 6, VBVTheme.accentAlpha(0x90));
            graphics.text(font, VBVUi.fit(font, row.title(), width - 150), 16, y + 3, 0xFFFFFFFF, true);
            String versions = "Installed " + row.installed() + "  ->  newest " + row.latest();
            graphics.text(font, VBVUi.fit(font, versions, width - 150), 16, y + 16, VBVUi.TEXT_DIM, false);
            String id = "Project " + row.projectId();
            graphics.text(font, VBVUi.fit(font, id, width - 150), 16, y + 27, VBVUi.TEXT_DISABLED, false);
        }
        if (rows.size() > visibleRows()) {
            String pages = "Scroll for more (" + (scroll + shown) + "/" + rows.size() + ")";
            graphics.text(font, pages, 12, height - 42, VBVUi.TEXT_DISABLED, false);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        VBVNav.open(parent);
    }
}
