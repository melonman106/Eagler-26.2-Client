package com.melonman106.vbvclient;

import java.util.ArrayList;
import java.util.List;

import org.teavm.jso.JSBody;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Notification centre shown in the top-right corner of the main menu.
 *
 * <p>The bell is only added to {@link VBVDungeonsTitleScreen}, so it never covers the in-game HUD or
 * another menu. The release check starts the first time the main menu is actually reached (after the
 * splash screen) and asks the public GitHub Releases API for
 * {@code melonman106/Eagler-26.2-Client}; the fetch runs in the browser and Java only polls the
 * result once per screen tick, so the render thread is never blocked.</p>
 *
 * <p>Entries are kept for the session: a newer release, a saved screenshot, and any Modrinth update
 * the browser finds all land here. The tag of a release that was already announced is written to the
 * settings store, so the same release is not announced on every launch. Each row has a small X at its
 * right edge that dismisses that notification.</p>
 */
public final class VBVNotifications {
    public static final String REPO = "melonman106/Eagler-26.2-Client";
    public static final String RELEASES_URL = "https://github.com/" + REPO + "/releases";

    /** One row in the centre. {@code link} may be empty. */
    public static final class Entry {
        final String title;
        final String detail;
        final String link;
        final long at;
        boolean read;

        Entry(String title, String detail, String link, long at) {
            this.title = title;
            this.detail = detail;
            this.link = link;
            this.at = at;
        }
    }

    private static final int MAX_ENTRIES = 24;
    private static final List<Entry> ENTRIES = new ArrayList<>();

    /** Last GitHub release tag the player was told about, persisted by {@link VBVConfigStore}. */
    static String seenTag = "";
    private static boolean checkedThisSession;
    private static boolean applied;
    private static boolean panelOpen;

    private static int panelX;
    private static int panelY;
    private static int panelW;
    private static int panelH;
    private static int footerY;
    private static final int[] rowY = new int[5];
    private static final Entry[] rowRef = new Entry[5];
    private static int rowCount;

    private VBVNotifications() {}

    // ---------------------------------------------------------------- entries

    /** Adds a notification and moves it to the top of the list. */
    public static void push(String title, String detail, String link) {
        String safeTitle = clean(title, "Notification");
        String safeDetail = clean(detail, "");
        String safeLink = link == null ? "" : link.trim();
        synchronized (ENTRIES) {
            ENTRIES.add(0, new Entry(safeTitle, safeDetail, safeLink, System.currentTimeMillis()));
            while (ENTRIES.size() > MAX_ENTRIES) ENTRIES.remove(ENTRIES.size() - 1);
        }
    }

    public static int unreadCount() {
        int count = 0;
        synchronized (ENTRIES) {
            for (Entry entry : ENTRIES) if (!entry.read) count++;
        }
        return count;
    }

    public static int size() {
        synchronized (ENTRIES) {
            return ENTRIES.size();
        }
    }

    private static void markAllRead() {
        synchronized (ENTRIES) {
            for (Entry entry : ENTRIES) entry.read = true;
        }
    }

    /** Removes one notification, by identity, when its X is clicked. */
    private static void dismiss(Entry target) {
        if (target == null) return;
        synchronized (ENTRIES) {
            for (int i = 0; i < ENTRIES.size(); i++) {
                if (ENTRIES.get(i) == target) {
                    ENTRIES.remove(i);
                    return;
                }
            }
        }
    }

    private static String clean(String value, String fallback) {
        if (value == null) return fallback;
        String text = value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim();
        return text.isEmpty() ? fallback : text;
    }

    // ------------------------------------------------------------- main menu

    /** Called once when the real main menu is built, not when the splash screen is showing. */
    public static void onMainMenuShown() {
        if (checkedThisSession) return;
        checkedThisSession = true;
        initialize();
        check();
    }

    /** Polls the bridge; safe to call every screen tick. */
    public static void tick() {
        if (!checkedThisSession || applied || checkBusy()) return;
        if (!checkDone()) return;
        applied = true;
        String tag = latestTag();
        if (tag.isEmpty() || tag.equals(seenTag) || !isNewer(tag, VBVClient.VERSION)) return;
        String name = latestName();
        String detail = "GitHub release " + tag + (name.isEmpty() || name.equals(tag) ? "" : " (" + name + ")")
            + " is newer than this build (v" + VBVClient.VERSION + ").";
        push("Client update available", detail, latestUrl().isEmpty() ? RELEASES_URL : latestUrl());
        seenTag = tag;
        VBVConfigStore.saveAll();
    }

