package com.melonman106.vbvclient;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Friends: add players by name, answer requests, see who is online and start or join a voice call.
 *
 * <p>The screen is one column: the account line, the add row, a voice strip and the list. The voice strip
 * is pinned above the list so a call stays reachable however far the list is scrolled, and the list is
 * split into requests, friends and sent requests. Only friends change with the filter chips, so the
 * sections above and below keep their place.</p>
 *
 * <p>Rows are drawn and hit-tested by hand instead of being widgets, so the list can filter, hover and
 * scroll without rebuilding the screen: a rebuild would drop the keyboard focus and the text being typed.
 * The only widgets are the text boxes. All network work stays in {@link VBVFriends} and {@link VBVVoice};
 * this screen only reads their state and calls them.</p>
 */
public final class VBVFriendsScreen extends Screen {
    private static final int ONLINE = 0xFF63E08A;
    private static final int OFFLINE = 0xFF6E6A63;
    private static final int EDGE = 0x33FFFFFF;

    private static final int HEADER_H = 18;
    private static final int ROW_H = 34;
    private static final int NOTE_H = 24;

    private enum Kind { HEADER, NOTE, REQUEST, FRIEND, SENT }

    /** A line of the list; {@code id} doubles as the section key on section headers. */
    private record Row(Kind kind, String id, String name, boolean online, String server, int voice, String room) {
        static Row header(String section, String label) {
            return new Row(Kind.HEADER, section, label, false, "", 0, "");
        }

        static Row note(String text) {
            return new Row(Kind.NOTE, "", text, false, "", 0, "");
        }
    }

    private enum Filter { ALL, ONLINE, CALL }

    /** A clickable rectangle of the last frame and what clicking it does. */
    private record Hot(int x, int y, int w, int h, Runnable action) {}

    private final Screen parent;
    private final List<Row> rows = new ArrayList<>();
    private final List<Hot> hots = new ArrayList<>();
    private EditBox addBox;
    private EditBox nameBox;
    private EditBox keyBox;
    private EditBox apiBox;
    private Filter filter = Filter.ALL;
    private int cachedVersion = -1;
    private boolean signedInAtInit;
    private String typedAdd = "";
    private String typedName = "";
    private String typedKey = "";
    private String typedApi = "";

    // Layout, recomputed from the current width and height.
    private int panelLeft;
    private int panelW;
    private int titleY;
    private int subtitleY;
    private int addY;
    private int voiceTop;
    private int voiceH;
    private int listCardTop;
    private int listTop;
    private int listBottom;
    private int listCardBottom;
    private int statusY;
    private int footerY;
    private int scrollPx;
    private int mx;
    private int my;

    // Counts of the cached rows, for the header and the chips.
    private int countOnline;
    private int countCall;
    private int countAll;

    private String confirmId = "";
    private long confirmAt;
    private boolean signOutArmed;
    private long signOutAt;

    public VBVFriendsScreen(Screen parent) {
        super(Component.literal("Friends"));
        this.parent = parent;
        VBVFriends.initialize();
        VBVVoice.initialize();
        // Once per visit, here and not in init(): init() also runs on every resize, and a refresh changes
        // the bridge's version, which would rebuild the screen again, over and over.
        VBVFriends.watch(true);
        VBVFriends.refresh();
    }

    @Override
    protected void init() {
        super.init();
        signedInAtInit = VBVFriends.signedIn();
        cachedVersion = VBVFriends.version();
        fillRows();
        layout();
        if (signedInAtInit) {
            buildSignedIn();
        } else {
            buildSignedOut();
        }
    }

    // ------------------------------------------------------------------- layout

    /** Computes every y position from the current GUI size; also used by the renderer. */
    private void layout() {
        boolean compact = height < 300;
        panelW = Math.min(width - 24, 380);
        panelLeft = width / 2 - panelW / 2;
        titleY = compact ? 8 : 12;
        subtitleY = titleY + 14;
        addY = compact ? 28 : 42;
        voiceTop = addY + 26;
        voiceH = VBVVoice.inCall() ? 50 : 28;
        listCardTop = voiceTop + voiceH + 6;
        footerY = height - 28;
        statusY = footerY - 14;
        listCardBottom = statusY - 6;
        listTop = listCardTop + 42;
        listBottom = listCardBottom - 8;
    }

