package com.melonman106.vbvclient;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Everything the client can say about itself without dev tools: the frame breakdown, the custom
 * model and layer counts, the LOD mesh and the heap state, wrapped to fit the window.
 *
 * <p>It is the same text the Copy button puts on the clipboard, so a screenshot and a pasted report
 * say the same thing.</p>
 */
public final class VBVDiagnosticsScreen extends Screen {
    private final Screen parent;
    private final List<String> lines = new ArrayList<>();
    private String text = "";

    public VBVDiagnosticsScreen(Screen parent) {
        super(Component.literal("Diagnostics"));
        this.parent = parent;
    }

    @Override protected void init() {
        super.init();
        // report() already carries the CEM and LOD diagnostics; only the governor detail is extra.
        text = VBVPerf.report() + VBVAdaptive.detail() + "\n";
        lines.clear();
        for (String raw : text.split("\n")) {
            wrap(raw).forEach(lines::add);
        }
        addRenderableWidget(Button.builder(Component.literal("Copy"), button -> {
            VBVPerf.copyReport();
            button.setMessage(Component.literal("Copied!"));
        }).bounds(width / 2 - 105, height - 30, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> VBVNav.open(parent))
            .bounds(width / 2 + 5, height - 30, 100, 20).build());
    }

    private List<String> wrap(String raw) {
        List<String> out = new ArrayList<>();
        int max = Math.max(120, width - 20);
        StringBuilder line = new StringBuilder();
        for (String word : raw.split(" ")) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (font.width(candidate) > max && line.length() > 0) {
                out.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (line.length() > 0) {
            out.add(line.toString());
        }
        return out;
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xFF151515);
        String heading = title.getString();
        graphics.text(font, heading, width / 2 - font.width(heading) / 2, 10, VBVTheme.accentLight(), true);
        int lineH = font.lineHeight + 1;
        int y = 26;
        int maxY = height - 38;
        int shown = 0;
        for (String line : lines) {
            if (y + lineH > maxY) {
                break;
            }
            graphics.text(font, line, 10, y, line.startsWith("Memory") ? 0xFFFFB070 : 0xFFDDDDDD, true);
            y += lineH;
            shown++;
        }
        if (shown < lines.size()) {
            graphics.text(font, "(" + (lines.size() - shown) + " more lines - use Copy)", 10, maxY, 0xFF8A8A8A, true);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }
}
