package com.melonman106.vbvclient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

import com.melonman106.vbvclient.litematica.LitematicNbtReader;
import com.melonman106.vbvclient.litematica.LitematicNbtReader.Region;
import com.melonman106.vbvclient.litematica.LitematicNbtReader.Schematic;
import net.lax1dude.eaglercraft.v1_8.EagRuntime;
import net.lax1dude.eaglercraft.v1_8.internal.FileChooserResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Native Eaglercraft implementation of the first Litematica-compatible
 * workflow: import a .litematic file in the browser and inspect its regions,
 * dimensions, block counts and palette. This intentionally does not pretend
 * to place blocks or render a ghost schematic yet.
 */
public final class LitematicaPortScreen extends Screen {
    private final Screen parent;
    private Schematic schematic;
    private String fileName = "No schematic loaded";
    private String status = "Import a .litematic schematic to begin.";
    private int scroll;

    public LitematicaPortScreen(Screen parent) {
        super(Component.literal("Litematica (Eagler Port)"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.literal("Import .litematic"), button -> {
            EagRuntime.displayFileChooser(null, "litematic");
            status = "Waiting for file selection…";
        }).bounds(width / 2 - 102, height - 32, 130, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), button -> {
            EagRuntime.clearFileChooserResult();
            minecraft.gui.setScreen(parent);
        }).bounds(width / 2 + 34, height - 32, 68, 20).build());
    }

    @Override
    public void tick() {
        if (!EagRuntime.fileChooserHasResult()) return;
        FileChooserResult result = EagRuntime.getFileChooserResult();
        EagRuntime.clearFileChooserResult();
        if (result == null || result.fileData == null) {
            status = "No file was returned by the browser.";
            return;
        }
        fileName = result.fileName == null ? "schematic.litematic" : result.fileName;
        try {
            schematic = LitematicNbtReader.readGzip(new ByteArrayInputStream(result.fileData));
            scroll = 0;
            status = "Loaded successfully. Schematic is read-only in this initial port.";
        } catch (IOException | RuntimeException ex) {
            schematic = null;
            status = "Could not read .litematic: " + safeMessage(ex);
            EagRuntime.showPopup(status);
        }
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isEmpty() ? error.getClass().getSimpleName() : message;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (schematic != null) {
            int count = schematic.regions().size();
            scroll = Math.max(0, Math.min(Math.max(0, count - 1), scroll - (int)Math.signum(scrollY) * 2));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        extractMenuBackground(graphics);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int center = width / 2;
        graphics.text(font, title, center - font.width(title) / 2, 12, 0xFFFFFFFF, true);
        graphics.text(font, fileName, 12, 32, 0xFFFFFFFF, true);
        graphics.text(font, status, 12, 44, 0xFFFFD070, true);

        if (schematic == null) {
            graphics.text(font, "Browser-compatible Litematica port", 12, 72, 0xFFDDDDDD, true);
            graphics.text(font, "Imports compressed .litematic NBT without Fabric or Mixins.", 12, 86, 0xFFBBBBBB, true);
            graphics.text(font, "This first port inspects schematics; world placement/render preview is not implemented yet.", 12, 100, 0xFFBBBBBB, true);
            return;
        }

        graphics.text(font, "Regions: " + schematic.regions().size(), 12, 62, 0xFF80E0A0, true);
        graphics.text(font, "Total blocks: " + schematic.blockCount(), 12, 74, 0xFF80E0A0, true);
        graphics.text(font, "Scroll to browse regions", 12, 88, 0xFFBBBBBB, true);

        List<Region> regions = schematic.regions();
        int y = 104;
        int visible = Math.max(1, (height - 150) / 38);
        int end = Math.min(regions.size(), scroll + visible);
        for (int i = scroll; i < end; i++) {
            Region region = regions.get(i);
            String line = region.name + "  [" + region.sizeX + " × " + region.sizeY + " × " + region.sizeZ + "]";
            graphics.text(font, line, 12, y, 0xFFFFFFFF, true);
            graphics.text(font, "Origin " + region.x + ", " + region.y + ", " + region.z
                    + " · palette " + region.palette().size() + " states", 20, y + 12, 0xFFB8B8B8, true);
            y += 36;
        }
    }

    @Override
    public void onClose() {
        EagRuntime.clearFileChooserResult();
        minecraft.gui.setScreen(parent);
    }
}
