package com.melonman106.vbvclient;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Local-only party editor; network invitations are not connected yet. */
public final class VBVPartyScreen extends Screen {
    private final Screen parent;
    private EditBox playerName;

    public VBVPartyScreen(Screen parent) {
        super(Component.literal("Party"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        playerName = new EditBox(font, width / 2 - 110, 112, 220, 20,
            Component.literal("Player name"));
        playerName.setHint(Component.literal("Enter a player name"));
        addRenderableWidget(playerName);
        addRenderableWidget(Button.builder(Component.literal("ADD PLAYER"), button -> {
            String name = playerName.getValue().trim();
            if (!name.isEmpty()) {
                VBVParty.addPlayer(name);
                playerName.setValue("");
            }
        }).bounds(width / 2 - 110, 140, 220, 20).build());
        addRenderableWidget(Button.builder(Component.literal("NEW PARTY CODE"),
            button -> VBVParty.newCode()).bounds(width / 2 - 110, 168, 220, 20).build());
        addRenderableWidget(Button.builder(Component.literal("BACK"),
            button -> VBVNav.open(parent)).bounds(width / 2 - 55, height - 34, 110, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xFF101010);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        String heading = "PARTY";
        graphics.text(font, heading, width / 2 - font.width(heading) / 2, 30, 0xFFFFFFFF, true);
        graphics.text(font, "Local party code: " + VBVParty.code(), width / 2 - 110, 64, 0xFFDDDDDD, true);
        graphics.text(font, "Local members", width / 2 - 110, 88, 0xFFAAAAAA, false);
        String[] members = VBVParty.players();
        for (int i = 0; i < Math.min(5, members.length); i++) {
            graphics.text(font, members[i], width / 2 - 100, 198 + i * 14, 0xFFFFFFFF, false);
        }
        graphics.text(font, "This prototype stores members in this browser only.",
            width / 2 - 140, height - 55, 0xFF888888, false);
    }

    @Override
    public void onClose() {
        VBVNav.open(parent);
    }
}
