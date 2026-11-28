package com.melonman106.vbvclient;

import com.melonman106.vbvclient.mods.ModAssets;
import com.melonman106.vbvclient.mods.ModBootstrap;
import com.melonman106.vbvclient.mods.ModEvents;
import com.melonman106.vbvclient.mods.ModPackage;
import com.melonman106.vbvclient.mods.ModPlatform;
import com.melonman106.vbvclient.mods.ModStore;
import com.melonman106.vbvclient.mods.ModView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.lax1dude.eaglercraft.v1_8.EagRuntime;
import net.lax1dude.eaglercraft.v1_8.internal.FileChooserResult;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Import, turn on/off and remove mods that were imported as .zip files.
 *
 * <p>Changes are written to browser storage straight away, but the game registers mods once while it starts, so every
 * change shows a "reload the page" prompt. What the screen says about a mod comes from {@link ModView}, which is
 * covered by plain-JVM tests; this class only does layout, colours and input.</p>
 */
public final class VBVImportedModsScreen extends Screen {
    private static final int ROW_HEIGHT = 28;
    private static final int LIST_TOP = 46;
    private static final int BADGE = 20;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int DIM = 0xFFAAAAAA;
    private static final int GOOD = 0xFF7FD67F;
    private static final int WARN = 0xFFFFD166;
    private static final int ERROR = 0xFFFF7B7B;

    private final Screen parent;
    private ModStore store;
    private List<ModStore.Installed> installed = new ArrayList<>();
    private ModStore.Plan plan;
    private final Map<String, Long> loaded = new HashMap<>();
    private boolean restartNeeded;
    private String selectedId;
    private int listScroll;
    private int detailScroll;
    private final List<ModView.Line> notice = new ArrayList<>();
    private final List<String> detailText = new ArrayList<>();
    private final List<Integer> detailColor = new ArrayList<>();
    private boolean waitingForFile;
    private boolean readingNoticeShown;
    private String confirmRemoveId;
    private String confirmAllowId;
    private boolean confirmReload;
    private int leftWidth;
    private int rightX;

    private Button importButton;
    private Button toggleButton;
    private Button removeButton;
    private Button commandsButton;
    private Button safeButton;
    private Button reloadButton;

    public VBVImportedModsScreen(Screen parent) {
        super(Component.literal("Imported Mods"));
        this.parent = parent;
    }

    // ---- data ----

    private void refresh() {
        store = ModPlatform.store();
        loaded.clear();
        for (ModPackage pkg : ModBootstrap.loaded()) loaded.put(pkg.manifest.id, pkg.crc);
        if (store == null) {
            installed = new ArrayList<>();
            plan = null;
            restartNeeded = false;
        } else {
            installed = store.installed();
            plan = store.plan();
            restartNeeded = store.restartNeeded(plan);
        }
        if (selectedId == null || find(selectedId) == null) {
            selectedId = installed.isEmpty() ? null : installed.get(0).id();
        }
        detailScroll = 0;
        listScroll = Math.max(0, Math.min(listScroll, maxListScroll()));
        rebuildDetails();
        updateButtons();
    }

    private ModStore.Installed find(String id) {
        for (ModStore.Installed mod : installed) if (mod.id().equals(id)) return mod;
        return null;
    }

    private ModStore.Installed selected() {
        return selectedId == null ? null : find(selectedId);
    }

    private void setNotice(List<ModView.Line> lines) {
        notice.clear();
        notice.addAll(lines);
        detailScroll = 0;
        rebuildDetails();
    }

    private void setNotice(String text, ModView.Tone tone) {
        List<ModView.Line> lines = new ArrayList<>();
        lines.add(new ModView.Line(text, tone));
        setNotice(lines);
    }

    private static int color(ModView.Tone tone) {
        switch (tone) {
            case GOOD: return GOOD;
            case WARN: return WARN;
            case ERROR: return ERROR;
            case HEADING: return WHITE;
            default: return DIM;
        }
    }