    private void buildSignedIn() {
        addBox = new EditBox(font, panelLeft, addY, panelW - 70, 20, Component.literal("Add a friend"));
        addBox.setMaxLength(16);
        addBox.setHint(Component.literal("Add a friend by name"));
        addBox.setValue(typedAdd);
        addBox.setResponder(value -> typedAdd = value);
        addRenderableWidget(addBox);
    }

    private void buildSignedOut() {
        boolean compact = height < 300;
        int x = panelLeft + 8;
        int w = panelW - 16;
        int nameLabelY = compact ? 26 : 46;
        int nameY = nameLabelY + 12;
        int createY = nameY + 24;
        int keyLabelY = createY + 26;
        int keyY = keyLabelY + 12;
        int signInY = keyY + 24;

        nameBox = new EditBox(font, x, nameY, w, 20, Component.literal("Friends name"));
        nameBox.setMaxLength(16);
        nameBox.setHint(Component.literal("Letters, numbers and _"));
        nameBox.setValue(typedName.isEmpty() ? VBVProfile.name() : typedName);
        nameBox.setResponder(value -> typedName = value);
        addRenderableWidget(nameBox);

        keyBox = new EditBox(font, x, keyY, w, 20, Component.literal("Account key"));
        keyBox.setMaxLength(64);
        keyBox.setHint(Component.literal("Account key from your other browser"));
        keyBox.setValue(typedKey);
        keyBox.setResponder(value -> typedKey = value);
        addRenderableWidget(keyBox);

        int addressY = footerY - 26;
        apiBox = new EditBox(font, x, addressY, w - 54, 18, Component.literal("Service address"));
        apiBox.setMaxLength(200);
        apiBox.setValue(typedApi.isEmpty() ? VBVFriends.api() : typedApi);
        apiBox.setResponder(value -> typedApi = value);
        addRenderableWidget(apiBox);
    }

    // --------------------------------------------------------------------- data

    private void fillRows() {
        rows.clear();
        String[] requests = VBVFriends.incomingRows();
        if (requests.length > 0) {
            rows.add(Row.header("requests", "REQUESTS (" + requests.length + ")"));
            for (String raw : requests) {
                String[] f = raw.split("\\t", -1);
                rows.add(new Row(Kind.REQUEST, f[0], f.length > 1 ? f[1] : "?", false, "", 0, ""));
            }
        }

        List<Row> friends = new ArrayList<>();
        for (String raw : VBVFriends.friendRows()) {
            String[] f = raw.split("\\t", -1);
            int voice = 0;
            if (f.length > 4) {
                try {
                    voice = Integer.parseInt(f[4]);
                } catch (NumberFormatException ignored) {
                    voice = 0;
                }
            }
            friends.add(new Row(Kind.FRIEND, f[0], f.length > 1 ? f[1] : "?", f.length > 2 && "1".equals(f[2]),
                f.length > 3 ? f[3] : "", voice, f.length > 5 ? f[5] : ""));
        }
        // In a call first, then online, then alphabetically: the people you can act on are at the top.
        friends.sort((a, b) -> {
            if ((a.voice() > 0) != (b.voice() > 0)) return a.voice() > 0 ? -1 : 1;
            if (a.online() != b.online()) return a.online() ? -1 : 1;
            return a.name().compareToIgnoreCase(b.name());
        });
        countAll = friends.size();
        countOnline = 0;
        countCall = 0;
        for (Row row : friends) {
            if (row.online()) countOnline++;
            if (row.voice() > 0) countCall++;
        }
        rows.add(Row.header("friends", "FRIENDS"));
        rows.addAll(friends);

        String[] sent = VBVFriends.outgoingRows();
        if (sent.length > 0) {
            rows.add(Row.header("sent", "SENT REQUESTS (" + sent.length + ")"));
            for (String raw : sent) {
                String[] f = raw.split("\\t", -1);
                rows.add(new Row(Kind.SENT, f[0], f.length > 1 ? f[1] : "?", false, "", 0, ""));
            }
        }
    }

