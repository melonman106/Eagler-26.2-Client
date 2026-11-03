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
    public VBVDungeonsTitleScreen() { super(); }

    @Override protected void init() {
        super.init();
        VBVClient.init();
        EaglerProfile.readIfNeeded();
        clearWidgets();

        // Dungeons-style left navigation: one dominant action followed by compact utility rows.
        int left = Math.max(28, width / 22);
        int menuWidth = Math.min(270, Math.max(220, width / 4));
        int smallWidth = (menuWidth - 6) / 2;
        int buttonHeight = 24;
        int gap = 6;
        int y = Math.max(154, height / 2 - 18);

        addNativeButton("PLAY", left, y, menuWidth, buttonHeight,
            b -> VBVNav.openMultiplayer(this));
        y += buttonHeight + gap;
        addNativeButton("SINGLEPLAYER", left, y, smallWidth, buttonHeight,
            b -> VBVNav.openSingleplayer(this));
        addNativeButton("PARTY", left + smallWidth + gap, y, smallWidth, buttonHeight,
            b -> VBVNav.open(new VBVPartyScreen(this)));
        y += buttonHeight + gap;
        addNativeButton("HERO", left, y, smallWidth, buttonHeight,
            b -> VBVNav.open(new net.lax1dude.eaglercraft.v1_8.profile.EaglerProfileScreen26(this)));
        addNativeButton("MODS", left + smallWidth + gap, y, smallWidth, buttonHeight,
            b -> VBVNav.open(new VBVModsScreen(this)));
        y += buttonHeight + gap;
        addNativeButton("OPTIONS", left, y, smallWidth, buttonHeight,
            b -> VBVNav.openOptions(this));
        addNativeButton("STORAGE", left + smallWidth + gap, y, smallWidth, buttonHeight,
            b -> VBVNav.open(new VBVStorageScreen(this)));
    }

    private void addNativeButton(String label, int x, int y, int w, int h, Button.OnPress action) {
        addRenderableWidget(Button.builder(Component.literal(label), action).bounds(x, y, w, h).build());
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        EaglerProfile.readIfNeeded();

        // Cinematic dark panels keep the panorama visible while giving the menu the layered Dungeons look.
        int left = Math.max(28, width / 22);
        int menuWidth = Math.min(270, Math.max(220, width / 4));
        int menuTop = Math.max(148, height / 2 - 24);
        graphics.fill(left - 14, menuTop - 18, left + menuWidth + 14, Math.min(height - 34, menuTop + 146), 0x66000000);
        graphics.fill(left - 10, menuTop - 14, left + menuWidth + 10, menuTop - 10, 0xAAFFFFFF);

        // Small status/profile area, similar to the player-facing corner UI used by Dungeons.
        String name = EaglerProfile.getName();
        if (name == null || name.isEmpty()) name = "Player";
        Identifier skin = EaglerProfile.getActiveSkinResourceLocation();
        int nameWidth = font.width(name);
        int textX = width - 16 - nameWidth;
        int headX = textX - 25;
        int headY = 12;
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

        // Menu section labels and a subtle footer keep the screen readable without covering the world.
        graphics.text(font, "ADVENTURE", left, menuTop - 30, 0xFFFFFFFF, true);
        graphics.text(font, "EXPLORE THE WORLD", left + 82, menuTop - 30, 0xFFCCCCCC, false);
        graphics.text(font, "VBV CLIENT  •  EAGLERCRAFT 26.2", left, height - 18, 0xCCFFFFFF, false);
        graphics.text(font, "PROFILE", width - 72, height - 18, 0xCCFFFFFF, false);
    }
}