    private static int statusColor(ModView.Status status) {
        switch (status) {
            case LOADED: return GOOD;
            case WILL_LOAD:
            case WILL_UPDATE:
            case WILL_UNLOAD:
            case SAFE_MODE: return WARN;
            case DISABLED: return DIM;
            default: return ERROR;
        }
    }

    private void rebuildDetails() {
        detailText.clear();
        detailColor.clear();
        if (font == null) return;
        int wrapWidth = Math.max(60, width - rightX - 16);
        List<ModView.Line> lines = new ArrayList<>(notice);
        ModStore.Installed mod = selected();
        if (store == null) {
            lines.add(new ModView.Line("Mod storage is not available in this browser session, so mods cannot be imported or loaded.", ModView.Tone.ERROR));
        } else if (mod != null && plan != null) {
            lines.addAll(ModView.details(mod, plan, loaded,
                mod.pkg() != null && store.commandsAllowed(mod.pkg()), ModBootstrap.commandsApplied(mod.id())));
            ModBootstrap.Report report = ModBootstrap.reports().get(mod.id());
            if (report != null && !report.failures.isEmpty()) {
                lines.add(new ModView.Line("Problems while loading", ModView.Tone.HEADING));
                for (String failure : report.failures) lines.add(new ModView.Line(failure, ModView.Tone.ERROR));
            }
        } else if (notice.isEmpty()) {
            lines.add(new ModView.Line("Choose Import .zip to add a mod. Each mod is one .zip with an info.json (or a fabric.mod.json), items.json and/or content.json, and an assets folder.", ModView.Tone.NORMAL));
        }
        List<String> startup = ModBootstrap.messages();
        if (!startup.isEmpty()) {
            lines.add(new ModView.Line("Start-up", ModView.Tone.HEADING));
            for (String message : startup) lines.add(new ModView.Line(message, ModView.Tone.WARN));
        }
        boolean first = true;
        for (ModView.Line line : lines) {
            if (line.tone() == ModView.Tone.HEADING && !first) {
                detailText.add("");
                detailColor.add(DIM);
            }
            first = false;
            for (String part : wrap(line.text(), wrapWidth)) {
                detailText.add(part);
                detailColor.add(color(line.tone()));
            }
        }
    }