    /** The rows as drawn: requests, then the friends that pass the filter, then sent requests. */
    private List<Row> view() {
        List<Row> out = new ArrayList<>();
        List<Row> friends = new ArrayList<>();
        for (Row row : rows) {
            if (row.kind() == Kind.FRIEND) friends.add(row);
        }
        for (Row row : rows) {
            if (row.kind() != Kind.FRIEND && !"friends".equals(row.id())) {
                out.add(row);
                continue;
            }
            if (row.kind() == Kind.FRIEND) continue;
            switch (filter) {
                case ALL -> {
                    out.add(Row.header("friends", "FRIENDS  " + countOnline + " of " + countAll + " online"));
                    out.addAll(friends);
                    if (friends.isEmpty()) out.add(Row.note("No friends yet. Type a name above and press ADD."));
                }
                case ONLINE -> {
                    out.add(Row.header("friends", "ONLINE  " + countOnline));
                    int added = 0;
                    for (Row friend : friends) {
                        if (friend.online()) {
                            out.add(friend);
                            added++;
                        }
                    }
                    if (added == 0) out.add(Row.note("None of your friends is online right now."));
                }
                case CALL -> {
                    out.add(Row.header("friends", "IN A CALL  " + countCall));
                    int added = 0;
                    for (Row friend : friends) {
                        if (friend.voice() > 0) {
                            out.add(friend);
                            added++;
                        }
                    }
                    if (added == 0) out.add(Row.note("Nobody on your list is in a voice chat right now."));
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------- render

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        mx = mouseX;
        my = mouseY;
        hots.clear();
        layout();
        renderHeading(graphics);
        if (VBVFriends.signedIn()) {
            renderSignedIn(graphics);
        } else {
            renderSignedOut(graphics);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void renderHeading(GuiGraphicsExtractor graphics) {
        String title = "FRIENDS";
        int tx = width / 2 - font.width(title) / 2;
        graphics.text(font, title, tx, titleY, 0xFFFFFFFF, true);
        graphics.fill(width / 2 - 22, titleY + 11, width / 2 + 22, titleY + 12, VBVTheme.accent());

        if (height < 300) return;
        String subtitle;
        if (VBVFriends.signedIn()) {
            subtitle = "Signed in as " + VBVFriends.name() + "  ·  " + countOnline + " of " + countAll + " online";
        } else {
            subtitle = "Create an account, or sign in with the key from another browser.";
        }
        String shown = VBVUi.fit(font, subtitle, width - 20);
        graphics.text(font, shown, width / 2 - font.width(shown) / 2, subtitleY, VBVUi.TEXT_DIM, false);
    }

    private void renderSignedIn(GuiGraphicsExtractor graphics) {
        // Add row: the box is a widget, the button sits next to it.
        drawButton(graphics, panelLeft + panelW - 64, addY, 64, 20, "ADD", true, this::submitAdd);

        renderVoice(graphics);
        renderList(graphics);

        String status = VBVFriends.status();
        if (status.isEmpty()) {
            status = "Requests, presence and calls refresh automatically.";
        }
        String line = VBVUi.fit(font, status, panelW);
        graphics.text(font, line, panelLeft, statusY, VBVUi.TEXT_DIM, false);

        int bw = (panelW - 18) / 4;
        drawButton(graphics, panelLeft, footerY, bw, 20, hiddenLabel(), false, () -> {
            VBVFriends.setHidden(!VBVFriends.hidden());
        });
        drawButton(graphics, panelLeft + bw + 6, footerY, bw, 20, "COPY KEY", false, VBVFriends::copyKey);
        drawButton(graphics, panelLeft + 2 * (bw + 6), footerY, bw, 20,
            signOutArmed && System.currentTimeMillis() < signOutAt ? "SURE?" : "SIGN OUT", false, () -> {
                if (signOutArmed && System.currentTimeMillis() < signOutAt) {
                    signOutArmed = false;
                    VBVFriends.signOut();
                } else {
                    signOutArmed = true;
                    signOutAt = System.currentTimeMillis() + 4000L;
                }
            });
        drawButton(graphics, panelLeft + 3 * (bw + 6), footerY, panelW - 3 * (bw + 6), 20, "BACK", false, this::onClose);
    }

    /** The pinned voice strip: state, actions, and who is in the call with you. */
    private void renderVoice(GuiGraphicsExtractor graphics) {
        String phase = VBVVoice.phase();
        boolean inCall = !"idle".equals(phase);
        String[] members = inCall ? VBVVoice.memberRows() : new String[0];
        List<String> names = new ArrayList<>();
        List<Boolean> talking = new ArrayList<>();
        for (String raw : members) {
            String[] f = raw.split("\\t", -1);
            if (f.length < 4) continue;
            talking.add("1".equals(f[2]));
            names.add("you".equals(f[3]) ? f[1] + " (you)" : f[1]);
        }

        graphics.fill(panelLeft, voiceTop, panelLeft + panelW, voiceTop + voiceH, VBVTheme.panel(0xE6));
        graphics.fill(panelLeft, voiceTop, panelLeft + 3, voiceTop + voiceH, VBVTheme.accent());

        int lineY = voiceTop + (inCall ? 7 : (voiceH - font.lineHeight) / 2);
        dot(graphics, panelLeft + 12, lineY + 2, inCall ? (VBVVoice.isMuted() ? OFFLINE : ONLINE) : OFFLINE);
        graphics.text(font, "VOICE CHAT", panelLeft + 22, lineY, 0xFFFFFFFF, true);
        String state = switch (phase) {
            case "starting" -> "connecting...";
            case "live" -> names.size() + " in the call" + (VBVVoice.isMuted() ? "  ·  muted" : "");
            default -> "not in a call";
        };
        int stateX = panelLeft + 22 + font.width("VOICE CHAT") + 8;
        graphics.text(font, VBVUi.fit(font, "·  " + state, panelLeft + panelW - 130 - stateX), stateX, lineY,
            VBVUi.TEXT_DIM, false);

        if (!inCall) {
            drawButton(graphics, panelLeft + panelW - 64, voiceTop + 5, 56, 18, "START", true, VBVVoice::start);
            return;
        }
        drawButton(graphics, panelLeft + panelW - 122, voiceTop + 5, 56, 18, VBVVoice.isMuted() ? "UNMUTE" : "MUTE",
            false, VBVVoice::toggleMute);
        drawButton(graphics, panelLeft + panelW - 62, voiceTop + 5, 54, 18, "LEAVE", false, VBVVoice::leave);
        if (voiceH < 40) return;

        int x = panelLeft + 12;
        int right = panelLeft + panelW - 12;
        int y = voiceTop + 28;
        int drawn = 0;
        for (int i = 0; i < names.size(); i++) {
            int need = 12 + font.width(names.get(i)) + 10;
            if (x + need > right) break;
            dot(graphics, x, y + 2, talking.get(i) ? ONLINE : OFFLINE);
            graphics.text(font, names.get(i), x + 8, y, talking.get(i) ? 0xFFFFFFFF : VBVUi.TEXT_DIM, false);
            x += need;
            drawn++;
        }
        if (drawn < names.size()) {
            graphics.text(font, "+" + (names.size() - drawn), x, y, VBVUi.TEXT_DIM, false);
        }
        if (drawn == 0) {
            graphics.text(font, VBVUi.fit(font, "Waiting for the others to connect...", panelW - 24), panelLeft + 12, y,
                VBVUi.TEXT_DIM, false);
        }
        String message = VBVVoice.message();
        if (!message.isEmpty() && height >= 300) {
            graphics.text(font, VBVUi.fit(font, message, panelW - 24), panelLeft + 12, y + 12, VBVUi.TEXT_DIM, false);
        }
    }

    private void renderList(GuiGraphicsExtractor graphics) {
        int cardH = Math.max(24, listCardBottom - listCardTop);
        VBVUi.panel(graphics, panelLeft, listCardTop, panelW, cardH);

        graphics.text(font, "FRIENDS", panelLeft + 10, listCardTop + 7, 0xFFFFFFFF, true);
        String count = countOnline + " / " + countAll + " online";
        graphics.text(font, count, panelLeft + panelW - 10 - font.width(count), listCardTop + 7, VBVUi.TEXT_DIM, false);

        int chipX = panelLeft + 10;
        chipX += chip(graphics, chipX, listCardTop + 22, "ALL " + countAll, filter == Filter.ALL, () -> filter = Filter.ALL) + 5;
        chipX += chip(graphics, chipX, listCardTop + 22, "ONLINE " + countOnline, filter == Filter.ONLINE,
            () -> filter = Filter.ONLINE) + 5;
        chip(graphics, chipX, listCardTop + 22, "IN CALL " + countCall, filter == Filter.CALL, () -> filter = Filter.CALL);
        drawButton(graphics, panelLeft + panelW - 66, listCardTop + 21, 58, 15, "REFRESH", false, VBVFriends::refresh);

        List<Row> view = view();
        int contentH = 0;
        for (Row row : view) contentH += rowHeight(row);
        if (listBottom <= listTop) {
            return;
        }
        int viewport = listBottom - listTop;
        int maxScroll = Math.max(0, contentH - viewport);
        scrollPx = Math.max(0, Math.min(maxScroll, scrollPx));

        graphics.enableScissor(panelLeft + 4, listTop, panelLeft + panelW - 4, listBottom);
        int y = listTop - scrollPx;
        for (Row row : view) {
            int h = rowHeight(row);
            if (y + h > listTop && y < listBottom) {
                boolean whole = y >= listTop && y + h <= listBottom;
                renderRow(graphics, row, y, whole);
            }
            y += h;
        }
        graphics.disableScissor();

        if (contentH > viewport) {
            int trackTop = listTop;
            int trackH = viewport;
            int thumbH = Math.max(16, trackH * viewport / contentH);
            int thumbY = trackTop + (trackH - thumbH) * scrollPx / Math.max(1, maxScroll);
            graphics.fill(panelLeft + panelW - 4, trackTop, panelLeft + panelW - 2, trackTop + trackH, 0x33FFFFFF);
            graphics.fill(panelLeft + panelW - 4, thumbY, panelLeft + panelW - 2, thumbY + thumbH, VBVTheme.accent());
        }
    }

    private static int rowHeight(Row row) {
        return switch (row.kind()) {
            case HEADER -> HEADER_H;
            case NOTE -> NOTE_H;
            default -> ROW_H;
        };
    }

    private void renderRow(GuiGraphicsExtractor graphics, Row row, int y, boolean whole) {
        int left = panelLeft + 10;
        int right = panelLeft + panelW - 10;
        switch (row.kind()) {
            case HEADER -> {
                graphics.text(font, row.name(), left, y + 5, 0xFFFFFFFF, true);
                int lineX = left + font.width(row.name()) + 8;
                if (lineX < right - 4) {
                    graphics.fill(lineX, y + 9, right, y + 10, EDGE);
                }
            }
            case NOTE -> graphics.text(font, VBVUi.fit(font, row.name(), right - left), left + 2, y + 7,
                VBVUi.TEXT_DIM, false);
            case REQUEST -> {
                int room = Math.max(24, right - 128 - (left + 28));
                avatar(graphics, left, y + 7, 20, row.name(), true);
                graphics.text(font, VBVUi.fit(font, row.name(), room), left + 28, y + 8, 0xFFFFFFFF, true);
                graphics.text(font, VBVUi.fit(font, "wants to be your friend", room), left + 28, y + 19,
                    VBVUi.TEXT_DIM, false);
                int bx = right - 58;
                drawRowButton(graphics, bx, y + 8, 58, 18, "DECLINE", false, whole, () -> VBVFriends.decline(row.id()));
                bx -= 62;
                drawRowButton(graphics, bx, y + 8, 58, 18, "ACCEPT", true, whole, () -> VBVFriends.accept(row.id()));
            }
            case SENT -> {
                int room = Math.max(24, right - 66 - (left + 28));
                avatar(graphics, left, y + 7, 20, row.name(), false);
                graphics.text(font, VBVUi.fit(font, row.name(), room), left + 28, y + 8, VBVUi.TEXT, true);
                graphics.text(font, VBVUi.fit(font, "Waiting for an answer", room), left + 28, y + 19,
                    VBVUi.TEXT_DIM, false);
                drawRowButton(graphics, right - 58, y + 8, 58, 18, "CANCEL", false, whole,
                    () -> VBVFriends.decline(row.id()));
            }
            case FRIEND -> {
                // Registered first, so the buttons added below are found first by the reverse click scan.
                if (whole && my >= y && my < y + ROW_H && row.voice() > 0 && !VBVVoice.inCall()) {
                    hots.add(new Hot(left - 4, y, right - left + 8, ROW_H, () -> VBVVoice.join(row.id())));
                }
                avatar(graphics, left, y + 7, 20, row.name(), row.online());
                int textRight = right - (row.voice() > 0 && !VBVVoice.inCall() ? 108 : 62);
                graphics.text(font, VBVUi.fit(font, row.name(), textRight - (left + 28)), left + 28, y + 8,
                    row.online() ? 0xFFFFFFFF : VBVUi.TEXT_DIM, true);
                String where;
                if (row.voice() > 0) {
                    where = "in a voice chat";
                } else if (!row.online()) {
                    where = "offline";
                } else if (!row.server().isEmpty()) {
                    where = "playing " + row.server();
                } else {
                    where = "online";
                }
                graphics.text(font, VBVUi.fit(font, where, Math.max(24, textRight - (left + 28))), left + 28, y + 19,
                    row.voice() > 0 || row.online() ? (row.voice() > 0 ? VBVTheme.accentLight() : ONLINE) : OFFLINE, false);

                int bx = right - 58;
                boolean arming = confirmId.equals(row.id()) && System.currentTimeMillis() < confirmAt;
                drawRowButton(graphics, bx, y + 8, 58, 18, arming ? "SURE?" : "REMOVE", false, whole, () -> {
                    if (confirmId.equals(row.id()) && System.currentTimeMillis() < confirmAt) {
                        confirmId = "";
                        VBVFriends.remove(row.id());
                    } else {
                        confirmId = row.id();
                        confirmAt = System.currentTimeMillis() + 4000L;
                    }
                });
                if (row.voice() > 0 && !VBVVoice.inCall()) {
                    bx -= 46;
                    drawRowButton(graphics, bx, y + 8, 42, 18, "JOIN", true, whole, () -> VBVVoice.join(row.id()));
                }
            }
        }
    }

    private void renderSignedOut(GuiGraphicsExtractor graphics) {
        boolean compact = height < 300;
        int x = panelLeft;
        int w = panelW;
        int nameLabelY = compact ? 26 : 46;
        int nameY = nameLabelY + 12;
        int createY = nameY + 24;
        int keyLabelY = createY + 26;
        int keyY = keyLabelY + 12;
        int signInY = keyY + 24;
        int cardTop = nameLabelY - 6;
        int cardBottom = signInY + 26;

        VBVUi.panelBackground(graphics, x, cardTop, w, cardBottom - cardTop);
        graphics.fill(x, cardTop, x + 3, cardBottom, VBVTheme.accent());

        int padX = x + 8;
        int contentW = w - 16;
        graphics.text(font, "YOUR NAME", padX, nameLabelY, 0xFFFFFFFF, true);
        graphics.text(font, VBVUi.fit(font, "Friends add you by this name.", contentW), padX + 74, nameLabelY,
            VBVUi.TEXT_DIM, false);
        drawButton(graphics, padX, createY, contentW, 20, "CREATE ACCOUNT", true, this::createAccount);

        graphics.text(font, "OR SIGN IN WITH A KEY", padX, keyLabelY, 0xFFFFFFFF, true);
        graphics.text(font, VBVUi.fit(font, "From the browser you already use.", contentW), padX + 128, keyLabelY,
            VBVUi.TEXT_DIM, false);
        drawButton(graphics, padX, signInY, contentW, 20, "SIGN IN", false, this::signIn);

        int addressY = footerY - 26;
        graphics.text(font, "SERVICE ADDRESS", x, addressY - 11, VBVUi.TEXT_DIM, false);
        drawButton(graphics, x + w - 50, addressY, 50, 18, "SAVE", false,
            () -> VBVFriends.setApi(apiBox == null ? "" : apiBox.getValue()));

        String status = VBVFriends.status();
        if (!status.isEmpty()) {
            // Above the service address, which owns the bottom of the screen when it is shown.
            graphics.text(font, VBVUi.fit(font, status, panelW), x, Math.min(statusY, cardBottom + 6), VBVUi.TEXT_DIM, false);
        }
        drawButton(graphics, width / 2 - 50, footerY, 100, 20, "BACK", false, this::onClose);
    }

    // ------------------------------------------------------------- small drawing

    /** A button drawn by hand; {@code action} also makes it clickable. */
    private void drawButton(GuiGraphicsExtractor graphics, int x, int y, int w, int h, String label, boolean primary,
                            Runnable action) {
        boolean over = mx >= x && mx < x + w && my >= y && my < y + h;
        int fill = primary
            ? (over ? VBVTheme.accentLight() : VBVTheme.accent())
            : (over ? 0xFF3B3B42 : 0xD917171B);
        int border = primary ? VBVTheme.accentLight() : (over ? 0x99FFFFFF : 0x44FFFFFF);
        int textColor = primary ? VBVTheme.onAccent() : (over ? 0xFFFFFFFF : VBVUi.TEXT_DIM);
        graphics.fill(x, y, x + w, y + h, fill);
        graphics.outline(x, y, w, h, border);
        String shown = VBVUi.fit(font, label, w - 6);
        graphics.text(font, shown, x + (w - font.width(shown)) / 2, y + (h - font.lineHeight) / 2, textColor, false);
        if (action != null) {
            hots.add(new Hot(x, y, w, h, action));
        }
    }

    /** A row action; only fully visible rows register it, so clipped buttons cannot be clicked. */
    private void drawRowButton(GuiGraphicsExtractor graphics, int x, int y, int w, int h, String label, boolean primary,
                               boolean whole, Runnable action) {
        drawButton(graphics, x, y, w, h, label, primary, whole ? action : null);
    }

    /** A filter chip; returns the width it used. */
    private int chip(GuiGraphicsExtractor graphics, int x, int y, String label, boolean active, Runnable action) {
        int w = font.width(label) + 12;
        boolean over = mx >= x && mx < x + w && my >= y && my < y + 14;
        graphics.fill(x, y, x + w, y + 14, active ? VBVTheme.accentDark() : (over ? 0x33FFFFFF : 0x26000000));
        graphics.outline(x, y, w, 14, active ? VBVTheme.accent() : 0x33FFFFFF);
        graphics.text(font, label, x + 6, y + 3, active ? 0xFFFFFFFF : VBVUi.TEXT_DIM, false);
        hots.add(new Hot(x, y, w, 14, action));
        return w;
    }

    private void avatar(GuiGraphicsExtractor graphics, int x, int y, int size, String name, boolean online) {
        graphics.fill(x, y, x + size, y + size, online ? 0xFF2A3441 : 0xFF202024);
        graphics.outline(x, y, size, size, online ? VBVTheme.accentDark() : 0x44FFFFFF);
        String initial = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT);
        graphics.text(font, initial, x + (size - font.width(initial)) / 2, y + (size - font.lineHeight) / 2,
            online ? 0xFFFFFFFF : VBVUi.TEXT_DIM, false);
        int d = 5;
        graphics.fill(x + size - d, y + size - d, x + size, y + size, online ? ONLINE : OFFLINE);
        graphics.outline(x + size - d, y + size - d, d, d, 0xFF101013);
    }

    private void dot(GuiGraphicsExtractor graphics, int x, int y, int color) {
        graphics.fill(x, y, x + 5, y + 5, color);
        graphics.outline(x, y, 5, 5, 0x66000000);
    }

    // -------------------------------------------------------------------- input

    private void submitAdd() {
        String name = addBox == null ? "" : addBox.getValue().trim();
        if (name.isEmpty()) {
            return;
        }
        VBVFriends.request(name);
        typedAdd = "";
        addBox.setValue("");
    }

    private void createAccount() {
        String name = nameBox == null ? "" : nameBox.getValue().trim();
        typedName = name;
        VBVFriends.register(name);
    }

    private void signIn() {
        String name = nameBox == null ? "" : nameBox.getValue().trim();
        String key = keyBox == null ? "" : keyBox.getValue().trim();
        typedName = name;
        typedKey = key;
        VBVFriends.login(name, key);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == 257 || key == 335) {
            if (addBox != null && addBox.isFocused()) {
                submitAdd();
                return true;
            }
            if (nameBox != null && nameBox.isFocused()) {
                createAccount();
                return true;
            }
            if (keyBox != null && keyBox.isFocused()) {
                signIn();
                return true;
            }
            if (apiBox != null && apiBox.isFocused()) {
                typedApi = apiBox.getValue();
                VBVFriends.setApi(typedApi);
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            for (int i = hots.size() - 1; i >= 0; i--) {
                Hot hot = hots.get(i);
                if (event.x() >= hot.x() && event.x() < hot.x() + hot.w()
                    && event.y() >= hot.y() && event.y() < hot.y() + hot.h()) {
                    hot.action().run();
                    clearFocus();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (VBVFriends.signedIn() && scrollY != 0.0
            && mouseX >= panelLeft && mouseX < panelLeft + panelW
            && mouseY >= listCardTop && mouseY < listCardBottom) {
            scrollPx -= (int) Math.round(scrollY * 34.0);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void tick() {
        super.tick();
        if (VBVFriends.version() != cachedVersion) {
            cachedVersion = VBVFriends.version();
            fillRows();
            if (VBVFriends.signedIn() != signedInAtInit) {
                rebuildWidgets();
            }
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

    /** Labelled as the action a click takes: go visible while hidden, hide while visible. */
    private static String hiddenLabel() {
        return VBVFriends.hidden() ? "APPEAR ON" : "APPEAR OFF";
    }
}
