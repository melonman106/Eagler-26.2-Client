package com.melonman106.vbvclient;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Friends: add players by name, accept or decline requests and see who is online and where.
 *
 * <p>Before an account exists the screen offers to create one (the name defaults to the Eagler profile
 * name) or to restore one with its account key. The service address can be changed at the bottom in case
 * the Worker is deployed under a different name. All network work happens in {@link VBVFriends}; this
 * screen only reads its state, and rebuilds itself when the state's version number changes.</p>
 */
public final class VBVFriendsScreen extends Screen {
    private static final int GREEN = 0xFF63E08A;
    private static final int GREY = 0xFF77736A;
    private static final int ROW_H = 22;

    private enum Kind { HEADER, REQUEST, FRIEND, SENT }

    private record Entry(Kind kind, String id, String name, boolean online, String server) {}

    private final Screen parent;
    private final List<Entry> entries = new ArrayList<>();
    private EditBox nameBox;
    private EditBox keyBox;
    private EditBox addBox;
    private EditBox apiBox;
    private String typedName = "";
    private int shownVersion = -1;
    private int scroll;
    private int listTop;
    private int listBottom;
    private int listLeft;
    private int listWidth;
    private int visibleRows;
    private VBVUi.ThemedButton hiddenButton;

    public VBVFriendsScreen(Screen parent) {
        super(Component.literal("Friends"));
        this.parent = parent;
        VBVFriends.initialize();
        // Once per visit, here and not in init(): init() also runs on every rebuild, and a refresh changes
        // the bridge's version, which would rebuild the screen again, over and over.
        VBVFriends.watch(true);
        VBVFriends.refresh();
    }

    @Override
    protected void init() {
        super.init();
        shownVersion = VBVFriends.version();
        if (VBVFriends.signedIn()) {
            buildSignedIn();
        } else {
            buildSignedOut();
        }
    }

    // ------------------------------------------------------------- signed out

    private void buildSignedOut() {
        int w = Math.min(240, width - 24);
        int x = width / 2 - w / 2;
        int y = 54;

        nameBox = new EditBox(font, x, y, w, 20, Component.literal("Friends name"));
        nameBox.setMaxLength(16);
        nameBox.setHint(Component.literal("Your name (letters, numbers, _)"));
        nameBox.setValue(typedName.isEmpty() ? VBVProfile.name() : typedName);
        addRenderableWidget(nameBox);
        addRenderableWidget(new VBVUi.ThemedButton(x, y + 24, w, 20, "CREATE FRIENDS ACCOUNT", VBVUi.Style.MENU, b -> {
            typedName = nameBox.getValue().trim();
            VBVFriends.register(typedName);
        }));

        int y2 = y + 78;
        keyBox = new EditBox(font, x, y2, w, 20, Component.literal("Account key"));
        keyBox.setMaxLength(64);
        keyBox.setHint(Component.literal("Account key from your other browser"));
        addRenderableWidget(keyBox);
        addRenderableWidget(new VBVUi.ThemedButton(x, y2 + 24, w, 20, "SIGN IN WITH NAME + KEY", VBVUi.Style.MENU, b -> {
            typedName = nameBox.getValue().trim();
            VBVFriends.login(typedName, keyBox.getValue().trim());
        }));
        buildFooter(false);
    }

    // -------------------------------------------------------------- signed in