    private List<String> wrap(String text, int maxWidth) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        StringBuilder current = new StringBuilder();
        for (String word : text.replace("\n", " ").split(" ")) {
            if (word.isEmpty()) continue;
            String candidate = current.length() == 0 ? word : current + " " + word;
            if (font.width(candidate) <= maxWidth) {
                current.setLength(0);
                current.append(candidate);
                continue;
            }
            if (current.length() > 0) {
                out.add(current.toString());
                current.setLength(0);
            }
            String rest = word;
            while (font.width(rest) > maxWidth && rest.length() > 1) {
                int cut = rest.length() - 1;
                while (cut > 1 && font.width(rest.substring(0, cut)) > maxWidth) cut--;
                out.add(rest.substring(0, cut));
                rest = rest.substring(cut);
            }
            current.append(rest);
        }
        if (current.length() > 0) out.add(current.toString());
        return out;
    }

    // ---- layout ----

    private int listBottom() { return height - 36; }
    private int visibleRows() { return Math.max(1, (listBottom() - LIST_TOP - 4) / ROW_HEIGHT); }
    private int maxListScroll() { return Math.max(0, installed.size() - visibleRows()); }
    private int rowWidth() { return leftWidth - (installed.size() > visibleRows() ? 20 : 14); }
    private int visibleDetailLines() { return Math.max(1, (listBottom() - LIST_TOP - 8) / (font.lineHeight + 1)); }
    private int maxDetailScroll() { return Math.max(0, detailText.size() - visibleDetailLines()); }

    @Override
    protected void init() {
        super.init();
        leftWidth = Math.max(150, width * 2 / 5);
        rightX = leftWidth + 8;

        int gap = 4;
        // Six buttons must share the width; without a fixed floor the row never runs off a narrow GUI.
        int buttonWidth = Math.max(42, Math.min(112, (width - 8 - gap * 5) / 6));
        int x = Math.max(4, (width - (buttonWidth * 6 + gap * 5)) / 2);
        int y = height - 28;
        importButton = addRenderableWidget(Button.builder(Component.literal("Import .zip"), button -> startImport())
            .bounds(x, y, buttonWidth, 20).build());
        toggleButton = addRenderableWidget(Button.builder(Component.literal("Turn off"), button -> toggleSelected())
            .bounds(x + (buttonWidth + gap), y, buttonWidth, 20).build());
        removeButton = addRenderableWidget(Button.builder(Component.literal("Remove"), button -> removeSelected())
            .bounds(x + (buttonWidth + gap) * 2, y, buttonWidth, 20).build());
        commandsButton = addRenderableWidget(Button.builder(Component.literal("Allow cmds"), button -> toggleCommands())
            .bounds(x + (buttonWidth + gap) * 3, y, buttonWidth, 20).build());
        safeButton = addRenderableWidget(Button.builder(Component.literal("Safe mode"), button -> toggleSafeMode())
            .bounds(x + (buttonWidth + gap) * 4, y, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), button -> VBVNav.open(parent))
            .bounds(x + (buttonWidth + gap) * 5, y, buttonWidth, 20).build());
        reloadButton = addRenderableWidget(Button.builder(Component.literal("Reload page"), button -> reload())
            .bounds(width - 112, 4, 106, 20).build());
        refresh();
    }

    private void updateButtons() {
        if (importButton == null) return;
        ModStore.Installed mod = selected();
        importButton.active = store != null;
        toggleButton.active = store != null && mod != null && mod.pkg() != null;
        toggleButton.setMessage(Component.literal(mod != null && !ModView.canTurnOff(mod) && mod.pkg() != null ? "Turn on" : "Turn off"));
        removeButton.active = store != null && mod != null;
        removeButton.setMessage(Component.literal(mod != null && mod.id().equals(confirmRemoveId) ? "Confirm?" : "Remove"));
        boolean hasCommands = store != null && mod != null && mod.pkg() != null && ModEvents.commandCount(mod.pkg().events) > 0;
        commandsButton.active = hasCommands;
        commandsButton.setMessage(Component.literal(!hasCommands ? "Allow cmds"
            : store.commandsAllowed(mod.pkg()) ? "Block cmds" : mod.id().equals(confirmAllowId) ? "Confirm?" : "Allow cmds"));
        boolean safe = store != null && store.safeMode();
        safeButton.active = store != null;
        safeButton.setMessage(Component.literal(safe ? "Safe: ON" : "Safe: OFF"));
        reloadButton.visible = restartNeeded && ModBootstrap.sessionSafeReason() == null;
        reloadButton.setMessage(Component.literal(confirmReload ? "Sure? Unsaved play is lost" : "Reload page"));
        reloadButton.setWidth(confirmReload ? 150 : 106);
        reloadButton.setX(width - reloadButton.getWidth() - 6);
    }

    // ---- actions ----

    private void startImport() {
        confirmRemoveId = null;
        confirmAllowId = null;
        confirmReload = false;
        waitingForFile = true;
        readingNoticeShown = false;
        setNotice("Choose a mod .zip file...", ModView.Tone.NORMAL);
        EagRuntime.displayFileChooser(null, "zip");
        updateButtons();
    }

    private void importResult(FileChooserResult result) {
        if (result == null || result.fileData == null) {
            setNotice("Import cancelled.", ModView.Tone.NORMAL);
            return;
        }
        ModStore.InstallResult installedResult;
        try {
            installedResult = store.install(result.fileData);
        } catch (Throwable t) {
            setNotice("The file could not be imported: " + t, ModView.Tone.ERROR);
            return;
        }
        List<ModView.Line> lines = new ArrayList<>();
        if (installedResult.ok()) {
            lines.add(new ModView.Line(installedResult.replacedVersion() == null
                ? "Imported " + installedResult.id() + "."
                : "Imported " + installedResult.id() + " (replaced version " + installedResult.replacedVersion() + ").", ModView.Tone.GOOD));
            for (String warning : installedResult.warnings()) lines.add(new ModView.Line(warning, ModView.Tone.WARN));
        } else {
            lines.add(new ModView.Line("Not imported:", ModView.Tone.ERROR));
            for (String problem : installedResult.problems()) lines.add(new ModView.Line(problem, ModView.Tone.ERROR));
            for (String warning : installedResult.warnings()) lines.add(new ModView.Line(warning, ModView.Tone.WARN));
        }
        if (installedResult.ok() && installedResult.id() != null) selectedId = installedResult.id();
        refresh();
        setNotice(lines);
        listScroll = Math.max(0, Math.min(listScroll, maxListScroll()));
    }

    private void toggleSelected() {
        ModStore.Installed mod = selected();
        if (store == null || mod == null || mod.pkg() == null) return;
        confirmRemoveId = null;
        confirmAllowId = null;
        if (!store.setEnabled(mod.id(), !mod.enabled())) {
            setNotice("The change could not be saved (browser storage may be full or blocked).", ModView.Tone.ERROR);
            return;
        }
        refresh();
        notice.clear();
        rebuildDetails();
    }

    private void removeSelected() {
        ModStore.Installed mod = selected();
        if (store == null || mod == null) return;
        if (!mod.id().equals(confirmRemoveId)) {
            confirmRemoveId = mod.id();
            setNotice("Click Confirm? to delete " + mod.id() + ". Items it added that are in your worlds or inventories may disappear or fail to load, so keep a backup of worlds you care about.", ModView.Tone.WARN);
            updateButtons();
            return;
        }
        confirmRemoveId = null;
        confirmAllowId = null;
        store.remove(mod.id());
        selectedId = null;
        refresh();
        setNotice("Removed " + mod.id() + ".", ModView.Tone.GOOD);
    }

    /** Allow needs two clicks: the first lists exactly which commands would be allowed, the second confirms. Blocking is one click. */
    private void toggleCommands() {
        ModStore.Installed mod = selected();
        if (store == null || mod == null || mod.pkg() == null || ModEvents.commandCount(mod.pkg().events) == 0) return;
        confirmRemoveId = null;
        if (store.commandsAllowed(mod.pkg())) {
            confirmAllowId = null;
            if (!store.revokeCommands(mod.id())) {
                setNotice("The change could not be saved (browser storage may be full or blocked).", ModView.Tone.ERROR);
                return;
            }
            refresh();
            setNotice("Commands are blocked for " + mod.id() + " from the next time the game starts.", ModView.Tone.NORMAL);
            return;
        }
        if (!mod.id().equals(confirmAllowId)) {
            confirmAllowId = mod.id();
            setNotice(ModView.allowPrompt(mod.pkg()));
            updateButtons();
            return;
        }
        confirmAllowId = null;
        if (!store.allowCommands(mod.pkg())) {
            setNotice("The change could not be saved (browser storage may be full or blocked).", ModView.Tone.ERROR);
            return;
        }
        refresh();
        setNotice("Commands are allowed for " + mod.id() + " from the next time the game starts.", ModView.Tone.GOOD);
    }

    private void toggleSafeMode() {
        if (store == null) return;
        confirmRemoveId = null;
        confirmAllowId = null;
        boolean now = store.safeMode();
        store.setSafeMode(!now);
        refresh();
        setNotice(now ? "Safe mode is off. Mods load again the next time the game starts."
            : "Safe mode is on. No imported mod loads the next time the game starts, which is the way out if a mod stops the game from starting. To skip mods for a single session instead, open the page with ?nomods=1 (or ?safe).",
            ModView.Tone.NORMAL);
    }

    private void reload() {
        if (!confirmReload) {
            confirmReload = true;
            updateButtons();
            return;
        }
        ModPlatform.reloadPage();
    }

    @Override
    public void tick() {
        super.tick();
        if (!waitingForFile) return;
        if (EagRuntime.fileChooserHasResult()) {
            waitingForFile = false;
            FileChooserResult result = EagRuntime.getFileChooserResult();
            EagRuntime.clearFileChooserResult();
            importResult(result);
        } else if (EagRuntime.fileChooserIsReading() && !readingNoticeShown) {
            readingNoticeShown = true;
            setNotice("Reading the file...", ModView.Tone.NORMAL);
        }
    }

    // ---- input ----

    private void select(String id) {
        if (id.equals(selectedId)) return;
        selectedId = id;
        confirmRemoveId = null;
        confirmAllowId = null;
        notice.clear();
        detailScroll = 0;
        rebuildDetails();
        updateButtons();
    }

    private void moveSelection(int delta) {
        if (installed.isEmpty()) return;
        int index = 0;
        for (int i = 0; i < installed.size(); i++) if (installed.get(i).id().equals(selectedId)) index = i;
        index = Math.max(0, Math.min(installed.size() - 1, index + delta));
        select(installed.get(index).id());
        if (index < listScroll) listScroll = index;
        if (index >= listScroll + visibleRows()) listScroll = index - visibleRows() + 1;
        listScroll = Math.max(0, Math.min(listScroll, maxListScroll()));
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (input.isUp()) { moveSelection(-1); return true; }
        if (input.isDown()) { moveSelection(1); return true; }
        return super.keyPressed(input);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubleClick) {
        if (super.mouseClicked(click, doubleClick)) return true;
        double mx = click.x();
        double my = click.y();
        if (mx >= 4 && mx <= 6 + rowWidth() && my >= LIST_TOP && my < listBottom()) {
            for (int k = 0; k < visibleRows(); k++) {
                int index = listScroll + k;
                if (index >= installed.size()) break;
                int top = LIST_TOP + 4 + k * ROW_HEIGHT - 2;
                if (my >= top && my < top + ROW_HEIGHT) {
                    select(installed.get(index).id());
                    return true;
                }
            }
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int step = scrollY > 0 ? -1 : scrollY < 0 ? 1 : 0;
        if (step == 0) return false;
        if (mouseX < leftWidth) listScroll = Math.max(0, Math.min(maxListScroll(), listScroll + step));
        else detailScroll = Math.max(0, Math.min(maxDetailScroll(), detailScroll + step));
        return true;
    }

    @Override
    public void onClose() {
        VBVNav.open(parent);
    }

    // ---- drawing ----

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.text(font, "Imported Mods", leftWidth / 2 - font.width("Imported Mods") / 2, 8, WHITE, true);
        String summary = store == null || plan == null ? "Storage unavailable" : ModView.summary(installed, plan, loaded);
        graphics.text(font, summary, 8, 24, DIM, false);
        if (ModBootstrap.sessionSafeReason() != null) {
            graphics.text(font, "No mods this session. Open the page without ?safe / ?nomods=1 to load them.", 8, 35, WARN, false);
        } else if (restartNeeded) {
            graphics.text(font, "Reload the page to apply your changes.", 8, 35, WARN, false);
        }
        drawPanel(graphics, 0, leftWidth, LIST_TOP, listBottom());
        drawList(graphics);
        drawPanel(graphics, rightX - 4, width, LIST_TOP, listBottom());
        drawDetails(graphics);
    }

    private void drawPanel(GuiGraphicsExtractor graphics, int x1, int x2, int top, int bottom) {
        // The same vanilla list panel as the Mods screen, so the two screens look like one menu.
        VBVUi.panel(graphics, x1, top, x2 - x1, bottom - top);
    }

    private void drawList(GuiGraphicsExtractor graphics) {
        if (installed.isEmpty()) {
            graphics.text(font, store == null ? "No storage" : "No mods imported yet", 10, LIST_TOP + 8, DIM, false);
            return;
        }
        int rowWidth = rowWidth();
        for (int k = 0; k < visibleRows(); k++) {
            int index = listScroll + k;
            if (index >= installed.size()) break;
            ModStore.Installed mod = installed.get(index);
            int y = LIST_TOP + 4 + k * ROW_HEIGHT;
            if (mod.id().equals(selectedId)) {
                graphics.fill(4, y - 2, 6 + rowWidth + 2, y + ROW_HEIGHT - 4, 0xFFFFFFFF);
                graphics.fill(5, y - 1, 6 + rowWidth + 1, y + ROW_HEIGHT - 5, 0xFF000000);
            }
            drawBadge(graphics, mod, 8, y + 1);
            int textX = 8 + BADGE + 4;
            int maxText = rowWidth - BADGE - 6;
            String name = mod.pkg() != null ? mod.pkg().manifest.name : mod.id();
            graphics.text(font, ellipsize(name, maxText), textX, y, WHITE, true);
            ModView.Status status = plan == null ? ModView.Status.INVALID : ModView.status(mod, plan, loaded);
            graphics.text(font, ellipsize(status.label, maxText), textX, y + font.lineHeight + 1, statusColor(status), false);
        }
        drawScrollbar(graphics, leftWidth - 6, LIST_TOP, listBottom(), installed.size(), visibleRows(), listScroll);
    }

    /** The mod's own icon once its pack is loaded, a coloured letter before that (and whenever the icon cannot be drawn). */
    private void drawBadge(GuiGraphicsExtractor graphics, ModStore.Installed mod, int x, int y) {
        int h = mod.id().hashCode();
        int r = 60 + (h & 0x9F);
        int g = 60 + ((h >> 8) & 0x9F);
        int b = 60 + ((h >> 16) & 0x9F);
        graphics.fill(x, y, x + BADGE, y + BADGE, 0xFF000000 | (r << 16) | (g << 8) | b);
        String name = mod.pkg() != null ? mod.pkg().manifest.name : mod.id();
        String letter = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT);
        graphics.text(font, letter, x + (BADGE - font.width(letter)) / 2, y + (BADGE - font.lineHeight) / 2 + 1, WHITE, true);
        int[] size = ModBootstrap.iconSize(mod.id());
        if (size == null) return;
        try {
            graphics.blit(RenderPipelines.GUI_TEXTURED, Identifier.parse(ModAssets.iconLocation(mod.id())), x, y, 0, 0, BADGE, BADGE, size[0], size[1], size[0], size[1]);
        } catch (RuntimeException ignored) {
            // the letter stays visible
        }
    }

    private void drawDetails(GuiGraphicsExtractor graphics) {
        int lineHeight = font.lineHeight + 1;
        int visible = visibleDetailLines();
        for (int k = 0; k < visible; k++) {
            int index = detailScroll + k;
            if (index >= detailText.size()) break;
            graphics.text(font, detailText.get(index), rightX, LIST_TOP + 5 + k * lineHeight, detailColor.get(index), false);
        }
        drawScrollbar(graphics, width - 6, LIST_TOP, listBottom(), detailText.size(), visible, detailScroll);
    }

    private String ellipsize(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        String body = text;
        while (!body.isEmpty() && font.width(body + "...") > maxWidth) body = body.substring(0, body.length() - 1);
        return body + "...";
    }

    private void drawScrollbar(GuiGraphicsExtractor graphics, int x, int top, int bottom, int total, int visible, int scroll) {
        if (total <= visible) return;
        int track = bottom - top;
        int thumb = Math.max(24, track * visible / total);
        int range = total - visible;
        int thumbY = top + (track - thumb) * scroll / range;
        graphics.fill(x, top, x + 6, bottom, 0xFF000000);
        graphics.fill(x, thumbY, x + 6, thumbY + thumb, 0xFF808080);
        graphics.fill(x, thumbY, x + 5, thumbY + thumb - 1, 0xFFC0C0C0);
    }
}
