package com.melonman106.vbvclient;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Network-backed party manager with invite codes and host moderation. */
public final class VBVPartyScreen extends Screen {
    private static final int MEMBERS_TOP = 242;
    private static final int MEMBER_ROW_HEIGHT = 19;
    private final Screen parent;
    private EditBox playerName;
    private EditBox partyCode;

    public VBVPartyScreen(Screen parent) {
        super(Component.literal("Party"));
        this.parent = parent;
        VBVParty.initialize();
    }

    @Override
    protected void init() {
        super.init();
        int x = width / 2 - 110;
        playerName = new EditBox(font, x, 66, 220, 20, Component.literal("Minecraft username"));
        playerName.setHint(Component.literal("Your Minecraft username"));
        partyCode = new EditBox(font, x, 124, 220, 20, Component.literal("Party code"));
        partyCode.setHint(Component.literal("Enter a six-character code"));
        partyCode.setMaxLength(6);
        addRenderableWidget(playerName);
        addRenderableWidget(partyCode);

        addRenderableWidget(Button.builder(Component.literal("CREATE PARTY"), button ->
            VBVParty.create(playerName.getValue().trim()))
            .bounds(x, 92, 220, 22).build());
        addRenderableWidget(Button.builder(Component.literal("JOIN PARTY"), button ->
            VBVParty.join(partyCode.getValue().trim(), playerName.getValue().trim()))
            .bounds(x, 150, 220, 22).build());
        addRenderableWidget(Button.builder(Component.literal("LEAVE"), button ->
            VBVParty.leave()).bounds(x, 180, 106, 20).build());
        addRenderableWidget(Button.builder(Component.literal("DISBAND"), button ->
            VBVParty.disband()).bounds(x + 114, 180, 106, 20).build());
        addRenderableWidget(Button.builder(Component.literal("REFRESH"), button ->
            VBVParty.poll()).bounds(width / 2 - 50, height - 32, 100, 20).build());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubleClick) {
        if (super.mouseClicked(click, doubleClick)) return true;
        if (!VBVParty.isHost() || click.x() < width / 2 + 70) return false;

        int index = (int)((click.y() - MEMBERS_TOP) / MEMBER_ROW_HEIGHT);
        if (click.y() < MEMBERS_TOP || index < 0) return false;
        String[] rows = VBVParty.memberRows();
        if (index >= rows.length) return false;
        String[] fields = rows[index].split("\\t", -1);
        if (fields.length < 3 || "1".equals(fields[2])) return false;
        if (click.x() <= width / 2 + 130) {
            VBVParty.kick(fields[1]);
            return true;
        }
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xFF101010);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        String heading = "PARTY";
        graphics.text(font, heading, width / 2 - font.width(heading) / 2, 24, 0xFFFFFFFF, true);
        String status = VBVParty.status();
        graphics.text(font, status, width / 2 - font.width(status) / 2, 210, 0xFFAAAAAA, false);

        String code = VBVParty.code();
        if (!code.isEmpty()) {
            String invite = "INVITE CODE  " + code;
            graphics.text(font, invite, width / 2 - font.width(invite) / 2, 42, 0xFF55FFAA, true);
        } else {
            graphics.text(font, "Create a party or join with a friend's code.", width / 2 - 130, 42, 0xFFAAAAAA, false);
        }

        String[] rows = VBVParty.memberRows();
        graphics.text(font, "PARTY MEMBERS  (" + rows.length + "/8)", width / 2 - 140, 226, 0xFFFFFFFF, true);
        for (int i = 0; i < Math.min(rows.length, 16); i++) {
            String[] fields = rows[i].split("\\t", -1);
            String name = fields.length > 0 ? fields[0] : "Unknown";
            String id = fields.length > 1 ? fields[1] : "";
            boolean host = fields.length > 2 && "1".equals(fields[2]);
            int y = MEMBERS_TOP + i * MEMBER_ROW_HEIGHT;
            graphics.fill(width / 2 - 140, y - 2, width / 2 + 140, y + 16, 0x55303030);
            graphics.text(font, name + (host ? "  (HOST)" : ""), width / 2 - 132, y + 2,
                host ? 0xFFFFD966 : 0xFFFFFFFF, true);
            if (VBVParty.isHost() && !host && !id.isEmpty()) {
                graphics.text(font, "KICK", width / 2 + 88, y + 2, 0xFFFF7777, true);
            }
        }
        if (rows.length == 0) {
            graphics.text(font, "No connected party members.", width / 2 - 82, MEMBERS_TOP + 4, 0xFF888888, false);
        }
        if (VBVParty.isHost()) {
            graphics.text(font, "As host, click KICK beside a player to remove them.", width / 2 - 150,
                height - 50, 0xFF999999, false);
        }
    }

    @Override
    public void onClose() {
        VBVNav.open(parent);
    }
}
