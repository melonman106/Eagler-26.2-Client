package com.melonman106.vbvclient;

import java.util.Random;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Native friends/party UI. Friend database is intentionally deferred; party data is local for now. */
public final class VBVFriendsScreen extends Screen {
    private final Screen parent;
    private EditBox playerBox;
    private String code;

    public VBVFriendsScreen(Screen parent) {
        super(Component.literal("Party"));
        this.parent = parent;
        this.code = VBVParty.code();
    }

    @Override
    protected void init() {
        super.init();
        int w = Math.min(360, Math.max(260, width - 32));
        int x = width / 2 - w / 2;
        playerBox = new EditBox(font, x, 86, w, 20, Component.literal("Player username"));
        addRenderableWidget(playerBox);
        addRenderableWidget(Button.builder(Component.literal("ADD PLAYER"), b -> addPlayer())
            .bounds(x, 112, w / 2 - 3, 22).build());
        addRenderableWidget(Button.builder(Component.literal("FRIENDS"), b -> {})
            .bounds(x + w / 2 + 3, 112, w / 2 - 3, 22).build());
        addRenderableWidget(Button.builder(Component.literal("GENERATE PARTY CODE"), b -> { code = VBVParty.newCode(); })
            .bounds(x, 140, w, 22).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> VBVNav.open(parent))
            .bounds(x, height - 34, w, 22).build());
    }

    private void addPlayer() {
        String name = playerBox.getValue().trim();
        if (!name.isEmpty()) {
            VBVParty.addPlayer(name);
            playerBox.setValue("");
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xFF101010);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        String title = "PARTY";
        graphics.text(font, title, width / 2 - font.width(title) / 2, 28, 0xFFFFFFFF, true);
        graphics.text(font, "Party code: " + code, width / 2 - 80, 54, 0xFF55FFAA, true);
        String[] players = VBVParty.players();
        int y = 174;
        for (String player : players) {
            // The head slot is reserved here; the skin renderer can be connected once
            // the Eagler skin texture cache is exposed to VBV.
            graphics.fill(width / 2 - 150, y - 2, width / 2 - 126, y + 22, 0xFF303030);
            graphics.text(font, player, width / 2 - 116, y + 4, 0xFFFFFFFF, true);
            y += 26;
        }
        if (players.length == 0) graphics.text(font, "No party members yet.", width / 2 - 58, y, 0xFF888888, false);
    }

    @Override
    public void onClose() { VBVNav.open(parent); }
}
