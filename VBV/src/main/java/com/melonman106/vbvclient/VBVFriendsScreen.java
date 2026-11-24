package com.melonman106.vbvclient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 *
 * <p>The top of the list is the voice chat: start a call, mute, leave, and see who is in it and who is speaking.
 * A friend who is in a call gets a JOIN button. The audio is handled by {@link VBVVoice}; who is speaking changes
 * many times a second, so it is read while drawing and does not rebuild the screen.</p>
 */
public final class VBVFriendsScreen extends Screen {
    private static final int GREEN = 0xFF63E08A;
    private static final int GREY = 0xFF77736A;
    private static final int ROW_H = 22;

    private enum Kind { HEADER, REQUEST, FRIEND, SENT, VOICE_HEAD, VOICE_MEMBER, VOICE_NOTE }

    /** {@code server} doubles as a voice member's state; {@code voice} and {@code room} are a friend's voice chat. */
    private record Entry(Kind kind, String id, String name, boolean online, String server, int voice, String room) {
        Entry(Kind kind, String id, String name, boolean online, String server) {
            this(kind, id, name, online, server, 0, "");
        }
    }

    private final Screen parent;
    private final List<Entry> entries = new ArrayList<>();
    private EditBox nameBox;
    private EditBox keyBox;
    private EditBox addBox;
    private EditBox apiBox;
    private String typedName = "";
    private int shownVersion = -1;
    private int shownVoiceVersion = -1;
    private final Map<String, Boolean> speaking = new HashMap<>();
    private VBVUi.ThemedButton muteButton;
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
        VBVVoice.initialize();
        // Once per visit, here and not in init(): init() also runs on every rebuild, and a refresh changes
        // the bridge's version, which would rebuild the screen again, over and over.
        VBVFriends.watch(true);
        VBVFriends.refresh();
    }

    @Override
    protected void init() {
        super.init();
        shownVersion = VBVFriends.version();
        shownVoiceVersion = VBVVoice.version();
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
                    if (entry.voice() > 0 && !VBVVoice.inCall()) {
                        bx -= 50;
                        addRenderableWidget(new VBVUi.ThemedButton(bx, y + 1, 46, ROW_H - 4, "JOIN", VBVUi.Style.SMALL,
                            b -> VBVVoice.join(id)));
                    }
                }
                case VOICE_HEAD -> {
                    String phase = VBVVoice.phase();
                    if ("idle".equals(phase)) {
                        bx -= 58;
                        addRenderableWidget(new VBVUi.ThemedButton(bx, y + 1, 58, ROW_H - 4, "START", VBVUi.Style.SMALL,
                            b -> VBVVoice.start()));
                    } else {
                        bx -= 58;
                        addRenderableWidget(new VBVUi.ThemedButton(bx, y + 1, 58, ROW_H - 4, "LEAVE", VBVUi.Style.SMALL,
                            b -> VBVVoice.leave()));
                        if ("live".equals(phase)) {
                            bx -= 66;
                            muteButton = new VBVUi.ThemedButton(bx, y + 1, 62, ROW_H - 4, muteLabel(), VBVUi.Style.SMALL, b -> {
                                VBVVoice.toggleMute();
                                muteButton.setLabel(muteLabel());
                            });
                            addRenderableWidget(muteButton);
                        }
                    }
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
        collectVoiceEntries();
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
            int voice = 0;
            if (f.length > 4) {
                try { voice = Integer.parseInt(f[4]); } catch (NumberFormatException ignored) { voice = 0; }
            }
            entries.add(new Entry(Kind.FRIEND, f[0], f.length > 1 ? f[1] : "?", f.length > 2 && "1".equals(f[2]),
                f.length > 3 ? f[3] : "", voice, f.length > 5 ? f[5] : ""));
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

    private void collectVoiceEntries() {
        String phase = VBVVoice.phase();
        boolean inCall = !"idle".equals(phase);
        String head = switch (phase) {
            case "starting" -> "VOICE CHAT: starting...";
            case "live" -> "VOICE CHAT: in a call";
            default -> "VOICE CHAT";
        };
        entries.add(new Entry(Kind.VOICE_HEAD, "", head, false, ""));
        if (inCall) {
            for (String row : VBVVoice.memberRows()) {
                String[] f = row.split("\\t", -1);
                if (f.length < 4) continue;
                entries.add(new Entry(Kind.VOICE_MEMBER, f[0], f[1], false, f[3]));
            }
        } else if (VBVVoice.message().isEmpty()) {
            entries.add(new Entry(Kind.VOICE_NOTE, "", "Press START, or JOIN beside a friend who is in a call.", false, ""));
        }
        String message = VBVVoice.message();
        if (!message.isEmpty()) {
            int room = Math.min(width - 24, 340) - 20;
            int lines = 0;
            for (String line : wrap(message, room)) {
                if (lines++ >= 4) break;
                entries.add(new Entry(Kind.VOICE_NOTE, "", line, false, ""));
            }
        }
    }

    /** Greedy word wrap to a pixel width; a single word wider than the line is cut by {@link VBVUi#fit}. */
    private List<String> wrap(String text, int maxWidth) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String next = line.length() == 0 ? word : line + " " + word;
            if (font.width(next) <= maxWidth || line.length() == 0) {
                line.setLength(0);
                line.append(next);
            } else {
                out.add(VBVUi.fit(font, line.toString(), maxWidth));
                line.setLength(0);
                line.append(word);
            }
        }
        if (line.length() > 0) out.add(VBVUi.fit(font, line.toString(), maxWidth));
        return out;
    }

    private static String muteLabel() {
        return VBVVoice.isMuted() ? "UNMUTE" : "MUTE";
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
        int voiceVersion = VBVVoice.version();
        if (version != shownVersion || voiceVersion != shownVoiceVersion) {
            shownVersion = version;
            shownVoiceVersion = voiceVersion;
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

        speaking.clear();
        if (VBVFriends.signedIn() && VBVVoice.inCall()) {
            for (String row : VBVVoice.memberRows()) {
                String[] f = row.split("\\t", -1);
                if (f.length > 3) speaking.put(f[0], "1".equals(f[2]));
            }
        }
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
                    boolean voiceOn = entry.voice() > 0;
                    String where = voiceOn
                        ? "in a voice chat (" + entry.voice() + ")"
                        : entry.online() ? (entry.server().isEmpty() ? "online" : entry.server()) : "offline";
                    int room = listWidth - 20 - nameW - 58 - 20 - (voiceOn && !VBVVoice.inCall() ? 50 : 0);
                    if (room > 30) {
                        graphics.text(font, VBVUi.fit(font, where, room), listLeft + 20 + nameW + 10, textY,
                            voiceOn || entry.online() ? GREEN : GREY, false);
                    }
                }
                case VOICE_HEAD -> graphics.text(font, entry.name(), listLeft + 8, textY, 0xFFFFFFFF, true);
                case VOICE_MEMBER -> {
                    boolean talking = Boolean.TRUE.equals(speaking.get(entry.id()));
                    graphics.fill(listLeft + 12, textY + 1, listLeft + 18, textY + 7, talking ? GREEN : GREY);
                    graphics.text(font, entry.name(), listLeft + 24, textY, 0xFFFFFFFF, true);
                    String state = switch (entry.server()) {
                        case "you" -> VBVVoice.isMuted() ? "you (muted)" : "you";
                        case "connecting" -> "connecting...";
                        case "waiting" -> "starting their microphone...";
                        default -> "";
                    };
                    if (!state.isEmpty()) {
                        graphics.text(font, state, listLeft + 24 + font.width(entry.name()) + 8, textY, VBVUi.TEXT_DIM, false);
                    }
                }
                case VOICE_NOTE -> graphics.text(font, entry.name(), listLeft + 8, textY, VBVUi.TEXT_DIM, false);
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
        if (VBVFriends.friendRows().length == 0) {
            int friendsHeader = -1;
            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i).kind() == Kind.HEADER && entries.get(i).name().startsWith("FRIENDS")) friendsHeader = i;
            }
            int shownAt = friendsHeader - scroll;
            // Only when nothing else (sent requests) follows the header: the text sits on the row below it.
            if (friendsHeader >= 0 && friendsHeader == entries.size() - 1 && shownAt >= -1 && shownAt + 1 < visibleRows) {
                graphics.text(font, "No friends yet. Type a name above and press ADD.", listLeft + 8,
                    listTop + (shownAt + 1) * ROW_H + 4, VBVUi.TEXT_DIM, false);
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
}
