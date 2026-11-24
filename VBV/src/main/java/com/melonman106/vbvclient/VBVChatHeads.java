package com.melonman106.vbvclient;

import com.mojang.authlib.GameProfile;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * Chat Heads: draws the sender's face beside each player chat line.
 *
 * <p>ChatListener hands over the sender profile just before a player message is shown,
 * and ChatComponent tags the GuiMessage it creates. The render hook reserves an 11 px
 * gutter in front of the chat text, so a head never covers a message. System output and
 * command feedback have no sender and simply leave the gutter empty.</p>
 */
public final class VBVChatHeads {
    /** Face size in GUI pixels. */
    public static final int HEAD = 8;
    /** Space reserved in front of the chat text: one pixel margin, the head, two pixels gap. */
    public static final int GUTTER = 11;

    public static boolean enabled = true;

    private static final Map<GuiMessage, GameProfile> SENDERS = new IdentityHashMap<>();
    private static GameProfile pending;

    private VBVChatHeads() {}

    public static void setPendingSender(GameProfile sender) {
        pending = sender;
    }

    /** Tags a freshly created chat message, then drops the pending sender. */
    public static void attach(GuiMessage message, boolean playerMessage) {
        if (playerMessage && pending != null) {
            SENDERS.put(message, pending);
            if (SENDERS.size() > 512) SENDERS.clear();
        }
        pending = null;
    }

    public static boolean hasSender(GuiMessage message) {
        return SENDERS.containsKey(message);
    }

    public static void render(GuiGraphicsExtractor graphics, GuiMessage message, int x, int y, int size, int color) {
        GameProfile profile = SENDERS.get(message);
        if (profile == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        PlayerSkin skin = null;
        if (minecraft.player != null && profile.id().equals(minecraft.player.getUUID())) {
            skin = minecraft.player.getSkin();
        } else if (minecraft.getConnection() != null) {
            PlayerInfo info = minecraft.getConnection().getPlayerInfo(profile.id());
            if (info != null) skin = info.getSkin();
        }
        if (skin == null) skin = DefaultPlayerSkin.get(profile);
        PlayerFaceExtractor.extractRenderState(graphics, skin, x, y, size, color);
    }
}
