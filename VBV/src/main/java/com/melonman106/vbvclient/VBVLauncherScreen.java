package com.melonman106.vbvclient;

import java.util.ArrayList;
import java.util.List;
import net.lax1dude.eaglercraft.v1_8.profile.EaglerProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * VBV Launcher: a Modrinth-app style hub with a tab sidebar.
 *
 * <ul>
 *   <li><b>Discover</b> - search Modrinth for resource packs and install them in one click.</li>
 *   <li><b>Library</b> - resource-pack manager, mods and imports.</li>
 *   <li><b>Hero</b> - your skin, cape and name.</li>
 *   <li><b>Party</b> - party status and manager.</li>
 *   <li><b>Storage</b> - the browser-folder storage settings.</li>
 *   <li><b>Appearance</b> - accent colour and hand position.</li>
 * </ul>
 *
 * Every position is derived from the current GUI size in {@link #computeLayout()} and the
 * screen rebuilds itself whenever that size changes, so it follows fullscreen toggles.
 */
public final class VBVLauncherScreen extends Screen {
    public enum Tab {
        DISCOVER("DISCOVER"), LIBRARY("LIBRARY"), HERO("HERO"),
        PARTY("PARTY"), STORAGE("STORAGE"), APPEARANCE("APPEARANCE");

        final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    private record Action(String label, String hint, Button.OnPress press) {}

    private record Hint(int x, int y, String text) {}

    // Remembered between visits so the launcher reopens where you left it.
    private static Tab lastTab = Tab.DISCOVER;
    private static String lastQuery = "";
    private static int lastSort = 1;
    private static boolean firstSearchDone;
    private static int lastRows = -1;

    private final Screen parent;
    private Tab tab;

    private int sideX;
    private int sideW;
    private int contentX;
    private int contentY;
    private int contentW;
    private int contentBottom;

    // Discover state
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

    private final List<Hint> hints = new ArrayList<>();

    public VBVLauncherScreen(Screen parent) {
        this(parent, lastTab);
    }

    public VBVLauncherScreen(Screen parent, Tab initialTab) {
        super(Component.literal("VBV Launcher"));
        this.parent = parent;
        this.tab = initialTab == null ? Tab.DISCOVER : initialTab;
        lastTab = this.tab;
    }

    // ---------------------------------------------------------------- layout

    private void computeLayout() {
        sideX = 8;
        sideW = Math.max(86, Math.min(128, width / 5));
        contentX = sideX + sideW + 12;
        contentY = 34;
        contentW = Math.max(120, width - contentX - 12);
        contentBottom = height - 12;
    }

    @Override
    protected void init() {
        super.init();
        VBVClient.init();
        VBVModrinth.initialize();
        VBVParty.initialize();
        EaglerProfile.readIfNeeded();
        computeLayout();
        hints.clear();
        installButtons.clear();
        searchBox = null;
        sortButton = null;
        prevButton = null;
        nextButton = null;

        buildSidebar();
        switch (tab) {
            case DISCOVER -> buildDiscover();
            case LIBRARY -> buildLibrary();
            case HERO -> buildHero();
            case PARTY -> buildParty();
            case STORAGE -> buildStorage();
            case APPEARANCE -> buildAppearance();
        }
    }

    private void switchTab(Tab next) {
        if (next == tab) return;
        tab = next;
        lastTab = next;
        notice = "";
        rebuildWidgets();
    }

    private void buildSidebar() {
        Tab[] tabs = Tab.values();
        int tabH = Math.max(14, Math.min(20, (height - 28 - 40) / tabs.length - 3));
        int y = 28;
        for (Tab t : tabs) {
            final Tab target = t;
            addRenderableWidget(new VBVUi.ThemedButton(sideX + 4, y, sideW - 8, tabH, t.label,
                VBVUi.Style.TAB, b -> switchTab(target)).selected(t == tab));
            y += tabH + 3;
        }
        addRenderableWidget(new VBVUi.ThemedButton(sideX + 4, height - 12 - 20, sideW - 8, 20, "< BACK",
            VBVUi.Style.SMALL, b -> VBVNav.open(parent)));
    }

    // ------------------------------------------------------------- discover

    private void buildDiscover() {
        int y0 = contentY;
        int searchW = 58;
        int sortW = 74;
        int boxW = Math.max(60, contentW - searchW - sortW - 8);

        searchBox = new EditBox(font, contentX, y0, boxW, 20, Component.literal("Search resource packs"));
        searchBox.setHint(Component.literal("Search Modrinth resource packs..."));
        searchBox.setMaxLength(64);
        searchBox.setValue(lastQuery);
        searchBox.setResponder(value -> lastQuery = value);
        addRenderableWidget(searchBox);

        sortButton = new VBVUi.ThemedButton(contentX + boxW + 4, y0, sortW, 20,
            VBVModrinth.SORT_NAMES[lastSort], VBVUi.Style.SMALL, b -> {
                lastSort = (lastSort + 1) % VBVModrinth.SORT_IDS.length;
                sortButton.setLabel(VBVModrinth.SORT_NAMES[lastSort]);
                runSearch(0);
            });
        addRenderableWidget(sortButton);
        addRenderableWidget(new VBVUi.ThemedButton(contentX + boxW + sortW + 8, y0, searchW, 20, "SEARCH",
            VBVUi.Style.SMALL, b -> runSearch(0)));

        cardH = height >= 260 ? 34 : 26;
        cardsTop = y0 + 26;
        int footerH = 40;
        rows = Math.max(1, Math.min(VBVModrinth.PAGE_SIZE, (contentBottom - footerH - cardsTop + 3) / (cardH + 3)));

        // Card artwork first, then the install buttons on top of it.
        addRenderableOnly((graphics, mouseX, mouseY, a) -> renderCards(graphics));
        for (int i = 0; i < rows; i++) {
            final int slot = i;
            VBVUi.ThemedButton install = new VBVUi.ThemedButton(contentX + contentW - 62,
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

        int pagerY = contentBottom - 20;
        prevButton = new VBVUi.ThemedButton(contentX, pagerY, 64, 20, "< PREV", VBVUi.Style.SMALL,
            b -> runSearch(Math.max(0, offset - rows)));
        nextButton = new VBVUi.ThemedButton(contentX + 68, pagerY, 64, 20, "NEXT >", VBVUi.Style.SMALL,
            b -> runSearch(offset + rows));
        addRenderableWidget(prevButton);
        addRenderableWidget(nextButton);

        if (!firstSearchDone || rows != lastRows) {
            firstSearchDone = true;
            lastRows = rows;
            runSearch(0);
        }
        hits = VBVModrinth.results();
        refreshDiscoverButtons();
    }

    private void runSearch(int newOffset) {
        offset = Math.max(0, newOffset);
        notice = "";
        VBVModrinth.search(lastQuery, offset, VBVModrinth.SORT_IDS[lastSort], rows);
    }

    private void refreshDiscoverButtons() {
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
            graphics.fill(contentX, y, contentX + contentW, y + cardH, VBVTheme.panel(0xC8));
            graphics.fill(contentX, y, contentX + 2, y + cardH, VBVTheme.accentAlpha(0xA0));

            int tx = contentX + 8;
            int ty = y + 4;
            graphics.fill(tx, ty, tx + tile, ty + tile, VBVTheme.accentAlpha(0x50));
            graphics.outline(tx, ty, tile, tile, VBVTheme.accentAlpha(0xA0));
            String letter = hit.title.isEmpty() ? "?" : hit.title.substring(0, 1).toUpperCase();
            graphics.text(font, letter, tx + (tile - font.width(letter)) / 2,
                ty + (tile - font.lineHeight) / 2 + 1, VBVTheme.accentLight(), true);

            int textX = tx + tile + 7;
            int textW = contentX + contentW - 70 - textX;
            graphics.text(font, VBVUi.fit(font, hit.title, textW), textX, y + 4, 0xFFFFFFFF, true);
            String meta = "by " + hit.author + "  -  " + VBVModrinth.formatCount(hit.downloads) + " downloads";
            graphics.text(font, VBVUi.fit(font, meta, textW), textX, y + 14, VBVUi.TEXT_DIM, false);
            if (cardH >= 34) {
                graphics.text(font, VBVUi.fit(font, hit.description, textW), textX, y + 24, 0xFF8C8578, false);
            }
        }
    }

    private void renderDiscoverFooter(GuiGraphicsExtractor graphics) {
        int footerY = contentBottom - 34;
        if (VBVModrinth.installBusy() || VBVModrinth.installProgress() >= 0) {
            String status = VBVModrinth.installStatus();
            int barW = Math.min(150, Math.max(60, contentW / 3));
            graphics.text(font, VBVUi.fit(font, status, contentW - barW - 10), contentX, footerY, VBVUi.TEXT, true);
            VBVUi.progressBar(graphics, contentX + contentW - barW, footerY, barW, 8, VBVModrinth.installProgress());
        } else {
            String status = !notice.isEmpty() ? notice
                : (!VBVModrinth.installStatus().isEmpty() ? VBVModrinth.installStatus() : VBVModrinth.searchStatus());
            if (!status.isEmpty()) {
                graphics.text(font, VBVUi.fit(font, status, contentW), contentX, footerY, VBVUi.TEXT, true);
            }
        }
        int total = VBVModrinth.totalHits();
        int pages = Math.max(1, (total + rows - 1) / rows);
        String pageText = "Page " + (offset / rows + 1) + " of " + pages + "  -  " + total + " packs";
        graphics.text(font, VBVUi.fit(font, pageText, contentW - 140), contentX + 140, contentBottom - 20 + 6,
            VBVUi.TEXT_DIM, false);
        if (hits.length == 0 && !VBVModrinth.searchBusy() && VBVModrinth.searchStatus().isEmpty()) {
            graphics.text(font, "Nothing to show yet.", contentX, cardsTop + 6, VBVUi.TEXT_DIM, false);
        }
    }

    // -------------------------------------------------------------- library

    private void buildLibrary() {
        List<Action> actions = new ArrayList<>();
        actions.add(new Action("RESOURCE PACKS", "Turn packs on or off and import ZIP files.", b -> openPackManager()));
        actions.add(new Action("IMPORT PACK FROM COMPUTER", "Copy a resource pack ZIP into your storage folder.",
            b -> VBVStorage.importResourcePack()));
        actions.add(new Action("MODS", "Browse and configure the VBV mods.", b -> VBVNav.open(new VBVModsScreen(this))));
        actions.add(new Action("HAND POSITION", "Move held items up, down, closer or further away.",
            b -> VBVNav.open(new VBVFirstPersonAnimationsConfigScreen(this))));
        addActions(contentY + 4, actions);
    }

    /** Opens Eagler's own pack manager; queued Modrinth downloads import as soon as it opens. */
    private void openPackManager() {
        Minecraft mc = Minecraft.getInstance();
        final Screen back = this;
        VBVNav.open(new PackSelectionScreen(mc.getResourcePackRepository(),
            repository -> {
                mc.options.updateResourcePacks(repository);
                VBVNav.open(back);
            },
            mc.getResourcePackDirectory(), Component.translatable("resourcePack.title")));
    }

    // ----------------------------------------------------------------- hero

    private void buildHero() {
        int cardBottom = contentY + (height >= 240 ? 70 : 50);
        List<Action> actions = new ArrayList<>();
        actions.add(new Action("EDIT HERO", "Change your skin, cape and player name.",
            b -> VBVNav.open(new net.lax1dude.eaglercraft.v1_8.profile.EaglerProfileScreen26(this))));
        addActions(cardBottom + 10, actions);
    }

    private void renderHero(GuiGraphicsExtractor graphics) {
        String name = EaglerProfile.getName();
        if (name == null || name.isEmpty()) name = "Player";
        Identifier skin = EaglerProfile.getActiveSkinResourceLocation();
        int cardH2 = height >= 240 ? 70 : 50;
        VBVUi.panel(graphics, contentX, contentY, Math.min(contentW, 300), cardH2);
        int head = cardH2 - 16;
        VBVUi.head(graphics, skin, contentX + 8, contentY + 8, head);
        int tx = contentX + 8 + head + 10;
        graphics.pose().pushMatrix();
        graphics.pose().translate(tx, contentY + 10);
        graphics.pose().scale(1.5F, 1.5F);
        graphics.text(font, VBVUi.fit(font, name, (Math.min(contentW, 300) - head - 40) * 2 / 3), 0, 0, 0xFFFFFFFF, true);
        graphics.pose().popMatrix();
        graphics.text(font, "Your hero in VBV", tx, contentY + 28, VBVTheme.accentLight(), false);
    }

    // ---------------------------------------------------------------- party

    private void buildParty() {
        List<Action> actions = new ArrayList<>();
        actions.add(new Action("PARTY MANAGER", "Create, join or leave a party with an invite code.",
            b -> VBVNav.open(new VBVPartyScreen(this))));
        addActions(contentBottom - 36, actions);
    }

    private void renderParty(GuiGraphicsExtractor graphics) {
        String[] players = VBVParty.players();
        int panelW = Math.min(contentW, 300);
        int shown = Math.max(1, Math.min(players.length, Math.max(1, (contentBottom - 60 - contentY - 26) / 14)));
        int panelH = 24 + shown * 14 + 4;
        VBVUi.panel(graphics, contentX, contentY, panelW, panelH);
        graphics.text(font, "PARTY MEMBERS", contentX + 8, contentY + 8, VBVTheme.accentLight(), true);
        if (players.length == 0) {
            graphics.text(font, "You are not in a party.", contentX + 8, contentY + 24, VBVUi.TEXT_DIM, false);
            return;
        }
        for (int i = 0; i < shown; i++) {
            graphics.text(font, VBVUi.fit(font, players[i], panelW - 20), contentX + 8, contentY + 24 + i * 14,
                VBVUi.TEXT, false);
        }
        if (players.length > shown) {
            graphics.text(font, "+" + (players.length - shown) + " more", contentX + 8, contentY + 24 + shown * 14,
                VBVUi.TEXT_DIM, false);
        }
    }

    // -------------------------------------------------------------- storage

    private void buildStorage() {
        List<Action> actions = new ArrayList<>();
        actions.add(new Action("CHOOSE STORAGE FOLDER", "Pick the folder on your computer VBV should use.",
            b -> VBVStorage.chooseFolder()));
        actions.add(new Action("RECONNECT FOLDER", "Re-open the folder you chose last time.", b -> VBVStorage.reconnect()));
        actions.add(new Action("IMPORT WORLD", "Copy a world into your storage folder.", b -> VBVStorage.importWorld()));
        actions.add(new Action("IMPORT RESOURCE PACK", "Copy a resource pack ZIP into storage.",
            b -> VBVStorage.importResourcePack()));
        actions.add(new Action("IMPORT DATAPACK", "Copy a datapack into storage.", b -> VBVStorage.importDatapack()));
        actions.add(new Action("SERVER PROFILES", "Per-server resource pack and storage profiles.",
            b -> VBVNav.open(new VBVServerProfilesScreen(this))));
        actions.add(new Action("ALL STORAGE TOOLS", "Worlds, packs, backups and more.",
            b -> VBVNav.open(new VBVStorageScreen(this))));
        addActions(contentY + 22, actions);
    }

    private void renderStorage(GuiGraphicsExtractor graphics) {
        boolean connected = VBVStorage.connected();
        String status = connected ? "Connected: " + VBVStorage.folderName() : "No computer folder connected";
        graphics.text(font, VBVUi.fit(font, status, contentW), contentX, contentY + 4,
            connected ? 0xFF6FE08A : VBVTheme.accentLight(), true);
    }

    // ----------------------------------------------------------- appearance

    private void buildAppearance() {
        int size = 22;
        int gap = 6;
        int perRow = Math.max(1, (contentW + gap) / (size + gap));
        int count = VBVTheme.presetCount();
        int y = contentY + 16;
        for (int i = 0; i < count; i++) {
            final int index = i;
            int col = i % perRow;
            int row = i / perRow;
            VBVUi.ThemedButton swatch = new VBVUi.ThemedButton(contentX + col * (size + gap), y + row * (size + gap),
                size, size, VBVTheme.presetName(i), VBVUi.Style.SWATCH, b -> {
                    VBVTheme.setPreset(index);
                    VBVConfigStore.saveAll();
                    rebuildWidgets();
                }).swatch(0xFF000000 | VBVTheme.rgbOf(i)).selected(VBVTheme.getPreset() == i);
            addRenderableWidget(swatch);
        }
        int swatchRows = (count + perRow - 1) / perRow;
        int customY = y + swatchRows * (size + gap) + 18;

        String[] channel = {"R", "G", "B"};
        int rowH = Math.max(20, Math.min(24, (contentBottom - customY - 50) / 3));
        for (int c = 0; c < 3; c++) {
            final int ch = c;
            int ry = customY + c * rowH;
            int shift = 16 - 8 * c;
            int value = (VBVTheme.getCustomRgb() >> shift) & 0xFF;
            addRenderableWidget(new VBVUi.ThemedButton(contentX, ry, 26, 18, "-16", VBVUi.Style.SMALL, b -> {
                VBVTheme.nudgeCustom(ch, -16);
                VBVConfigStore.saveAll();
                rebuildWidgets();
            }));
            VBVUi.ThemedButton label = new VBVUi.ThemedButton(contentX + 30, ry, 70, 18,
                channel[c] + ": " + value, VBVUi.Style.SMALL, b -> { });
            label.active = false;
            addRenderableWidget(label);
            addRenderableWidget(new VBVUi.ThemedButton(contentX + 104, ry, 26, 18, "+16", VBVUi.Style.SMALL, b -> {
                VBVTheme.nudgeCustom(ch, 16);
                VBVConfigStore.saveAll();
                rebuildWidgets();
            }));
        }

        int previewX = contentX + 144;
        int previewW = Math.min(contentW - 144, 190);
        if (previewW >= 90) {
            addRenderableWidget(new VBVUi.ThemedButton(previewX, customY, previewW, 26, "PLAY", VBVUi.Style.PRIMARY, b -> { }));
            addRenderableWidget(new VBVUi.ThemedButton(previewX, customY + 30, previewW, 20, "OPTIONS", VBVUi.Style.MENU, b -> { }));
        }
        int handY = customY + 3 * rowH + 6;
        if (handY + 22 <= contentBottom) {
            addRenderableWidget(new VBVUi.ThemedButton(contentX, handY, Math.min(contentW, 260), 22, "HAND POSITION",
                VBVUi.Style.MENU, b -> VBVNav.open(new VBVFirstPersonAnimationsConfigScreen(this))));
        }
    }

    private void renderAppearance(GuiGraphicsExtractor graphics) {
        graphics.text(font, "ACCENT COLOR  -  " + VBVTheme.presetName(), contentX, contentY + 2,
            VBVTheme.accentLight(), true);
        int size = 22;
        int gap = 6;
        int perRow = Math.max(1, (contentW + gap) / (size + gap));
        int swatchRows = (VBVTheme.presetCount() + perRow - 1) / perRow;
        int customY = contentY + 16 + swatchRows * (size + gap) + 18;
        graphics.text(font, "CUSTOM COLOR", contentX, customY - 12, VBVUi.TEXT_DIM, false);
    }

    // ------------------------------------------------------------- generic

    /** Stacks action buttons (with optional hint lines) and flows into two columns if needed. */
    private void addActions(int startY, List<Action> actions) {
        int n = actions.size();
        if (n == 0) return;
        int availH = Math.max(24, contentBottom - startY);
        int btnH = 22;
        int hintH = 11;
        int rowWithHint = btnH + hintH + 5;
        boolean twoCols = contentW >= 330 && n * rowWithHint > availH && n > 1;
        int perCol = twoCols ? (n + 1) / 2 : n;
        boolean showHints = perCol * rowWithHint <= availH;
        int rowH = showHints ? rowWithHint : Math.max(22, Math.min(26, availH / perCol));
        if (rowH < 24) btnH = 20;
        int colW = twoCols ? (contentW - 8) / 2 : Math.min(contentW, 300);

        for (int i = 0; i < n; i++) {
            Action action = actions.get(i);
            int col = twoCols ? i / perCol : 0;
            int row = twoCols ? i % perCol : i;
            int x = contentX + col * (colW + 8);
            int y = startY + row * rowH;
            addRenderableWidget(new VBVUi.ThemedButton(x, y, colW, btnH, action.label(), VBVUi.Style.MENU, action.press()));
            if (showHints && !action.hint().isEmpty()) {
                hints.add(new Hint(x + 2, y + btnH + 2, VBVUi.fit(font, action.hint(), colW - 4)));
            }
        }
    }

    // ---------------------------------------------------------------- frame

    @Override
    public void tick() {
        super.tick();
        if (tab == Tab.DISCOVER) {
            hits = VBVModrinth.results();
            refreshDiscoverButtons();
        }
        if (VBVModrinth.packReady()) {
            String name = VBVModrinth.packName();
            byte[] data = VBVModrinth.takePack();
            if (data != null) {
                VBVPackInstaller.queue(name, data);
                openPackManager();
            }
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        boolean enter = event.key() == 257 || event.key() == 335;
        if (enter && tab == Tab.DISCOVER && searchBox != null && searchBox.isFocused()) {
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
        VBVUi.panel(graphics, sideX, 6, sideW, height - 12);
        VBVUi.panel(graphics, contentX - 8, 6, width - contentX - 4, height - 12);

        graphics.text(font, "VBV", sideX + 9, 12, VBVTheme.accentLight(), true);
        graphics.text(font, "LAUNCHER", sideX + 9 + font.width("VBV "), 12, VBVUi.TEXT, true);

        graphics.pose().pushMatrix();
        graphics.pose().translate(contentX, 12);
        graphics.pose().scale(1.5F, 1.5F);
        graphics.text(font, tab.label, 0, 0, 0xFFFFFFFF, true);
        graphics.pose().popMatrix();
        graphics.fill(contentX, 28, contentX + 30, 29, VBVTheme.accent());

        switch (tab) {
            case HERO -> renderHero(graphics);
            case PARTY -> renderParty(graphics);
            case STORAGE -> renderStorage(graphics);
            case APPEARANCE -> renderAppearance(graphics);
            default -> { }
        }
        for (Hint hint : hints) {
            graphics.text(font, hint.text(), hint.x(), hint.y(), VBVUi.TEXT_DIM, false);
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        if (tab == Tab.DISCOVER) {
            renderDiscoverFooter(graphics);
        }
    }

    @Override
    public void onClose() {
        VBVNav.open(parent);
    }
}