    /** True when the numeric part of {@code latest} is greater than {@code current}. */
    static boolean isNewer(String latest, String current) {
        int[] a = versionParts(latest);
        int[] b = versionParts(current);
        // A tag that carries no number at all cannot be compared, so it is not announced.
        if (a.length == 0) return false;
        if (b.length == 0) return true;
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int left = i < a.length ? a[i] : 0;
            int right = i < b.length ? b[i] : 0;
            if (left != right) return left > right;
        }
        return false;
    }

    private static String stripV(String value) {
        String text = value == null ? "" : value.trim();
        if (!text.isEmpty() && (text.charAt(0) == 'v' || text.charAt(0) == 'V')) text = text.substring(1);
        return text;
    }

    private static int[] versionParts(String value) {
        // Release tags in this repository are sometimes prefixed with the game version, such as
        // "26.2-2.0.1"; the client version is the part after that prefix.
        String text = stripV(value).trim();
        if (text.startsWith("26.2")) {
            text = text.substring(4);
            while (!text.isEmpty() && (text.charAt(0) == '.' || text.charAt(0) == '-' || text.charAt(0) == '_')) {
                text = text.substring(1);
            }
        }
        List<Integer> parts = new ArrayList<>();
        StringBuilder number = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '0' && c <= '9') {
                number.append(c);
            } else if (number.length() > 0) {
                parts.add(parse(number));
                number.setLength(0);
            }
        }
        if (number.length() > 0) parts.add(parse(number));
        int[] out = new int[parts.size()];
        for (int i = 0; i < out.length; i++) out[i] = parts.get(i);
        return out;
    }

    private static int parse(StringBuilder number) {
        try {
            return Integer.parseInt(number.toString());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    // ---------------------------------------------------------------- drawing

    /** The bell widget for {@link VBVDungeonsTitleScreen}; add it with {@code addRenderableWidget}. */
    public static Button createButton(Screen owner, int x, int y) {
        return new BellButton(x, y);
    }

    private static final class BellButton extends Button {
        BellButton(int x, int y) {
            super(x, y, 20, 20, Component.literal("Notifications"), button -> togglePanel(), DEFAULT_NARRATION);
            setTooltip(Tooltip.create(Component.literal("Notifications")));
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            int x = getX();
            int y = getY();
            boolean lit = isHoveredOrFocused() || panelOpen;
            int unread = unreadCount();
            graphics.fill(x, y, x + 20, y + 20, lit ? VBVTheme.accentAlpha(0x50) : VBVTheme.panel(0xD0));
            graphics.outline(x, y, 20, 20, lit ? VBVTheme.accentLight() : VBVTheme.accentAlpha(0x90));

            int color = lit ? 0xFFFFFFFF : VBVUi.TEXT;
            // A small filled bell: handle, dome, rim and clapper.
            graphics.fill(x + 9, y + 4, x + 11, y + 6, color);
            for (int row = 0; row < 7; row++) {
                int half = 2 + row / 3;
                graphics.fill(x + 10 - half, y + 6 + row, x + 10 + half, y + 7 + row, color);
            }
            graphics.fill(x + 4, y + 13, x + 16, y + 14, color);
            graphics.fill(x + 9, y + 15, x + 11, y + 16, color);

            if (unread > 0) {
                int bx = x + 12;
                int by = y + 1;
                graphics.fill(bx, by, bx + 7, by + 7, 0xFFE05A4E);
                String count = unread > 9 ? "!" : Integer.toString(unread);
                Font font = net.minecraft.client.Minecraft.getInstance().font;
                graphics.text(font, count, bx + (7 - font.width(count)) / 2, by, 0xFFFFFFFF, false);
            }
        }
    }

    private static void togglePanel() {
        panelOpen = !panelOpen;
        if (panelOpen) markAllRead();
    }

    /** Draws the open panel. Call after {@code super.extractRenderState} so it sits above the menu. */
    public static void renderPanel(GuiGraphicsExtractor graphics) {
        rowCount = 0;
        if (!panelOpen) return;
        Font font = net.minecraft.client.Minecraft.getInstance().font;
        int screenW = graphics.guiWidth();
        int screenH = graphics.guiHeight();
        panelW = Math.min(252, Math.max(180, screenW - 12));
        List<Entry> entries;
        synchronized (ENTRIES) {
            entries = new ArrayList<>(ENTRIES);
        }
        int maxVisible = Math.max(1, Math.min(5, (screenH - 90) / 34));
        int shown = Math.min(maxVisible, entries.size());
        panelH = 30 + shown * 34 + 26;
        panelX = Math.max(6, screenW - panelW - 6);
        panelY = 30;
        if (panelY + panelH > screenH - 6) panelY = Math.max(6, screenH - panelH - 6);

        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, VBVTheme.panel(0xF2));
        graphics.fill(panelX, panelY, panelX + panelW, panelY + 1, VBVTheme.accent());
        graphics.outline(panelX, panelY, panelW, panelH, VBVTheme.accentAlpha(0xB0));
        graphics.text(font, "NOTIFICATIONS", panelX + 8, panelY + 7, VBVTheme.accentLight(), true);
        String count = entries.isEmpty() ? "0" : Integer.toString(entries.size());
        graphics.text(font, count, panelX + panelW - 8 - font.width(count), panelY + 7, VBVUi.TEXT_DIM, true);

        if (entries.isEmpty()) {
            graphics.text(font, "No notifications yet.", panelX + 8, panelY + 36, VBVUi.TEXT_DIM, false);
            graphics.text(font, "Updates are checked on the main menu.", panelX + 8, panelY + 48, VBVUi.TEXT_DISABLED, false);
        } else {
            for (int i = 0; i < shown; i++) {
                Entry entry = entries.get(i);
                int y = panelY + 26 + i * 34;
                rowY[rowCount] = y;
                rowRef[rowCount] = entry;
                rowCount++;
                if (i > 0) graphics.fill(panelX + 8, y - 2, panelX + panelW - 8, y - 1, VBVTheme.accentAlpha(0x30));
                graphics.fill(panelX + 8, y + 3, panelX + 11, y + 6, entry.read ? VBVTheme.accentAlpha(0x60) : VBVTheme.accentLight());
                graphics.text(font, VBVUi.fit(font, entry.title, panelW - 44), panelX + 15, y + 1,
                    entry.read ? VBVUi.TEXT : 0xFFFFFFFF, true);
                int detailWidth = panelW - (entry.link.isEmpty() ? 44 : 64);
                graphics.text(font, VBVUi.fit(font, entry.detail, detailWidth), panelX + 15, y + 13,
                    VBVUi.TEXT_DIM, false);
                String when = age(entry.at);
                graphics.text(font, when, panelX + panelW - 26 - font.width(when), y + 1, VBVUi.TEXT_DISABLED, false);
                if (!entry.link.isEmpty()) {
                    graphics.text(font, "open", panelX + panelW - 26 - font.width("open"), y + 13,
                        VBVTheme.accentAlpha(0xC0), false);
                }
                // Dismiss X in the right-hand margin of the row.
                int dx = panelX + panelW - 18;
                int dy = y + 9;
                int xColor = entry.read ? VBVUi.TEXT_DISABLED : VBVUi.TEXT_DIM;
                for (int step = 0; step < 8; step++) {
                    graphics.fill(dx + step, dy + step, dx + step + 1, dy + step + 1, xColor);
                    graphics.fill(dx + 7 - step, dy + step, dx + 8 - step, dy + step + 1, xColor);
                }
            }
        }

        footerY = panelY + panelH - 20;
        graphics.fill(panelX + 1, footerY - 3, panelX + panelW - 1, footerY - 2, VBVTheme.accentAlpha(0x40));
        String footer;
        if (checkBusy()) footer = "Checking GitHub releases...";
        else if (!checkError().isEmpty()) footer = "Check failed: " + checkError();
        else if (checkDone()) {
            String tag = latestTag();
            footer = tag.isEmpty() ? "No releases found" : (isNewer(tag, VBVClient.VERSION)
                ? "Update available: " + tag : "Up to date (v" + VBVClient.VERSION + ")");
        } else footer = "Checking GitHub releases...";
        graphics.text(font, VBVUi.fit(font, footer, panelW - 76), panelX + 8, footerY + 1, VBVUi.TEXT_DIM, false);
        String again = "CHECK";
        graphics.text(font, again, panelX + panelW - 8 - font.width(again), footerY + 1, VBVTheme.accentLight(), false);
    }

    /**
     * Handles a click while the panel is open. Returns true when the click was consumed; a click
     * outside the panel closes it and is passed on to the menu underneath.
     */
    public static boolean clickPanel(double mouseX, double mouseY) {
        if (!panelOpen) return false;
        // The bell is a widget underneath this overlay. Consume the click that closes the panel so
        // the widget does not immediately reopen it.
        if (mouseY >= 4 && mouseY <= 24 && mouseX >= panelX + panelW - 20) {
            panelOpen = false;
            return true;
        }
        if (mouseX < panelX || mouseX > panelX + panelW || mouseY < panelY || mouseY > panelY + panelH) {
            // Dismiss the overlay without also activating whatever menu row is underneath it.
            panelOpen = false;
            return true;
        }
        if (mouseY >= footerY - 2) {
            if (mouseX >= panelX + panelW - 52) {
                applied = false;
                check();
            } else {
                openUrl(RELEASES_URL);
            }
            return true;
        }
        for (int i = 0; i < rowCount; i++) {
            int top = rowY[i];
            if (mouseY < top || mouseY >= top + 32) continue;
            Entry entry = rowRef[i];
            if (entry == null) continue;
            // The right-hand margin of the row is the dismiss button, not the link.
            if (mouseX >= panelX + panelW - 22) {
                dismiss(entry);
                return true;
            }
            entry.read = true;
            if (!entry.link.isEmpty()) openUrl(entry.link);
            return true;
        }
        return true;
    }

    public static boolean isPanelOpen() {
        return panelOpen;
    }

    public static void closePanel() {
        panelOpen = false;
    }

    private static String age(long at) {
        long seconds = Math.max(0L, (System.currentTimeMillis() - at) / 1000L);
        if (seconds < 45L) return "now";
        long minutes = seconds / 60L;
        if (minutes < 60L) return minutes + "m";
        long hours = minutes / 60L;
        if (hours < 24L) return hours + "h";
        return (hours / 24L) + "d";
    }

    // ----------------------------------------------------------------- bridge

    @JSBody(script = """
        (function () {
          if (window.VBVNotifyBridge) return;
          var state = { busy: false, done: false, tag: "", name: "", url: "", published: "", error: "" };
          async function checkReleases() {
            if (state.busy) return;
            state.busy = true; state.done = false; state.error = "";
            try {
              var response = await fetch(
                "https://api.github.com/repos/melonman106/Eagler-26.2-Client/releases?per_page=10",
                { headers: { "Accept": "application/vnd.github+json" }, cache: "no-store" });
              if (!response.ok) throw new Error("GitHub HTTP " + response.status);
              var list = await response.json();
              if (!Array.isArray(list) || !list.length) throw new Error("no published releases");
              var pick = list.filter(function (r) { return r && !r.draft && !r.prerelease; })[0];
              if (!pick) pick = list.filter(function (r) { return r && !r.draft; })[0];
              if (!pick) throw new Error("no published releases");
              state.tag = String(pick.tag_name || "");
              state.name = String(pick.name || pick.tag_name || "");
              state.url = String(pick.html_url || "");
              state.published = String(pick.published_at || "");
            } catch (e) {
              state.error = String(e && e.message || e);
            } finally {
              state.busy = false; state.done = true;
            }
          }
          window.VBVNotifyBridge = { state: state, check: checkReleases };
        })();
        """)
    private static native void initializeJS();

    private static void initialize() {
        try {
            initializeJS();
        } catch (RuntimeException ignored) {
        }
    }

    private static void check() {
        try {
            checkJS();
        } catch (RuntimeException ignored) {
        }
    }

    @JSBody(script = "try { if (window.VBVNotifyBridge) window.VBVNotifyBridge.check(); } catch (e) { console.error(e); }")
    private static native void checkJS();

    @JSBody(params = {"key"}, script = "try { return window.VBVNotifyBridge ? !!window.VBVNotifyBridge.state[key] : false; } catch (e) { return false; }")
    private static native boolean flagJS(String key);

    @JSBody(params = {"key"}, script = "try { return window.VBVNotifyBridge ? String(window.VBVNotifyBridge.state[key] || '') : ''; } catch (e) { return ''; }")
    private static native String textJS(String key);

    @JSBody(params = {"url"}, script = "try { if (!url) return; var el = document.createElement('a'); el.href = url; el.target = '_blank'; el.rel = 'noopener'; el.style.display = 'none'; document.body.appendChild(el); el.click(); document.body.removeChild(el); } catch (e) { console.error(e); }")
    private static native void openUrlJS(String url);

    private static boolean checkBusy() {
        try { return flagJS("busy"); } catch (RuntimeException ignored) { return false; }
    }

    private static boolean checkDone() {
        try { return flagJS("done"); } catch (RuntimeException ignored) { return false; }
    }

    private static String latestTag() {
        try { return textJS("tag"); } catch (RuntimeException ignored) { return ""; }
    }

    private static String latestName() {
        try { return textJS("name"); } catch (RuntimeException ignored) { return ""; }
    }

    private static String latestUrl() {
        try { return textJS("url"); } catch (RuntimeException ignored) { return ""; }
    }

    private static String checkError() {
        try { return textJS("error"); } catch (RuntimeException ignored) { return ""; }
    }

    private static void openUrl(String url) {
        try {
            openUrlJS(url);
        } catch (RuntimeException ignored) {
        }
    }
}
