package com.melonman106.vbvclient;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.lax1dude.eaglercraft.v1_8.profile.EaglerProfile;
import net.minecraft.network.chat.Component;

/** VBV home screen styled after the cinematic Minecraft Dungeons II menu layout. */
public final class VBVDungeonsTitleScreen extends TitleScreen {
    private int layoutWidth = -1;
    private int layoutHeight = -1;

    public VBVDungeonsTitleScreen() { super(); }

    @Override protected void init() {
        super.init();
        VBVClient.init();
        VBVParty.initialize();
        EaglerProfile.readIfNeeded();
        clearWidgets();

        // Dungeons-inspired vertical navigation with one prominent Play action.
        int left = Math.max(28, width / 22);
        int menuWidth = Math.min(280, Math.max(220, width / 4));
        int primaryHeight = 30;
        int buttonHeight = 24;
        int gap = 5;
        int totalHeight = primaryHeight + 6 * buttonHeight + 6 * gap;
        int y = Math.max(64, (height - totalHeight) / 2);

        addNativeButton("PLAY", left, y, menuWidth, primaryHeight,
            b -> VBVNav.openMultiplayer(this));
        y += primaryHeight + gap;
        addNativeButton("SINGLEPLAYER", left, y, menuWidth, buttonHeight,
            b -> VBVNav.openSingleplayer(this));
        y += buttonHeight + gap;
        addNativeButton("PARTY", left, y, menuWidth, buttonHeight,
            b -> VBVNav.open(new VBVPartyScreen(this)));
        y += buttonHeight + gap;
        addNativeButton("HERO", left, y, menuWidth, buttonHeight,
            b -> VBVNav.open(new net.lax1dude.eaglercraft.v1_8.profile.EaglerProfileScreen26(this)));
        y += buttonHeight + gap;
        addNativeButton("MODS", left, y, menuWidth, buttonHeight,
            b -> VBVNav.open(new VBVModsScreen(this)));
        y += buttonHeight + gap;
        addNativeButton("OPTIONS", left, y, menuWidth, buttonHeight,
            b -> VBVNav.openOptions(this));
        y += buttonHeight + gap;
        addNativeButton("STORAGE", left, y, menuWidth, buttonHeight,
            b -> VBVNav.open(new VBVStorageScreen(this)));
        layoutWidth = width;
        layoutHeight = height;
    }

    private void addNativeButton(String label, int x, int y, int w, int h, Button.OnPress action) {
        addRenderableWidget(Button.builder(Component.literal(label), action).bounds(x, y, w, h).build());
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int currentWidth = graphics.guiWidth();
        int currentHeight = graphics.guiHeight();
        if (currentWidth != layoutWidth || currentHeight != layoutHeight) {
            // Browser fullscreen can resize the canvas without a normal window event.
            // Rebuild all button bounds against the new GUI dimensions before drawing.
            this.init(currentWidth, currentHeight);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        EaglerProfile.readIfNeeded();

        // Recompute the same anchor used by init(), including after browser fullscreen changes.
        int left = Math.max(28, width / 22);
        int menuWidth = Math.min(280, Math.max(220, width / 4));
        int primaryHeight = 30;
        int buttonHeight = 24;
        int gap = 5;
        int totalHeight = primaryHeight + 6 * buttonHeight + 6 * gap;
        int menuTop = Math.max(64, (height - totalHeight) / 2);
        Identifier logo = Identifier.parse("minecraft:textures/gui/title/minecraft.png");
        graphics.blit(RenderPipelines.GUI_TEXTURED, logo, left, Math.max(8, menuTop - 78),
            0, 0, menuWidth, 68, 256, 64, 256, 64);
        graphics.text(font, "YOUR ADVENTURE STARTS HERE", left, menuTop - 8, 0xFFDDDDDD, true);

        // Small status/profile area, similar to the player-facing corner UI used by Dungeons.
        String name = EaglerProfile.getName();
        if (name == null || name.isEmpty()) name = "Player";
        Identifier skin = EaglerProfile.getActiveSkinResourceLocation();
        int nameWidth = font.width(name);
        int textX = width - 16 - nameWidth;
        int headX = textX - 25;
        int headY = 12;
        graphics.fill(headX - 5, headY - 4, width - 10, headY + 25, 0x66000000);
        graphics.blit(RenderPipelines.GUI_TEXTURED, skin, headX, headY, 8, 8, 20, 20, 8, 8, 64, 64);
        graphics.blit(RenderPipelines.GUI_TEXTURED, skin, headX, headY, 40, 8, 20, 20, 8, 8, 64, 64);
        graphics.text(font, name, textX, headY + 6, 0xFFFFFFFF, true);

        String[] party = VBVParty.players();
        int py = headY + 28;
        if (party.length > 0) {
            graphics.fill(width - 188, py - 4, width - 10, py + Math.min(4, party.length) * 22 + 2, 0x55000000);
            graphics.text(font, "PARTY", width - 176, py, 0xFFFFFFFF, true);
            py += 14;
            for (int i = 0; i < Math.min(4, party.length); i++) {
                graphics.text(font, party[i], width - 166, py, 0xFFE8E8E8, false);
                py += 18;
            }
        }

        // A small edition label anchors the lower edge without obscuring the panorama.
        graphics.text(font, "VBV EDITION  •  26.2", left, height - 18, 0xFFB8B8B8, false);
    }
}
