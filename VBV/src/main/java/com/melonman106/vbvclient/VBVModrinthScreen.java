package com.melonman106.vbvclient;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * Modrinth resource-pack browser. Opened from the "Modrinth..." button in Options > Resource Packs:
 * search, sort, and install a pack in one click. A finished download is handed to
 * {@link VBVPackInstaller} and the player is returned to the Resource Packs screen, which imports it.
 */
public final class VBVModrinthScreen extends Screen {
    // Remembered between visits so the browser reopens on the same search.
    private static String lastQuery = "";
    private static int lastSort = 1;
    private static boolean firstSearchDone;
    private static int lastRows = -1;

    private final Screen parent;

    private int left;
    private int contentW;
    private int top;
    private int bottom;

    private EditBox searchBox;
    private VBVUi.ThemedButton sortButton;
    private VBVUi.ThemedButton prevButton;
    private VBVUi.ThemedButton nextButton;
    private final List<VBVUi.ThemedButton> installButtons = new ArrayList<>();
    private VBVModrinth.Hit[] hits = new VBVModrinth.Hit[0];
    private int rows = 4;
    private int cardH = 30;
    private int cardsTop;
    private int offset;
    private String notice = "";

    public VBVModrinthScreen(Screen parent) {
        super(Component.literal("Modrinth Resource Packs"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        VBVClient.init();
        VBVModrinth.initialize();
        installButtons.clear();

        contentW = Math.max(160, Math.min(width - 24, 460));
        left = (width - contentW) / 2;
        top = 34;
        bottom = height - 12;

        int searchW = 58;
        int sortW = 74;
        int boxW = Math.max(60, contentW - searchW - sortW - 8);

        searchBox = new EditBox(font, left, top, boxW, 20, Component.literal("Search resource packs"));
        searchBox.setHint(Component.literal("Search Modrinth resource packs..."));
        searchBox.setMaxLength(64);
        searchBox.setValue(lastQuery);
        searchBox.setResponder(value -> lastQuery = value);
        addRenderableWidget(searchBox);

        sortButton = new VBVUi.ThemedButton(left + boxW + 4, top, sortW, 20,
            VBVModrinth.SORT_NAMES[lastSort], VBVUi.Style.SMALL, b -> {
                lastSort = (lastSort + 1) % VBVModrinth.SORT_IDS.length;
                sortButton.setLabel(VBVModrinth.SORT_NAMES[lastSort]);
                runSearch(0);
            });
        addRenderableWidget(sortButton);
        addRenderableWidget(new VBVUi.ThemedButton(left + boxW + sortW + 8, top, searchW, 20, "SEARCH",
            VBVUi.Style.SMALL, b -> runSearch(0)));

        cardH = height >= 260 ? 34 : 26;
        cardsTop = top + 26;
        int footerH = 40;
        rows = Math.max(1, Math.min(VBVModrinth.PAGE_SIZE, (bottom - footerH - cardsTop + 3) / (cardH + 3)));

        // Card artwork first, then the install buttons on top of it.
        addRenderableOnly((graphics, mouseX, mouseY, a) -> renderCards(graphics));
        for (int i = 0; i < rows; i++) {
            final int slot = i;
            VBVUi.ThemedButton install = new VBVUi.ThemedButton(left + contentW - 62,
                cardsTop + i * (cardH + 3) + (cardH - 18) / 2, 56, 18, "INSTALL", VBVUi.Style.SMALL, b -> {
                    if (slot < hits.length && !VBVModrinth.installBusy()) {
                        notice = "";
                        VBVModrinth.install(hits[slot].id, hits[slot].title);
                    }
                });
            install.visible = false;
            installButtons.add(install);
            addRenderableWidget(install);
        }

        int pagerY = bottom - 20;
        prevButton = new VBVUi.ThemedButton(left, pagerY, 64, 20, "< PREV", VBVUi.Style.SMALL,
            b -> runSearch(Math.max(0, offset - rows)));
        nextButton = new VBVUi.ThemedButton(left + 68, pagerY, 64, 20, "NEXT >", VBVUi.Style.SMALL,
            b -> runSearch(offset + rows));
        addRenderableWidget(prevButton);
        addRenderableWidget(nextButton);
        addRenderableWidget(new VBVUi.ThemedButton(left + contentW - 80, pagerY, 80, 20, "< BACK", VBVUi.Style.SMALL,
            b -> VBVNav.open(parent)));
        addRenderableWidget(new VBVUi.ThemedButton(left + contentW - 62, 10, 56, 18, "UPDATES", VBVUi.Style.SMALL,
            b -> VBVNav.open(new VBVModrinthUpdatesScreen(this))));

        if (!firstSearchDone || rows != lastRows) {
            firstSearchDone = true;
            lastRows = rows;
            runSearch(0);
        }
        hits = VBVModrinth.results();
        refreshButtons();
    }

    private void runSearch(int newOffset) {
        offset = Math.max(0, newOffset);
        notice = "";
        VBVModrinth.search(lastQuery, offset, VBVModrinth.SORT_IDS[lastSort], rows);
    }

    private void refreshButtons() {
        boolean busy = VBVModrinth.installBusy();
        for (int i = 0; i < installButtons.size(); i++) {
            VBVUi.ThemedButton button = installButtons.get(i);
            button.visible = i < hits.length;
            button.active = !busy;
        }
        if (prevButton != null) prevButton.active = offset > 0 && !VBVModrinth.searchBusy();
        if (nextButton != null) {
            nextButton.active = offset + rows < VBVModrinth.totalHits() && !VBVModrinth.searchBusy();
        }
    }

    private void renderCards(GuiGraphicsExtractor graphics) {
        int tile = cardH - 8;
        for (int i = 0; i < hits.length && i < rows; i++) {
            VBVModrinth.Hit hit = hits[i];
            int y = cardsTop + i * (cardH + 3);
            VBVUi.panelBackground(graphics, left, y, contentW, cardH);
            graphics.fill(left, y, left + 2, y + cardH, VBVTheme.accentAlpha(0xA0));

            int tx = left + 8;
            int ty = y + 4;
            graphics.fill(tx, ty, tx + tile, ty + tile, VBVTheme.accentAlpha(0x50));
            graphics.outline(tx, ty, tile, tile, VBVTheme.accentAlpha(0xA0));
            String letter = hit.title.isEmpty() ? "?" : hit.title.substring(0, 1).toUpperCase();
            graphics.text(font, letter, tx + (tile - font.width(letter)) / 2,
                ty + (tile - font.lineHeight) / 2 + 1, 0xFFFFFFFF, true);

            int textX = tx + tile + 7;
            int textW = left + contentW - 70 - textX;
            graphics.text(font, VBVUi.fit(font, hit.title, textW), textX, y + 4, 0xFFFFFFFF, true);
            String meta = "by " + hit.author + "  -  " + VBVModrinth.formatCount(hit.downloads) + " downloads";
            graphics.text(font, VBVUi.fit(font, meta, textW), textX, y + 14, VBVUi.TEXT_DIM, false);
            if (cardH >= 34) {
                graphics.text(font, VBVUi.fit(font, hit.description, textW), textX, y + 24, 0xFF8C8578, false);
            }
        }
    }

    private void renderFooter(GuiGraphicsExtractor graphics) {
        int footerY = bottom - 34;
        if (VBVModrinth.installBusy() || VBVModrinth.installProgress() >= 0) {
            String status = VBVModrinth.installStatus();
            int barW = Math.min(150, Math.max(60, contentW / 3));
            graphics.text(font, VBVUi.fit(font, status, contentW - barW - 10), left, footerY, VBVUi.TEXT, true);
            VBVUi.progressBar(graphics, left + contentW - barW, footerY, barW, 8, VBVModrinth.installProgress());
        } else {
            String status = !notice.isEmpty() ? notice
                : (!VBVModrinth.installStatus().isEmpty() ? VBVModrinth.installStatus() : VBVModrinth.searchStatus());
            if (!status.isEmpty()) {
                graphics.text(font, VBVUi.fit(font, status, contentW), left, footerY, VBVUi.TEXT, true);
            }
        }
        int total = VBVModrinth.totalHits();
        int pages = Math.max(1, (total + rows - 1) / rows);
        String pageText = "Page " + (offset / rows + 1) + " of " + pages + "  -  " + total + " packs";
        graphics.text(font, VBVUi.fit(font, pageText, contentW - 230), left + 140, bottom - 20 + 6,
            VBVUi.TEXT_DIM, false);
        if (hits.length == 0 && !VBVModrinth.searchBusy() && VBVModrinth.searchStatus().isEmpty()) {
            graphics.text(font, "Nothing to show yet.", left, cardsTop + 6, VBVUi.TEXT_DIM, false);
        }
    }

    @Override
    public void tick() {
        super.tick();
        hits = VBVModrinth.results();
        refreshButtons();
        if (VBVModrinth.packReady()) {
            // Required dependency packs are queued behind the chosen one; move all of them into the
            // importer before the Resource Packs screen is shown again, where they are unpacked.
            VBVModrinth.drainPacks();
            VBVModrinth.finishInstallIfNeeded();
            VBVNav.open(parent);
            return;
        }
        VBVModrinth.finishInstallIfNeeded();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        boolean enter = event.key() == 257 || event.key() == 335;
        if (enter && searchBox != null && searchBox.isFocused()) {
            runSearch(0);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (graphics.guiWidth() != width || graphics.guiHeight() != height) {
            // Browser fullscreen can change the canvas without a window event; re-layout now.
            resize(graphics.guiWidth(), graphics.guiHeight());
        }
        VBVUi.panel(graphics, left - 10, 6, contentW + 20, height - 12);
        graphics.text(font, "VBV", left, 12, 0xFFFFFFFF, true);
        graphics.text(font, "MODRINTH", left + font.width("VBV "), 12, VBVUi.TEXT, true);
        graphics.text(font, "Resource packs", left, 22, VBVUi.TEXT_DIM, false);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        renderFooter(graphics);
    }

    @Override
    public void onClose() {
        VBVNav.open(parent);
    }
}
