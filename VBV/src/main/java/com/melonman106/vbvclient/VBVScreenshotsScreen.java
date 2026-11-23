package com.melonman106.vbvclient;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Screenshot Manager: lists the PNGs the storage bridge wrote after each F2 press.
 *
 * <p>Every row is one index entry from {@code vbv.screenshots.v1}: open it in a new tab, download a
 * copy, or delete it from the managed folder. The header reports whether a storage folder is
 * connected; without one screenshots still download normally and are listed as
 * {@code Downloads/...}.</p>
 */
public final class VBVScreenshotsScreen extends Screen {
    private static final int ROW_HEIGHT = 30;
    private static final int MIN_ROWS = 2;
    private static final int MAX_ROWS = 8;

    private final Screen parent;
    private final List<Row> rows = new ArrayList<>();
    private int scroll;
    private String pendingDelete = "";
    private long pendingSince;

    private record Row(String name, String path, long bytes, int width, int height, long at) {}

    public VBVScreenshotsScreen(Screen parent) {
        super(Component.literal("Screenshots"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        reload();
        rebuild();
    }

    private void reload() {
        rows.clear();
        String[] lines = VBVScreenshots.entries();
        for (String line : lines) {
            if (line == null || line.trim().isEmpty()) continue;
            String[] f = line.split("\t", -1);
            if (f.length < 6) continue;
            long bytes;
            int width;
            int height;
            long at;
            try {
                bytes = Long.parseLong(f[2]);
            } catch (NumberFormatException e) {
                bytes = 0L;
            }
            try {
                width = Integer.parseInt(f[3]);
            } catch (NumberFormatException e) {
                width = 0;
            }
            try {
                height = Integer.parseInt(f[4]);
            } catch (NumberFormatException e) {
                height = 0;
            }
            try {
                at = Long.parseLong(f[5]);
            } catch (NumberFormatException e) {
                at = 0L;
            }
            rows.add(new Row(f[0], f[1], bytes, width, height, at));
        }
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rows.size() - visibleRows())));
    }

    /** Rows that fit between the header and the footer buttons at the current GUI size. */
    private int visibleRows() {
        return Math.max(MIN_ROWS, Math.min(MAX_ROWS, (height - 100) / ROW_HEIGHT));
    }

    private void rebuild() {
        clearWidgets();
        addRenderableWidget(Button.builder(Component.literal("BACK"), button -> VBVNav.open(parent))
            .bounds(width / 2 - 150, height - 28, 96, 20).build());
        addRenderableWidget(Button.builder(Component.literal("REFRESH"), button -> {
            reload();
            rebuild();
        }).bounds(width / 2 - 50, height - 28, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal(VBVScreenshots.connected() ? "STORAGE FOLDER" : "CHOOSE FOLDER"),
            button -> VBVScreenshots.chooseFolder()).bounds(width / 2 + 54, height - 28, 104, 20).build());

        int shown = Math.min(visibleRows(), Math.max(0, rows.size() - scroll));
        int top = 52;
        for (int i = 0; i < shown; i++) {
            int index = scroll + i;
            Row row = rows.get(index);
            int y = top + i * ROW_HEIGHT;
            // A download-only row (no storage folder was connected when it was taken) has no file in
            // the managed folder to open or copy again; it can still be removed from the list.
            boolean inFolder = row.path().startsWith("Screenshots/");
            Button open = Button.builder(Component.literal("OPEN"), button -> VBVScreenshots.open(row.path()))
                .bounds(width - 232, y + 4, 54, 20).build();
            open.active = inFolder;
            addRenderableWidget(open);
            Button save = Button.builder(Component.literal("SAVE"), button -> VBVScreenshots.download(row.path()))
                .bounds(width - 174, y + 4, 54, 20).build();
            save.active = inFolder;
            addRenderableWidget(save);
            boolean confirm = pendingDelete.equals(row.path());
            addRenderableWidget(Button.builder(Component.literal(confirm ? "SURE?" : "DELETE"), button -> {
                if (pendingDelete.equals(row.path()) && System.currentTimeMillis() - pendingSince < 4000L) {
                    VBVScreenshots.delete(row.path());
                    pendingDelete = "";
                    reload();
                    rebuild();
                } else {
                    pendingDelete = row.path();
                    pendingSince = System.currentTimeMillis();
                    rebuild();
                }
            }).bounds(width - 116, y + 4, 62, 20).build());
        }
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
    public boolean mouseClicked(MouseButtonEvent click, boolean doubleClick) {
        if (pendingDelete != null && !pendingDelete.isEmpty()
            && System.currentTimeMillis() - pendingSince >= 4000L) {
            pendingDelete = "";
            rebuild();
        }
        return super.mouseClicked(click, doubleClick);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (graphics.guiWidth() != width || graphics.guiHeight() != height) {
            resize(graphics.guiWidth(), graphics.guiHeight());
        }
        graphics.fill(0, 0, width, height, 0xFF101010);
        String heading = "SCREENSHOTS";
        graphics.text(font, heading, width / 2 - font.width(heading) / 2, 14, 0xFFFFFFFF, true);
        String storage = VBVScreenshots.connected()
            ? "Saving to: " + VBVStorage.folderName() + "/Screenshots"
            : "No storage folder connected - new screenshots download to your computer";
        String count = rows.isEmpty() ? "No screenshots yet - press F2 in game"
            : rows.size() + (rows.size() == 1 ? " screenshot" : " screenshots");
        int countWidth = font.width(count);
        graphics.text(font, VBVUi.fit(font, storage, width - 24 - countWidth - 8), 12, 30,
            VBVScreenshots.connected() ? 0xFF7FE07F : 0xFFFFC46B, false);
        graphics.text(font, count, width - 12 - countWidth, 30, VBVUi.TEXT_DIM, false);

        int shown = Math.min(visibleRows(), Math.max(0, rows.size() - scroll));
        if (rows.isEmpty()) {
            String hint = "The screenshot key saves a PNG into the storage folder's Screenshots directory.";
            graphics.text(font, hint, width / 2 - font.width(hint) / 2, height / 2 - 10, VBVUi.TEXT_DIM, false);
        } else {
            for (int i = 0; i < shown; i++) {
                Row row = rows.get(scroll + i);
                int y = 52 + i * ROW_HEIGHT;
                graphics.fill(8, y - 2, width - 8, y + ROW_HEIGHT - 4, 0x60101010);
                graphics.fill(8, y - 2, 10, y + ROW_HEIGHT - 4, VBVTheme.accentAlpha(0x90));
                graphics.text(font, VBVUi.fit(font, row.name(), width - 290), 16, y + 3, 0xFFFFFFFF, true);
                String meta = (row.width() > 0 ? row.width() + "x" + row.height() + "  -  " : "")
                    + size(row.bytes()) + "  -  " + date(row.at());
                graphics.text(font, VBVUi.fit(font, meta, width - 290), 16, y + 15, VBVUi.TEXT_DIM, false);
            }
            if (rows.size() > visibleRows()) {
                String pages = "Scroll for more (" + (scroll + shown) + "/" + rows.size() + ")";
                graphics.text(font, pages, 12, height - 42, VBVUi.TEXT_DISABLED, false);
            }
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private static String size(long bytes) {
        if (bytes <= 0L) return "PNG";
        if (bytes >= 1048576L) return String.format(Locale.ROOT, "%.1f MB", bytes / 1048576.0D);
        return Math.max(1L, bytes / 1024L) + " KB";
    }

    private static String date(long at) {
        if (at <= 0L) return "unknown date";
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(at);
        return String.format(Locale.ROOT, "%04d-%02d-%02d %02d:%02d",
            calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.DAY_OF_MONTH),
            calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE));
    }

    @Override
    public void onClose() {
        VBVNav.open(parent);
    }
}