    private void buildSignedIn() {
        listWidth = Math.min(width - 24, 340);
        listLeft = width / 2 - listWidth / 2;

        addBox = new EditBox(font, listLeft, 48, listWidth - 66, 20, Component.literal("Add a friend"));
        addBox.setMaxLength(16);
        addBox.setHint(Component.literal("Add a friend by name"));
        addRenderableWidget(addBox);
        addRenderableWidget(new VBVUi.ThemedButton(listLeft + listWidth - 62, 48, 62, 20, "ADD", VBVUi.Style.MENU, b -> {
            String name = addBox.getValue().trim();
            if (!name.isEmpty()) {
                VBVFriends.request(name);
                addBox.setValue("");
            }
        }));

        collectEntries();
        listTop = 76;
        listBottom = height - 74;
        visibleRows = Math.max(1, (listBottom - listTop) / ROW_H);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, entries.size() - visibleRows)));

        int shown = Math.min(visibleRows, entries.size() - scroll);
        for (int i = 0; i < shown; i++) {
            Entry entry = entries.get(scroll + i);
            int y = listTop + i * ROW_H;
            int bx = listLeft + listWidth - 4;
            switch (entry.kind()) {
                case REQUEST -> {
                    bx -= 58;
                    final String id = entry.id();
                    addRenderableWidget(new VBVUi.ThemedButton(bx, y + 1, 58, ROW_H - 4, "DECLINE", VBVUi.Style.SMALL,
                        b -> VBVFriends.decline(id)));
                    bx -= 62;
                    addRenderableWidget(new VBVUi.ThemedButton(bx, y + 1, 58, ROW_H - 4, "ACCEPT", VBVUi.Style.SMALL,
                        b -> VBVFriends.accept(id)));
                }
                case FRIEND -> {
                    bx -= 58;
                    final String id = entry.id();
                    addRenderableWidget(new VBVUi.ThemedButton(bx, y + 1, 58, ROW_H - 4, "REMOVE", VBVUi.Style.SMALL,
                        b -> VBVFriends.remove(id)));
                }
                case SENT -> {
                    bx -= 58;
                    final String id = entry.id();
                    addRenderableWidget(new VBVUi.ThemedButton(bx, y + 1, 58, ROW_H - 4, "CANCEL", VBVUi.Style.SMALL,
                        b -> VBVFriends.decline(id)));
                }
                default -> { }
            }
        }
        buildFooter(true);
    }

    private void collectEntries() {
        entries.clear();
        String[] requests = VBVFriends.incomingRows();
        if (requests.length > 0) {
            entries.add(new Entry(Kind.HEADER, "", "REQUESTS (" + requests.length + ")", false, ""));
            for (String row : requests) {
                String[] f = row.split("\\t", -1);
                entries.add(new Entry(Kind.REQUEST, f[0], f.length > 1 ? f[1] : "?", false, ""));
            }
        }
        String[] friends = VBVFriends.friendRows();
        entries.add(new Entry(Kind.HEADER, "", "FRIENDS (" + VBVFriends.onlineCount() + " online of " + friends.length + ")",
            false, ""));
        for (String row : friends) {
            String[] f = row.split("\\t", -1);
            entries.add(new Entry(Kind.FRIEND, f[0], f.length > 1 ? f[1] : "?", f.length > 2 && "1".equals(f[2]),
                f.length > 3 ? f[3] : ""));
        }
        String[] sent = VBVFriends.outgoingRows();
        if (sent.length > 0) {
            entries.add(new Entry(Kind.HEADER, "", "SENT REQUESTS (" + sent.length + ")", false, ""));
            for (String row : sent) {
                String[] f = row.split("\\t", -1);
                entries.add(new Entry(Kind.SENT, f[0], f.length > 1 ? f[1] : "?", false, ""));
            }
        }
    }

    // ------------------------------------------------------------------ footer

    private void buildFooter(boolean signedIn) {
        int bottom = height - 26;
        if (signedIn) {
            int w = Math.min(width - 24, 340);
            int x = width / 2 - w / 2;
            int third = (w - 8) / 3;
            hiddenButton = new VBVUi.ThemedButton(x, height - 50, third, 18, hiddenLabel(), VBVUi.Style.SMALL, b -> {
                VBVFriends.setHidden(!VBVFriends.hidden());
                hiddenButton.setLabel(hiddenLabel());
            });
            addRenderableWidget(hiddenButton);
            addRenderableWidget(new VBVUi.ThemedButton(x + third + 4, height - 50, third, 18, "COPY KEY", VBVUi.Style.SMALL,
                b -> VBVFriends.copyKey()));
            addRenderableWidget(new VBVUi.ThemedButton(x + 2 * (third + 4), height - 50, third, 18, "SIGN OUT",
                VBVUi.Style.SMALL, b -> VBVFriends.signOut()));
        } else {
            int w = Math.min(width - 24, 340);
            int x = width / 2 - w / 2;
            apiBox = new EditBox(font, x, height - 50, w - 54, 18, Component.literal("Service address"));
            apiBox.setMaxLength(200);
            apiBox.setValue(VBVFriends.api());
            addRenderableWidget(apiBox);
            addRenderableWidget(new VBVUi.ThemedButton(x + w - 50, height - 50, 50, 18, "SAVE", VBVUi.Style.SMALL,
                b -> VBVFriends.setApi(apiBox.getValue())));
        }
        int half = 98;
        addRenderableWidget(new VBVUi.ThemedButton(width / 2 - half - 2, bottom, half, 20, "REFRESH", VBVUi.Style.SMALL,
            b -> VBVFriends.refresh()));
        addRenderableWidget(new VBVUi.ThemedButton(width / 2 + 2, bottom, half, 20, "< BACK", VBVUi.Style.SMALL,
            b -> onClose()));
    }

    private static String hiddenLabel() {
        return VBVFriends.hidden() ? "APPEAR: OFFLINE" : "APPEAR: ONLINE";
    }

    // ------------------------------------------------------------------- input

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (VBVFriends.signedIn() && entries.size() > visibleRows && scrollY != 0.0
            && mouseY >= listTop && mouseY <= listBottom) {
            int max = entries.size() - visibleRows;
            int next = Math.max(0, Math.min(max, scroll + (scrollY < 0.0 ? 1 : -1)));
            if (next != scroll) {
                scroll = next;
                rebuildWidgets();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void tick() {
        super.tick();
        int version = VBVFriends.version();
        if (version != shownVersion) {
            shownVersion = version;
            // Keep what is typed in the boxes across the rebuild.
            String add = addBox == null ? "" : addBox.getValue();
            String name = nameBox == null ? typedName : nameBox.getValue();
            String key = keyBox == null ? "" : keyBox.getValue();
            String api = apiBox == null ? null : apiBox.getValue();
            typedName = name;
            rebuildWidgets();
            if (addBox != null) addBox.setValue(add);
            if (keyBox != null) keyBox.setValue(key);
            if (apiBox != null && api != null) apiBox.setValue(api);
        }
    }

    // ------------------------------------------------------------------ render

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        String heading = "FRIENDS";
        graphics.text(font, heading, width / 2 - font.width(heading) / 2, 16, 0xFFFFFFFF, true);

        if (VBVFriends.signedIn()) {
            String who = "Signed in as " + VBVFriends.name();
            graphics.text(font, who, width / 2 - font.width(who) / 2, 30, VBVUi.TEXT_DIM, false);
            renderList(graphics);
        } else {
            int w = Math.min(240, width - 24);
            int x = width / 2 - w / 2;
            graphics.text(font, "New here? Pick the name friends will add you by.", x, 38, VBVUi.TEXT_DIM, false);
            graphics.text(font, "Already have an account on another browser?", x, 54 + 78 - 12, VBVUi.TEXT_DIM, false);
            graphics.text(font, "Service address:", width / 2 - Math.min(width - 24, 340) / 2, height - 62, VBVUi.TEXT_DIM, false);
        }

        String status = VBVFriends.status();
        if (!status.isEmpty()) {
            int max = width - 24;
            String line = VBVUi.fit(font, status, max);
            graphics.text(font, line, width / 2 - font.width(line) / 2, height - 62 + (VBVFriends.signedIn() ? 0 : 12),
                VBVFriends.busy() ? VBVUi.TEXT_DIM : VBVUi.TEXT, false);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void renderList(GuiGraphicsExtractor graphics) {
        VBVUi.panel(graphics, listLeft, listTop - 4, listWidth, listBottom - listTop + 8);
        int shown = Math.min(visibleRows, entries.size() - scroll);
        for (int i = 0; i < shown; i++) {
            Entry entry = entries.get(scroll + i);
            int y = listTop + i * ROW_H;
            int textY = y + (ROW_H - font.lineHeight) / 2;
            switch (entry.kind()) {
                case HEADER -> graphics.text(font, entry.name(), listLeft + 8, textY, 0xFFFFFFFF, true);
                case FRIEND -> {
                    graphics.fill(listLeft + 8, textY + 1, listLeft + 14, textY + 7, entry.online() ? GREEN : GREY);
                    int nameW = font.width(entry.name());
                    graphics.text(font, entry.name(), listLeft + 20, textY, entry.online() ? 0xFFFFFFFF : VBVUi.TEXT_DIM, true);
                    String where = entry.online()
                        ? (entry.server().isEmpty() ? "online" : entry.server())
                        : "offline";
                    int room = listWidth - 20 - nameW - 58 - 20;
                    if (room > 30) {
                        graphics.text(font, VBVUi.fit(font, where, room), listLeft + 20 + nameW + 10, textY,
                            entry.online() ? GREEN : GREY, false);
                    }
                }
                case REQUEST, SENT -> graphics.text(font, entry.name(), listLeft + 20, textY, VBVUi.TEXT, true);
                default -> { }
            }
        }
        if (entries.size() > visibleRows) {
            int trackH = listBottom - listTop;
            int thumbH = Math.max(12, trackH * visibleRows / entries.size());
            int maxScroll = entries.size() - visibleRows;
            int thumbY = listTop + (trackH - thumbH) * scroll / Math.max(1, maxScroll);
            graphics.fill(listLeft + listWidth - 3, thumbY, listLeft + listWidth - 1, thumbY + thumbH, VBVTheme.accent());
        }
        if (entries.size() == 1 && VBVFriends.friendRows().length == 0) {
            graphics.text(font, "No friends yet. Type a name above and press ADD.", listLeft + 8, listTop + ROW_H + 4,
                VBVUi.TEXT_DIM, false);
        }
    }

    @Override
    public void removed() {
        VBVFriends.watch(false);
        super.removed();
    }

    @Override
    public void onClose() {
        VBVNav.open(parent);
    }
}
