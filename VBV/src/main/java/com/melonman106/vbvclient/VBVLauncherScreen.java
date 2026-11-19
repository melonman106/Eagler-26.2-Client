package com.melonman106.vbvclient;

import java.util.ArrayList;
import java.util.List;
import net.lax1dude.eaglercraft.v1_8.profile.EaglerProfile;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * VBV Extras: the hub behind the EXTRAS button on the main menu, a tab sidebar with
 *
 * <ul>
 *   <li><b>Panoramas</b> - pick the animated backdrop for the menus (or the vanilla one).</li>
 *   <li><b>Hero</b> - your skin, cape and name.</li>
 *   <li><b>Friends</b> - your friends list (Cloudflare Worker + D1) and who is online.</li>
 *   <li><b>Party</b> - party status and manager.</li>
 *   <li><b>Storage</b> - the browser-folder storage settings.</li>
 *   <li><b>Appearance</b> - accent colour and hand position.</li>
 * </ul>
 *
 * Resource packs and the Modrinth browser moved to Options &gt; Resource Packs.
 *
 * Every position is derived from the current GUI size in {@link #computeLayout()} and the
 * screen rebuilds itself whenever that size changes, so it follows fullscreen toggles.
 */
public final class VBVLauncherScreen extends Screen {
    public enum Tab {
        PANORAMAS("PANORAMAS"), HERO("HERO"), FRIENDS("FRIENDS"), PARTY("PARTY"), STORAGE("STORAGE"), APPEARANCE("APPEARANCE");

        final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    private record Action(String label, String hint, Button.OnPress press) {}

    private record Hint(int x, int y, String text) {}

    // Remembered between visits so Extras reopens where you left it.
    private static Tab lastTab = Tab.PANORAMAS;

    private final Screen parent;
    private Tab tab;

    private int sideX;
    private int sideW;
    private int contentX;
    private int contentY;
    private int contentW;
    private int contentBottom;

    // Panoramas state
    private final List<PanoramaCard> cards = new ArrayList<>();
    private int cardW;
    private int thumbH;
    private int cardH;
    private int cardGap = 8;
    private int perRow = 1;
    private int cardsTop;
    private int visibleRows = 1;
    private int scrollRow;

    private final List<Hint> hints = new ArrayList<>();

    public VBVLauncherScreen(Screen parent) {
        this(parent, lastTab);
    }

    public VBVLauncherScreen(Screen parent, Tab initialTab) {
        super(Component.literal("VBV Extras"));
        this.parent = parent;
        this.tab = initialTab == null ? Tab.PANORAMAS : initialTab;
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
        VBVParty.initialize();
        VBVFriends.initialize();
        EaglerProfile.readIfNeeded();
        computeLayout();
        hints.clear();
        cards.clear();

        buildSidebar();
        switch (tab) {
            case PANORAMAS -> buildPanoramas();
            case HERO -> buildHero();
            case FRIENDS -> buildFriends();
            case PARTY -> buildParty();
            case STORAGE -> buildStorage();
            case APPEARANCE -> buildAppearance();
        }
    }

    private void switchTab(Tab next) {
        if (next == tab) return;
        if (tab == Tab.PANORAMAS) VBVPanoramaOverlay.releasePreviews();
        tab = next;
        lastTab = next;
        scrollRow = 0;
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

    // ------------------------------------------------------------ panoramas

    /** Card index 0 is "vanilla" (no overlay); cards 1..n are the panoramas from the panorama folder. */
    private void buildPanoramas() {
        int total = VBVPanoramaOverlay.count() + 1;
        cardGap = 8;
        int columns = contentW >= 360 ? 3 : (contentW >= 240 ? 2 : 1);
        cardW = Math.max(80, Math.min(170, (contentW - cardGap * (columns - 1)) / columns));
        perRow = Math.max(1, (contentW + cardGap) / (cardW + cardGap));
        thumbH = cardW * 9 / 16;
        cardH = thumbH + 16;
        cardsTop = contentY + 18;
        visibleRows = Math.max(1, (contentBottom - cardsTop + cardGap) / (cardH + cardGap));
        int totalRows = (total + perRow - 1) / perRow;
        scrollRow = Math.max(0, Math.min(scrollRow, Math.max(0, totalRows - visibleRows)));

        for (int i = 0; i < total; i++) {
            final int panorama = i - 1; // -1 = vanilla
            PanoramaCard card = new PanoramaCard(panorama);
            cards.add(card);
            addRenderableWidget(card);
        }
        // Freezes the backdrop on one frame: no per-frame decode, upload or blend in the menu.
        if (contentW >= 200) {
            addRenderableWidget(new VBVUi.ThemedButton(contentX + contentW - 86, contentY + 1, 86, 13,
                motionLabel(), VBVUi.Style.SMALL, button -> {
                    VBVPanoramaOverlay.toggleMotion();
                    VBVConfigStore.saveAll();
                    button.setMessage(Component.literal(motionLabel()));
                }));
        }
        layoutCards();
    }

    private static String motionLabel() {
        return VBVPanoramaOverlay.isMotion() ? "MOTION  ON" : "MOTION  OFF";
    }

    private void layoutCards() {
        for (int i = 0; i < cards.size(); i++) {
            PanoramaCard card = cards.get(i);
            int row = i / perRow - scrollRow;
            int col = i % perRow;
            card.setX(contentX + col * (cardW + cardGap));
            card.setY(cardsTop + row * (cardH + cardGap));
            card.setWidth(cardW);
            card.setHeight(cardH);
            card.visible = row >= 0 && row < visibleRows;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (tab == Tab.PANORAMAS && !cards.isEmpty()) {
            int totalRows = (cards.size() + perRow - 1) / perRow;
            int maxScroll = Math.max(0, totalRows - visibleRows);
            if (maxScroll > 0 && scrollY != 0.0) {
                scrollRow = Math.max(0, Math.min(maxScroll, scrollRow + (scrollY < 0.0 ? 1 : -1)));
                layoutCards();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void renderPanoramasHeader(GuiGraphicsExtractor graphics) {
        int index = VBVPanoramaOverlay.selectedIndex();
        String active = index < 0 ? "Vanilla panorama" : VBVPanoramaOverlay.name(index);
        int headerW = contentW - (contentW >= 200 ? 90 : 0);
        graphics.text(font, VBVUi.fit(font, "MENU BACKDROP  -  " + active.toUpperCase(), headerW), contentX, contentY + 2,
            VBVTheme.accentLight(), true);
        int totalRows = (cards.size() + perRow - 1) / perRow;
        if (totalRows > visibleRows) {
            graphics.text(font, "Scroll for more", contentX, contentBottom - 8, VBVUi.TEXT_DISABLED, false);
        } else if (VBVPanoramaOverlay.count() == 0) {
            graphics.text(font, "No panoramas yet. Add videos to the panorama folder.", contentX, cardsTop + cardH + 6,
                VBVUi.TEXT_DIM, false);
        }
    }

    /** A thumbnail with the panorama's name under it; the active one is outlined in the accent colour. */
    private final class PanoramaCard extends Button {
        private final int panorama;

        PanoramaCard(int panorama) {
            super(0, 0, 80, 60, Component.literal(panorama < 0 ? "Vanilla" : VBVPanoramaOverlay.name(panorama)),
                b -> {
                    VBVPanoramaOverlay.select(panorama);
                    VBVConfigStore.saveAll();
                }, DEFAULT_NARRATION);
            this.panorama = panorama;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            Font f = VBVLauncherScreen.this.font;
            int x = this.getX();
            int y = this.getY();
            int w = this.getWidth();
            int th = w * 9 / 16;
            boolean active = VBVPanoramaOverlay.selectedIndex() == panorama;
            boolean lit = this.isHoveredOrFocused();

            if (panorama < 0) {
                graphics.fillGradient(x, y, x + w, y + th, 0xFF2B3A52, 0xFF121822);
                String label = "MINECRAFT";
                graphics.text(f, label, x + (w - f.width(label)) / 2, y + (th - f.lineHeight) / 2, VBVUi.TEXT_DIM, true);
            } else {
                Identifier preview = VBVPanoramaOverlay.preview(panorama);
                graphics.blit(RenderPipelines.GUI_TEXTURED, preview, x, y, 0, 0, w, th, w, th, w, th);
            }
            int edge = active ? VBVTheme.accent() : (lit ? VBVTheme.accentLight() : VBVTheme.accentAlpha(0x60));
            graphics.outline(x, y, w, th, edge);
            if (active) {
                graphics.outline(x + 1, y + 1, w - 2, th - 2, edge);
            }

            String label = VBVUi.fit(f, getMessage().getString(), w - (active ? 44 : 0));
            graphics.text(f, label, x, y + th + 4, active || lit ? 0xFFFFFFFF : VBVUi.TEXT_DIM, true);
            if (active) {
                String tag = "ACTIVE";
                graphics.text(f, tag, x + w - f.width(tag), y + th + 4, VBVTheme.accentLight(), true);
            }
        }
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

    // -------------------------------------------------------------- friends

    private void buildFriends() {
        List<Action> actions = new ArrayList<>();
        actions.add(new Action("FRIENDS LIST", "Add friends, answer requests and see who is online.",
            b -> VBVNav.open(new VBVFriendsScreen(this))));
        addActions(contentBottom - 36, actions);
    }

    private void renderFriends(GuiGraphicsExtractor graphics) {
        int panelW = Math.min(contentW, 300);
        VBVUi.panel(graphics, contentX, contentY, panelW, 56);
        graphics.text(font, "FRIENDS", contentX + 8, contentY + 8, VBVTheme.accentLight(), true);
        if (!VBVFriends.signedIn()) {
            graphics.text(font, "No Friends account yet.", contentX + 8, contentY + 24, VBVUi.TEXT_DIM, false);
            graphics.text(font, "Open the list to create one.", contentX + 8, contentY + 38, VBVUi.TEXT_DIM, false);
            return;
        }
        int friends = VBVFriends.friendRows().length;
        int requests = VBVFriends.incomingRows().length;
        graphics.text(font, VBVUi.fit(font, "Signed in as " + VBVFriends.name(), panelW - 16), contentX + 8, contentY + 24,
            VBVUi.TEXT, false);
        graphics.text(font, VBVFriends.onlineCount() + " of " + friends + " friends online"
            + (requests > 0 ? "  |  " + requests + " request" + (requests == 1 ? "" : "s") : ""),
            contentX + 8, contentY + 38, VBVUi.TEXT_DIM, false);
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
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (graphics.guiWidth() != width || graphics.guiHeight() != height) {
            // Browser fullscreen can change the canvas without a window event; re-layout now.
            resize(graphics.guiWidth(), graphics.guiHeight());
        }
        VBVUi.panel(graphics, sideX, 6, sideW, height - 12);
        VBVUi.panel(graphics, contentX - 8, 6, width - contentX - 4, height - 12);

        graphics.text(font, "VBV", sideX + 9, 12, VBVTheme.accentLight(), true);
        graphics.text(font, "EXTRAS", sideX + 9 + font.width("VBV "), 12, VBVUi.TEXT, true);

        graphics.pose().pushMatrix();
        graphics.pose().translate(contentX, 12);
        graphics.pose().scale(1.5F, 1.5F);
        graphics.text(font, tab.label, 0, 0, 0xFFFFFFFF, true);
        graphics.pose().popMatrix();
        graphics.fill(contentX, 28, contentX + 30, 29, VBVTheme.accent());

        switch (tab) {
            case PANORAMAS -> renderPanoramasHeader(graphics);
            case HERO -> renderHero(graphics);
            case FRIENDS -> renderFriends(graphics);
            case PARTY -> renderParty(graphics);
            case STORAGE -> renderStorage(graphics);
            case APPEARANCE -> renderAppearance(graphics);
        }
        for (Hint hint : hints) {
            graphics.text(font, hint.text(), hint.x(), hint.y(), VBVUi.TEXT_DIM, false);
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void removed() {
        super.removed();
        VBVPanoramaOverlay.releasePreviews();
    }

    @Override
    public void onClose() {
        VBVNav.open(parent);
    }
}
